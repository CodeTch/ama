import {test} from 'node:test';import assert from 'node:assert/strict';
import {usePublicationReadLease} from '../../src/features/minutes/usePublicationReadLease.js';
test('published view drops cached content on denied or stalled renewal',async()=>{
 let denied=0;const first=usePublicationReadLease(async()=>{throw Error('revoked');},()=> 'owner/meeting',()=>denied++,{interval:5,timeout:40});first.start();await new Promise(r=>setTimeout(r,25));assert.equal(denied,1);first.dispose();
 const stalled=usePublicationReadLease(()=>new Promise<void>(()=>{}),()=> 'owner/meeting',()=>denied++,{interval:5,timeout:35});stalled.start();await new Promise(r=>setTimeout(r,65));assert.equal(denied,2);stalled.dispose();
});
test('old renewal completion cannot invalidate a different meeting or account',async()=>{
 let scope='old',reject!:(cause:unknown)=>void,denied=0;const renewal=usePublicationReadLease(()=>new Promise<void>((_,fail)=>{reject=fail;}),()=>scope,()=>denied++,{interval:5,timeout:60});renewal.start();await new Promise(r=>setTimeout(r,15));scope='new';renewal.dispose();reject(Error('old denied'));await new Promise(r=>setTimeout(r,10));assert.equal(denied,0);
});
