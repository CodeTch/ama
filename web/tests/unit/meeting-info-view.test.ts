import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h} from 'vue';import {renderToString} from 'vue/server-renderer';
test('information form makes saved roster confirmation distinct and preserves uncertainty/conflict actions',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{const component=(await server.ssrLoadModule('/src/features/meetings/MeetingInfoForm.vue')).default;const props={value:{name:'会议',summary:'草稿',category:null,occurredOn:null,rosterDraftIds:['a'],externalRosterNames:[]},dirty:true,busy:false,blocked:false,state:'EMPTY',operation:null,conflict:false,canConfirm:false,categories:[],people:[],optionsBusy:false,optionsError:null,categoryMore:false,peopleMore:false,error:null,storageStatus:'SAVED'};const render=(extra:Record<string,unknown>)=>renderToString(createSSRApp({render:()=>h(component,{...props,...extra})}));
 const normal=await render({});assert.match(normal,/会议名称/);assert.match(normal,/>保存</);assert.doesNotMatch(normal,/确认已保存名单/);assert.match(normal,/最终确认时确认名单并更新参会权限/);
 const confirmable=await render({dirty:false,canConfirm:true});assert.match(confirmable,/确认已保存名单/);assert.match(confirmable,/保存后的名单才会更新参会权限/);
 const unknown=await render({state:'UNCERTAIN',operation:'SAVE_INFO'});assert.match(unknown,/重试原请求/);assert.match(unknown,/结果尚未确认/);
 const conflict=await render({conflict:true,state:'CONFLICT',conflictToken:'9007199254740993',conflictValue:{...props.value,name:'最新服务器名称',rosterDraftIds:['new-enterprise-person'],externalRosterNames:['新增外部人员']}});assert.match(conflict,/保留草稿并应用到最新版本/);assert.match(conflict,/放弃本地修改/);assert.match(conflict,/最新服务器名称/);assert.match(conflict,/new-enterprise-person/);assert.match(conflict,/新增外部人员/);assert.match(conflict,/9007199254740993/);
 assert.match(await render({blocked:true}),/恢复记录/);
 }finally{await server.close();}
});
