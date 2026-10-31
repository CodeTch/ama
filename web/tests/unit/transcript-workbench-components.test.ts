import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('speaker and correction panels keep anonymous and pending states explicit',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const speaker=(await server.ssrLoadModule('/src/features/transcript/SpeakerEditor.vue')).default;const correction=(await server.ssrLoadModule('/src/features/transcript/CorrectionPanel.vue')).default;
  const html=await renderToString(createSSRApp({render:()=>h('div',[h(speaker,{speakers:[{id:'s',label:'匿名说话人',enterpriseId:null,anonymous:true}]}),h(correction,{corrections:[{id:'c',source:'计划',replacement:'方案',status:'PENDING'}]})])}));
  assert.match(html,/匿名/);assert.match(html,/待处理 1/);assert.match(html,/接受/);
 }finally{await server.close();}
});
