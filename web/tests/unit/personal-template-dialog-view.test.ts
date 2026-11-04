import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createSSRApp,h} from 'vue';
import {renderToString} from 'vue/server-renderer';

test('personal template dialog keeps prototype chapter inputs and explicit immutable recovery states',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true},appType:'custom'});
 try{
  const View=(await server.ssrLoadModule('/src/features/minutes/PersonalTemplateDialogView.vue')).default;
  const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(View,{mode:'create',name:'原模板😀',themeText:'会议决定\n行动',pending:false,busy:false,composing:false,state:'EMPTY',error:'',concealed:false,accepted:false,...extra})}));
  const normal=await render({});assert.match(normal,/新增总结模板/);assert.match(normal,/段落主题（每行一个，1—8个）/);assert.match(normal,/仅本人可见；保存并使用后，重新生成并采用才应用到正文。/);assert.match(normal,/保存并使用/);assert.doesNotMatch(normal,/尚未接入|maxlength=/);
  const pending=await render({pending:true,state:'UNCERTAIN'});assert.match(pending,/readonly/);assert.match(pending,/查回原模板保存结果/);assert.doesNotMatch(pending,/aria-label="关闭" disabled/);
  const denied=await render({concealed:true,pending:true,state:'DENIED'});assert.doesNotMatch(denied,/原模板😀|会议决定/);
  const accepted=await render({accepted:true,error:'模板已保存，本会议尚未使用。'});assert.match(accepted,/使用已保存模板/);assert.match(accepted,/模板已保存，本会议尚未使用。/);
 }finally{await server.close();}
});
