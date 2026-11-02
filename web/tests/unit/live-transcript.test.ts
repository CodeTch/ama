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

test('recording presentation groups genuine part identities and keeps latest text, relative time and recoverable status',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/recording/LiveTranscript.vue')).default,events=[{eventId:'1',partId:'part-one',segmentId:'one',revision:1,status:'temporary',text:'旧临时内容',startMs:0,endMs:1000},{eventId:'2',partId:'part-one',segmentId:'one',revision:2,status:'stable',text:'非常非常重要，编号12345。',startMs:0,endMs:1000},{eventId:'3',partId:'part-two',segmentId:'two',revision:2,status:'stable',text:'第二段原始内容',startMs:9000,endMs:10000}],parts=[{partId:'part-one',partNo:1,startMs:0},{partId:'part-two',partNo:2,startMs:8000}];const render=(extra:any={})=>renderToString(createSSRApp({render:()=>h(component,{events,parts,presentation:'recording',...extra})}));const html=await render();assert.equal((html.match(/class="acr-live-segment"/g)??[]).length,2);assert.match(html,/第1段录音/);assert.match(html,/第2段录音/);assert.match(html,/00:00:01/);assert.match(html,/非常非常重要，编号12345。/);assert.doesNotMatch(html,/旧临时内容|<ol|企业身份|说话人 1/);assert.match(html,/稳定/);const delayed=await render({delayed:true,afterEventId:'3'});assert.match(delayed,/识别积压/);assert.match(delayed,/恢复断线字幕/);const empty=await render({events:[]});assert.match(empty,/acr-live-empty/);assert.match(empty,/全部录音段的实时转写将在此累计展示/);
 }finally{await server.close();}
});
