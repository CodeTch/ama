import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createServer} from 'vite';
import {createSSRApp,h,ref,shallowReadonly,nextTick} from 'vue';
import {createMemoryHistory,createRouter} from 'vue-router';
import {renderToString} from 'vue/server-renderer';
import type {RecordingPart,RecordingSession,PartState} from '../../src/features/recording/recording-types';

type Phase='IDLE'|'AUTHORIZING'|'SAVING'|'RECORDING'|'PAUSED'|'INTERRUPTED'|'FINISHED'|'ERROR';
const meetingId='00000000-0000-4000-8000-000000000001';
const emptyRecord=():RecordingSession=>({schema:1,actor:'actor-1',meeting:meetingId,finished:false,parts:[]});
const part=(state:PartState):RecordingPart=>({id:'part-1',index:1,name:'录音片段 01.webm',mime:'audio/webm',state,elapsedMs:9000,savedElapsedMs:9000,bytes:1024,chunks:['chunk-1'],incomplete:false});

test('capacity limits block a new capture while retaining the recovery action for failed audio',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application'),full={...emptyRecord(),parts:[{...part('INTERRUPTED'),elapsedMs:7200000,savedElapsedMs:7200000,chunks:Array.from({length:3600},(_,i)=>String(i))}]};const stopped=await renderPage(component,applicationKey,'INTERRUPTED',full);assert.match(stopped.match(/<button[^>]*aria-label="开始下一段"[^>]*>/)?.[0]??'',/disabled/);const failed=await renderPage(component,applicationKey,'ERROR',full),retry=failed.match(/<button[^>]*aria-label="重试保存"[^>]*>/)?.[0];assert(retry);assert.doesNotMatch(retry,/disabled/);}finally{await server.close();}
});

async function renderPage(component:unknown,applicationKey:unknown,phase:Phase='IDLE',record=emptyRecord(),capture?:(state:any,scope:any)=>void){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/meetings',component:{render:()=>null}},{path:'/recording/:id?',component:{render:()=>null}}]});
 await router.push('/recording/'+meetingId);await router.isReady();
 const current=ref({actor:{enterpriseId:'actor-1',status:'ACTIVE',roles:['USER'] as const},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00.000Z'});
 const recorder={record:shallowReadonly(ref(record)),phase:shallowReadonly(ref(phase)),error:shallowReadonly(ref<string|null>(null)),partElapsed:shallowReadonly(ref(9000)),elapsed:shallowReadonly(ref(9000)),attach:async()=>{},start:async()=>{},pause:()=>{},resume:async()=>{},interrupt:async()=>{},end:async()=>{},retry:async()=>{},files:async()=>[],finish:async()=>{},invalidate:async()=>{},logout:async()=>{},dispose:()=>{}};
 const command={state:shallowReadonly(ref('EMPTY')),snapshot:shallowReadonly(ref(null)),ack:shallowReadonly(ref(null))};
 const pcmRecord=record.parts.length?{schema:1,actor:record.actor,meetingId:record.meeting,session:{sessionId:meetingId,partId:record.parts.at(-1)!.id,writerEpoch:2,backendId:'a'.repeat(64)},parts:record.parts.map(p=>({partId:p.id,finalSequence:p.chunks.length,sampleCount:p.elapsedMs*16,confirmedSequence:1,confirmedSamples:p.savedElapsedMs*16,endReason:['RECORDING','PAUSED','SAVING'].includes(phase)?'ACTIVE':'MANUAL',tailIntegrity:'COMPLETE'})),finished:record.finished}:null;
 const pcmRecorder={...recorder,failure:ref(null),operation:ref(null),notice:ref(null),syncing:ref(false),record:shallowReadonly(ref(pcmRecord)),meetingId:ref(meetingId),pendingCount:ref(phase==='RECORDING'||phase==='PAUSED'?1:0),pendingBytes:ref(!record.finished&&record.parts.length?1024:0),connected:ref(true),reconnect:async()=>{},restore:async()=>null};
 const storage=new Map<string,string>();
 const app={pcmRecorder,subtitleApi:{read:async()=>({meetingId,sessionId:meetingId,events:[],nextCursor:'0',hasMore:false,delayed:false,state:'IDLE'})},recordingApi:{archive:async()=>({taskId:meetingId,state:'SUCCEEDED',available:true,sampleCount:144000}),audioUrl:()=>'/api/v1/recordings/'+meetingId+'/audio'},session:{current:shallowReadonly(current),status:shallowReadonly(ref('ACTIVE')),refresh:async()=>{},invalidate:()=>{},logout:async()=>{},onLogout:()=>()=>{}},recorder,recordingStore:{latest:async()=>null,load:async()=>null,save:async()=>{},append:async()=>{},file:async()=>new File([], 'empty.webm'),clear:async()=>{}},create:{command,submit:async()=>({id:meetingId})},uploadQueue:{ready:shallowReadonly(ref(false)),blocked:shallowReadonly(ref(false)),items:shallowReadonly(ref([])),importCommand:command,open:async()=>{},add:async()=>{},retry:async()=>{},submitImport:async()=>{}},setMeetingScope:()=>{},meetingScope:shallowReadonly(ref(meetingId)),infoScopeReady:shallowReadonly(ref(true)),info:{},journals:{tab:'recording-page-view',recovery:'NEW_NAMESPACE',storage:{getItem:(key:string)=>storage.get(key)??null,setItem:(key:string,value:string)=>storage.set(key,value),removeItem:(key:string)=>storage.delete(key)}},options:{people:async()=>({items:[{stableId:'actor-1',displayName:'录音负责人'}],nextCursor:null})},dispose:()=>{}};
 const view=capture?{...(component as any),setup(props:any,context:any){const state=(component as any).setup(props,context);capture(state,{pcmRecorder,current,router});return state;}}:component;const html=await renderToString(createSSRApp({render:()=>h(view as any)}).provide(applicationKey as any,app).use(router));
 return html;
}

test('online recording view matches prototype structure and state copy',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default;
  const {applicationKey}=await server.ssrLoadModule('/src/application');
  const empty=await renderPage(component,applicationKey);
  assert.match(empty,/返回会议列表/);
  assert.doesNotMatch(empty,/aria-label="返回列表"/);
  assert.match(empty,/等待录音数据/);
  assert.match(empty,/全部录音段/);
  assert.doesNotMatch(empty,/模拟识别|未接入.*服务端保存/);assert.match(empty,/未确认音频/);
  assert.match(empty,/rect x="5" y="5" width="14" height="14" rx="2"/);
  assert.match(empty,/M12 4v14m-5-5 5 5 5-5M5 21h14/);
  const active=await renderPage(component,applicationKey,'RECORDING',{...emptyRecord(),parts:[part('RECORDING')]});
  assert.match(active,/暂停录音/);
  assert.match(active,/class="recording-live"/);
  assert.doesNotMatch(active,/实时识别尚未安装/);
  assert.match(active,/待服务端确认/);assert.doesNotMatch(active,/已保存录音 · 1个文件/);
  const interrupted=await renderPage(component,applicationKey,'INTERRUPTED',{...emptyRecord(),parts:[part('INTERRUPTED')]});
  assert.match(interrupted,/内容保留/);
  assert.match(interrupted,/已保留可恢复的音频范围，可开始下一段。/);
  const finished=await renderPage(component,applicationKey,'FINISHED',{...emptyRecord(),finished:true,parts:[part('SAVED')]});
  assert.match(finished,/录音已保存/);assert.doesNotMatch(finished,/aria-label="录音归档"|aria-label="录音处理"/);
 }finally{await server.close();}
});

