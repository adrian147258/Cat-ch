// Parse one file at a time; never retain the entire nine-image upload in memory.
export class UploadError extends Error {
 constructor(message,status=400){super(message);this.status=status;}
}
export async function* postImages(req,maxImage,maxFiles=9){
 const contentType=req.headers.get('Content-Type')||'';
 const match=/^multipart\/form-data\s*;\s*boundary=(?:"([^"\r\n]+)"|([^;\s]+))\s*$/i.exec(contentType);
 const boundary=match?.[1]||match?.[2];
 if(!boundary||boundary.length>70||!req.body)throw new UploadError('图片格式不正确');
 const enc=new TextEncoder(),first=enc.encode('--'+boundary+'\r\n'),marker=enc.encode('\r\n--'+boundary),separator=enc.encode('\r\n\r\n');
 const maxTotal=maxImage*maxFiles+65536;
 if(Number(req.headers.get('Content-Length')||0)>maxTotal)throw new UploadError('图片总大小超过限制',413);
 const reader=req.body.getReader();let buffer=new Uint8Array(),chunk=null,chunkOffset=0,total=0,ended=false,count=0;
 const indexOf=(hay,needle,start=0)=>{outer:for(let i=start;i<=hay.length-needle.length;i++){for(let j=0;j<needle.length;j++)if(hay[i+j]!==needle[j])continue outer;return i;}return -1;};
 async function more(){
  if(ended)return false;
  if(!chunk||chunkOffset===chunk.length){const next=await reader.read();if(next.done){ended=true;return false;}chunk=next.value;chunkOffset=0;total+=chunk.length;if(total>maxTotal)throw new UploadError('图片总大小超过限制',413);}
  const end=Math.min(chunk.length,chunkOffset+65536),part=chunk.subarray(chunkOffset,end),joined=new Uint8Array(buffer.length+part.length);joined.set(buffer);joined.set(part,buffer.length);buffer=joined;chunkOffset=end;return true;
 }
 async function need(n){while(buffer.length<n)if(!await more())throw new UploadError('图片上传不完整，请重试');}
 try{
  await need(first.length);if(indexOf(buffer,first)!==0)throw new UploadError('图片格式不正确');buffer=buffer.slice(first.length);
  while(true){
   let headEnd;while((headEnd=indexOf(buffer,separator))<0){if(buffer.length>8192)throw new UploadError('图片格式不正确');if(!await more())throw new UploadError('图片上传不完整，请重试');}
   if(headEnd>8192)throw new UploadError('图片格式不正确');
   const header=new TextDecoder().decode(buffer.subarray(0,headEnd));buffer=buffer.slice(headEnd+4);
   const disposition=header.split('\r\n').find(line=>/^content-disposition:/i.test(line))||'';
   if(!/^content-disposition:\s*form-data\s*;/i.test(disposition)||!/(?:;\s*)name="images"(?:;|$)/i.test(disposition)||!/(?:;\s*)filename="[^"\r\n]*"/i.test(disposition))throw new UploadError('只能上传 images 图片字段');
   if(++count>maxFiles)throw new UploadError('最多上传 9 张图片');
   const pieces=[];let size=0,finished=false;
   const keep=bytes=>{size+=bytes.length;if(size>maxImage)throw new UploadError('图片超过 5MB，请换一张',413);pieces.push(bytes.slice());};
   while(true){
    let at=indexOf(buffer,marker),search=0;
    while(at>=0){
     await need(at+marker.length+2);
     const a=buffer[at+marker.length],b=buffer[at+marker.length+1];
     if((a===13&&b===10)||(a===45&&b===45)){finished=a===45;break;}
     search=at+1;at=indexOf(buffer,marker,search);
    }
    if(at>=0){keep(buffer.subarray(0,at));buffer=buffer.slice(at+marker.length+2);break;}
    const safe=Math.max(0,buffer.length-marker.length-2);if(safe){keep(buffer.subarray(0,safe));buffer=buffer.slice(safe);}
    if(!await more())throw new UploadError('图片上传不完整，请重试');
   }
   if(!size)throw new UploadError('没有收到图片');
   const bytes=new Uint8Array(size);let offset=0;for(const piece of pieces){bytes.set(piece,offset);offset+=piece.length;}pieces.length=0;
   yield bytes;
   if(finished)return;
  }
 }finally{try{await reader.cancel()}catch{}reader.releaseLock();}
}
