import test from 'node:test';
import assert from 'node:assert/strict';
import {request} from '../../src/shared/api/client';
const meeting='12345678-1234-4234-8234-123456789abc';
const turn=()=>new Promise(resolve=>setImmediate(resolve));

test('authority renewal starts while four ordinary response bodies occupy transport slots',async t=>{
 const sent:string[]=[],bodies=new Map<string,ReadableStreamDefaultController<Uint8Array>>();
 t.mock.method(globalThis,'fetch',async(path:string,options:RequestInit)=>{
  sent.push(path);
  if(path.endsWith('/workspace/access')){assert.equal(options.priority,'high');return new Response(null,{status:204});}
  return new Response(new ReadableStream<Uint8Array>({start(controller){bodies.set(path,controller);}}));
 });
 // The same-origin business facade passes absolute URLs to the transport.
 const paths=Array.from({length:5},(_,i)=>`https://ama.test/api/v1/meetings/${meeting}/minutes?request=${i}`);
 const pending=paths.map(path=>request(path));await turn();assert.deepEqual(sent,paths.slice(0,4));
 await request(`https://ama.test/api/v1/meetings/${meeting}/workspace/access`,{expectedStatus:204});assert.equal(sent.length,5);assert(!sent.includes(paths[4]!));
 const finish=(path:string)=>{bodies.get(path)!.enqueue(new TextEncoder().encode('{}'));bodies.get(path)!.close();};
 finish(paths[0]!);await turn();assert(sent.includes(paths[4]!));for(const path of paths.slice(1))finish(path);
 assert.deepEqual(await Promise.all(pending),Array(5).fill({}));
});

test('queued cancellation never sends a stale request and failures release ordinary slots',async t=>{
 const sent:string[]=[],finish:Array<()=>void>=[];
 t.mock.method(globalThis,'fetch',(path:string)=>{sent.push(path);return new Promise<Response>(resolve=>finish.push(()=>resolve(new Response('{}'))));});
 const pending=Array.from({length:4},(_,i)=>request(`/api/v1/directory/people?page=${i}`));await turn();
 const controller=new AbortController();const cancelled=request('/api/v1/directory/people?page=cancelled',{signal:controller.signal});
 const rejected=assert.rejects(cancelled,{name:'AbortError'});controller.abort();await rejected;assert.equal(sent.length,4);
 for(const complete of finish)complete();await Promise.all(pending);
 t.mock.method(globalThis,'fetch',async()=>{throw new Error('transport down');});
 for(let i=0;i<6;i++)await assert.rejects(request('/api/v1/session'),/网络连接不可用/);
 t.mock.method(globalThis,'fetch',async()=>new Response('{}'));assert.deepEqual(await request('/api/v1/session'),{});
});
