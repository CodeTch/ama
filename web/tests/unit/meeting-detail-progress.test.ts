import {test} from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';
import {createServer} from 'vite';import {parse,compileScript,compileTemplate} from '@vue/compiler-sfc';
import ts from 'typescript';
import * as Vue from 'vue';import {createMemoryHistory,createRouter} from 'vue-router';
test('actual detail refreshes saved progress without unmounting a draft and clears it on scope loss',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});const originals:Array<()=>void>=[];let app:any;
 try{
  const {applicationKey}=await server.ssrLoadModule('/src/application.ts');
  async function client(path:string){const c=(await server.ssrLoadModule(path)).default;const {descriptor}=parse(await readFile(new URL('../../'+path.slice(1),import.meta.url),'utf8'));const bindingMetadata=compileScript(descriptor,{id:path}).bindings;
   const raw=compileTemplate({source:descriptor.template!.content,filename:path,id:path,compilerOptions:{bindingMetadata}}).code;const code=ts.transpileModule(raw,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText.replace(/import \{([^}]+)\} from "vue"/g,(_m,n)=>'const {'+n.replace(/\bas\b/g,':')+'}=Vue;').replace('export function render','return function render');
   const old=c.render;c.render=new Function('Vue',code)(Vue);originals.push(()=>c.render=old);return c;}
  const detail=await client('/src/features/meetings/MeetingDetail.vue');await client('/src/features/meetings/MeetingProgress.vue');
  let mounts=0,emitMinutes:any,emitTranscript:any,emitGeneration:any;const draft=Vue.ref('保留的输入法草稿');
  for(const [path,kind] of [['meetings/MeetingInfoDrawer','drawer'],['meetings/MeetingInfoEditor','info'],['meetings/WorkspaceOverview','overview'],['meetings/WorkspaceTasks','tasks'],['minutes/MinutesWorkspace','minutes'],['minutes/MinutesGenerationPanel','generation'],['transcript/TranscriptEditor','transcript'],['transcript/TranscriptPlayer','player'],['import/UploadImportPanel','upload'],['import/SavedFilesPanel','saved-files']] as const){
   const c=(await server.ssrLoadModule('/src/features/'+path+'.vue')).default,old=c.setup;c.setup=(_p:any,ctx:any)=>{if(kind==='minutes'){mounts++;emitMinutes=ctx.emit;}if(kind==='transcript')emitTranscript=ctx.emit;if(kind==='generation')emitGeneration=ctx.emit;if(kind==='upload')ctx.expose({close:()=>true,forceClose(){},showModal:()=>true,open:false});return()=>Vue.h('input',{'data-stub':kind,value:kind==='minutes'?draft.value:''});};originals.push(()=>c.setup=old);
  }
  const meeting='00000000-0000-4000-8000-000000000001';let reads=0;
  let nav:any={meetingId:meeting,stage:'ENTRY',relation:'OWNER',navigationState:'CHECK_PRODUCER',saved:{transcriptRevisionId:null,transcriptConfirmationId:null,minutesRevisionId:null,elementSetId:null},privateSections:{finalConfirmationId:null}};
  let pending:((value:any)=>void)|undefined,defer=false;const current=Vue.shallowRef<any>({actor:{enterpriseId:'owner'}});
  const value:any={session:{current,status:Vue.ref('READY')},info:{saved:async()=>({id:meeting,name:'会议',stage:'ENTRY',confirmedRosterId:null,confirmedRoster:[]})},workspace:{navigation:async()=>{reads++;if(defer)return new Promise(resolve=>pending=resolve);return nav;}},infoScopeReady:Vue.ref(true),transcriptScopeReady:Vue.ref(true),generationScopeReady:Vue.ref(true),minutesScopeReady:Vue.ref(true),setMeetingScope:()=>{}};
  const renderer=Vue.createRenderer<any,any>({createElement:tag=>({tag,props:{},style:{},close(){},children:[]}),createText:text=>({text}),createComment:text=>({comment:text}),setText:(n,text)=>n.text=text,setElementText:(n,text)=>{n.text=text;n.children=[];},parentNode:n=>n.parent,nextSibling:n=>n.parent?.children[n.parent.children.indexOf(n)+1]??null,insertStaticContent(text,p,a){const n={text,parent:p};const at=a?p.children.indexOf(a):-1;p.children.splice(at<0?p.children.length:at,0,n);return[n,n];},patchProp:(n,key,_old,v)=>n.props[key]=v,insert(n,p,anchor){if(n.parent){const at=n.parent.children.indexOf(n);if(at>=0)n.parent.children.splice(at,1);}n.parent=p;const at=anchor?p.children.indexOf(anchor):-1;p.children.splice(at<0?p.children.length:at,0,n);},remove(n){const at=n.parent?.children.indexOf(n);if(at>=0)n.parent.children.splice(at,1);}});
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/meeting/:id/:stage?',component:detail}]});await router.push('/meeting/'+meeting);await router.isReady();app=renderer.createApp(detail);app.use(router);app.provide(Vue.ssrContextKey,{modules:new Set()});app.provide(applicationKey,value);const root={children:[]};app.mount(root);
  const flush=async()=>{for(let n=0;n<15;n++){await Promise.resolve();await Vue.nextTick();}};await flush();const nodes=(n:any):any[]=>[n,...(n.children??[]).flatMap(nodes)];const steps=()=>nodes(root).filter(n=>n.props?.['data-step']).map(n=>n.props['data-complete']);
  assert.deepEqual(steps(),[false,false,false,false,false]);assert.equal(mounts,1);
  nav={...nav,saved:{...nav.saved,transcriptRevisionId:'saved',transcriptConfirmationId:'confirmed'}};emitTranscript('changed');await flush();assert.deepEqual(steps(),[true,true,false,false,false]);
  nav={...nav,saved:{...nav.saved,minutesRevisionId:'minutes',elementSetId:'elements'}};emitMinutes('changed');await flush();assert.deepEqual(steps(),[true,true,true,false,false]);
  defer=true;emitMinutes('changed');await flush();assert.ok(pending);assert.equal(mounts,1);assert.equal(nodes(root).find(n=>n.props?.['data-stub']==='minutes')?.props.value,draft.value);
  nav={...nav,privateSections:{finalConfirmationId:'frozen'}};pending!(nav);await flush();assert.deepEqual(steps(),[true,true,true,true,false]);assert.equal(mounts,1);assert.ok(reads>=4);
  emitMinutes('published');await flush();assert.deepEqual(steps(),[true,true,true,true,true]);current.value=null;await flush();assert.deepEqual(steps(),[]);
 }finally{try{app?.unmount();}finally{for(const restore of originals.reverse())restore();await server.close();}}
});
