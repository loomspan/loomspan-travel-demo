import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {identityApi, IdentityApiError} from './identityApi';

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});

describe('identityApi', () => {
  const fetchMock = vi.fn();
  beforeEach(() => { vi.stubGlobal('fetch', fetchMock); document.cookie = 'XSRF-TOKEN=csrf-value; path=/'; });
  afterEach(() => { vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; max-age=0; path=/'; fetchMock.mockReset(); });

  it('sends only the csrf cookie token for unsafe identity requests', async () => {
    fetchMock.mockResolvedValueOnce(json(201, {email: 'ada@example.test'})).mockResolvedValueOnce(new Response(null, {status: 204}));
    await identityApi.register('ada@example.test', 'aaaaaaaaaaaa');
    await identityApi.login('ada@example.test', 'aaaaaaaaaaaa');
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/register', expect.objectContaining({credentials: 'same-origin', headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-value'}}));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/login', expect.objectContaining({credentials: 'same-origin', headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-value'}}));
  });

  it('normalizes csrf and malformed responses to safe outcomes', async () => {
    document.cookie = 'XSRF-TOKEN=; max-age=0; path=/';
    await expect(identityApi.logout()).rejects.toMatchObject({kind: 'csrf-missing'} satisfies Partial<IdentityApiError>);
    expect(fetchMock).not.toHaveBeenCalled();
    fetchMock.mockResolvedValueOnce(new Response('not json', {status: 500}));
    await expect(identityApi.getProfile()).rejects.toMatchObject({kind: 'unexpected', status: 500} satisfies Partial<IdentityApiError>);
  });

  it('rejects malformed successful profile payloads', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {email: 42}));
    await expect(identityApi.getProfile()).rejects.toMatchObject({kind: 'unexpected'} satisfies Partial<IdentityApiError>);
    fetchMock.mockResolvedValueOnce(json(200, {email: ' '}));
    await expect(identityApi.getProfile()).rejects.toMatchObject({kind: 'unexpected'} satisfies Partial<IdentityApiError>);
  });
});
