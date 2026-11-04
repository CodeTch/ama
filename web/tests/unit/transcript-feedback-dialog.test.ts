import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h,ref} from 'vue';import {renderToString} from 'vue/server-renderer';import {fileURLToPath} from 'node:url';
test('correction feedback uses the six original prototype types and independent editable values',async()=>{
 const server=await createServer({root:fileURLToPath(new URL('../../',import.meta.url)),server:{middlewareMode:true},appType:'custom'});
 try{const View=(await server.ssrLoadModule('/src/features/transcript/TranscriptFeedbackDialog.vue')).default,{applicationKey}=await server.ssrLoadModule('/src/application');
 const app=createSSRApp({render:()=>h(View,{meetingId:'00000000-0000-4000-8000-000000000001',revisionToken:'57',segmentId:'00000000-0000-4000-8000-000000000002',source:'招安债',replacement:'招商安泰债券基金'})});app.provide(applicationKey,{operationsCommands:{pending:ref(false),available:ref(true),command:{state:ref('EMPTY')}}});
 const html=await renderToString(app);for(const label of ['提交语料反馈','人名纠错','部门纠错','产品纠错','金融术语纠错','产品别名','术语读音','原始值','建议值','补充说明','招安债','招商安泰债券基金','可选：说明语境或读音','取消','提交反馈'])assert.ok(html.includes(label),label);assert.equal((html.match(/<option /g)??[]).length,6);assert.doesNotMatch(html,/来源版本|提交版本反馈|反馈内容/);assert.match(html,/two-col/);
 }finally{await server.close();}
});
