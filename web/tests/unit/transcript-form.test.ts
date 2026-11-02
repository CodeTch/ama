import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('transcript form keeps conflict comparison and original recovery visible, with immutable member view',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default,props={value:[{segmentId:'id',body:'my draft',marks:['KEY']}],editable:true,busy:false,dirty:true,conflict:null,conflictToken:null,state:'UNCERTAIN',blocked:false,storageStatus:'SAVED',error:null,confirmed:false,canConfirm:true};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));const html=await render({});assert.match(html,/恢复原操作结果/);assert.match(html,/my draft/);assert.match(html,/确认转写并生成纪要/);assert.match(await render({state:'CONFLICT',conflict:[{segmentId:'id',body:'server saved',marks:[]}],conflictToken:'9007199254740993'}),/server saved/);assert.doesNotMatch(await render({editable:false,canConfirm:false}),/class="segment-input"/);assert.match(await render({editable:false,canConfirm:false}),/class="mk" disabled/);assert.doesNotMatch(await render({editable:false,canConfirm:false}),/>保存正文与标记</);
 }finally{await server.close();}
});
test('real media timestamps are actionable while document paragraphs never invent a clock',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default;const props={value:[{segmentId:'seg',body:'speech',marks:[]}],editable:false,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:false,canConfirm:false};
 const media=await renderToString(createSSRApp({render:()=>h(component,{...props,provenance:[{segmentId:'seg',sourceFileId:'file',startMs:30500,endMs:35000,speakerId:null}],playableFiles:['file']})}));assert.match(media,/00:30/);assert.match(media,/aria-label="定位原录音/);assert.doesNotMatch(media,/TXT 第/);
 const text=await renderToString(createSSRApp({render:()=>h(component,props)}));assert.match(text,/此片段无对应原录音/);assert.doesNotMatch(text,/00:30/);
 }finally{await server.close();}
});

test('the original transcript stays continuous beyond forty segments and exposes the exact edit modal',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default;const value=Array.from({length:45},(_,i)=>({segmentId:'seg-'+i,body:'第'+i+'条原始内容',marks:[]}));const html=await renderToString(createSSRApp({render:()=>h(component,{value,editable:true,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:false,canConfirm:true})}));assert.equal((html.match(/class="seg"/g)??[]).length,45);assert.doesNotMatch(html,/转写段落分页/);assert.match(html,/编辑转写片段/);assert.match(html,/aria-label="转写内容"/);assert.match(html,/placeholder="搜索转写"/);
 }finally{await server.close();}
});


test('published transcript presents the frozen readback without editing or disabled correction controls',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default;
  const html=await renderToString(createSSRApp({render:()=>h(component,{published:true,value:[{segmentId:'seg',body:'Frozen published speech',marks:['KEY']}],editable:false,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:true,canConfirm:false,provenance:[{segmentId:'seg',sourceFileId:'file',startMs:30500,endMs:35000,speakerId:null}],playableFiles:['file']})}));
  assert.match(html,/发布绑定确认转写/);
  assert.match(html,/Frozen published speech/);
  assert.match(html,/00:30/);
  assert.doesNotMatch(html,/搜索转写|批量替换|全部接受|全部拒绝|编辑转写片段|建立人工工作版本/);
 }finally{await server.close();}
});


test('published untimed text uses an unavailable clock instead of inventing an audio position',async()=>{
 const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default;const html=await renderToString(createSSRApp({render:()=>h(component,{published:true,value:[{segmentId:'text',body:'Untimed original text',marks:[]}],editable:false,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:true,canConfirm:false})}));assert.match(html,/此片段无对应原录音/);assert.match(html,/>--:--<\/button>/);assert.doesNotMatch(html,/00:00/);}finally{await server.close();}
});

test('bulk correction controls are enabled only for qualified saved pending candidates',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default,props={value:[{segmentId:'seg',body:'待校正',marks:[]}],editable:true,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:false,canConfirm:false,correctionsReady:true,correctionCount:1};
 const html=await renderToString(createSSRApp({render:()=>h(component,props)}));assert.doesNotMatch(html,/class="btn sm aci-inline-accept" disabled/);assert.doesNotMatch(html,/纠错建议服务尚未接入/);const dirty=await renderToString(createSSRApp({render:()=>h(component,{...props,dirty:true})}));assert.match(dirty,/aci-inline-accept" disabled/);
 }finally{await server.close();}
});

test('versioned human speaker labels are used in both workbench and published readback',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true,hmr:false},appType:'custom'});try{const component=(await server.ssrLoadModule('/src/features/transcript/TranscriptForm.vue')).default,props={value:[{segmentId:'seg',body:'语音内容',marks:[]}],editable:false,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:true,canConfirm:false,provenance:[{segmentId:'seg',sourceFileId:'file',startMs:1000,endMs:8000,speakerId:'speaker'}],speakerLabels:{speaker:'人工核对王工'}};
 for(const published of [false,true]){const html=await renderToString(createSSRApp({render:()=>h(component,{...props,published})}));assert.match(html,/人工核对王工/);assert.doesNotMatch(html,/文本段落|匿名说话人/);}
 }finally{await server.close();}
});
