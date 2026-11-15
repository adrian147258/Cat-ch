import { makeAuth } from './auth.mjs';
import { postImages, UploadError } from './post-upload.mjs';
const MAX_IMAGE = 5_000_000;
const MAX_POSTS = 100_000;
const DAILY_PER_USER = 20;
const cors={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS','Access-Control-Allow-Headers':'Authorization,Content-Type,X-Meow-Name,X-Meow-Text,X-Meow-Breed,X-Legacy-Token'};
const BREEDS = new Set(['橘猫','布偶','狸花','英短','美短','三花','其它']);
const headers={'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store',...cors};
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers});
const fail=(message,status=400)=>json({error:message},status);
const digest=async token=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token))),v=>v.toString(16).padStart(2,'0')).join('');
async function user(req,env,auth){const session=await auth.api.getSession({headers:req.headers});return session?.user?.emailVerified?session.user:null;}
function decodeHeader(v,max){try{return decodeURIComponent(v||'').trim().slice(0,max)}catch{return ''}}
function typeOfImage(bytes){if(bytes.length>3&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'image/jpeg';if(bytes.length>8&&bytes[0]===137&&bytes[1]===80&&bytes[2]===78&&bytes[3]===71&&bytes[4]===13&&bytes[5]===10&&bytes[6]===26&&bytes[7]===10)return 'image/png';if(bytes.length>12&&String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP')return 'image/webp';return null;}
function coverPath(userId,coverKey){
 if(!coverKey)return null;
 return '/profile-covers/'+encodeURIComponent(userId)+'?v='+encodeURIComponent(coverKey.split('/').pop());
}
async function readImage(req){
 const size=Number(req.headers.get('Content-Length')||0);
 if(size>MAX_IMAGE)return {error:fail('图片超过 5MB，请换一张',413)};
 if(!req.body)return {error:fail('没有收到图片')};
 const chunks=[];let total=0;const reader=req.body.getReader();
 while(true){
  const {done,value}=await reader.read();if(done)break;
  total+=value.byteLength;
  if(total>MAX_IMAGE){await reader.cancel();return {error:fail('图片超过 5MB，请换一张',413)};}
  chunks.push(value);
 }
 if(!total)return {error:fail('没有收到图片')};
 const bytes=new Uint8Array(total);let offset=0;
 for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 const type=typeOfImage(bytes);
 if(!type)return {error:fail('只支持 JPG、PNG 和 WebP 图片')};
 return {bytes,type};
}
async function readSmallJson(req){
 if(Number(req.headers.get('Content-Length')||0)>4096)return {error:fail('请求过大',413)};
 if(!req.body)return {error:fail('请求格式不正确')};
 const reader=req.body.getReader(),chunks=[];let total=0;
 while(true){
  const {done,value}=await reader.read();if(done)break;
  total+=value.byteLength;
  if(total>4096){await reader.cancel();return {error:fail('请求过大',413)};}
  chunks.push(value);
 }
 const bytes=new Uint8Array(total);let offset=0;
 for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 try{return {value:JSON.parse(new TextDecoder().decode(bytes))};}
 catch{return {error:fail('请求格式不正确')};}
}
async function handle(req,env){
 const u=new URL(req.url),path=u.pathname;if(req.method==='OPTIONS')return new Response(null,{status:204,headers:cors});
 if(req.method==='GET'&&path==='/health')return json({ok:true,service:'meowclub',version:1});
 const auth=makeAuth(env);if(path.startsWith('/api/auth/'))return auth.handler(req);
 const match=path.match(/^\/images\/(cloud_[0-9a-f-]{36})(?:\/([1-8]))?$/);
 if(req.method==='GET'&&match){const post=match[2]?await env.DB.prepare('SELECT object_key,content_type FROM post_images WHERE post_id=? AND position=?').bind(match[1],Number(match[2])).first():await env.DB.prepare('SELECT object_key,content_type FROM posts WHERE id=?').bind(match[1]).first();if(!post)return fail('图片不存在',404);const obj=await env.PICTURES.get(post.object_key);if(!obj)return fail('图片不存在',404);return new Response(obj.body,{headers:{'Content-Type':post.content_type,'Cache-Control':'public,max-age=300','X-Content-Type-Options':'nosniff',...cors}});}
 const coverMatch=path.match(/^\/profile-covers\/([A-Za-z0-9_-]{1,128})$/);
 if(req.method==='GET'&&coverMatch){
  const profile=await env.DB.prepare('SELECT cover_key,cover_type FROM profiles WHERE user_id=?').bind(coverMatch[1]).first();
  if(!profile?.cover_key)return fail('封面不存在',404);
  const object=await env.PICTURES.get(profile.cover_key);
  if(!object)return fail('封面不存在',404);
  return new Response(object.body,{headers:{'Content-Type':profile.cover_type,'Cache-Control':'public,max-age=300','X-Content-Type-Options':'nosniff',...cors}});
 }
 const me=await user(req,env,auth);if(!me)return fail('身份已失效，请重新打开应用',401);
 if(path==='/profile'&&req.method==='GET'){
  const profile=await env.DB.prepare('SELECT signature,cover_key FROM profiles WHERE user_id=?').bind(me.id).first();
  return json({id:me.id,name:me.name,signature:profile?.signature||'',cover:coverPath(me.id,profile?.cover_key)});
 }
 if(path==='/profile'&&req.method==='PUT'){
  const parsed=await readSmallJson(req);if(parsed.error)return parsed.error;
  const input=parsed.value;
  if(typeof input?.signature!=='string')return fail('签名格式不正确');
  const signature=input.signature.trim();
  if(Array.from(signature).length>120)return fail('签名最多 120 字');
  await env.DB.prepare(`INSERT INTO profiles(user_id,signature,updated_at) VALUES(?,?,?)
   ON CONFLICT(user_id) DO UPDATE SET signature=excluded.signature,updated_at=excluded.updated_at`).bind(me.id,signature,Date.now()).run();
  return json({signature});
 }
 if(path==='/profile/cover'&&req.method==='PUT'){
  const image=await readImage(req);if(image.error)return image.error;
  const key='profile-covers/'+me.id+'/'+crypto.randomUUID();
  await env.PICTURES.put(key,image.bytes,{httpMetadata:{contentType:image.type}});
  let previous=null,updated=false;
  try{
   await env.DB.prepare('INSERT OR IGNORE INTO profiles(user_id,updated_at) VALUES(?,?)').bind(me.id,Date.now()).run();
   for(let attempt=0;attempt<3&&!updated;attempt++){
    const profile=await env.DB.prepare('SELECT cover_key FROM profiles WHERE user_id=?').bind(me.id).first();
    previous=profile?.cover_key||null;
    const result=await env.DB.prepare('UPDATE profiles SET cover_key=?,cover_type=?,updated_at=? WHERE user_id=? AND cover_key IS ?').bind(key,image.type,Date.now(),me.id,previous).run();
    updated=!!result.meta.changes;
   }
   if(!updated)throw new Error('Concurrent cover update');
  }catch(error){await env.PICTURES.delete(key);throw error;}
  if(previous){try{await env.PICTURES.delete(previous)}catch(error){console.error(JSON.stringify({event:'cover_cleanup_failed'}));}}
  return json({id:me.id,cover:coverPath(me.id,key)});
 }
 const profileMatch=path.match(/^\/profiles\/([A-Za-z0-9_-]{1,128})$/);
 if(req.method==='GET'&&profileMatch){
  const owner=await env.DB.prepare('SELECT id,name FROM "user" WHERE id=? AND emailVerified=1').bind(profileMatch[1]).first();
  if(!owner)return fail('猫友不存在',404);
  const profile=await env.DB.prepare('SELECT signature,cover_key FROM profiles WHERE user_id=?').bind(owner.id).first();
  const before=u.searchParams.get('before'),beforeId=u.searchParams.get('before_id');
  if((before===null)!==(beforeId===null))return fail('作品分页参数不正确');
  if(before!==null&&(!/^\d+$/.test(before)||!Number.isSafeInteger(Number(before))||!/^cloud_[0-9a-f-]{36}$/.test(beforeId)))return fail('作品分页参数不正确');
  const query=before===null
   ?env.DB.prepare("SELECT p.id,p.name,p.text,p.breed,p.created_at,1+(SELECT count(*) FROM post_images i WHERE i.post_id=p.id) AS image_count,(SELECT count(*) FROM reactions r WHERE r.post_id=p.id AND r.kind='like') AS like_count FROM posts p WHERE p.owner_id=? ORDER BY p.created_at DESC,p.id DESC LIMIT 51").bind(owner.id)
   :env.DB.prepare("SELECT p.id,p.name,p.text,p.breed,p.created_at,1+(SELECT count(*) FROM post_images i WHERE i.post_id=p.id) AS image_count,(SELECT count(*) FROM reactions r WHERE r.post_id=p.id AND r.kind='like') AS like_count FROM posts p WHERE p.owner_id=? AND (p.created_at<? OR (p.created_at=? AND p.id<?)) ORDER BY p.created_at DESC,p.id DESC LIMIT 51").bind(owner.id,Number(before),Number(before),beforeId);
  const result=await query.all(),items=result.results.slice(0,50);
  const count=await env.DB.prepare('SELECT COUNT(*) AS count FROM posts WHERE owner_id=?').bind(owner.id).first();
  const last=items[items.length-1];
  // Return only the viewer's own button states, never the profile owner's reaction history.
  const own=items.length?(await env.DB.prepare(`SELECT post_id,kind FROM reactions WHERE user_id=? AND post_id IN (${items.map(()=>'?').join(',')})`).bind(me.id,...items.map(p=>p.id)).all()).results:[];
  const liked=new Set(own.filter(x=>x.kind==='like').map(x=>x.post_id)),saved=new Set(own.filter(x=>x.kind==='save').map(x=>x.post_id));
  return json({id:owner.id,name:owner.name,signature:profile?.signature||'',cover:coverPath(owner.id,profile?.cover_key),post_count:count?.count||0,next:result.results.length>50?{before:last.created_at,before_id:last.id}:null,posts:items.map(p=>({id:p.id,name:p.name,text:p.text,breed:p.breed,created_at:p.created_at,image_count:p.image_count,like_count:p.like_count,liked:liked.has(p.id),saved:saved.has(p.id),image:'/images/'+p.id}))});
 }
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
  const r=await env.DB.prepare(`SELECT p.id,p.name,p.text,p.breed,p.owner_id,p.created_at,u.id AS author_id,
   EXISTS(SELECT 1 FROM reactions x WHERE x.post_id=p.id AND x.user_id=? AND x.kind='like') AS liked,
   EXISTS(SELECT 1 FROM reactions x WHERE x.post_id=p.id AND x.user_id=? AND x.kind='save') AS saved,
   (SELECT count(*) FROM reactions x WHERE x.post_id=p.id AND x.kind='like') AS like_count,
   1+(SELECT count(*) FROM post_images i WHERE i.post_id=p.id) AS image_count
   FROM posts p LEFT JOIN "user" u ON u.id=p.owner_id AND u.emailVerified=1 ORDER BY p.created_at DESC,p.id DESC LIMIT 100`).bind(me.id,me.id).all();
  return json({posts:r.results.map(p=>({id:p.id,name:p.name,text:p.text,breed:p.breed,author_id:p.author_id,mine:p.owner_id===me.id,liked:!!p.liked,saved:!!p.saved,like_count:p.like_count,image_count:p.image_count,created_at:p.created_at,image:'/images/'+p.id}))});
 }
 if(req.method==='POST'&&path==='/posts'){
  const rawBreed=req.headers.get('X-Meow-Breed');
  const breed=rawBreed===null?null:decodeHeader(rawBreed,24);
  // Older clients do not send a breed; preserve compatibility without guessing.
  if(rawBreed!==null&&!BREEDS.has(breed))return fail('请选择有效的小猫种类');
  if(/^multipart\/form-data(?:;|$)/i.test(req.headers.get('Content-Type')||'')){
   if(!BREEDS.has(breed))return fail('请选择有效的小猫种类');
   const id='cloud_'+crypto.randomUUID(),name=decodeHeader(req.headers.get('X-Meow-Name'),24)||'猫猫爱好者',text=decodeHeader(req.headers.get('X-Meow-Text'),200)||'今日份小猫 🐾',now=Date.now(),keys=[],imgs=[];let committed=false;
   try{
    for await(const bytes of postImages(req,MAX_IMAGE)){
     const type=typeOfImage(bytes);if(!type)return fail('只支持 JPG、PNG 和 WebP 图片');
     const index=imgs.length,key='posts/'+id+(index?'/'+index:'');keys.push(key);
     await env.PICTURES.put(key,bytes,{httpMetadata:{contentType:type}});imgs.push({type,size:bytes.length});
    }
    const statements=[env.DB.prepare('INSERT INTO posts(id,owner_id,name,text,breed,object_key,content_type,size_bytes,created_at) SELECT ?,?,?,?,?,?,?,?,? WHERE (SELECT uploads FROM quota WHERE id=1) < ? AND (SELECT COUNT(*) FROM posts WHERE owner_id=? AND created_at>?) < ?').bind(id,me.id,name,text,breed,keys[0],imgs[0].type,imgs[0].size,now,MAX_POSTS,me.id,now-86400000,DAILY_PER_USER)];
    for(let i=1;i<imgs.length;i++)statements.push(env.DB.prepare('INSERT INTO post_images(post_id,position,object_key,content_type,size_bytes) SELECT ?,?,?,?,? WHERE EXISTS (SELECT 1 FROM posts WHERE id=?)').bind(id,i,keys[i],imgs[i].type,imgs[i].size,id));
    const results=await env.DB.batch(statements);committed=!!results[0].meta.changes;if(!committed)return fail('发布已达测试额度，请稍后再试',429);return json({id},201);
   }catch(error){if(error instanceof UploadError)return fail(error.message,error.status);throw error;}finally{if(!committed)for(const key of keys)try{await env.PICTURES.delete(key)}catch{}}
  }
  const image=await readImage(req);if(image.error)return image.error;
  const {bytes,type}=image,n=bytes.length;
  const id='cloud_'+crypto.randomUUID(),key='posts/'+id;const name=decodeHeader(req.headers.get('X-Meow-Name'),24)||'猫猫爱好者',text=decodeHeader(req.headers.get('X-Meow-Text'),200)||'今日份小猫 🐾';const now=Date.now();
  await env.PICTURES.put(key,bytes,{httpMetadata:{contentType:type}});
  try{const result=await env.DB.prepare(`INSERT INTO posts(id,owner_id,name,text,breed,object_key,content_type,size_bytes,created_at)
   SELECT ?,?,?,?,?,?,?,?,? WHERE (SELECT uploads FROM quota WHERE id=1) < ? AND (SELECT COUNT(*) FROM posts WHERE owner_id=? AND created_at>?) < ?`).bind(id,me.id,name,text,breed,key,type,n,now,MAX_POSTS,me.id,now-86400000,DAILY_PER_USER).run();if(!result.meta.changes){await env.PICTURES.delete(key);return fail('发布已达测试额度，请稍后再试',429);}}
  catch(e){await env.PICTURES.delete(key);throw e;}return json({id},201);
 }
 const react=path.match(/^\/posts\/(cloud_[0-9a-f-]{36})\/(like|save)$/);
 if(req.method==='PUT'&&react){const body=await req.json().catch(()=>null);if(!body||typeof body.active!=='boolean')return fail('请求格式不正确');const p=await env.DB.prepare('SELECT id FROM posts WHERE id=?').bind(react[1]).first();if(!p)return fail('帖子不存在',404);if(body.active)await env.DB.prepare('INSERT OR IGNORE INTO reactions(post_id,user_id,kind) VALUES(?,?,?)').bind(react[1],me.id,react[2]).run();else await env.DB.prepare('DELETE FROM reactions WHERE post_id=? AND user_id=? AND kind=?').bind(react[1],me.id,react[2]).run();const count=await env.DB.prepare("SELECT count(*) AS n FROM reactions WHERE post_id=? AND kind='like'").bind(react[1]).first();return json({ok:true,like_count:count.n});}
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
 if(req.method==='DELETE'&&del){const p=await env.DB.prepare('SELECT object_key FROM posts WHERE id=? AND owner_id=?').bind(del[1],me.id).first();if(!p)return fail('没有权限或帖子不存在',404);const extras=await env.DB.prepare('SELECT object_key FROM post_images WHERE post_id=?').bind(del[1]).all();await env.DB.batch([env.DB.prepare('DELETE FROM comments WHERE post_id=?').bind(del[1]),env.DB.prepare('DELETE FROM reactions WHERE post_id=?').bind(del[1]),env.DB.prepare('DELETE FROM post_images WHERE post_id=?').bind(del[1]),env.DB.prepare('DELETE FROM posts WHERE id=? AND owner_id=?').bind(del[1],me.id)]);await env.PICTURES.delete(p.object_key);for(const image of extras.results)await env.PICTURES.delete(image.object_key);return json({ok:true});}
 return fail('接口不存在',404);
}
export default {async fetch(req,env){try{return await handle(req,env)}catch(e){console.error(JSON.stringify({event:'request_error',message:String(e)}));return fail('服务暂时不可用，请稍后重试',500)}}};
