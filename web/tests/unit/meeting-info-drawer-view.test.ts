import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('information editor is presented as an accessible right-side drawer',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const component=(await server.ssrLoadModule('/src/features/meetings/MeetingInfoDrawer.vue')).default;
  const render=(props:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,props,{default:()=>h('div',{class:'editor-slot'},'编辑内容')})}));
  const closed=await render({open:false,title:'编辑会议信息'});
  assert.doesNotMatch(closed,/编辑内容/);
  const open=await render({open:true,title:'编辑会议信息'});
  assert.match(open,/role="dialog"/);
  assert.match(open,/aria-modal="true"/);
  assert.match(open,/编辑会议信息/);
  assert.match(open,/编辑内容/);
  assert.match(open,/关闭编辑会议信息/);
 }finally{await server.close();}
});
