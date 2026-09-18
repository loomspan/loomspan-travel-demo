export type Profile = {email: string};

type ErrorEnvelope = {code?: unknown; message?: unknown; fields?: unknown};

export class IdentityApiError extends Error {
  constructor(
    public readonly kind: 'api' | 'network' | 'unexpected' | 'csrf-missing',
    public readonly status?: number,
    public readonly code?: string,
    public readonly fields: Record<string, string> = {},
  ) {
    super('Identity request failed');
  }
}

function csrfToken(): string | undefined {
  return document.cookie.split('; ').find((value) => value.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length);
}

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const unsafe = method !== 'GET';
  const token = unsafe ? csrfToken() : undefined;
  if (unsafe && !token) throw new IdentityApiError('csrf-missing');

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: unsafe ? {'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(token!)} : undefined,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new IdentityApiError('network');
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T;
    try { return await response.json() as T; } catch { throw new IdentityApiError('unexpected', response.status); }
  }

  let envelope: ErrorEnvelope = {};
  try { envelope = await response.json() as ErrorEnvelope; } catch { throw new IdentityApiError('unexpected', response.status); }
  const fields = typeof envelope.fields === 'object' && envelope.fields !== null
    ? Object.fromEntries(Object.entries(envelope.fields).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
    : {};
  throw new IdentityApiError('api', response.status, typeof envelope.code === 'string' ? envelope.code : undefined, fields);
}

export const identityApi = {
  getProfile: async (): Promise<Profile> => {
    const profile = await request<unknown>('/api/profile');
    const email = typeof profile === 'object' && profile !== null ? (profile as {email?: unknown}).email : undefined;
    if (typeof email !== 'string' || !email.trim()) {
      throw new IdentityApiError('unexpected');
    }
    return {email};
  },
  register: (email: string, password: string) => request<Profile>('/api/auth/register', 'POST', {email, password}),
  login: (email: string, password: string) => request<void>('/api/auth/login', 'POST', {email, password}),
  logout: () => request<void>('/api/auth/logout', 'POST'),
  changePassword: (currentPassword: string, newPassword: string) => request<void>('/api/profile/password', 'PUT', {currentPassword, newPassword}),
};
