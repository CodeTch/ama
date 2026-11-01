import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h,shallowRef} from 'vue';import {renderToString} from 'vue/server-renderer';
test('saved editor offers Markdown and review offers three formats while restored requests remain explicit',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});
 try{const View=(await server.ssrLoadModule('/src/features/minutes/MinutesExportPanel.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application.ts');let posts=0;
  const commands={busy:shallowRef(false),available:shallowRef(true),pending:shallowRef(true),job:shallowRef(null),download:shallowRef(null),error:shallowRef(null),command:{snapshot:shallowRef({body:'{"sourceRevision":"00000000-0000-4000-8000-000000000001","format":"md"}'}),state:shallowRef('PREPARED')},start:async()=>{posts++;},recover:async()=>{posts++;}};
  for(const review of [false,true]){const app=createSSRApp({render:()=>h(View,{sourceRevision:'00000000-0000-4000-8000-000000000001',review,dirty:false})});app.provide(applicationKey,{minutesExportCommands:commands,minutesExportScopeError:shallowRef(null)});const html=await renderToString(app);assert.match(html,/Markdown/);assert.match(html,/查回原导出/);assert.equal(/PDF/.test(html),review);assert.equal(/Word/.test(html),review);}
  assert.equal(posts,0);
 }finally{await server.close();}
});
