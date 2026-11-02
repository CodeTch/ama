import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('speech review exposes real candidate states and only allows server-qualified undo',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});try{const panel=(await server.ssrLoadModule('/src/features/transcript/CorrectionPanel.vue')).default;
  const corrections=[{id:'a',source:'原稿',replacement:'增强。',status:'ACCEPTED',canUndo:false},{id:'b',source:'旧稿',replacement:'旧稿。',status:'STALE',canAccept:false,canReject:false},{id:'c',source:'撤销前',replacement:'撤销前。',status:'UNDONE',canUndo:false}];
  const html=await renderToString(createSSRApp({render:()=>h(panel,{corrections,busy:false})}));assert.match(html,/已过期/);assert.match(html,/已撤销/);assert.doesNotMatch(html,/>撤销<\/button>/);
 }finally{await server.close();}
});
