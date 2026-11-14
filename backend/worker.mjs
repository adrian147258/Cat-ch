import { makeAuth } from './auth.mjs';
const MAX_IMAGE = 2_000_000;
const MAX_POSTS = 1000;
const DAILY_PER_USER = 20;
const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'Authorization,Content-Type,X-Meow-Name,X-Meow-Text,X-Legacy-Token'};
const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store',...cors};
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers});
const fail=(message,status=400)=>json({error:message},status);
const digest=async token=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token))),v=>v.toString(16).padStart(2,'0')).join('');
async function user(req,env,auth){const session=await auth.api.getSession({headers:req.headers});return session?.user?.emailVerified?session.user:null;}
function decodeHeader(v,max){try{return decodeURIComponent(v||'').trim().slice(0,max)}catch{return ''}}
function typeOfImage(bytes){if(bytes.length>3&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'image/jpeg';if(bytes.length>8&&bytes[0]===137&&bytes[1]===80&&bytes[2]===78&&bytes[3]===71&&bytes[4]===13&&bytes[5]===10&&bytes[6]===26&&bytes[7]===10)return 'image/png';if(bytes.length>12&&String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP')return 'image/webp';return null;}
async function handle(req,env){
 const u=new URL(req.url),path=u.pathname;if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
 if(req.method==='GET'&&path==='/health')return json({ok:true,service:'meowclub',version:1});
 const auth=makeAuth(env);if(path.startsWith('/api/auth/'))return auth.handler(req);
 const match=path.match(/^\/images\/(cloud_[0-9a-f-]{36})$/);
 if(req.method==='GET'&&match){const post=await env.DB.prepare('SELECT object_key,content_type FROM posts WHERE id=?').bind(match[1]).first();if(!post)return fail('图片不存在',404);const obj=await env.PICTURES.get(post.object_key);if(!obj)return fail('图片不存在',404);return new Response(obj.body,{headers:{'Content-Type':post.content_type,'Cache-Control':'public,max-age=300','X-Content-Type-Options':'nosniff',...cors}});}
 const me=await user(req,env,auth);if(!me)return fail('身份已失效，请重新打开应用',401);
 if(path==='/cards' && req.method==='GET'){
  const day=new Date(Date.now()+8*3600000).toISOString().slice(0,10);
  const cards=await env.DB.prepare('SELECT day,tier,card_index,created_at FROM card_draws WHERE user_id=? ORDER BY created_at DESC,day DESC LIMIT 2000').bind(me.id).all();
  return json({day,cards:cards.results,drawn_today:cards.results.some(c=>c.day===day)});
 }
 if(path==='/cards/draw' && req.method==='POST'){
  const now=Date.now(),day=new Date(now+8*3600000).toISOString().slice(0,10);
  const uniform=n=>{const lim=Math.floor(4294967296/n)*n,a=new Uint32Array(1);do{crypto.getRandomValues(a)}while(a[0]>=lim);return a[0]%n};
  const roll=uniform(100),tier=roll<70?'green':roll<90?'blue':roll<98?'purple':'gold';
  const count={green:20,blue:10,purple:8,gold:5}[tier],index=uniform(count);
  const r=await env.DB.prepare('INSERT OR IGNORE INTO card_draws(user_id,day,tier,card_index,created_at) VALUES(?,?,?,?,?)').bind(me.id,day,tier,index,now).run();
  if(!r.meta.changes)return fail('今天已经抽过啦，请明日再来',409);
  return json({day,tier,index,created_at:now},201);
 }
 if(req.method==='POST'&&path==='/migrate-anonymous'){
  const token=req.headers.get('X-Legacy-Token')||'';if(!/^[a-f0-9]{64}$/.test(token))return fail('旧身份无效',400);
  const old=await env.DB.prepare('SELECT id FROM users WHERE token_hash=?').bind(await digest(token)).first();if(!old)return fail('旧身份不存在',404);
  await env.DB.batch([
   env.DB.prepare('UPDATE posts SET owner_id=? WHERE owner_id=?').bind(me.id,old.id),
   env.DB.prepare('INSERT OR IGNORE INTO reactions(post_id,user_id,kind) SELECT post_id,?,kind FROM reactions WHERE user_id=?').bind(me.id,old.id),
   env.DB.prepare('DELETE FROM reactions WHERE user_id=?').bind(old.id),
   env.DB.prepare('DELETE FROM users WHERE id=?').bind(old.id)
  ]);return json({ok:true});
 }
 if(req.method==='GET'&&path==='/posts'){
  const r=await env.DB.prepare(`SELECT p.id,p.name,p.text,p.owner_id,p.created_at,
   EXISTS(SELECT 1 FROM reactions x WHERE x.post_id=p.id AND x.user_id=? AND x.kind='like') AS liked,
   EXISTS(SELECT 1 FROM reactions x WHERE x.post_id=p.id AND x.user_id=? AND x.kind='save') AS saved
   FROM posts p ORDER BY p.created_at DESC,p.id DESC LIMIT 100`).bind(me.id,me.id).all();
  return json({posts:r.results.map(p=>({id:p.id,name:p.name,text:p.text,mine:p.owner_id===me.id,liked:!!p.liked,saved:!!p.saved,created_at:p.created_at,image:'/images/'+p.id}))});
 }
 if(req.method==='POST'&&path==='/posts'){
  const size=Number(req.headers.get('Content-Length')||0);if(size>MAX_IMAGE)return fail('图片超过 2MB，请换一张',413);
  if(!req.body)return fail('没有收到图片');const chunks=[];let n=0;const reader=req.body.getReader();while(true){const {done,value}=await reader.read();if(done)break;n+=value.byteLength;if(n>MAX_IMAGE){await reader.cancel();return fail('图片超过 2MB，请换一张',413);}chunks.push(value)}if(!n)return fail('没有收到图片');const bytes=new Uint8Array(n);let pos=0;for(const c of chunks){bytes.set(c,pos);pos+=c.length}const type=typeOfImage(bytes);if(!type)return fail('只支持 JPG、PNG 和 WebP 图片');
  const id='cloud_'+crypto.randomUUID(),key='posts/'+id;const name=decodeHeader(req.headers.get('X-Meow-Name'),24)||'猫猫爱好者',text=decodeHeader(req.headers.get('X-Meow-Text'),200)||'今日份小猫 🐾';const now=Date.now();
  await env.PICTURES.put(key,bytes,{httpMetadata:{contentType:type}});
  try{const result=await env.DB.prepare(`INSERT INTO posts(id,owner_id,name,text,object_key,content_type,size_bytes,created_at)
   SELECT ?,?,?,?,?,?,?,? WHERE (SELECT uploads FROM quota WHERE id=1) < ? AND (SELECT COUNT(*) FROM posts WHERE owner_id=? AND created_at>?) < ?`).bind(id,me.id,name,text,key,type,n,now,MAX_POSTS,me.id,now-86400000,DAILY_PER_USER).run();if(!result.meta.changes){await env.PICTURES.delete(key);return fail('发布已达测试额度，请稍后再试',429);}}
  catch(e){await env.PICTURES.delete(key);throw e;}return json({id},201);
 }
 const react=path.match(/^\/posts\/(cloud_[0-9a-f-]{36})\/(like|save)$/);
 if(req.method==='PUT'&&react){const body=await req.json().catch(()=>null);if(!body||typeof body.active!=='boolean')return fail('请求格式不正确');const p=await env.DB.prepare('SELECT id FROM posts WHERE id=?').bind(react[1]).first();if(!p)return fail('帖子不存在',404);if(body.active)await env.DB.prepare('INSERT OR IGNORE INTO reactions(post_id,user_id,kind) VALUES(?,?,?)').bind(react[1],me.id,react[2]).run();else await env.DB.prepare('DELETE FROM reactions WHERE post_id=? AND user_id=? AND kind=?').bind(react[1],me.id,react[2]).run();return json({ok:true});}
 const commentsPath=path.match(/^\/posts\/(cloud_[0-9a-f-]{36})\/comments$/);
 if(commentsPath && req.method==='GET'){
  const post=await env.DB.prepare('SELECT id FROM posts WHERE id=?').bind(commentsPath[1]).first();if(!post)return fail('帖子不存在',404);
  const data=await env.DB.prepare('SELECT id,parent_id,name,text,created_at FROM comments WHERE post_id=? ORDER BY created_at ASC,id ASC LIMIT 200').bind(commentsPath[1]).all();return json({comments:data.results});
 }
 if(commentsPath && req.method==='POST'){
  const input=await req.json().catch(()=>null), text=typeof input?.text==='string'?input.text.trim():'';
  if(!text||Array.from(text).length>300)return fail('评论请输入 1 到 300 字');
  if(!await env.DB.prepare('SELECT id FROM posts WHERE id=?').bind(commentsPath[1]).first())return fail('帖子不存在',404);
  const parent=input.parent_id||null;if(parent && (typeof parent!=='string'||!await env.DB.prepare('SELECT id FROM comments WHERE id=? AND post_id=?').bind(parent,commentsPath[1]).first()))return fail('回复的评论不存在',400);
  const id=crypto.randomUUID(),name=String(me.name||'猫猫爱好者').slice(0,24),now=Date.now();
  await env.DB.prepare('INSERT INTO comments(id,post_id,parent_id,owner_id,name,text,created_at) VALUES(?,?,?,?,?,?,?)').bind(id,commentsPath[1],parent,me.id,name,text,now).run();return json({id},201);
 }
 const del=path.match(/^\/posts\/(cloud_[0-9a-f-]{36})$/);
 if(req.method==='DELETE'&&del){const p=await env.DB.prepare('SELECT object_key FROM posts WHERE id=? AND owner_id=?').bind(del[1],me.id).first();if(!p)return fail('没有权限或帖子不存在',404);await env.DB.batch([env.DB.prepare('DELETE FROM comments WHERE post_id=?').bind(del[1]),env.DB.prepare('DELETE FROM reactions WHERE post_id=?').bind(del[1]),env.DB.prepare('DELETE FROM posts WHERE id=? AND owner_id=?').bind(del[1],me.id)]);await env.PICTURES.delete(p.object_key);return json({ok:true});}
 return fail('接口不存在',404);
}
export default {async fetch(req,env){try{return await handle(req,env)}catch(e){console.error(JSON.stringify({event:'request_error',message:String(e)}));return fail('服务暂时不可用，请稍后重试',500)}}};
