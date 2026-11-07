import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createServer} from 'vite';
import {createSSRApp,h,shallowRef} from 'vue';
import {renderToString} from 'vue/server-renderer';

const meeting='00000000-0000-4000-8000-000000000001';

function application(){
 return {
  session:{current:shallowRef({actor:{enterpriseId:'owner'}})},
  minutesScopeReady:shallowRef(true),
  journals:undefined,
  workspace:{navigation:async()=>({relation:'OWNER',navigationState:'CHECK_PRODUCER'})},
  publications:{
   authorize:async()=>true,
   current:async()=>({publication:null}),
   final:async()=>({confirmation:null}),
   readiness:async()=>({ready:false,blockers:['BODY_REQUIRED'],pendingCandidateIds:[],outstandingTaskIds:[],required:[],optionalReviewIds:[],observation:{revisionToken:'1'}}),
   send:async()=>{throw new Error('not used in SSR');}
  }
 } as any;
}

test('meeting confirmation view keeps the prototype cards and labels an unread preview as loading',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{
  const {applicationKey}=await server.ssrLoadModule('/src/application.ts');
  const component=(await server.ssrLoadModule('/src/features/minutes/PublicationPanel.vue')).default;
  const app=createSSRApp({render:()=>h(component,{meetingId:meeting,owner:false,locked:false,stage:'confirm'})});
  app.provide(applicationKey,application());
  const html=await renderToString(app);
  assert.match(html,/会议信息/);
  assert.match(html,/纪要预览/);
  assert.doesNotMatch(html,/最终确认并继续发布/);
  assert.match(html,/正在读取已保存纪要/);
  assert.doesNotMatch(html,/尚无已保存纪要/);
  assert.doesNotMatch(html,/undefined/);
 }finally{await server.close();}
});

test('confirmation checks render all twelve outcomes and bind failed targets without exposing blocker codes',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/minutes/FinalConfirmationChecks.vue')).default;
  const {finalConfirmationChecks}=await import('../../src/features/minutes/final-confirmation-checks');
  const checks=finalConfirmationChecks({ready:false,blockers:['REVIEW_REQUIRED'],required:[{targetId:meeting,reviewKind:'ACTION_FIELDS',satisfied:false}],pendingCandidateIds:[],outstandingTaskIds:[]} as any,true,{[meeting]:'确认交付负责人'});
  const html=await renderToString(createSSRApp({render:()=>h(component,{checks,busy:false})}));
  assert.match(html,/确认检查/);assert.match(html,/确认交付负责人/);assert.match(html,/负责人和截止信息/);
  assert.doesNotMatch(html,/REVIEW_REQUIRED/);assert.equal(checks.length,12);assert.equal(checks.filter(c=>c.status==='failed').length,1);
 }finally{await server.close();}
});

test('meeting confirmation styles preserve prototype geometry and narrow-screen stacking',async()=>{
 const source=await readFile(new URL('../../src/features/minutes/PublicationPanel.vue',import.meta.url),'utf8');
 assert.match(source,/\.ac-confirm-grid\{[^}]*grid-template-columns:minmax\(0,\.9fr\) minmax\(0,1.3fr\);gap:16px/);
 assert.match(source,/\.ac-confirm-body\{[^}]*line-height:1\.85/);
 assert.match(source,/\.ac-confirm-check\{[^}]*margin-top:16px/);
 assert.match(source,/@media\(max-width:760px\)\{[^}]*\.ac-confirm-grid\{grid-template-columns:minmax\(0,1fr\)\}/);
 assert.equal((source.match(/class="card ac-confirm-info"/g)??[]).length,1);
});

test('meeting confirmation keeps the production state path without a runtime demo branch',async()=>{
 const source=await readFile(new URL('../../src/features/minutes/PublicationPanel.vue',import.meta.url),'utf8');
 const start=source.indexOf(`<template v-if="stage==='confirm'">`),end=source.indexOf(`<template v-if="stage==='publish'">`);
 assert.ok(start>=0&&end>start);
 const confirmTemplate=source.slice(start,end);
 assert.match(confirmTemplate,/@click="confirmFinal"/);
 assert.doesNotMatch(confirmTemplate,/VITE_LOCAL_DEV_LOGIN/);
 assert.match(confirmTemplate,/footer v-if="owner"/);
 assert.match(confirmTemplate,/v-if="p\.final\.value\?\.confirmation"[^>]*>继续发布/);
});
