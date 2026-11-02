import {test} from 'node:test';import assert from 'node:assert/strict';import {createServer} from 'vite';import {createSSRApp,h,shallowRef} from 'vue';import {renderToString} from 'vue/server-renderer';
test('success modal is mounted outside the hidden publication view and scope cleanup closes the old success',async()=>{
 const server=await createServer({root:new URL('../../',import.meta.url).pathname,server:{middlewareMode:true,hmr:false},appType:'custom'});let state:any;
 try{const {applicationKey}=await server.ssrLoadModule('/src/application.ts'),component=(await server.ssrLoadModule('/src/features/minutes/PublicationPanel.vue')).default;
 const app=createSSRApp({render:()=>h('div',{style:'display:none'},h({...component,setup(props:any,context:any){state=component.setup(props,context);return state;}},{meetingId:'00000000-0000-4000-8000-000000000001',owner:false,locked:true,stage:'published'}))});app.provide(applicationKey,{session:{current:shallowRef(null)},minutesScopeReady:shallowRef(true),publications:{}});
 const context:any={},html=await renderToString(app,context);assert.doesNotMatch(html,/id="publication-success-title"/,'A hidden publication subtree must never own the live modal');assert.match(context.teleports?.body??'',/id="publication-success-title"/);
 let closed=0;state.successDialog.value={close(){closed++;}};state.submitted.value=true;state.successShown.value=true;state.clear();assert.equal(closed,1);assert.equal(state.submitted.value,false);assert.equal(state.successShown.value,false);
 }finally{state?.p.dispose();state?.destinations.dispose();await server.close();}
});
