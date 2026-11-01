import {test} from 'node:test';import assert from 'node:assert/strict';
import {usePublicationReadLease} from '../../src/features/minutes/usePublicationReadLease.js';
test('published view drops cached content on denied or stalled renewal',async()=>{
 let denied=0;const first=usePublicationReadLease(async()=>{throw Error('revoked');},()=> 'owner/meeting',()=>denied++,{interval:5,timeout:40});first.start();await new Promise(r=>setTimeout(r,25));assert.equal(denied,1);first.dispose();
 const stalled=usePublicationReadLease(()=>new Promise<void>(()=>{}),()=> 'owner/meeting',()=>denied++,{interval:5,timeout:35});stalled.start();await new Promise(r=>setTimeout(r,65));assert.equal(denied,2);stalled.dispose();
});
test('old renewal completion cannot invalidate a different meeting or account',async()=>{
 let scope='old',reject!:(cause:unknown)=>void,denied=0;const renewal=usePublicationReadLease(()=>new Promise<void>((_,fail)=>{reject=fail;}),()=>scope,()=>denied++,{interval:5,timeout:60});renewal.start();await new Promise(r=>setTimeout(r,15));scope='new';renewal.dispose();reject(Error('old denied'));await new Promise(r=>setTimeout(r,10));assert.equal(denied,0);
});

// A successful authority read is fresh from its start, so latency must not be
// added to the polling interval or a healthy service loses the display lease.
test('slow successful renewals keep their start cadence without extending authority lifetime',async t=>{
 t.mock.method(performance,'now',()=>Date.now());t.mock.timers.enable({apis:['setTimeout','Date'],now:1000});
 let denied=0;const starts:number[]=[];
 const lease=usePublicationReadLease(()=>{starts.push(Date.now());return new Promise<void>(resolve=>setTimeout(resolve,18));},()=> 'owner/meeting',()=>denied++,{interval:20,timeout:45});
 try{lease.start();for(let n=0;n<150;n++){t.mock.timers.tick(1);await Promise.resolve();}assert.equal(denied,0);assert(starts.length>=6);assert(starts.slice(1).every((v,i)=>v-starts[i]!<=20));}finally{lease.dispose();}
});
