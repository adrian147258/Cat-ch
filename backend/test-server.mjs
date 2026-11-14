import { DatabaseSync } from 'node:sqlite';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import { Readable } from 'node:stream';
import worker from './worker.mjs';

const db = new DatabaseSync(':memory:');
for (const file of ['schema.sql', 'auth-schema.sql', 'comments-migration.sql', 'cards-migration.sql']) {
  db.exec(await readFile(new URL(file, import.meta.url), 'utf8'));
}

class Statement {
  constructor(sql) { this.sql = sql; this.args = []; }
  bind(...args) { this.args = args; return this; }
  async first() { return db.prepare(this.sql).get(...this.args) ?? null; }
  async all() {
    const results = db.prepare(this.sql).all(...this.args);
    const changes = Number(db.prepare('SELECT changes() AS count').get().count);
    return { results, meta: { changes, last_row_id: 0 } };
  }
  async run() {
    const result = db.prepare(this.sql).run(...this.args);
    return { meta: { changes: Number(result.changes), last_row_id: Number(result.lastInsertRowid) } };
  }
}

const objects = new Map();
const env = {
  AUTH_BASE_URL: 'http://localhost:8765',
  BETTER_AUTH_SECRET: 'local-test-only-secret-12345678901234567890',
  BREVO_API_KEY: 'local-test-only',
  BREVO_SENDER_EMAIL: 'local@example.test',
  DB: {
    exec: async sql => db.exec(sql),
    prepare: sql => new Statement(sql),
    batch: async statements => {
      db.exec('BEGIN');
      try {
        const results = [];
        for (const statement of statements) results.push(await statement.all());
        db.exec('COMMIT');
        return results;
      } catch (error) {
        db.exec('ROLLBACK');
        throw error;
      }
    }
  },
  PICTURES: {
    put: async (key, value) => objects.set(key, value),
    get: async key => objects.has(key) ? { body: objects.get(key) } : null,
    delete: async key => objects.delete(key)
  }
};

// Local QA prints verification mail and never contacts the email provider.
globalThis.fetch = async (url, options) => {
  if (url !== 'https://api.brevo.com/v3/smtp/email') throw new Error('Unexpected local QA fetch');
  const mail = JSON.parse(options.body);
  console.log('Local verification mail:', mail.to[0].email, mail.textContent);
  return Response.json({ messageId: 'local-test' });
};

http.createServer(async (req, res) => {
  try {
    if (req.url === '/__qa') {
      const count = table => Number(db.prepare('SELECT COUNT(*) AS count FROM ' + table).get().count);
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({
        posts: count('posts'),
        likes: Number(db.prepare("SELECT COUNT(*) AS count FROM reactions WHERE kind='like'").get().count),
        saves: Number(db.prepare("SELECT COUNT(*) AS count FROM reactions WHERE kind='save'").get().count),
        users: count('user')
      }));
      return;
    }
    const body = ['GET', 'HEAD'].includes(req.method) ? undefined : Readable.toWeb(req);
    const request = new Request(env.AUTH_BASE_URL + req.url, {
      method: req.method,
      headers: req.headers,
      ...(body ? { body, duplex: 'half' } : {})
    });
    const response = await worker.fetch(request, env);
    res.writeHead(response.status, Object.fromEntries(response.headers.entries()));
    res.end(Buffer.from(await response.arrayBuffer()));
  } catch (error) {
    console.error(error);
    res.writeHead(500);
    res.end('error');
  }
}).listen(8765, '127.0.0.1', () => console.log('Local QA server ready at http://localhost:8765'));
