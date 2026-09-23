import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp, rm, stat, writeFile} from 'node:fs/promises';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const jar = path.join(root, 'target', 'detour-0.1.0-SNAPSHOT.jar');
await stat(jar).catch(() => { throw new Error('Build first with .\\mvnw.cmd clean verify.'); });
const temp = await mkdtemp(path.join(os.tmpdir(), 'detour-release-'));
const log = path.join(temp, 'application.log');
const database = path.join(temp, 'release').replaceAll('\\', '/');
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const env = Object.fromEntries(Object.entries(process.env).filter(([name]) => !/OPENAI|ANTHROPIC|MODEL|API_KEY|TOKEN|SECRET/i.test(name)));
// Windows process termination is immediate; flush each committed test write before restart.
env.DETOUR_DATABASE_URL = `jdbc:h2:file:${database};DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0`;
env.DETOUR_SECURE_COOKIES = 'false';

async function freePort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

let child;
let output = '';
async function start() {
  const port = await freePort();
  env.DETOUR_PORT = String(port);
  child = spawn(java, ['-jar', jar, '--detour.clock.fixed-instant=2027-02-01T12:00:00Z'], {cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
  child.stdout.on('data', chunk => { output += chunk.toString(); });
  child.stderr.on('data', chunk => { output += chunk.toString(); });
  const base = `http://127.0.0.1:${port}`;
  for (let attempt = 0; attempt < 100; attempt++) {
    if (child.exitCode !== null) throw new Error(`Packaged app exited during startup. See ${log}`);
    try {
      const response = await fetch(base);
      if (response.status === 200 && (await response.text()).includes('DeTour')) return base;
    } catch { /* startup in progress */ }
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  throw new Error(`Packaged app did not become ready. See ${log}`);
}

async function stop() {
  if (!child) return;
  if (child.exitCode === null) {
    child.kill();
    await Promise.race([
      new Promise(resolve => child.once('exit', resolve)),
      new Promise(resolve => setTimeout(resolve, 5000)),
    ]);
    if (child.exitCode === null) child.kill('SIGKILL');
  }
  child = undefined;
}

function cookie(response, name) {
  const value = response.headers.getSetCookie().find(item => item.startsWith(`${name}=`));
  assert.ok(value, `Missing ${name} cookie`);
  return value.split(';', 1)[0];
}

async function request(base, route, {method = 'GET', session, csrf, body, expected = 200} = {}) {
  const headers = {};
  if (session || csrf) headers.Cookie = [session, csrf].filter(Boolean).join('; ');
  if (body) headers['Content-Type'] = 'application/json';
  if (method !== 'GET' && csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf.split('=')[1]);
  const response = await fetch(base + route, {method, headers, body: body && JSON.stringify(body)});
  const raw = await response.text();
  assert.equal(response.status, expected, `${method} ${route}: ${response.status} ${raw}`);
  return {response, data: raw ? JSON.parse(raw) : null};
}

try {
  let base = await start();
  const rootResponse = await fetch(base);
  const csrf = cookie(rootResponse, 'XSRF-TOKEN');
  const email = 'release-check@example.test';
  const password = 'release-check-password';
  const registered = await request(base, '/api/auth/register', {method: 'POST', csrf, body: {email, password}, expected: 201});
  const session = cookie(registered.response, 'JSESSIONID');
  const created = (await request(base, '/api/trips', {method: 'POST', session, csrf, body: {
    destinationKey: 'destination-sfo', startDate: '2027-03-10', endDate: '2027-03-14',
    travelerCount: 1, travelerAges: [30], budgetCents: 500000,
  }, expected: 201})).data;
  const tripId = created.id;
  const draftId = created.drafts[0].id;
  await request(base, `/api/trips/${tripId}/revisions`, {method: 'POST', session, csrf, body: {expectedVersion: 0}, expected: 404});
  await request(base, `/api/trips/${tripId}/drafts/${draftId}/car`, {session, expected: 404});
  const options = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/airfare`, {session})).data.options;
  assert.ok(options.length > 0, 'Expected a seeded airfare combination');
  const selected = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/airfare`, {method: 'PUT', session, csrf, body: {
    expectedVersion: created.version, expectedDraftVersion: created.drafts[0].version,
    outboundFlightInstanceId: options[0].outbound.flightInstanceId,
    returnFlightInstanceId: options[0].returnFlight.flightInstanceId,
  }})).data;
  const readiness = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/readiness`, {session})).data;
  assert.equal(readiness.ready, true, JSON.stringify(readiness));
  const planned = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/plan`, {method: 'POST', session, csrf, body: {
    expectedVersion: selected.version, expectedDraftVersion: selected.drafts[0].version,
    budgetOverageAcknowledged: true,
  }, expected: 201})).data;
  assert.equal(planned.planned.length, 1);
  const booking = (await request(base, `/api/trips/${tripId}/bookings`, {method: 'POST', session, csrf, body: {
    plannedItineraryId: planned.planned[0].id, expectedVersion: planned.version, idempotencyKey: 'release-smoke-booking',
  }, expected: 201})).data;
  assert.ok(booking.bookingReference);
  const replay = (await request(base, `/api/trips/${tripId}/bookings`, {method: 'POST', session, csrf, body: {
    plannedItineraryId: planned.planned[0].id, expectedVersion: planned.version, idempotencyKey: 'release-smoke-booking',
  }, expected: 200})).data;
  assert.equal(replay.bookingReference, booking.bookingReference);
  const afterBooking = (await request(base, `/api/trips/${tripId}`, {session})).data;
  await request(base, `/api/trips/${tripId}/bookings/${booking.id}/cancel`, {method: 'POST', session, csrf, body: {expectedVersion: afterBooking.version}});
  const beforeRestart = (await request(base, `/api/trips/${tripId}/bookings`, {session})).data;
  assert.equal(beforeRestart[0].status, 'CANCELED');
  await stop();

  base = await start();
  await request(base, '/api/profile', {session, expected: 401});
  const secondRoot = await fetch(base);
  const newCsrf = cookie(secondRoot, 'XSRF-TOKEN');
  const login = await request(base, '/api/auth/login', {method: 'POST', csrf: newCsrf, body: {email, password}, expected: 204});
  const newSession = cookie(login.response, 'JSESSIONID');
  const persisted = (await request(base, `/api/trips/${tripId}`, {session: newSession})).data;
  assert.equal(persisted.planned[0].id, planned.planned[0].id);
  const history = (await request(base, `/api/trips/${tripId}/bookings`, {session: newSession})).data;
  assert.equal(history.length, 1);
  assert.equal(history[0].bookingReference, booking.bookingReference);
  assert.equal(history[0].status, 'CANCELED');
  assert.ok(history[0].canceledAt);
  assert.equal(history[0].grandTotalCents, booking.grandTotalCents);
  assert.equal(history[0].airfareReference, booking.airfareReference);
  assert.equal(history[0].selections.airfare.outboundFlightInstanceId, booking.selections.airfare.outboundFlightInstanceId);
  const other = await request(base, '/api/auth/register', {method: 'POST', csrf: newCsrf, body: {
    email: 'other-release-check@example.test', password,
  }, expected: 201});
  await request(base, `/api/trips/${tripId}`, {session: cookie(other.response, 'JSESSIONID'), expected: 404});
  console.log('PASS packaged JAR: clean migration, registration, airfare plan, idempotent booking, cancellation, restart, new login, persisted snapshots/history, owner isolation; no model credentials.');
} catch (error) {
  await writeFile(log, output);
  console.error(error);
  console.error(`Packaged application log: ${log}`);
  process.exitCode = 1;
} finally {
  await stop();
  if (process.exitCode !== 1) await rm(temp, {recursive: true, force: true});
}
