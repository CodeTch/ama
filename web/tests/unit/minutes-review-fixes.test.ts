import {test} from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';import {createServer} from 'vite';import * as Vue from 'vue';
const meeting='00000000-0000-4000-8000-000000000001',bodyId='00000000-0000-4000-8000-000000000002',exclusion='00000000-0000-4000-8000-000000000003',candidateId='00000000-0000-4000-8000-000000000004',A='00000000-0000-4000-8000-000000000005',B='00000000-0000-4000-8000-000000000006';
const current=(revision='7')=>({body:{id:bodyId,title:'saved',body:'saved body',titleOrigin:'HUMAN',revisionToken:revision},setId:null,elements:{OVERVIEW:[],DISCUSSION:[],CONCLUSION:[],ACTION:[],RISK:[],OPEN_QUESTION:[],DISAGREEMENT:[]},evidence:[A,B].map(locator_id=>({locator_id,minutes_revision_id:bodyId,element_id:null})),sensitive:[{category:'INTERNAL_BUSINESS_DATA',locatorIds:[A,B]}],background:[],observation:{revisionToken:revision,readOnlyReason:null,currentBodyId:bodyId,currentSetId:null,exclusionRevisionId:exclusion}});
const member=(locator_id:string,category='INTERNAL_BUSINESS_DATA',reason:string|null='saved reason')=>({revision_id:exclusion,locator_id,category,reason});
const settings=(items:any[]=[],revision=exclusion)=>({items,exclusionRevision:{id:revision,revisionToken:'1'},observation:{revisionToken:'7'}});
async function harness(path:string,extra:Record<string,unknown>={}){
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});const c=(await server.ssrLoadModule(path)).default,{applicationKey}=await server.ssrLoadModule('/src/application.ts');let state:any,saved=settings(),v:any=current();const events:any[]=[],sent:any[]=[],sources:any[]=[];const actor=Vue.shallowRef<any>({actor:{enterpriseId:'owner'}}),props=Vue.reactive<any>({meetingId:meeting,current:v,locked:false,owner:true,...extra});
 const value:any={session:{current:actor},minutesScopeReady:Vue.ref(true),workspace:{navigation:async()=>({relation:'OWNER',navigationState:'CHECK_PRODUCER'})},minutesInspection:{settings:async()=>saved,source:async(_m:string,locator:string,target:any)=>{sources.push({locator,target});return{text:locator,locator:{id:locator}};}},minutesSaved:{current:async()=>v,intent:async()=>({result:{kind:'EDIT_INTENT',meetingId:meeting},replayed:false,current:{meetingId:meeting,state:'KNOWN',intentEpochToken:'1',observation:{revision:7,revisionToken:v.observation.revisionToken,accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentMinutesRevisionId:bodyId}}}),send:async(command:any)=>{sent.push(command);v={...v,observation:{...v.observation,revisionToken:String(Number(v.observation.revisionToken)+1)}};return{operation:command.operation,resultId:candidateId};}}};
 const renderer=Vue.createRenderer<any,any>({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},patchProp(){},insertStaticContent(){return[{},{}];}});
 const wrapper={...c,setup(p:any,ctx:any){state=c.setup(p,{...ctx,emit:(...args:any[])=>events.push(args)});return()=>null;}};const app=renderer.createApp({setup:()=>()=>Vue.h(wrapper,props)});app.provide(applicationKey,value);app.provide(Vue.ssrContextKey,{modules:new Set()});app.mount({});const flush=async()=>{for(let n=0;n<18;n++){await Promise.resolve();await Vue.nextTick();}};await flush();
 return {get s(){return state;},props,events,sent,sources,actor,flush,setSettings(next:any){saved=next;},async refresh(){props.current={...props.current,observation:{...props.current.observation,revisionToken:String(Number(props.current.observation.revisionToken)+1)}};await flush();},async close(){app.unmount();await server.close();}};
}
test('sensitive full replacement preserves saved-only tuples/reasons until explicit removal',async()=>{
 const h=await harness('/src/features/minutes/MinutesSensitive.vue');try{h.setSettings(settings([member(A),member(B,'POLITICAL_SENSITIVE','keep B reason')]));h.props.current={...current(),sensitive:[{category:'INTERNAL_BUSINESS_DATA',locatorIds:[A]}]};await h.refresh();assert.equal(h.s.dirty.value,false);assert.equal(h.s.prepare().items.length,2);assert.equal(h.s.choices.value.find((x:any)=>x.locatorId===B)?.savedOnly,true);
 h.s.chosen.value=h.s.chosen.value.filter((key:string)=>key!==h.s.choices.value.find((x:any)=>x.locatorId===A).key);assert.deepEqual(h.s.prepare().items,[{locatorId:B,category:'POLITICAL_SENSITIVE',reason:'keep B reason'}]);h.s.chosen.value=[];assert.deepEqual(h.s.prepare().items,[]);
 }finally{await h.close();}
});
test('sensitive draft survives root refresh, detects exclusion conflict, and discards only explicitly',async()=>{
 const h=await harness('/src/features/minutes/MinutesSensitive.vue');try{h.s.toggleAll(true);assert.equal(h.s.dirty.value,true);await h.refresh();assert.equal(h.s.chosen.value.length,2);assert.equal(h.s.dirty.value,true);
 h.setSettings(settings([member(B)],candidateId));await h.refresh();assert.equal(h.s.chosen.value.length,2);assert.ok(h.s.conflict.value);assert.equal(h.s.prepare(),null);h.s.discard();assert.equal(h.s.dirty.value,false);assert.equal(h.s.chosen.value.length,1);assert.equal(h.s.prepare().sourceExclusionRevisionId,candidateId);
 h.s.toggleAll(true);h.actor.value=null;await h.flush();assert.equal(h.s.chosen.value.length,0);assert.equal(h.s.settings.value,null);
 }finally{await h.close();}
});
test('sensitive source carries the exact second locator and its body binding',async()=>{
 const h=await harness('/src/features/minutes/MinutesSensitive.vue');try{h.s.source(B);assert.deepEqual(h.events.at(-1),['source',{kind:'BODY',id:bodyId,locatorId:B}]);}finally{await h.close();}
});
test('workspace inspection fetches the selected locator, and rejects a mismatched binding',async()=>{
 const h=await harness('/src/features/minutes/MinutesWorkspace.vue');try{h.s.inspect({kind:'BODY',id:bodyId,locatorId:B});await h.flush();assert.equal(h.sources.at(-1)?.locator,B);const count=h.sources.length;h.s.inspect({kind:'ELEMENT',id:candidateId,locatorId:B});await h.flush();assert.equal(h.sources.length,count);assert.match(h.s.error.value,/来源/);}finally{await h.close();}
});
test('workspace sensitive dirty participates in stay/save/failure/discard before leave',async()=>{
 const h=await harness('/src/features/minutes/MinutesWorkspace.vue');try{const dirty=Vue.ref(true);let saves=0,discards=0,fail=false;h.s.sensitiveRef.value={get dirty(){return dirty.value;},async save(){saves++;if(fail)return false;dirty.value=false;return true;},discard(){dirty.value=false;discards++;}};h.s.dirtyDialog.value={showModal(){},close(){}};let resolved=false;const stay=h.s.beforeLeave().then((v:boolean)=>{resolved=true;return v;});await h.flush();assert.equal(resolved,false);await h.s.decideLeave('stay');assert.equal(await stay,false);assert.equal(dirty.value,true);
 const failed=h.s.beforeLeave();fail=true;await h.s.decideLeave('save');assert.equal(dirty.value,true);fail=false;await h.s.decideLeave('save');assert.equal(await failed,true);assert.equal(saves,2);
 dirty.value=true;const discard=h.s.beforeLeave();await h.s.decideLeave('discard');assert.equal(await discard,true);assert.equal(discards,1);
 }finally{await h.close();}
});
test('keep-current candidate uses one protected reject/dispose and keeps the current body',async()=>{
 const src=await readFile(new URL('../../src/features/minutes/MinutesWorkspace.vue',import.meta.url),'utf8');assert.match(src,/@click="keepCurrentCandidate"[^>]*>保留当前稿/);
 const h=await harness('/src/features/minutes/MinutesWorkspace.vue');try{for(const [state,operation] of [['PENDING','MINUTES_REJECT'],['STALE','MINUTES_DISPOSE']]){h.s.candidate.value={candidate:{candidateId,state}};await h.s.keepCurrentCandidate();assert.equal(h.sent.at(-1)?.operation,operation);assert.equal(h.s.value.value.body.id,bodyId);assert.equal(h.s.value.value.body.body,'saved body');}assert.equal(h.sent.length,2);}finally{await h.close();}
});

