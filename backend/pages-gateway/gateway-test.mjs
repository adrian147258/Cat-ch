import assert from 'node:assert/strict';
import test from 'node:test';
import gateway from './_worker.mjs';

test('forwards an authenticated request and rewrites the gateway origin', async () => {
  const body = new Uint8Array([1, 2, 3, 4]);
  const request = new Request('https://cat-ch-api.pages.dev/posts?limit=5', {
    method: 'POST',
    headers: {
      origin: 'https://cat-ch-api.pages.dev',
      authorization: 'Bearer test-token',
      'content-type': 'image/jpeg',
      'x-meow-breed': encodeURIComponent('布偶')
    },
    body
  });
  const response = await gateway.fetch(request, {
    CATCH_BACKEND: {
      async fetch(forwarded) {
        assert.equal(forwarded.url, 'https://meowclub-api.meowclub-eb2c8.workers.dev/posts?limit=5');
        assert.equal(forwarded.method, 'POST');
        assert.equal(forwarded.headers.get('origin'), 'https://meowclub-api.meowclub-eb2c8.workers.dev');
        assert.equal(forwarded.headers.get('authorization'), 'Bearer test-token');
        assert.equal(forwarded.headers.get('x-meow-breed'), encodeURIComponent('布偶'));
        assert.deepEqual(new Uint8Array(await forwarded.arrayBuffer()), body);
        return Response.json({ ok: true }, { status: 201 });
      }
    }
  });
  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { ok: true });
});

test('does not make an unrelated browser origin trusted', async () => {
  const request = new Request('https://cat-ch-api.pages.dev/api/auth/sign-in/email', {
    headers: { origin: 'https://untrusted.example' }
  });
  await gateway.fetch(request, {
    CATCH_BACKEND: {
      async fetch(forwarded) {
        assert.equal(forwarded.headers.get('origin'), 'https://untrusted.example');
        return new Response(null, { status: 403 });
      }
    }
  });
});
