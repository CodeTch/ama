import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('batch topic defaults to keep and preserves the approved batch-only choices',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/meetings/TopicDialogView.vue')).default;
  const render=(names:string[],selected:string)=>renderToString(createSSRApp({render:()=>h(View,{mode:'assign',name:'',description:'',selected,topics:[{id:'35ba4322-9725-4383-8d8a-82c65d086a6a',name:'年度复盘',description:'会议归集',revisionToken:'0'}],meetingNames:names,pending:false,busy:false,composing:false,state:'EMPTY',error:null})}));
  const batch=await render(['会议一','会议二'],'__keep');
  assert.match(batch,/批量调整专题（2 场会议）/);assert.match(batch,/value="__keep"[^>]*checked/);assert.match(batch,/移动到所选专题：/);assert.match(batch,/移出专题（不归入任何专题）/);assert.match(batch,/>应用<\/button>/);
  assert.doesNotMatch(batch,/会议：会议一/);assert.doesNotMatch(batch,/快速创建一级专题/);assert.doesNotMatch(batch,/会议归集/);
  const single=await render(['会议一'],'');assert.match(single,/会议：会议一/);assert.match(single,/暂不归入专题/);assert.match(single,/快速创建一级专题/);assert.match(single,/会议归集/);assert.match(single,/class="radio-dot"/);
 }finally{await server.close();}
});
test('known nonempty topic explains the real member count before a delete can be offered',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});try{
  const View=(await server.ssrLoadModule('/src/features/meetings/TopicDialogView.vue')).default;
  const html=await renderToString(createSSRApp({render:()=>h(View,{mode:'delete',name:'原专题',description:'',selected:'',topics:[],meetingNames:[],pending:false,busy:false,composing:false,state:'EMPTY',error:null,deleteCount:2})}));
  assert.match(html,/无法删除专题/);assert.match(html,/仍有 2 场会议/);assert.match(html,/请先将会议移出该专题/);assert.match(html,/>知道了<\/button>/);assert.doesNotMatch(html,/>删除<\/button>/);
 }finally{await server.close();}
});
