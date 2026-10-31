import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('manual operations show selected locations and preserve readonly original recovery without invented previews',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});try{const View=(await server.ssrLoadModule('/src/features/transcript/TranscriptOperationsDialog.vue')).default;
 const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(View,{matches:null,segments:[],pending:false,busy:false,searching:false,canMutate:true,state:'EMPTY',original:null,error:null,...extra})}));
 const normal=await render({});assert.match(normal,/按位置替换/);assert.match(normal,/撤销当前工作版本/);assert.match(normal,/纠错建议服务尚未接入/);
 const pending=await render({pending:true,state:'UNCERTAIN',original:{find:'重名',replacement:'新名',occurrenceIds:['a']}});assert.match(pending,/重试原修订请求/);assert.match(pending,/原查找：重名/);assert.match(pending,/选中 1 处/);assert.doesNotMatch(pending,/data-occurrence/);assert.match(pending,/readonly/);assert.match(pending,/aria-label="关闭" disabled/);
 const many=Array.from({length:120},(_,i)=>({occurrenceId:String(i),segmentId:'segment',startScalar:1,endScalar:3}));const results=await render({matches:{query:'重名',complete:true,occurrences:many},segments:[{segmentId:'segment',body:'😀重名后文'}]});assert.equal((results.match(/data-occurrence=/g)??[]).length,50);assert.match(results,/共 120 处/);assert.match(results,/😀<mark[^>]*>重名/);
 }finally{await server.close();}
});
