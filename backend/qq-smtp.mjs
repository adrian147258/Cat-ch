// QQ Mail SMTP submission over implicit TLS; credentials never leave the Worker.
export async function sendResetEmail(env,email,otp,connectOverride){
 const user=env.QQ_SMTP_USER,password=env.QQ_SMTP_PASSWORD;
 const address=/^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+$/;
 if(!user||!address.test(user)||!password||/[\r\n]/.test(password)||!address.test(email)||!/^\d{6}$/.test(otp))throw new Error('QQ SMTP is not configured');
 const connect=connectOverride||(await import('cloudflare:sockets')).connect;
 const socket=connect({hostname:'smtp.qq.com',port:465},{secureTransport:'on'}),reader=socket.readable.getReader(),writer=socket.writable.getWriter();
 const encoder=new TextEncoder(),decoder=new TextDecoder();let pending='',timer;
 const readReply=async()=>{
  let code='';
  while(true){
   let end=pending.indexOf('\r\n');
   while(end<0){const {value,done}=await reader.read();if(done)throw new Error('SMTP connection closed');pending+=decoder.decode(value,{stream:true});if(pending.length>16384)throw new Error('SMTP response too large');end=pending.indexOf('\r\n');}
   const line=pending.slice(0,end);pending=pending.slice(end+2);const match=/^(\d{3})([ -])/.exec(line);if(!match||code&&code!==match[1])throw new Error('Invalid SMTP response');code=match[1];if(match[2]===' ')return Number(code);
  }
 };
 const expect=async(...codes)=>{if(!codes.includes(await readReply()))throw new Error('SMTP server rejected request');};
 const send=async command=>writer.write(encoder.encode(command+'\r\n'));
 const base64=value=>btoa(String.fromCharCode(...encoder.encode(value)));
 const sendMail=async()=>{
  await expect(220);await send('EHLO catch.local');await expect(250);
  await send('AUTH LOGIN');await expect(334);await send(base64(user));await expect(334);await send(base64(password));await expect(235);
  await send('MAIL FROM:<'+user+'>');await expect(250);await send('RCPT TO:<'+email+'>');await expect(250,251);await send('DATA');await expect(354);
  const subject='=?UTF-8?B?'+base64('Cat-ch 重置密码验证码')+'?=';
  const content='您的重置密码验证码是：'+otp+'。验证码 10 分钟内有效。如果不是您本人操作，请忽略此邮件。';
  await send('From: Cat-ch <'+user+'>\r\nTo: <'+email+'>\r\nSubject: '+subject+'\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n'+base64(content)+'\r\n.');await expect(250);
 };
 try{await Promise.race([sendMail(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('SMTP connection timed out')),15000);})]);}
 finally{clearTimeout(timer);try{await socket.close()}catch{}try{reader.releaseLock()}catch{}try{writer.releaseLock()}catch{}}
}
