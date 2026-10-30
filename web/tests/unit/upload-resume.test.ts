import {test} from 'node:test';import assert from 'node:assert/strict';
import {prepareUpload,UPLOAD_FILE_BYTES} from '../../src/features/import/upload-bytes.js';
const hash=async(value:string)=>Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))).toString('hex');
test('whole file identity and exact accepted receipt boundaries survive a changed part suggestion',async()=>{
 const prepared=await prepareUpload(new File(['abcdefghij'],' 原文😀.txt'),new AbortController().signal);assert.equal(prepared.begin.name,' 原文😀.txt');assert.equal(prepared.begin.size,10);assert.equal(prepared.begin.sha256,await hash('abcdefghij'));
 const first=await prepared.plan([], '4');assert.deepEqual(first.map(p=>p.size),[4,4,2]);assert.ok(first[0]);assert.equal(await prepared.body(first[0]).text(),'abcd');
 const resumed=await prepared.plan([{part:1,sizeBytes:4,sizeBytesToken:'4',sha256:await hash('abcd')}], '3');assert.deepEqual(resumed.map(p=>[p.part,p.size,p.accepted]),[[1,4,true],[2,3,false],[3,3,false]]);assert.ok(resumed[0]);assert.ok(resumed[1]);assert.equal(await prepared.body(resumed[1]).text(),'efg');assert.equal(resumed[0].sha256,first[0].sha256);assert.ok(Object.isFrozen(resumed));assert.ok(Object.isFrozen(prepared.begin));
});
test('reselected bytes must match exact expected size/hash before any resume plan',async()=>{
 const file=new File(['abc'],'same.txt');await assert.rejects(prepareUpload(file,new AbortController().signal,{sizeToken:'3',sha256:await hash('abd')}));await assert.rejects(prepareUpload(file,new AbortController().signal,{sizeToken:'4',sha256:await hash('abc')}));const accepted=await prepareUpload(file,new AbortController().signal,{sizeToken:'3',sha256:await hash('abc')});assert.equal(accepted.begin.sha256,await hash('abc'));
});
test('invalid receipt gaps, hash changes, impossible sizes and suggestion tokens cannot be resliced',async()=>{
 const prepared=await prepareUpload(new File(['abcdef'],'x.txt'),new AbortController().signal),sum=await hash('abc');
 for(const receipts of [[{part:2,sizeBytes:3,sizeBytesToken:'3',sha256:sum}],[{part:1,sizeBytes:3,sizeBytesToken:'3',sha256:'0'.repeat(64)}],[{part:1,sizeBytes:7,sizeBytesToken:'7',sha256:sum}],[{part:1,sizeBytes:3,sizeBytesToken:'03',sha256:sum}]])await assert.rejects(prepared.plan(receipts,'2'));
 for(const token of ['0','-1','01','9223372036854775808','9'.repeat(100)])await assert.rejects(prepared.plan([],token));assert.throws(()=>prepared.body({part:1,size:3,sha256:sum,accepted:false}));
});
test('local browser allocation bound and cancellation are checked before reading file bytes',async()=>{
 let reads=0;const huge=new File([],'large.txt');Object.defineProperty(huge,'size',{value:UPLOAD_FILE_BYTES+1});Object.defineProperty(huge,'arrayBuffer',{value:async()=>{reads++;return new ArrayBuffer(0);}});await assert.rejects(prepareUpload(huge,new AbortController().signal));assert.equal(reads,0);
 const stopped=new AbortController();stopped.abort();await assert.rejects(prepareUpload(new File(['x'],'x.txt'),stopped.signal),{name:'AbortError'});await assert.rejects(prepareUpload(new File(['x'],'x.exe'),new AbortController().signal));
});
test('empty TXT gets one zero-byte part and no unsafe numeric conversion of large suggestions',async()=>{
 const prepared=await prepareUpload(new File([],'empty.TXT'),new AbortController().signal),parts=await prepared.plan([],'9223372036854775807');assert.equal(parts.length,1);assert.ok(parts[0]);assert.equal(parts[0].size,0);assert.equal(parts[0].sha256,await hash(''));assert.equal(prepared.body(parts[0]).size,0);
});

test('receipt inputs are captured before async hashing and cannot change a recovery plan in flight',async()=>{
 const prepared=await prepareUpload(new File(['abcdef'],'x.txt'),new AbortController().signal),receipts=[{part:1,sizeBytes:3,sizeBytesToken:'3',sha256:await hash('abc')},{part:2,sizeBytes:3,sizeBytesToken:'3',sha256:await hash('def')}];
 const pending=prepared.plan(receipts,'3');receipts[1]!.sha256='0'.repeat(64);const result=await pending;assert.deepEqual(result.map(p=>p.accepted),[true,true]);
});
test('cancel after byte read and excessive part count leave no usable plan',async()=>{
 const stopped=new AbortController(),file=new File(['x'],'x.txt');Object.defineProperty(file,'arrayBuffer',{value:async()=>{stopped.abort();return new Uint8Array([120]).buffer;}});await assert.rejects(prepareUpload(file,stopped.signal),{name:'AbortError'});
 const prepared=await prepareUpload(new File([new Uint8Array(4097)],'many.txt'),new AbortController().signal);await assert.rejects(prepared.plan([],'1'));
});

test('expected reselect identity is captured before async reads and cannot be replaced in flight',async()=>{
 const expected={sizeToken:'3',sha256:await hash('abd')},replacement=await hash('abc'),pending=prepareUpload(new File(['abc'],'same.txt'),new AbortController().signal,expected);expected.sha256=replacement;await assert.rejects(pending,/Reselected file bytes differ/);
});

test('all declared document formats retain exact original bytes and identity through upload resume',async()=>{
 for(const ext of ['doc','docx','pdf','pptx','txt']){const bytes=new Uint8Array([0x50,0x4b,0,255,7]),file=new File([bytes],'会议.'+ext),prepared=await prepareUpload(file,new AbortController().signal);assert.equal(prepared.begin.name,file.name);assert.equal(prepared.begin.size,bytes.length);const parts=await prepared.plan([],'8');assert.deepEqual(new Uint8Array(await prepared.body(parts[0]!).arrayBuffer()),bytes);const resumed=await prepareUpload(file,new AbortController().signal,{sizeToken:String(bytes.length),sha256:prepared.begin.sha256});assert.equal(resumed.begin.sha256,prepared.begin.sha256);}
});
