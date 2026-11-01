import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h,shallowRef} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('published readback exposes core and body tabs with saved elements initially visible and editing absent',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});
 let state:any;
 try{
  const {applicationKey}=await server.ssrLoadModule('/src/application.ts');
  const component=(await server.ssrLoadModule('/src/features/minutes/MinutesWorkspace.vue')).default;
  const id='00000000-0000-4000-8000-000000000001';
  const app=createSSRApp({render:()=>h({...component,setup(props:any,context:any){
   state=component.setup(props,context);
   state.publicCurrent.value={body:{id,title:'Frozen title',body:'Frozen body'},setId:id,elements:{OVERVIEW:[{id,body:'Frozen overview'}],DISCUSSION:[],CONCLUSION:[],ACTION:[],RISK:[],OPEN_QUESTION:[],DISAGREEMENT:[]},evidence:[],sensitive:[],background:[],observation:{revisionToken:'1',readOnlyReason:'PUBLISHED'}};
   return state;
  }},{meetingId:id,owner:false,stage:'published'})});
  app.provide(applicationKey,{session:{current:shallowRef(null)},minutesScopeReady:shallowRef(true),minutesSaved:{},publications:{}});
  const html=await renderToString(app);
  const tabs=html.match(/<div[^>]*class="am-header-group"[^>]*>/)?.[0];
  assert.ok(tabs,'Published view must provide the core/body tab container');
  assert.doesNotMatch(tabs,/display:\s*none/,'Published readers need a visible way to switch saved content');
  assert.match(html,/aria-label="纪要内容"/);
  const core=html.match(/<div[^>]*class="core-layout am-layout"[^>]*>/)?.[0];
  assert.ok(core);
  assert.doesNotMatch(core,/display:\s*none/,'Open the published core view shown in the adopted prototype');
  assert.match(html,/Frozen overview/);
  assert.doesNotMatch(html,/aria-label="编辑要素"|aria-label="编辑正文"|aria-label="局部重新生成"/);
 }finally{state?.editor.dispose();state?.actions.dispose();await server.close();}
});