test('production recording page has no fixed demo or interface bypass',()=>{
 const source=readFileSync(new URL('../../src/features/meetings/RecordingPage.vue',import.meta.url),'utf8');
 assert.doesNotMatch(source,/MTG-|mock|demoSeed|VITE_USE_RECORDING_FIXTURE/i);
 assert.match(source,/useRecorder|useApplication/);
});

test('recording scope-loss cleanup closes private decisions and late finish cannot navigate the next actor',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});let state:any,scope:any,release:(()=>void)|undefined,pending:Promise<void>|undefined;try{const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application');await renderPage(component,applicationKey,'PAUSED',{...emptyRecord(),parts:[part('PAUSED')]},(s,c)=>{state=s;scope=c;});const end={open:true,close(){this.open=false;}},interrupt={open:true,close(){this.open=false;}};state.endDialog.value=end;state.interruptDialog.value=interrupt;scope.pcmRecorder.finish=()=>new Promise<void>(r=>release=r);pending=state.finish('save');assert.equal(state.busy.value,true);assert.equal(await state.closeDecision(end),false);assert.equal(end.open,true);scope.current.value=null;state.resetPrivateDialogs();await nextTick();assert.equal(end.open,false);assert.equal(interrupt.open,false);release!();await pending;assert.equal(scope.router.currentRoute.value.path,'/recording/'+meetingId);assert.equal(state.endError.value,null);
 }finally{release?.();await pending;state?.subtitles.dispose();state?.flow.dispose();await server.close();}
});

test('recording distinguishes local duration from confirmed server duration and exposes meeting navigation',async()=>{const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application');const html=await renderPage(component,applicationKey,'PAUSED',{...emptyRecord(),parts:[{...part('PAUSED'),savedElapsedMs:2000}]});assert.match(html,/服务端已确认[^<]*00:00:02/);assert.match(html,/aria-label="返回会议列表"/);}finally{await server.close();}});

test('authorization and saving cannot advertise an interrupted part ready to continue',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default;
  const {applicationKey}=await server.ssrLoadModule('/src/application');
  const record={...emptyRecord(),parts:[part('INTERRUPTED')]};
  const authorizing=await renderPage(component,applicationKey,'AUTHORIZING',record);
  assert.match(authorizing,/第1段 · 等待麦克风授权/);
  assert.doesNotMatch(authorizing,/可开始下一段|已中断，内容保留/);
  const saving=await renderPage(component,applicationKey,'SAVING',record);
  assert.match(saving,/第1段 · 保存中/);assert.doesNotMatch(saving,/已保存录音 · 1个文件/);
  assert.doesNotMatch(saving,/可开始下一段|已中断，内容保留/);
 }finally{await server.close();}
});

test('cancelling an unaccepted end decision clears its durable process intent',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});let state:any;
 try{
  const component=(await server.ssrLoadModule('/src/features/meetings/RecordingPage.vue')).default;
  const {applicationKey}=await server.ssrLoadModule('/src/application');
  await renderPage(component,applicationKey,'PAUSED',{...emptyRecord(),parts:[part('PAUSED')]},s=>state=s);
  const dialog={open:true,close(){this.open=false;}};state.endDialog.value=dialog;
  state.processIntent.save('actor-1',meetingId,{actor:'actor-1',meeting:meetingId,session:meetingId,requestId:meetingId});
  await state.closeDecision(state.endDialog.value);
  assert.equal(state.processIntent.load('actor-1',meetingId).kind,'EMPTY');
  assert.equal(dialog.open,false);
 }finally{state?.subtitles.dispose();state?.flow.dispose();await server.close();}
});
