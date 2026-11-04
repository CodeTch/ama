import {test,expect} from '@playwright/test';
test('PCM journal survives reload, isolates identities and never clears unverified chunks',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const sid=crypto.randomUUID(),partId=crypto.randomUUID(),meetingId=crypto.randomUUID();
  const session={sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()};const journal=createPcmJournal('owner',{limitBytes:8});await journal.begin(meetingId,session);
  const pcm=new Uint8Array([1,2,3,4]).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');const one={sessionId:sid,partId,seq:1,sampleStart:0,sampleCount:2,sha256,pcm};await journal.put(one);journal.close();
  const restored=createPcmJournal('owner',{limitBytes:8});const pending=[];for await(const c of restored.pending(sid))pending.push(c);const before=await restored.load(sid);await restored.removeConfirmed({...one,sampleStart:9});const retained=[];for await(const c of restored.pending(sid))retained.push(c.seq);await restored.removeConfirmed(one);const after=await restored.load(sid);const other=createPcmJournal('member',{limitBytes:8});const foreign=await other.load(sid);const invisible=[];for await(const c of other.pending(sid))invisible.push(c.seq);restored.close();other.close();return{count:pending.length,bytes:Array.from(new Uint8Array(pending[0].pcm)),before:before.parts,after:after.parts,retained,foreign,invisible};
 });expect(result.count).toBe(1);expect(result.bytes).toEqual([1,2,3,4]);expect(result.before[0].finalSequence).toBe(1);expect(result.after[0].sampleCount).toBe(result.before[0].sampleCount);expect(result.after[0].confirmedSamples).toBe(2);expect(result.after[0].confirmedSequence).toBe(1);expect(result.retained).toEqual([1]);expect(result.foreign).toBeNull();expect(result.invisible).toEqual([]);
});
test('PCM quota and conflicting transactions preserve the last complete checkpoint',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const journal=createPcmJournal('owner',{limitBytes:8}),sid=crypto.randomUUID(),partId=crypto.randomUUID();await journal.begin(crypto.randomUUID(),{sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});const pcm=new Uint8Array([1,2,3,4]).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');const chunk=(seq:number)=>({sessionId:sid,partId,seq,sampleStart:(seq-1)*2,sampleCount:2,sha256,pcm});await journal.put(chunk(1));await journal.put(chunk(2));let quota=false,conflict=false;try{await journal.put(chunk(3));}catch{quota=true;}try{await journal.put({...chunk(1),sampleStart:8});}catch{conflict=true;}const pending=[];for await(const c of journal.pending(sid))pending.push(c.seq);const checkpoint=await journal.load(sid);await journal.removeConfirmed(chunk(1));await journal.put(chunk(3));const recovered=await journal.load(sid);journal.close();return{quota,conflict,pending,checkpoint:checkpoint.parts[0].finalSequence,recovered:recovered.parts[0].finalSequence};
 });expect(result).toEqual({quota:true,conflict:true,pending:[1,2],checkpoint:2,recovered:3});
});
test('a new part and collector epoch are saved without losing the old part manifest',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{
  const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path);const journal=createPcmJournal('owner'),sid=crypto.randomUUID(),partId=crypto.randomUUID(),session={sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()};await journal.begin(crypto.randomUUID(),session);const next={...session,partId:crypto.randomUUID(),writerEpoch:3};await journal.updateSession(next);let stale=false;try{await journal.updateSession(session);}catch{stale=true;}const state=await journal.load(sid);journal.close();return{stale,parts:state.parts.length,epoch:state.session.writerEpoch,oldPart:state.parts[0].partId===partId};
 });expect(result).toEqual({stale:true,parts:2,epoch:3,oldPart:true});
});
test('adopting a complete server manifest preserves the old cursor without deleting local pending audio',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path),journal=createPcmJournal('owner'),sid=crypto.randomUUID(),partId=crypto.randomUUID();await journal.begin(crypto.randomUUID(),{sessionId:sid,partId,writerEpoch:3,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()},[{partId,finalSequence:2,sampleCount:64000}]);const record=await journal.load(sid);journal.close();return record.parts[0];});expect(result.finalSequence).toBe(2);expect(result.sampleCount).toBe(64000);
});
test('an explicit meeting reopens its finished archive while automatic recovery finds only unfinished recordings',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{const path='/src/features/recording/pcm-journal.ts';const {createPcmJournal}=await import(path),journal=createPcmJournal('owner'),sid=crypto.randomUUID(),partId=crypto.randomUUID(),meeting=crypto.randomUUID(),pcm=new Uint8Array([1,2]).buffer,sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');await journal.begin(meeting,{sessionId:sid,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});const chunk={sessionId:sid,partId,seq:1,sampleStart:0,sampleCount:1,sha256,pcm};await journal.put(chunk);await journal.removeConfirmed(chunk);await journal.finish(sid,crypto.randomUUID());const explicit=await journal.latest(meeting),automatic=await journal.latest();journal.close();return{finished:explicit?.finished,automatic};});expect(result).toEqual({finished:true,automatic:null});
});

