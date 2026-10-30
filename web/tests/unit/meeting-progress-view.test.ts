import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';

test('meeting progress reflects saved facts and never treats hidden member details as final confirmation',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/meetings/MeetingProgress.vue')).default;
  const render=(value:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{value,active:(value as any).stage==='PUBLISHED'?'published':(value as any).saved.minutesRevisionId?'confirm':'transcript'})}));
  const base={stage:'ENTRY',relation:'MEMBER',saved:{transcriptRevisionId:'saved',transcriptConfirmationId:null,minutesRevisionId:null,elementSetId:null},privateSections:null};
  const working=await render(base);assert.match(working,/aria-label="会议处理环节"/);assert.match(working,/<button[^>]*data-step="2"[^>]*aria-current="step"/);assert.doesNotMatch(working,/已发布/);
  const minutes=await render({...base,saved:{...base.saved,transcriptConfirmationId:'confirmed',minutesRevisionId:'minutes',elementSetId:'elements'}});assert.match(minutes,/<button[^>]*data-step="4"[^>]*aria-current="step"/);assert.match(minutes,/data-step="4"[^>]*data-complete="false"/);
  const published=await render({...base,stage:'PUBLISHED'});assert.match(published,/data-step="5"[^>]*aria-current="step"/);assert.equal((published.match(/data-complete="true"/g)??[]).length,5);
 }finally{await server.close();}
});
