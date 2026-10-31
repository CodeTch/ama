import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createChunkStore,type Chunk} from './chunk-store';

const chunk=(seq:number):Chunk=>({sessionId:'s',partId:'p',seq,sampleStart:seq,sampleCount:1,sha256:String(seq),pcm:new ArrayBuffer(2)});
test('chunk store bounds cache and removes an exact confirmed chunk',async()=>{const store=createChunkStore(4);await store.put(chunk(1));await store.put(chunk(2));await assert.rejects(store.put({...chunk(3),pcm:new ArrayBuffer(3)}));await store.removeConfirmed(chunk(1));const pending=[];for await(const c of store.pending('s'))pending.push(c.seq);assert.deepEqual(pending,[2]);});
test('stored PCM and yielded PCM are independent immutable snapshots',async()=>{const store=createChunkStore();const original=chunk(1);new Uint8Array(original.pcm)[0]=9;await store.put(original);original.seq=8;new Uint8Array(original.pcm)[0]=7;for await(const c of store.pending('s')){assert.equal(c.seq,1);assert.equal(new Uint8Array(c.pcm)[0],9);new Uint8Array(c.pcm)[0]=2;}for await(const c of store.pending('s'))assert.equal(new Uint8Array(c.pcm)[0],9);});
test('same identity with a different sample range or bytes cannot overwrite pending audio',async()=>{const store=createChunkStore();await store.put(chunk(1));await store.put(chunk(1));await assert.rejects(store.put({...chunk(1),sampleStart:9}));const different=chunk(1);new Uint8Array(different.pcm)[0]=1;await assert.rejects(store.put(different));});
