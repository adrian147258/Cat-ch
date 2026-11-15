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
res=await call('/email-otp/request-password-reset',{email:'alice@example.test'});assert.equal(res.status,200);assert.equal(mail.at(-1).type,'forget-password');const resetOtp=mail.at(-1).otp;
res=await call('/email-otp/reset-password',{email:'alice@example.test',otp:'000000',password:'new-fake-password'});assert.notEqual(res.status,200);
res=await call('/email-otp/reset-password',{email:'alice@example.test',otp:resetOtp,password:'new-fake-password'});assert.equal(res.status,200);
assert.equal(await auth.api.getSession({headers:new Headers({Authorization:'Bearer '+token})}),null,'Password reset must revoke old sessions');
res=await call('/email-otp/reset-password',{email:'alice@example.test',otp:resetOtp,password:'another-password'});assert.notEqual(res.status,200);
res=await call('/sign-in/email',{email:'alice@example.test',password:'a-fake-test-password'});assert.notEqual(res.status,200);
res=await call('/sign-in/email',{email:'alice@example.test',password:'new-fake-password'});assert.equal(res.status,200);
const before=mail.length;res=await call('/email-otp/request-password-reset',{email:'missing@example.test'});assert.equal(res.status,200);assert.equal(mail.length,before);
// Exercise the real provider routing with a fake transport, without sending email.
const sent=[],commands=[],originalFetch=globalThis.fetch;let failSmtp=false;
const fakeConnect=(address,options)=>{assert.deepEqual(address,{hostname:'smtp.qq.com',port:465});assert.equal(options.secureTransport,'on');let chunks=['220 ready\r\n','250-server\r\n250 AUTH LOGIN\r\n','334 username\r\n','334 password\r\n',failSmtp?'535 denied\r\n':'235 ok\r\n','250 ok\r\n','250 ok\r\n','354 data\r\n','250 queued\r\n'];return {readable:new ReadableStream({pull(c){if(chunks.length)c.enqueue(new TextEncoder().encode(chunks.shift()));else c.close();}}),writable:new WritableStream({write(c){commands.push(new TextDecoder().decode(c));}}),close:async()=>{}};};
globalThis.fetch=async(url,options)=>{sent.push({url,body:JSON.parse(options.body)});return Response.json({id:'fake'});};
try{
 const routed=makeAuth({DB:db,BETTER_AUTH_SECRET:'test-only-not-a-real-key-1234567890123456789',AUTH_BASE_URL:'http://localhost:8787',BREVO_API_KEY:'fake',BREVO_SENDER_EMAIL:'test@example.test',QQ_SMTP_USER:'123456@qq.com',QQ_SMTP_PASSWORD:'test-authorization-code'},null,fakeConnect);
 await routed.api.sendVerificationOTP({body:{email:'alice@example.test',type:'sign-in'}});
 await routed.api.requestPasswordResetEmailOTP({body:{email:'alice@example.test'}});
 assert.deepEqual(sent.map(x=>x.url),['https://api.brevo.com/v3/smtp/email']);assert.ok(commands.includes('RCPT TO:<alice@example.test>\r\n'));assert.ok(commands.some(c=>c.includes('From: Cat-ch <123456@qq.com>')));
 failSmtp=true;
 // Better Auth intentionally keeps the outward response generic, even on mail failure.
 assert.equal((await routed.api.requestPasswordResetEmailOTP({body:{email:'alice@example.test'}})).success,true);
 assert.equal(sent.length,1,'Reset failure must not fall back to Brevo');
}finally{globalThis.fetch=originalFetch;}
console.log('Passed signup, verification, wrong/correct password, bearer session, OTP login, OTP replay prevention');
