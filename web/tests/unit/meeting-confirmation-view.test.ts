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

test('meeting confirmation view keeps the prototype card structure and empty preview state',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const {applicationKey}=await server.ssrLoadModule('/src/application.ts');
  const component=(await server.ssrLoadModule('/src/features/minutes/PublicationPanel.vue')).default;
  const app=createSSRApp({render:()=>h(component,{meetingId:meeting,owner:false,locked:false,stage:'confirm'})});
  app.provide(applicationKey,application());
  const html=await renderToString(app);
  assert.match(html,/会议信息/);
  assert.match(html,/纪要预览/);
  assert.match(html,/确认检查/);
  assert.match(html,/尚无已保存纪要/);
  assert.doesNotMatch(html,/undefined/);
 }finally{await server.close();}
});

test('meeting confirmation source keeps readiness blocking and the real final-confirm handler',async()=>{
 const source=await readFile(new URL('../../src/features/minutes/PublicationPanel.vue',import.meta.url),'utf8');
 assert.match(source,/class="card card-p ac-confirm-check"/);
 assert.match(source,/BODY_REQUIRED/);
 assert.match(source,/请先生成或保存纪要正文/);
 assert.match(source,/最终确认并继续发布/);
 assert.match(source,/@click="confirmFinal"/);
 assert.match(source,/:disabled="[^"]*canPrepareFinal/);
});
