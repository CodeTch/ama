import {createApp,h,ref,nextTick} from 'vue';
import {createRouter,createMemoryHistory} from 'vue-router';
import {applicationKey,createApplication} from '../../src/application';
import '../../src/shared/styles/tokens.css';
import '../../src/shared/styles/prototype.css';
import '../../src/shared/styles/parity.css';

const modules=import.meta.glob('/src/**/*.vue');
const fixture=document.getElementById('audit-root')!;
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>null}}]});
await router.push('/meetings');
let currentApp:ReturnType<typeof createApp>|undefined;
let application:ReturnType<typeof createApplication>|undefined;

Object.assign(window,{uiAudit:{
 async mount(file:string,props:Record<string,unknown>={},authenticated=false){
  currentApp?.unmount();application?.dispose();fixture.replaceChildren();
  application=createApplication(location.origin,{tab:crypto.randomUUID(),storage:window.sessionStorage,recovery:'NEW_NAMESPACE'});
  if(authenticated){await application.session.refresh();application.setMeetingScope('00000000-0000-4000-8000-000000000001');}
  const component=(await modules['/src/'+file]!() as {default:any}).default;
  const drawer=file==='features/meetings/MeetingInfoForm.vue'&&props.presentation!=='final'?(await modules['/src/features/meetings/MeetingInfoDrawer.vue']!() as {default:any}).default:null;
  const state=ref(props),view=ref<any>(),drawerOpen=ref(true);
  const close=()=>{drawerOpen.value=false;state.value={...state.value,open:false};view.value?.close?.();};
  const renderView=()=>h(component,{...state.value,ref:view,onClose:close});
  currentApp=createApp({render:()=>drawer?h(drawer,{open:drawerOpen.value,onClose:close},{default:renderView}):renderView()});
  currentApp.provide(applicationKey,application).use(router);currentApp.mount(fixture);await nextTick();
  return {update:async(patch:Record<string,unknown>)=>{state.value={...state.value,...patch};await nextTick();},show:()=>{if(view.value?.show)view.value.show();else view.value?.showModal?.();},open:(...args:unknown[])=>view.value?.open?.(...args)};
 },
 async specimens(rows:Array<{file:string;line:number;column:number;tag:string;role:string|null;classes:string;ancestors:string[];label:string}>){
  currentApp?.unmount();fixture.replaceChildren();
  for(const file of [...new Set(rows.map(row=>row.file))])await modules['/src/'+file]!();
  for(const row of rows){
   const module=await modules['/src/'+row.file]!() as {default:{__scopeId?:string}};
   let parent=fixture;
   // Isolated specimens exercise each source control's styles without executing business commands.
   const block=document.createElement('section');block.style.cssText='position:relative;display:block;width:900px;min-height:80px;padding:20px';fixture.append(block);parent=block;
   for(const className of row.ancestors){const ancestor=document.createElement('div');ancestor.className=className;if(module.default.__scopeId)ancestor.setAttribute(module.default.__scopeId,'');parent.append(ancestor);parent=ancestor;}
   const button=document.createElement(row.tag==='RouterLink'?'a':row.tag);button.className=row.classes;button.textContent=row.label.replace(/\{\{.*?\}\}/g,'操作');button.dataset.source=`${row.file}:${row.line}:${row.column}`;if(row.role)button.setAttribute('role',row.role);
   if(module.default.__scopeId)button.setAttribute(module.default.__scopeId,'');parent.append(button);
  }
  await nextTick();
 }
}});
