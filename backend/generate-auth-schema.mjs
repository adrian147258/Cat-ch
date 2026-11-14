import {DatabaseSync} from 'node:sqlite';
import {getMigrations} from 'better-auth/db/migration';
import {makeAuth} from './auth.mjs';
import {writeFile} from 'node:fs/promises';
const db=new DatabaseSync(':memory:');const auth=makeAuth({DB:db,BETTER_AUTH_SECRET:'test-only-not-a-real-key-1234567890123456789',AUTH_BASE_URL:'http://localhost:8787'},async()=>{});
const opts=(await auth.$context).options;
const migrations=await getMigrations(opts);await writeFile('auth-schema.sql',await migrations.compileMigrations());console.log('Migration plan:',migrations.toBeCreated.map(x=>x.table).join(', '));
