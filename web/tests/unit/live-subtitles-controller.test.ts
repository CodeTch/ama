import {test} from 'node:test';import assert from 'node:assert/strict';import {useLiveSubtitles} from '../../src/features/recording/useLiveSubtitles.js';import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',session='00000000-0000-4000-8000-000000000002',part='00000000-0000-4000-8000-000000000003',segment='00000000-0000-4000-8000-000000000004';
const event=(id:string,revision:number,status='temporary',text='非常非常重要12345')=>({eventId:id,sessionId:session,partId:part,segmentId:segment,revision,status,text,startMs:0,endMs:revision*2000,sourceFinalSeq:revision,createdAt:'2026-11-01T00:00:00Z'});
function fixture(){let actor:string|null='owner',sid=session;const calls:string[]=[],responses:any[]=[];const api={read:async(_m:string,_s:string,cursor:string)=>{calls.push(cursor);const response=responses.shift();if(response instanceof Error)throw response;return typeof response==='function'?response():response;}};return{api,calls,responses,options:{actor:()=>actor,meeting:()=>meeting,sessionId:()=>sid,eligible:()=>!!actor},actor:(v:string|null)=>{actor=v;},session:(v:string)=>{sid=v;}};}
const page=(events:any[],cursor:string)=>({meetingId:meeting,sessionId:session,events,nextCursor:cursor,hasMore:false,delayed:false,state:'IDLE'});
test('live revisions replace one segment without deleting repeated words and reconnect uses its accepted cursor',async()=>{const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);try{f.responses.push(page([event('1',1)],'1'));c.synchronize();await c.refresh();assert.equal(c.events.value[0]!.text,'非常非常重要12345');f.responses.push(page([event('2',2,'stable','非常非常重要12345。')],'2'));await c.refresh();assert.equal(c.events.value.length,1);assert.equal(c.events.value[0]!.revision,2);assert.deepEqual(f.calls,['0','1']);}finally{c.dispose();}});
test('late data cannot return after actor or recording scope changes',async()=>{const f=fixture();let resolve!:(v:any)=>void;f.responses.push(()=>new Promise(r=>{resolve=r;}));const c=useLiveSubtitles(f.api as any,f.options);try{c.synchronize();const pending=c.refresh();f.actor(null);c.synchronize();resolve(page([event('1',1)],'1'));await pending;assert.deepEqual(c.events.value,[]);assert.equal(c.cursor.value,'0');}finally{c.dispose();}});
test('current permission denial clears private captions and never advances an unverified cursor',async()=>{const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);try{f.responses.push(page([event('1',1)],'1'));c.synchronize();await c.refresh();f.responses.push(new ApiError('http',403,'denied'));await c.refresh();assert.deepEqual(c.events.value,[]);assert.equal(c.cursor.value,'0');assert(c.error.value);}finally{c.dispose();}});
for(const cause of [new ApiError('http',503,'temporarily unavailable'),new DOMException('timeout','TimeoutError'),new ApiError('response',200,'invalid page')])test(`transient ${cause.message} preserves verified captions and resumes from the accepted cursor`,async()=>{const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);try{f.responses.push(page([event('1',1,'stable')],'1'));c.synchronize();await c.refresh();f.responses.push(cause);await c.refresh();assert.equal(c.events.value.length,1);assert.equal(c.cursor.value,'1');assert.equal(c.ready.value,true);assert(c.error.value);f.responses.push(page([event('2',2,'stable','恢复字幕')],'2'));await c.refresh();assert.deepEqual(f.calls,['0','1','1']);assert.equal(c.events.value[0]!.text,'恢复字幕');}finally{c.dispose();}});
test('same recording scope updates preserve captions and the accepted replay cursor',async()=>{
 const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);try{
  f.responses.push(page([event('1',1,'stable')],'1'));c.synchronize();await c.refresh();
  c.synchronize();assert.equal(c.cursor.value,'1');assert.equal(c.events.value.length,1);assert.deepEqual(f.calls,['0']);
  f.responses.push(page([event('2',2,'stable','新字幕')],'2'));await c.refresh();assert.deepEqual(f.calls,['0','1']);
 }finally{c.dispose();}
});
test('same recording scope updates do not abandon an in-flight subtitle page',async()=>{
 const f=fixture();let resolve!:(value:any)=>void;f.responses.push(()=>new Promise(r=>resolve=r));const c=useLiveSubtitles(f.api as any,f.options);try{
  c.synchronize();const pending=c.refresh();c.synchronize();assert.deepEqual(f.calls,['0']);resolve(page([event('1',1)],'1'));await pending;assert.equal(c.cursor.value,'1');
 }finally{resolve?.(page([],'0'));c.dispose();}
});

