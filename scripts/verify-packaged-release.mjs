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
  await request(base, '/api/trips', {method: 'POST', csrf, body: {
    name: 'Spring getaway', destinationKey: 'destination-sfo', startDate: '2027-03-10', endDate: '2027-03-14',
    travelerCount: 1, travelerAges: [30],
  }, expected: 401});
  const registered = await request(base, '/api/auth/register', {method: 'POST', csrf, body: {email, password}, expected: 201});
  const session = cookie(registered.response, 'JSESSIONID');
  const created = (await request(base, '/api/trips', {method: 'POST', session, csrf, body: {
    name: 'Spring getaway',
    destinationKey: 'destination-sfo', startDate: '2027-03-10', endDate: '2027-03-14',
    travelerCount: 1, travelerAges: [30],
  }, expected: 201})).data;
  const tripId = created.id;
  assert.equal(created.name, 'Spring getaway');
  assert.equal(created.drafts.length, 1);
  assert.equal(created.planned.length, 0);
  const draftId = created.workingPlan.id;
  await request(base, `/api/trips/${tripId}/revisions`, {method: 'POST', session, csrf, body: {expectedVersion: 0}, expected: 404});
  await request(base, `/api/trips/${tripId}/drafts/${draftId}/car`, {session, expected: 404});
  const options = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/airfare`, {session})).data.options;
  assert.ok(options.length > 0, 'Expected a seeded airfare combination');
  const selected = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/airfare`, {method: 'PUT', session, csrf, body: {
    expectedVersion: created.version, expectedDraftVersion: created.workingPlan.version,
    outboundFlightInstanceId: options[0].outbound.flightInstanceId,
    returnFlightInstanceId: options[0].returnFlight.flightInstanceId,
  }})).data;
  const readiness = (await request(base, `/api/trips/${tripId}/drafts/${draftId}/readiness`, {session})).data;
  assert.equal(readiness.blockingIssues.budgetCents, 'Provide a budget before planning.');
  await request(base, `/api/trips/${tripId}/drafts/${draftId}/plan`, {method: 'POST', session, csrf, body: {
    expectedVersion: selected.version, expectedDraftVersion: selected.workingPlan.version,
  }, expected: 409});
  const firstOption = (await request(base, `/api/trips/${tripId}/plans/${draftId}/copy`, {method: 'POST', session, csrf, body: {
    name: 'Early departure', expectedVersion: selected.version, expectedPlanVersion: selected.plans[0].version,
  }, expected: 201})).data;
  const laterId = firstOption.plans[1].id;
  const changedDates = (await request(base, `/api/trips/${tripId}/plans/${laterId}`, {method: 'PUT', session, csrf, body: {
    expectedVersion: firstOption.version, expectedPlanVersion: firstOption.plans[1].version,
    startDate: '2027-03-15', endDate: '2027-03-19', travelerCount: 2, travelerAges: [30, 35],
  }})).data;
  assert.equal(changedDates.primaryPlanId, draftId);
  assert.equal(changedDates.plans[0].startDate, '2027-03-10');
  assert.equal(changedDates.plans[0].travelerCount, 1);
  const laterFlights = (await request(base, `/api/trips/${tripId}/plans/${laterId}/airfare`, {session})).data.options;
  assert.ok(laterFlights.length > 0, 'Expected seeded airfare on later dates');
  const laterSelected = (await request(base, `/api/trips/${tripId}/plans/${laterId}/airfare`, {method: 'PUT', session, csrf, body: {
    expectedVersion: changedDates.version, expectedDraftVersion: changedDates.plans[1].version,
    outboundFlightInstanceId: laterFlights[0].outbound.flightInstanceId,
    returnFlightInstanceId: laterFlights[0].returnFlight.flightInstanceId,
  }})).data;
  const planned = (await request(base, `/api/trips/${tripId}/plans/${laterId}/name`, {method: 'PUT', session, csrf, body: {
    name: 'Later departure', expectedVersion: laterSelected.version, expectedPlanVersion: laterSelected.plans[1].version,
  }})).data;
  await request(base, `/api/trips/${tripId}/plans/${laterId}/primary`, {method: 'PUT', session, body: {
    expectedVersion: planned.version, expectedPlanVersion: planned.plans[1].version,
  }, expected: 403});
  const booking = (await request(base, `/api/trips/${tripId}/bookings`, {method: 'POST', session, csrf, body: {
    plannedItineraryId: laterId, expectedVersion: planned.version, idempotencyKey: 'release-smoke-booking',
  }, expected: 201})).data;
  assert.equal(booking.purchasedTravelerCount, 2);
  const replay = (await request(base, `/api/trips/${tripId}/bookings`, {method: 'POST', session, csrf, body: {
    plannedItineraryId: laterId, expectedVersion: planned.version, idempotencyKey: 'release-smoke-booking',
  }, expected: 200})).data;
  assert.equal(replay.bookingReference, booking.bookingReference);
  const afterBooking = (await request(base, `/api/trips/${tripId}`, {session})).data;
  const promoted = (await request(base, `/api/trips/${tripId}/plans/${laterId}/primary`, {method: 'PUT', session, csrf, body: {
    expectedVersion: afterBooking.version, expectedPlanVersion: afterBooking.plans[1].version,
  }})).data;
  assert.equal(promoted.primaryPlanId, laterId);
  const edited = (await request(base, `/api/trips/${tripId}/plans/${laterId}`, {method: 'PUT', session, csrf, body: {
    expectedVersion: promoted.version, expectedPlanVersion: promoted.plans[0].version,
    startDate: '2027-03-16', endDate: '2027-03-20', travelerCount: 3, travelerAges: [30, 35, 8],
  }})).data;
  assert.deepEqual(edited.booking, booking);
  await request(base, `/api/trips/${tripId}/plans/${laterId}/airfare`, {method: 'DELETE', session, csrf, body: {
    expectedVersion: edited.version, expectedDraftVersion: edited.plans[0].version,
  }, expected: 409});
  const copied = (await request(base, `/api/trips/${tripId}/plans/${laterId}/copy`, {method: 'POST', session, csrf, body: {
    name: 'Unconfirmed copy', expectedVersion: edited.version, expectedPlanVersion: edited.plans[0].version,
  }, expected: 201})).data;
  assert.equal(copied.plans[2].booked, false);
  assert.deepEqual(copied.plans[2].lockedComponents, []);
  await request(base, `/api/trips/${tripId}/bookings/${booking.id}/cancel`, {method: 'POST', session, csrf, body: {expectedVersion: copied.version}});
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
  assert.equal(persisted.plans.length, 3);
  assert.equal(persisted.primaryPlanId, laterId);
  assert.equal(persisted.plans[0].name, 'Later departure');
  assert.equal(persisted.plans[0].startDate, '2027-03-16');
  assert.equal(persisted.plans[0].travelerCount, 3);
  assert.equal(persisted.booking.purchasedStartDate, '2027-03-15');
  assert.equal(persisted.booking.purchasedTravelerCount, 2);
  await request(base, `/api/trips/${tripId}/plans/${laterId}`, {method: 'DELETE', session: newSession, csrf: newCsrf, body: {
    expectedVersion: persisted.version, expectedPlanVersion: persisted.plans[0].version,
    confirmed: true, expectedPlanCount: 3, replacementPrimaryPlanId: draftId,
  }, expected: 409});
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
  console.log('PASS packaged JAR: public write protection, registration, canonical primary, independent dates/party, copy without purchases, booked promotion, confirmed locks, CSRF, idempotent booking, cancellation, restart, persisted snapshots/history, owner isolation; no model credentials.');
} catch (error) {
  await writeFile(log, output);
  console.error(error);
  console.error(`Packaged application log: ${log}`);
  process.exitCode = 1;
} finally {
  await stop();
  if (process.exitCode !== 1) await rm(temp, {recursive: true, force: true});
}
