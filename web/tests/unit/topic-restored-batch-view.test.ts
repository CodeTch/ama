import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h,shallowRef} from 'vue';import {renderToString} from 'vue/server-renderer';
test('cold restored batch derives its count from original meeting IDs without inventing names',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const Workspace=(await server.ssrLoadModule('/src/features/meetings/TopicWorkspace.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application.ts');
  const topic='00000000-0000-4000-8000-000000000001',one='00000000-0000-4000-8000-000000000002',two='00000000-0000-4000-8000-000000000003',body={topicId:topic,meetings:[{meetingId:one,expectedRevisionToken:'0'},{meetingId:two,expectedRevisionToken:'1'}]},snapshot={operation:'TOPIC_BATCH_ASSIGN',actor:'owner',resource:'2bcefbf7-c9cf-4b01-b4ad-de375d12e16f',path:'/api/v1/topics/assignments',key:'00000000-0000-4000-8000-000000000004',body:JSON.stringify(body)};
  const manager={pending:shallowRef(true),busy:shallowRef(false),available:shallowRef(true),original:shallowRef(body),topics:shallowRef([{id:topic,name:'原专题',description:null,revision:0,revisionToken:'0'}]),command:{snapshot:shallowRef(snapshot),state:shallowRef('UNCERTAIN')}};
  const app=createSSRApp({render:()=>h(Workspace)});app.provide(applicationKey,{topicCommands:manager,topicScopeError:shallowRef(null),session:{current:shallowRef({actor:{enterpriseId:'owner'}})}});
  const html=await renderToString(app);assert.match(html,/批量调整专题（2 场会议）/);assert.doesNotMatch(html,/会议：未命名/);assert.match(html,/value="00000000-0000-4000-8000-000000000001"[^>]*checked[^>]*disabled/);assert.equal(manager.command.snapshot.value.body,JSON.stringify(body));
 }finally{await server.close();}
});
