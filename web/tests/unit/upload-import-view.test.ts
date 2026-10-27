import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('upload view separates original-file recovery, import acceptance and processing completion',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/import/UploadImportView.vue')).default,props={items:[],ready:true,blocked:false,error:null,importState:'EMPTY',importBusy:false,taskId:null};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));
 const empty=await render({});assert.match(empty,/选择 TXT 文件/);assert.match(empty,/最多同时上传 2 个文件/);
 const recovery=await render({items:[{key:'original-key',name:'会议.txt',size:3,status:'UNRESOLVED',error:'响应丢失',uploadId:null,fileId:null}],importState:'UNCERTAIN'});assert.match(recovery,/重新选择原文件/);assert.match(recovery,/查询原上传/);assert.match(recovery,/重试原导入请求/);assert.match(recovery,/文件清单已冻结/);assert.match(recovery,/选择 TXT 文件<input[^>]*disabled/);assert.match(recovery,/上移/);assert.match(recovery,/从本批移除/);assert.doesNotMatch(recovery,/转写已完成/);
 const ack=await render({importState:'ACKNOWLEDGED',taskId:'task-id'});assert.match(ack,/导入任务已受理/);assert.match(ack,/不表示转写已完成/);assert.match(ack,/开始新一批导入/);
 }finally{await server.close();}
});