for(const attached of [false,true])test(`denied automatic recovery offers a fresh recording and retains the old PCM (${attached?'already attached':'cold entry'})`,async({page})=>{
 const actor='recording-owner',meetingId='10000000-0000-4000-8000-000000000001',
  sessionId='10000000-0000-4000-8000-000000000002',partId='10000000-0000-4000-8000-000000000003';
 let checks=0,subtitles=0,creates=0;
 await page.route('**/api/v1/**',async route=>{
  const {pathname}=new URL(route.request().url());
  if(pathname==='/api/v1/session')return route.fulfill({json:{
   actor:{enterpriseId:actor,status:'ACTIVE',roles:['USER']},csrfToken:'test',expiresAt:'2099-01-01T00:00:00Z',
  }});
  if(pathname==='/api/v1/recordings/'+sessionId){checks++;return route.fulfill({status:403,json:{error:{code:'FORBIDDEN',message:'Access denied',traceId:sessionId}}});}
  if(pathname==='/api/v1/recordings/'+sessionId+'/subtitles'){subtitles++;return route.fulfill({status:403,json:{}});}
  if(pathname==='/api/v1/meetings'&&route.request().method()==='POST'){creates++;return route.fulfill({status:503,json:{}});}
  return route.abort();
 });
 await page.goto('/meetings');
 await page.evaluate(async({actor,meetingId,sessionId,partId})=>{
  const path='/src/features/recording/pcm-journal.ts';
  const {createPcmJournal}=await import(path),journal=createPcmJournal(actor);
  const pcm=new Uint8Array([1,2,3,4]).buffer;
  const sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');
  await journal.begin(meetingId,{sessionId,partId,writerEpoch:2,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()});
  await journal.put({sessionId,partId,seq:1,sampleStart:0,sampleCount:2,sha256,pcm});
  journal.close();
 },{actor,meetingId,sessionId,partId});
 if(attached){
  await page.goto('/recording/'+meetingId);
  await page.getByRole('button',{name:'重试读取字幕',exact:true}).waitFor();
  await page.getByRole('button',{name:'返回会议列表',exact:true}).click();
  subtitles=0;
 }
 await page.getByRole('button',{name:'开始录音',exact:true}).click();
 await expect(page.getByText('上次录音已无法访问，可以开始新录音。')).toBeVisible();
 await expect(page).toHaveURL(/\/recording$/);
 expect({checks,subtitles,creates}).toEqual({checks:1,subtitles:0,creates:0});
 await page.getByRole('button',{name:'开始新录音',exact:true}).click();
 await expect.poll(()=>creates).toBe(1);
 const old=await page.evaluate(async({actor,meetingId,sessionId})=>{
  const path='/src/features/recording/pcm-journal.ts';
  const {createPcmJournal}=await import(path),journal=createPcmJournal(actor);
  try{
   const pending=[];
   for await(const chunk of journal.pending(sessionId))pending.push(Array.from(new Uint8Array(chunk.pcm)));
   return {pending,explicit:(await journal.latest(meetingId))?.session.sessionId,automatic:await journal.latest()};
  }finally{journal.close();}
 },{actor,meetingId,sessionId});
 expect(old).toEqual({pending:[[1,2,3,4]],explicit:sessionId,automatic:null});
 expect({checks,subtitles}).toEqual({checks:1,subtitles:0});
});

for(const firstStatus of [503,401])test(`automatic recovery preserves the original session on HTTP ${firstStatus}`,async({page})=>{
 const actor='recording-owner',meetingId='20000000-0000-4000-8000-000000000001',
  sessionId='20000000-0000-4000-8000-000000000002',partId='20000000-0000-4000-8000-000000000003';
 const session={sessionId,partId,writerEpoch:2,backendId:'a'.repeat(64)},calls:string[]=[];
 const recording='/api/v1/recordings/'+sessionId;
 let unavailable=true;
 await page.route('**/api/v1/**',async route=>{
  const {pathname}=new URL(route.request().url());
  if(pathname==='/api/v1/session')return route.fulfill({json:{
   actor:{enterpriseId:actor,status:'ACTIVE',roles:['USER']},csrfToken:'test',expiresAt:'2099-01-01T00:00:00Z',
  }});
  if(pathname===recording){
   calls.push('check');
   return unavailable?route.fulfill({status:firstStatus,json:{}}):route.fulfill({json:session});
  }
  if(pathname===recording+'/subtitles'){
   calls.push('subtitles');
   return route.fulfill({json:{meetingId,sessionId,events:[],nextCursor:'0',hasMore:false,delayed:false,state:'IDLE'}});
  }
  if(pathname===recording+'/writer-lease'){
   calls.push('writer');
   return route.fulfill({status:503,json:{}});
  }
  if(pathname==='/api/v1/meetings'&&route.request().method()==='POST')calls.push('new');
  return route.abort();
 });
 await page.goto('/meetings');
 await page.evaluate(async({actor,meetingId,session})=>{
  const path='/src/features/recording/pcm-journal.ts';
  const {createPcmJournal}=await import(path),journal=createPcmJournal(actor);
  try{await journal.begin(meetingId,{...session,collectorId:crypto.randomUUID()});}finally{journal.close();}
 },{actor,meetingId,session});
 await page.getByRole('button',{name:'开始录音',exact:true}).click();
 const controls=page.getByRole('region',{name:'会议录音'});
 await expect(controls.getByRole('alert')).toContainText(firstStatus===401?'登录状态不可用':'服务暂不可用');
 expect(calls).toEqual(['check']);
 await expect(page.getByRole('button',{name:'开始新录音',exact:true})).toHaveCount(0);
 if(firstStatus===401){
  await expect(controls.getByRole('link',{name:'重新登录',exact:true})).toHaveAttribute('href','/login');
  return;
 }
 unavailable=false;
 await page.getByRole('button',{name:'重新授权并开始',exact:true}).click();
 await expect(page).toHaveURL(new RegExp('/recording/'+meetingId+'$'));
 await expect.poll(()=>calls.includes('writer')).toBe(true);
 expect(calls.slice(0,2)).toEqual(['check','check']);
 expect(calls).toContain('subtitles');
 expect(calls).not.toContain('new');
});
