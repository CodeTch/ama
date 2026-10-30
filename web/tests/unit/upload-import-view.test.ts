import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('upload view separates original-file recovery, import acceptance and processing completion',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/import/UploadImportView.vue')).default,props={items:[],ready:true,blocked:false,error:null,importState:'EMPTY',importBusy:false,taskId:null};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));
 const empty=await render({});assert.match(empty,/选择会议文件/);assert.match(empty,/最多16个文件/);
 const recovery=await render({items:[{key:'original-key',name:'会议.txt',size:3,status:'UNRESOLVED',error:'响应丢失',uploadId:null,fileId:null}],importState:'UNCERTAIN'});assert.match(recovery,/重新选择原文件/);assert.match(recovery,/查询原上传/);assert.match(recovery,/重试原导入请求/);assert.match(recovery,/文件清单已冻结/);assert.match(recovery,/<input[^>]*aria-label="选择会议文件"[^>]*disabled/);assert.match(recovery,/上移/);assert.match(recovery,/从本批移除/);assert.doesNotMatch(recovery,/转写已完成/);
 const ack=await render({importState:'ACKNOWLEDGED',taskId:'task-id'});assert.match(ack,/导入任务已受理/);assert.match(ack,/不表示转写已完成/);assert.match(ack,/开始新一批导入/);
 }finally{await server.close();}
});
test('import main section uses final prototype copy and keeps limits out of the empty visual layout',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const view=(await server.ssrLoadModule('/src/features/import/UploadImportView.vue')).default;const html=await renderToString(createSSRApp({render:()=>h(view,{items:[],ready:true,blocked:false,error:null,importState:'EMPTY',importBusy:false,taskId:null})}));assert.match(html,/aci-import-section/);assert.match(html,/支持多段录音及多文件添加/);assert.match(html,/选择或拖入会议录音、视频或转写文件/);assert.match(html,/音频：MP3 \/ WAV \/ M4A \/ AAC \/ FLAC \/ OGG \/ WEBM/);assert.match(html,/文本：TXT \/ DOC \/ DOCX \/ PDF \/ PPTX \/ SRT \/ VTT/);assert.doesNotMatch(html,/>必填</);assert.doesNotMatch(html,/>开始转写</);assert.match(html,/\.doc,/);assert.match(html,/\.pdf,/);
 }finally{await server.close();}
});
