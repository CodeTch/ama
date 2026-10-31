import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h,ref,shallowReadonly} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('live transcript renders temporary and stable events without inventing speakers',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/recording/LiveTranscript.vue')).default;
  const html=await renderToString(createSSRApp({render:()=>h(component,{events:[{eventId:'e1',segmentId:'s1',revision:1,status:'temporary',text:'你好',startMs:0,endMs:100},{eventId:'e2',segmentId:'s2',revision:1,status:'stable',text:'你好。',startMs:100,endMs:200}]})}));
  assert.match(html,/你好。/);assert.match(html,/临时/);assert.doesNotMatch(html,/真实企业/);
 }finally{await server.close();}
});
