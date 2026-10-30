import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
import {ApiError} from '../../src/shared/api/client.js';
import type {components} from '../../src/shared/api/generated.js';
const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'T'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('business mutations attach current in-memory CSRF and stable key and cannot redirect',async()=>{
 const original=globalThis.fetch;let observed:RequestInit|undefined;
 globalThis.fetch=async(_input,init)=>{observed=init;return new Response('{"accepted":true}',{status:200});};
 try{const client=createBusinessClient('https://ama.test',()=>session,()=>{});await client.json('/api/v1/meetings',{method:'POST',headers:{'Idempotency-Key':'original'},body:'{}'});const headers=new Headers(observed?.headers);assert.equal(headers.get('X-CSRF-Token'),session.csrfToken);assert.equal(headers.get('Idempotency-Key'),'original');assert.equal(observed?.credentials,'include');assert.equal(observed?.redirect,'error');}
 finally{globalThis.fetch=original;}
});
test('external, management and normalized escape routes fail before fetch',async()=>{
 const client=createBusinessClient('https://ama.test',()=>session,()=>{});const original=globalThis.fetch;let calls=0;globalThis.fetch=async()=>{calls++;return new Response('{}');};
 try{for(const path of ['https://other.test/api/v1/meetings','//other.test/api/v1/meetings','/internal/health/live','/api/v1/../../internal/health/live','/api/v1/%2e%2e/%2e%2e/internal/health/live','/api/v1/meetings#secret'])await assert.rejects(client.json(path));assert.equal(calls,0);}
 finally{globalThis.fetch=original;}
});
test('missing session prevents commands and an actual401 freezes session without retries',async()=>{
 let active:components['schemas']['Session']|null=null;let revoked=0;let calls=0;const client=createBusinessClient('https://ama.test',()=>active,()=>{revoked++;active=null;});const original=globalThis.fetch;globalThis.fetch=async()=>{calls++;return new Response('{}',{status:401});};
 try{await assert.rejects(client.json('/api/v1/meetings',{method:'POST',body:'{}'}),(e:unknown)=>e instanceof ApiError&&e.status===401);assert.equal(calls,0);active=session;await assert.rejects(client.json('/api/v1/meetings'));assert.equal(calls,1);assert.equal(active,null);assert.ok(revoked>=1);}
 finally{globalThis.fetch=original;}
});

test('late401 from an old session cannot revoke a newer in-memory session',async()=>{
 let active:components['schemas']['Session']|null=session;let revoked=0;let release:(value:Response)=>void=()=>{};const original=globalThis.fetch;
 globalThis.fetch=()=>new Promise<Response>(resolve=>{release=resolve;});
 try{const client=createBusinessClient('https://ama.test',()=>active,()=>{revoked++;active=null;});const pending=client.json('/api/v1/meetings');const newer={...session,csrfToken:'N'.repeat(43)};active=newer;release(new Response('{}',{status:401}));await assert.rejects(pending);assert.equal(revoked,0);assert.equal(active,newer);}
 finally{globalThis.fetch=original;}
});

test('binary source reads are bounded and require the same session and origin',async()=>{
 const old=globalThis.fetch;let calls=0,revoked=0;let active:Session|null=session;let release:(value:Response)=>void=()=>{};
 try{const client=createBusinessClient('https://ama.test',()=>active,()=>{revoked++;active=null;});globalThis.fetch=async()=>{calls++;return new Response(new Uint8Array([1,2,3]),{headers:{'Content-Length':'3','Content-Disposition':"attachment; filename*=UTF-8''%E4%BC%9A%E8%AE%AE.wav"}});};const signal=new AbortController().signal;const value=await client.bytes('/api/v1/meetings/source',{signal,size:3});assert.deepEqual([...new Uint8Array(value.bytes)],[1,2,3]);assert.equal(value.name,'会议.wav');await assert.rejects(client.bytes('https://other.test/api/v1/file',{signal,size:3}));assert.equal(calls,1);await assert.rejects(client.bytes('/api/v1/file',{signal,size:2}));globalThis.fetch=()=>new Promise(resolve=>{release=resolve;});const pending=client.bytes('/api/v1/file',{signal,size:3});active={...session,actor:{...session.actor,enterpriseId:'other'}};release(new Response(new Uint8Array([1,2,3])));await assert.rejects(pending,{name:'AbortError'});assert.equal(revoked,0);active=session;globalThis.fetch=async()=>new Response(null,{status:401});await assert.rejects(client.bytes('/api/v1/file',{signal,size:3}));assert.equal(revoked,1);
 }finally{globalThis.fetch=old;}
});