test('empty subtitle polls preserve the rendered history while updating service state',async()=>{
 const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);
 try{
  f.responses.push(page([event('1',1)],'1'));
  c.synchronize();await c.refresh();
  const history=c.events.value;
  f.responses.push({...page([],'1'),state:'DELAYED',delayed:true});
  await c.refresh();
  assert.equal(c.events.value,history);
  assert.equal(c.delayed.value,true);
 }finally{c.dispose();}
});

test('caught-up terminal subtitles stop polling until explicitly refreshed',async(t)=>{
 const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);t.mock.timers.enable({apis:['setTimeout']});
 try{f.responses.push({...page([event('1',2,'stable')],'1'),state:'DONE'});c.synchronize();await c.refresh();t.mock.timers.tick(10000);await Promise.resolve();assert.deepEqual(f.calls,['0']);f.responses.push({...page([],'1'),state:'DONE'});await c.refresh();assert.deepEqual(f.calls,['0','1']);}finally{c.dispose();t.mock.timers.reset();}
});
test('revisions from one response are accepted atomically and never replace history partially',async()=>{
 const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);
 try{f.responses.push(page([event('1',2,'stable')],'1'));c.synchronize();await c.refresh();const before=c.events.value;f.responses.push(page([event('2',3),event('3',2)],'3'));await c.refresh();assert.equal(c.events.value,before);assert.equal(c.cursor.value,'1');assert(c.error.value);}finally{c.dispose();}
});
test('recognition retry is single flight while verified captions and the reading anchor remain available',async()=>{
 const f=fixture();let release:()=>void=()=>{},calls=0;const gate=new Promise<void>(resolve=>release=resolve);const c=useLiveSubtitles({...f.api,retry:async()=>{calls++;await gate;}} as any,f.options);
 try{f.responses.push(page([event('1',2,'stable')],'1'));c.synchronize();await c.refresh();c.reading.following=false;c.reading.scrollTop=400;f.responses.push(page([],'1'));const first=c.retryRecognition(),second=c.retryRecognition();assert.equal(calls,1);assert.equal(c.retrying.value,true);release();await Promise.all([first,second]);assert.equal(c.retrying.value,false);assert.equal(c.cursor.value,'1');assert.equal(c.reading.following,false);assert.equal(c.reading.scrollTop,400);}finally{release();c.dispose();}
});

test('a hidden tab stops presentation polling and returns using the accepted cursor',async(t)=>{
 const original=Object.getOwnPropertyDescriptor(globalThis,'document'),target=new EventTarget();let visibility='visible';Object.defineProperty(target,'visibilityState',{get:()=>visibility});Object.defineProperty(globalThis,'document',{configurable:true,value:target});t.mock.timers.enable({apis:['setTimeout']});const f=fixture(),c=useLiveSubtitles(f.api as any,f.options);
 try{f.responses.push(page([event('1',2,'stable')],'1'));c.synchronize();await c.refresh();visibility='hidden';target.dispatchEvent(new Event('visibilitychange'));t.mock.timers.tick(10000);await Promise.resolve();assert.deepEqual(f.calls,['0']);assert.equal(c.events.value.length,1);f.responses.push(page([],'1'));visibility='visible';target.dispatchEvent(new Event('visibilitychange'));await c.refresh();assert.deepEqual(f.calls,['0','1']);assert.equal(c.events.value.length,1);}finally{c.dispose();t.mock.timers.reset();if(original)Object.defineProperty(globalThis,'document',original);else Reflect.deleteProperty(globalThis,'document');}
});
