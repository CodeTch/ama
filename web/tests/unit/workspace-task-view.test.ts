import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
import type {WorkspaceTaskPage} from '../../src/shared/api/workspace-tasks.js';
// Compiles the actual SFC and renders its user-facing states; this is not browser/visual qualification.
test('task view distinguishes loading, unavailable, filtered continuation and persisted task status without ACK claims',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const module=await server.ssrLoadModule('/src/features/meetings/WorkspaceTaskPage.vue');const component=module.default;const props={kind:'ALL',page:null,loading:false,error:null};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));
 const loading=await render({loading:true});assert.match(loading,/正在读取任务/);assert.doesNotMatch(loading,/本页没有/);
 const error=await render({error:'任务暂不可用'});assert.match(error,/role="alert"/);assert.match(error,/任务暂不可用/);assert.doesNotMatch(error,/本页没有/);
 const page:WorkspaceTaskPage={items:[],hasMore:true,nextCursor:'opaque',verification:'PERSISTED_RELATION'};const empty=await render({page});assert.match(empty,/本页没有符合筛选的任务/);assert.match(empty,/后续记录/);assert.match(empty,/<button[^>]*>下一页<\/button>/);assert.doesNotMatch(empty,/全部完成/);
 const rows=await render({page:{...page,items:[{taskId:'00000000-0000-4000-8000-000000000002',kind:'KNOWLEDGE_PUBLISH_V1',state:'RECONCILING',attemptToken:'2',businessRevisionToken:'9007199254740993',inputId:'00000000-0000-4000-8000-000000000003',detailPath:'/api/v1/meetings/00000000-0000-4000-8000-000000000001/publications/00000000-0000-4000-8000-000000000003'}]}});assert.match(rows,/知识库发布/);assert.match(rows,/正在核对结果/);assert.doesNotMatch(rows,/发布成功|编辑权限|已确认发布/);
 }finally{await server.close();}
});
