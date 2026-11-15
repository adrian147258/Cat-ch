import assert from 'node:assert/strict';
import {postImages,UploadError} from './post-upload.mjs';
const enc=new TextEncoder(),boundary='test-boundary';
const file=(data)=>`--${boundary}\r\nContent-Disposition: form-data; name="images"; filename="cat.jpg"\r\nContent-Type: image/jpeg\r\n\r\n${data}\r\n`;
function request(body,chunkSize=7){const bytes=enc.encode(body);let offset=0;return new Request('https://test.invalid/posts',{method:'POST',headers:{'Content-Type':`multipart/form-data; boundary="${boundary}"`},duplex:'half',body:new ReadableStream({pull(c){if(offset===bytes.length){c.close();return;}const end=Math.min(bytes.length,offset+chunkSize);c.enqueue(bytes.slice(offset,end));offset=end;}})});}
async function parse(body,chunk=7,limit=1000){const result=[];for await(const bytes of postImages(request(body,chunk),limit))result.push(new TextDecoder().decode(bytes));return result;}
const sample='binary\u0000\u00ff\r\n--test-boundaryNOT-A-DELIMITER';
for(const chunk of [1,2,7,19,65536])assert.deepEqual(await parse(file(sample)+file('second')+`--${boundary}--\r\n`,chunk),[sample,'second']);
assert.deepEqual(await parse(file('12345')+`--${boundary}--`,1,5),['12345']);
await assert.rejects(()=>parse(file('123456')+`--${boundary}--`,1,5),e=>e instanceof UploadError&&e.status===413);
await assert.rejects(()=>parse(file('truncated')),UploadError);
await assert.rejects(()=>parse(file('ok').repeat(10)+`--${boundary}--`),UploadError);
await assert.rejects(()=>parse(file('ok').replace('name="images"','name="unexpected"')+`--${boundary}--`),UploadError);
assert.equal((await parse(file('ok').repeat(9)+`--${boundary}--`)).length,9);
console.log('Passed multipart chunk boundaries, binary data, truncation, size and file count limits');
