import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
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
