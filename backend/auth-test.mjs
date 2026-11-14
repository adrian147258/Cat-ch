import assert from 'node:assert/strict';import {DatabaseSync} from 'node:sqlite';import {readFile} from 'node:fs/promises';import {makeAuth} from './auth.mjs';
const db=new DatabaseSync(':memory:');db.exec(await readFile('auth-schema.sql','utf8'));let mail=[];const auth=makeAuth({DB:db,BETTER_AUTH_SECRET:'test-only-not-a-real-key-1234567890123456789',AUTH_BASE_URL:'http://localhost:8787'},async m=>mail.push(m));
async function call(path,body,headers={}){return auth.handler(new Request('http://localhost:8787/api/auth'+path,{method:'POST',headers:{'content-type':'application/json',...headers},body:JSON.stringify(body)}));}
let res=await call('/sign-up/email',{name:'Test User',email:'alice@example.test',password:'a-fake-test-password'});console.log('signup:',res.status, await res.clone().text(),'emails:',mail.length);assert.equal(res.status,200);assert.equal(mail.length,1);assert.equal(mail[0].type,'email-verification');
res=await call('/sign-in/email',{email:'alice@example.test',password:'a-fake-test-password'});assert.equal(res.status,403);
res=await call('/email-otp/verify-email',{email:'alice@example.test',otp:'000000'});assert.notEqual(res.status,200);
res=await call('/email-otp/verify-email',{email:'alice@example.test',otp:mail[0].otp});console.log('verify:',res.status,await res.clone().text(),Boolean(res.headers.get('set-auth-token')));assert.equal(res.status,200);
res=await call('/sign-in/email',{email:'alice@example.test',password:'incorrect-password'});assert.notEqual(res.status,200);
res=await call('/sign-in/email',{email:'alice@example.test',password:'a-fake-test-password'});console.log('password:',res.status,Boolean(res.headers.get('set-auth-token')));assert.equal(res.status,200);const token=res.headers.get('set-auth-token');assert.ok(token);
const session=await auth.api.getSession({headers:new Headers({Authorization:'Bearer '+token})});assert.equal(session.user.email,'alice@example.test');
res=await call('/email-otp/send-verification-otp',{email:'alice@example.test',type:'sign-in'});assert.equal(res.status,200);const otp=mail.at(-1).otp;
res=await call('/sign-in/email-otp',{email:'alice@example.test',otp});console.log('otp:',res.status,Boolean(res.headers.get('set-auth-token')));assert.equal(res.status,200);assert.ok(res.headers.get('set-auth-token'));
res=await call('/sign-in/email-otp',{email:'alice@example.test',otp});assert.notEqual(res.status,200);
console.log('Passed signup, verification, wrong/correct password, bearer session, OTP login, OTP replay prevention');
