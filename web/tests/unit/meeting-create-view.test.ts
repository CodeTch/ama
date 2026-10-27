import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('creation UI distinguishes uncertain request, lookup observation, ACK and blocked recovery',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/meetings/MeetingCreatePanel.vue')).default;const props={state:'EMPTY',busy:false,blocked:false,storageStatus:'EMPTY',cleanupFailed:false,message:null,discovery:null,discovering:false,discoveryError:false};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));
 assert.match(await render({}),/创建并打开/);
 const pending=await render({state:'UNCERTAIN',discovery:'FOUND'});assert.match(pending,/重试原请求/);assert.match(pending,/查到原请求关联/);assert.doesNotMatch(pending,/创建成功|已确认创建/);
 const ack=await render({state:'ACKNOWLEDGED'});assert.match(ack,/打开已创建会议/);
 const blocked=await render({blocked:true,storageStatus:'INVALID'});assert.match(blocked,/恢复记录暂无法读取/);assert.match(blocked,/disabled/);assert.match(blocked,/重新读取恢复记录/);
 const storage=await render({state:'UNCERTAIN',storageStatus:'UNAVAILABLE'});assert.match(storage,/刷新或关闭页面可能无法恢复/);
 }finally{await server.close();}
});
