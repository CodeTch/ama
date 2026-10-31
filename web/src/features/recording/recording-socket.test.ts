import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {encodeRecordingFrame} from './recording-socket';
import type {Chunk} from './chunk-store';
const session={sessionId:'12345678-1234-5678-9abc-123456789abc',partId:'12345678-1234-5678-9abc-123456789abd',writerEpoch:7,backendId:'backend',collectorId:'12345678-1234-5678-9abc-123456789abe'};
function chunk():Chunk{const pcm=new Uint8Array([1,2,3,4]);return{sessionId:session.sessionId,partId:session.partId,seq:5,sampleStart:64000,sampleCount:2,sha256:createHash('sha256').update(pcm).digest('hex'),pcm:pcm.buffer};}
test('browser frame encoder matches the independent80-byte network contract',async()=>{
 const value=chunk(),frame=await encodeRecordingFrame(session,value),view=new DataView(frame),bytes=new Uint8Array(frame);assert.equal(frame.byteLength,84);assert.equal(Buffer.from(bytes.subarray(0,4)).toString('ascii'),'AMA1');assert.equal(Buffer.from(bytes.subarray(4,20)).toString('hex'),session.partId.replaceAll('-',''));assert.equal(view.getBigInt64(20),7n);assert.equal(view.getBigInt64(28),5n);assert.equal(view.getBigInt64(36),64000n);assert.equal(view.getUint32(44),2);assert.equal(Buffer.from(bytes.subarray(48,80)).toString('hex'),value.sha256);assert.deepEqual(Array.from(bytes.subarray(80)),[1,2,3,4]);
});
test('browser refuses foreign scope damaged audio and unsafe numeric fields',async()=>{
 for(const value of [{...chunk(),sessionId:session.partId},{...chunk(),partId:session.sessionId},{...chunk(),sampleCount:3},{...chunk(),sha256:'0'.repeat(64)},{...chunk(),sampleStart:Number.MAX_SAFE_INTEGER+1}])await assert.rejects(encodeRecordingFrame(session,value));
 await assert.rejects(encodeRecordingFrame({...session,writerEpoch:Number.MAX_SAFE_INTEGER+1},chunk()));
});
test('encoding snapshots PCM before the async hash so caller mutation cannot change sent bytes',async()=>{
 const value=chunk(),pending=encodeRecordingFrame(session,value);new Uint8Array(value.pcm).fill(99);const encoded=await pending;assert.deepEqual(Array.from(new Uint8Array(encoded).subarray(80)),[1,2,3,4]);
});
test('encoding also snapshots session epoch and sample metadata before async work',async()=>{
 const value=chunk(),current={...session},pending=encodeRecordingFrame(current,value);current.writerEpoch=9;value.seq=10;value.sampleStart=70000;const encoded=await pending,view=new DataView(encoded);assert.equal(view.getBigInt64(20),7n);assert.equal(view.getBigInt64(28),5n);assert.equal(view.getBigInt64(36),64000n);
});

import {connectRecordingSocket} from './recording-socket';
import {ApiError} from '../../shared/api/client';
class Socket extends EventTarget{
 readyState=0;protocol='ama.recording.v1';sent:(string|ArrayBuffer)[]=[];closed=false;
 send(value:string|ArrayBuffer){this.sent.push(value);this.dispatchEvent(new Event('sent'));}
 close(){this.closed=true;this.readyState=3;this.dispatchEvent(new Event('close'));}
 open(){this.readyState=1;this.dispatchEvent(new Event('open'));}
 message(value:unknown){this.dispatchEvent(new MessageEvent('message',{data:JSON.stringify(value)}));}
}
const ready=()=>({type:'READY',sessionId:session.sessionId,partId:session.partId,writerEpoch:session.writerEpoch});
async function bound(timeoutMs=2000){const socket=new Socket();let url='',protocols:string[]=[];const pending=connectRecordingSocket('https://ama.test',session,'T'.repeat(43),()=>true,{create:(u,p)=>{url=u;protocols=p;return socket;},timeoutMs});socket.open();socket.message(ready());return{socket,client:await pending,url,protocols};}
async function binarySent(socket:Socket){if(socket.sent.some(v=>v instanceof ArrayBuffer))return;await new Promise<void>((resolve,reject)=>{const timer=setTimeout(()=>{cleanup();reject(Error('Expected binary frame was never sent'));},2000),sent=()=>{if(socket.sent.some(v=>v instanceof ArrayBuffer)){cleanup();resolve();}};function cleanup(){clearTimeout(timer);socket.removeEventListener('sent',sent);}socket.addEventListener('sent',sent);});}
test('a scoped rate-limit error retains a typed retryable refusal and never resolves an ACK',async()=>{const {socket,client}=await bound();const pending=client.put(chunk());await binarySent(socket);socket.message({type:'ERROR',code:'RATE_LIMITED'});await assert.rejects(pending,(error:unknown)=>error instanceof ApiError&&error.status===429&&error.code==='RECORDING_RATE_LIMITED');assert.equal(socket.closed,true);});
test('socket uses same-origin cookies and a CSRF protocol without credentials in the URL',async()=>{const {socket,client,url,protocols}=await bound();assert.equal(url,`wss://ama.test/api/v1/recordings/${session.sessionId}/stream`);assert.deepEqual(protocols,['ama.recording.v1','ama.csrf.'+'T'.repeat(43)]);assert.deepEqual(JSON.parse(socket.sent[0] as string),{type:'HELLO',collectorId:session.collectorId,writerEpoch:7});const pending=client.put(chunk());await binarySent(socket);assert.ok(socket.sent[1] instanceof ArrayBuffer);socket.message({...ready(),type:'ACK',highestContiguousSeq:5,missingSeqs:[]});assert.deepEqual(await pending,{highestContiguousSeq:5,missingSeqs:[]});client.close();assert.ok(socket.closed);});
test('foreign ACK and close reject pending writes without returning a receipt',async()=>{for(const action of ['foreign','close']){const {socket,client}=await bound();const pending=client.put(chunk());await binarySent(socket);if(action==='foreign')socket.message({...ready(),type:'ACK',partId:session.sessionId,highestContiguousSeq:5,missingSeqs:[]});else socket.close();await assert.rejects(pending);assert.ok(socket.closed);client.close();}});
test('missing ACK times out and overlapping writes cannot share one confirmation',async()=>{const {socket,client}=await bound(100);const pending=client.put(chunk());await assert.rejects(client.put(chunk()));await assert.rejects(pending);assert.ok(socket.closed);});
test('scope changes and bad handshake proof close the collector before any binary send',async()=>{const socket=new Socket();let active=true;const pending=connectRecordingSocket('http://ama.test',session,'T'.repeat(43),()=>active,{create:()=>socket,timeoutMs:100});socket.open();socket.message(ready());const client=await pending;active=false;await assert.rejects(client.put(chunk()));assert.equal(socket.sent.length,1);assert.ok(socket.closed);await assert.rejects(connectRecordingSocket('https://other.test/path',session,'bad',()=>true,{create:()=>socket}));});
