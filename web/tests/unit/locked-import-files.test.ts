import {test} from 'node:test';
import assert from 'node:assert/strict';
import {lockedImportFiles} from '../../src/features/import/locked-import-files';
import type {UploadReceipt} from '../../src/shared/api/uploads';
const sources=Array.from({length:16},(_,i)=>({fileId:'f'+i,uploadId:'u'+i,ordinal:i+1,sha256:'a'.repeat(64),sizeBytes:10,sizeBytesToken:'10'}));
function receipt(index:number):UploadReceipt{
 return {completed:{...sources[index],originalPurpose:'TRANSCRIPT_INPUT'}} as UploadReceipt;
}
test('sixteen locked receipts use at most four simultaneous reads and retain manifest order',async()=>{
 let concurrent=0,peak=0;
 const verified=await lockedImportFiles(sources,async(_meeting,id)=>{
  concurrent++;peak=Math.max(peak,concurrent);const index=Number(id.slice(1));
  await new Promise(resolve=>setTimeout(resolve,index%4===0?10:1));concurrent--;
  return receipt(index);
 },'meeting',new AbortController().signal);
 assert.equal(peak,4);
 assert.deepEqual(verified.map(f=>f.fileId),sources.map(f=>f.fileId));
});
test('a foreign upload receipt or cancelled scope cannot publish a partial batch',async()=>{
 await assert.rejects(lockedImportFiles(sources.slice(0,1),async()=>receipt(1),'meeting',new AbortController().signal),/不匹配/);
 const controller=new AbortController();controller.abort();
 await assert.rejects(lockedImportFiles(sources,async()=>{throw Error('unexpected read');},'meeting',controller.signal),{name:'AbortError'});
});