test('readiness renders business labels for every actual server blocker code',async()=>{const server=await createServer({server:{middlewareMode:true},appType:'custom'});try{const c=(await server.ssrLoadModule('/src/features/minutes/PublicationPanel.vue')).default;const src=await readFile(new URL('../../../backend/adapters/src/main/java/ama/transcript/JdbcMinutesSavedQueries.java',import.meta.url),'utf8');const match=await readFile(new URL('../../src/features/minutes/PublicationPanel.vue',import.meta.url),'utf8');for(const code of new Set([...src.matchAll(/blockers.add\("([A-Z_]+)"\)/g)].map(m=>m[1]))){assert.match(match,new RegExp(code+":'[^']*[\\u4e00-\\u9fff]"),code);}assert.ok(c);}finally{await server.close();}});

test('sensitive save adopts only verified full readback, preserving choices through its root refresh',async()=>{
 let h:any,requests=0;h=await harness('/src/features/minutes/MinutesSensitive.vue',{saveSelection:async(fields:any)=>{requests++;h.setSettings(settings(fields.items.map((i:any)=>member(i.locatorId,i.category,i.reason)),candidateId));await h.refresh();return candidateId;}});try{h.s.toggleAll(true);assert.equal(await h.s.save(),true);assert.equal(requests,1);assert.equal(h.s.chosen.value.length,2);assert.equal(h.s.dirty.value,false);assert.equal(h.s.prepare().sourceExclusionRevisionId,candidateId);
 }finally{await h.close();}
});
test('sensitive failed or conflicting save keeps a complete draft, and explicit reapply preserves reasons',async()=>{
 let h:any;h=await harness('/src/features/minutes/MinutesSensitive.vue',{saveSelection:async()=>null});try{h.setSettings(settings([member(B,'POLITICAL_SENSITIVE','retained B reason')]));await h.refresh();h.s.toggleAll(true);const before=h.s.prepare();assert.equal(await h.s.save(),false);assert.deepEqual(h.s.prepare(),before);assert.equal(h.s.dirty.value,true);
 h.setSettings(settings([],candidateId));await h.refresh();assert.equal(h.s.prepare(),null);h.s.reapply();const next=h.s.prepare();assert.equal(next.sourceExclusionRevisionId,candidateId);assert.equal(next.items.find((i:any)=>i.category==='POLITICAL_SENSITIVE').reason,'retained B reason');assert.equal(next.items.length,3);
 }finally{await h.close();}
});
test('sensitive selection operates over the entire directory across twenty-row pages',async()=>{
 const h=await harness('/src/features/minutes/MinutesSensitive.vue');try{h.props.current={...current(),sensitive:[{category:'INTERNAL_BUSINESS_DATA',locatorIds:Array.from({length:45},(_,i)=>'locator-'+i)}]};await h.flush();h.s.toggleAll(true);h.s.page.value=2;assert.equal(h.s.prepare().items.length,45);h.s.chosen.value=h.s.chosen.value.filter((k:string)=>k!==h.s.choices.value[44].key);assert.equal(h.s.prepare().items.length,44);await h.refresh();assert.equal(h.s.chosen.value.length,44);
 }finally{await h.close();}
});

test('sensitive rows load distinct exact source excerpts for two locators sharing one body',async()=>{
 const h=await harness('/src/features/minutes/MinutesSensitive.vue');try{await h.flush();assert.deepEqual(h.sources.map(x=>x.locator),[A,B]);assert.equal(h.s.previews.value[A],A);assert.equal(h.s.previews.value[B],B);assert.ok(h.sources.every(x=>x.target.kind==='BODY'&&x.target.id===bodyId));}finally{await h.close();}
});
