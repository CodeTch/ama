import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h,shallowRef} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('restored template workspace shows immutable original chapter values without automatically applying them',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/minutes/PersonalTemplateWorkspace.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application.ts');
  const body={name:'原模板😀',themes:['结论','行动']},snapshot={operation:'PERSONAL_TEMPLATE_CREATE',actor:'owner',resource:'6621f7bd-88eb-4212-90a5-5c87501d87a5',path:'/api/v1/templates',key:'00000000-0000-4000-8000-000000000004',body:JSON.stringify(body)};let applied=0;
  const manager={pending:shallowRef(true),busy:shallowRef(false),available:shallowRef(true),original:shallowRef(body),page:shallowRef(null),command:{snapshot:shallowRef(snapshot),state:shallowRef('UNCERTAIN')}};
  const app=createSSRApp({render:()=>h(View,{meetingId:'00000000-0000-4000-8000-000000000001',locked:false,apply:async()=>{applied++;return true;}})});
  app.provide(applicationKey,{personalTemplateCommands:manager,personalTemplateScopeError:shallowRef(null),session:{current:shallowRef({actor:{enterpriseId:'owner'}})}});
  const html=await renderToString(app);assert.match(html,/原模板😀/);assert.match(html,/结论\n行动/);assert.match(html,/readonly/);assert.match(html,/查回原模板保存结果/);assert.equal(manager.command.snapshot.value.body,JSON.stringify(body));assert.equal(applied,0);
 }finally{await server.close();}
});
