import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('feedback table preserves approved seven columns badges counts and a real detail action',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/meetings/FeedbackTableView.vue')).default;
  const props={items:[{id:'00000000-0000-4000-8000-000000000001',meetingId:'00000000-0000-4000-8000-000000000002',category:'WORDING',comment:'原始建议😀',state:'NEEDS_MORE',revisionToken:'2',submittedAt:'2026-10-31T00:00:00.000000Z',reviewReason:'请补充'}],counts:{ALL:1,PENDING:0,ACCEPTED:0,REJECTED:0,NEEDS_MORE:1},selected:null,loading:false,error:null};
  const html=await renderToString(createSSRApp({render:()=>h(View,props)}));
  assert.equal((html.match(/<th(?:\s[^>]*)?>/g)??[]).length,7);for(const label of ['原始值','建议值 / 人员','提交时间','审核意见','需要补充','原始建议😀','请补充'])assert.ok(html.includes(label));assert.match(html,/查看反馈详情/);assert.match(html,/class="cnt"[^>]*>1/);assert.doesNotMatch(html,/服务尚未接入/);
 }finally{await server.close();}
});
