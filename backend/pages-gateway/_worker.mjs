const BACKEND_ORIGIN = 'https://meowclub-api.meowclub-eb2c8.workers.dev';

export default {
  async fetch(request, env) {
    const incomingUrl = new URL(request.url);
    const backendUrl = new URL(incomingUrl.pathname + incomingUrl.search, BACKEND_ORIGIN);
    const headers = new Headers(request.headers);

    // Better Auth trusts the backend origin. Only translate the gateway's own
    // origin; unrelated origins must still be checked by the backend.
    if (headers.get('origin') === incomingUrl.origin) {
      headers.set('origin', BACKEND_ORIGIN);
    }
    headers.delete('host');

    const forwarded = new Request(backendUrl.toString(), request);
    return env.CATCH_BACKEND.fetch(new Request(forwarded, { headers }));
  }
};
