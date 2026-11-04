import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';import {fileURLToPath} from 'node:url';
test('feedback table preserves approved seven columns badges counts and a real detail action',async()=>{
 const server=await createServer({root:fileURLToPath(new URL('../../',import.meta.url)),server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/meetings/FeedbackTableView.vue')).default;
  const props={items:[{id:'00000000-0000-4000-8000-000000000001',meetingId:'00000000-0000-4000-8000-000000000002',category:'WORDING',kind:'CORPUS',feedbackType:null,originalValue:null,suggestedValue:null,note:null,comment:'原始建议😀',state:'PENDING',revisionToken:'2',submittedAt:'2026-10-31T00:00:00.000000Z',reviewReason:'请补充'}],counts:{ALL:1,PENDING:1,ACCEPTED:0,REJECTED:0,NEEDS_MORE:0},selected:null,loading:false,error:null};
  const html=await renderToString(createSSRApp({render:()=>h(View,props)}));
  assert.equal((html.match(/<th(?:\s[^>]*)?>/g)??[]).length,7);for(const label of ['原始值','建议值 / 人员','提交时间','审核意见','需要补充','原始建议😀','请补充'])assert.ok(html.includes(label));assert.match(html,/查看反馈详情/);assert.match(html,/class="cnt"[^>]*>1/);assert.doesNotMatch(html,/服务尚未接入/);
 }finally{await server.close();}
});

test('prototype feedback shows independent original and suggested values and includes voice applications',async()=>{
 const server=await createServer({root:fileURLToPath(new URL('../../',import.meta.url)),server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/meetings/FeedbackTableView.vue')).default;
  const base={meetingId:'00000000-0000-4000-8000-000000000002',revisionToken:'1',submittedAt:'2026-11-03T00:00:00.000000Z',reviewReason:'请补充授权样本'};
  const items=[{...base,id:'00000000-0000-4000-8000-000000000003',kind:'CORPUS',category:'WORDING',feedbackType:'PRODUCT_ALIAS',originalValue:'招安债',suggestedValue:'招商安泰债券基金',note:'简称纠错',comment:'旧兼容内容不应成为建议值',state:'ACCEPTED'},
   {...base,id:'00000000-0000-4000-8000-000000000004',kind:'VOICEPRINT',category:'VOICEPRINT',feedbackType:'VOICEPRINT',originalValue:'会议匿名说话人',suggestedValue:'郑凯风',note:'',comment:'',state:'NEEDS_MORE'}];
  const html=await renderToString(createSSRApp({render:()=>h(View,{items,counts:{ALL:2,PENDING:0,ACCEPTED:1,REJECTED:0,NEEDS_MORE:1},selected:null,loading:false,error:null})}));
  for(const value of ['产品别名','招安债','招商安泰债券基金','声纹样本','会议匿名说话人','郑凯风','请补充授权样本'])assert.ok(html.includes(value),value+' must be rendered from the original prototype fields');
  assert.match(html,/(?:tag blue|blue tag)/);assert.doesNotMatch(html,/旧兼容内容不应成为建议值/);
 }finally{await server.close();}
});

test('failed feedback read is not rendered as a successful empty list or zero counts',async()=>{
 const server=await createServer({root:fileURLToPath(new URL('../../',import.meta.url)),server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/meetings/FeedbackTableView.vue')).default;
  const html=await renderToString(createSSRApp({render:()=>h(View,{items:[],counts:{ALL:0,PENDING:0,ACCEPTED:0,REJECTED:0,NEEDS_MORE:0},selected:null,loading:false,error:'暂时无法读取反馈'})}));
  assert.ok(html.includes('暂时无法读取反馈'));assert.doesNotMatch(html,/暂无反馈记录/);assert.doesNotMatch(html,/class="cnt"[^>]*>0/);
 }finally{await server.close();}
});
