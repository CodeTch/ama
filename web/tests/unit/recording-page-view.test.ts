import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createServer} from 'vite';
import {createSSRApp,h,ref,shallowReadonly} from 'vue';
import {createMemoryHistory,createRouter} from 'vue-router';
import {renderToString} from 'vue/server-renderer';
import type {RecordingPart,RecordingSession,PartState} from '../../src/features/recording/recording-types';

type Phase='IDLE'|'RECORDING'|'PAUSED'|'INTERRUPTED'|'FINISHED'|'ERROR';
const meetingId='00000000-0000-4000-8000-000000000001';
const emptyRecord=():RecordingSession=>({schema:1,actor:'actor-1',meeting:meetingId,finished:false,parts:[]});
const part=(state:PartState):RecordingPart=>({id:'part-1',index:1,name:'录音片段 01.webm',mime:'audio/webm',state,elapsedMs:9000,savedElapsedMs:9000,bytes:1024,chunks:['chunk-1'],incomplete:false});

async function renderPage(component:unknown,applicationKey:unknown,phase:Phase='IDLE',record=emptyRecord()){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/meetings',component:{render:()=>null}},{path:'/recording/:id?',component:{render:()=>null}}]});
 await router.push('/recording/'+meetingId);await router.isReady();
 const current=ref({actor:{enterpriseId:'actor-1',status:'ACTIVE',roles:['USER'] as const},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00.000Z'});
 const recorder={record:shallowReadonly(ref(record)),phase:shallowReadonly(ref(phase)),error:shallowReadonly(ref<string|null>(null)),partElapsed:shallowReadonly(ref(9000)),elapsed:shallowReadonly(ref(9000)),attach:async()=>{},start:async()=>{},pause:()=>{},resume:async()=>{},interrupt:async()=>{},end:async()=>{},retry:async()=>{},files:async()=>[],finish:async()=>{},invalidate:async()=>{},logout:async()=>{},dispose:()=>{}};
 const command={state:shallowReadonly(ref('EMPTY')),snapshot:shallowReadonly(ref(null)),ack:shallowReadonly(ref(null))};
 const app={session:{current:shallowReadonly(current),status:shallowReadonly(ref('ACTIVE')),refresh:async()=>{},invalidate:()=>{},logout:async()=>{},onLogout:()=>()=>{}},recorder,recordingStore:{latest:async()=>null,load:async()=>null,save:async()=>{},append:async()=>{},file:async()=>new File([], 'empty.webm'),clear:async()=>{}},create:{command,submit:async()=>({id:meetingId})},uploadQueue:{ready:shallowReadonly(ref(false)),blocked:shallowReadonly(ref(false)),items:shallowReadonly(ref([])),importCommand:command,open:async()=>{},add:async()=>{},retry:async()=>{},submitImport:async()=>{}},setMeetingScope:()=>{},meetingScope:shallowReadonly(ref(meetingId)),infoScopeReady:shallowReadonly(ref(true)),info:{},journals:{},options:{},dispose:()=>{}};
 const html=await renderToString(createSSRApp({render:()=>h(component as any)}).provide(applicationKey as any,app).use(router));
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
  assert.match(empty,/未接入服务端保存/);
  assert.match(empty,/全部录音段 · 当前累计内容 · 模拟识别/);
  assert.match(empty,/实时文字和AI处理为示例模拟，未接入真实识别及服务端保存/);
  assert.match(empty,/rect x="5" y="5" width="14" height="14" rx="2"/);
  assert.match(empty,/M12 4v14m-5-5 5 5 5-5M5 21h14/);
  const active=await renderPage(component,applicationKey,'RECORDING',{...emptyRecord(),parts:[part('RECORDING')]});
  assert.match(active,/暂停录音/);
  assert.match(active,/全部录音段 · 识别中 · 模拟识别/);
  const interrupted=await renderPage(component,applicationKey,'INTERRUPTED',{...emptyRecord(),parts:[part('INTERRUPTED')]});
  assert.match(interrupted,/已中断，内容保留/);
  assert.match(interrupted,/本段已保存，可开始下一段。/);
  const finished=await renderPage(component,applicationKey,'FINISHED',{...emptyRecord(),finished:true,parts:[part('SAVED')]});
  assert.match(finished,/已保存至服务端/);
 }finally{await server.close();}
});

test('production recording page has no fixed demo or interface bypass',()=>{
 const source=readFileSync(new URL('../../src/features/meetings/RecordingPage.vue',import.meta.url),'utf8');
 assert.doesNotMatch(source,/MTG-|mock|demoSeed|VITE_USE_RECORDING_FIXTURE/i);
 assert.match(source,/useRecorder|useApplication/);
});
