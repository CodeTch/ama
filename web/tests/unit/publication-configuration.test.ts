import {test} from 'node:test';import assert from 'node:assert/strict';import {ref} from 'vue';
import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001';
const rules={maxCount:2,maxLength:4,allowEmpty:true,trim:true,caseSensitive:true};
const location={providerId:'kb',libraryId:'library',folderId:'root'};
test('an unsaved tag draft survives component disposal and refresh without overwriting a newer server version',async()=>{
 const {usePublicationConfiguration}=await import('../../src/features/minutes/usePublicationConfiguration.js');const store=new Map<string,string>(),journal:any={tab:'00000000-0000-4000-8000-000000000009',recovery:'RESUME_NAMESPACE',storage:{getItem:(k:string)=>store.get(k)??null,setItem:(k:string,v:string)=>store.set(k,v),removeItem:(k:string)=>store.delete(k)}};let version='1',saved=['旧标签'];
 const api:any={configuration:async()=>({meetingId:meeting,configurationVersionToken:version,configuration:{...location,tags:saved},tagRules:rules})};
 const make=()=>usePublicationConfiguration(api,()=> 'owner',()=>meeting,journal);const first=make();await first.load();first.tagsText.value='我的草稿';first.dispose();version='2';saved=['其他会话'];
 const second=make();await second.load();assert.equal(second.tagsText.value,'我的草稿');assert.equal(second.current.value?.configurationVersionToken,'2');assert.equal(second.conflict.value,true);assert.equal(await second.save(location),false);second.keepDraft();assert.equal(second.conflict.value,false);second.discard();assert.equal(second.tagsText.value,'其他会话');second.dispose();
});
test('a lost save response is read back before another write and a conflict keeps exact input',async()=>{
 const {usePublicationConfiguration}=await import('../../src/features/minutes/usePublicationConfiguration.js');let version='1',saved=['旧'],mode='lost',writes=0;
 const api:any={configuration:async()=>({meetingId:meeting,configurationVersionToken:version,configuration:{...location,tags:saved},tagRules:rules}),saveConfiguration:async(_m:string,b:any)=>{writes++;version=String(Number(version)+1);if(mode==='lost'){saved=b.tags;throw new ApiError('network',0,'lost');}saved=['并发版本'];throw new ApiError('http',409,'conflict');}};
 const c=usePublicationConfiguration(api,()=> 'owner',()=>meeting);await c.load();c.tagsText.value=' 新标签 ';assert.equal(await c.save(location),true);assert.equal(writes,1);assert.equal(c.tagsText.value,'新标签');mode='conflict';c.tagsText.value='我的输入';assert.equal(await c.save(location),false);assert.equal(c.tagsText.value,'我的输入');assert.equal(c.conflict.value,true);assert.equal(writes,2);c.dispose();
});
test('saving a directory selection sends only writable IDs, version and tags, never display metadata',async()=>{
 const {usePublicationConfiguration}=await import('../../src/features/minutes/usePublicationConfiguration.js');let command:any;
 const api:any={configuration:async()=>({meetingId:meeting,configurationVersionToken:'0',configuration:null,tagRules:rules}),saveConfiguration:async(_id:string,body:any)=>{command=body;return{meetingId:meeting,configurationVersionToken:'1',configuration:{...location,tags:body.tags},tagRules:rules};}};
 const c=usePublicationConfiguration(api,()=> 'owner',()=>meeting);await c.load();c.tagsText.value='验收';
 const selected={...location,libraryName:'知识库',folderName:'根目录',path:[],description:'库说明',visibility:'读者可见'};
 assert.equal(await c.save(selected),true);assert.deepEqual(command,{...location,expectedConfigurationVersionToken:'0',tags:['验收']});c.dispose();
});
test('configuration loads per meeting, survives a fresh session, and failed save keeps tags',async()=>{
 const {usePublicationConfiguration}=await import('../../src/features/minutes/usePublicationConfiguration.js');let saved:any=null,version='0',fail=false;
 const api:any={configuration:async(id:string)=>({meetingId:id,configurationVersionToken:version,configuration:saved,tagRules:rules}),saveConfiguration:async(id:string,b:any)=>{if(fail)throw Error('保存失败');assert.equal(b.expectedConfigurationVersionToken,version);version=String(Number(version)+1);saved={...location,configurationVersionToken:version,tags:b.tags};return{meetingId:id,configurationVersionToken:version,configuration:saved,tagRules:rules};}};
 const c=usePublicationConfiguration(api,()=> 'owner',()=>meeting);await c.load();c.tagsText.value=' 项目,评审 ';assert.equal(await c.save(location),true);c.dispose();
 const restored=usePublicationConfiguration(api,()=> 'owner',()=>meeting);await restored.load();assert.equal(restored.tagsText.value,'项目, 评审');restored.tagsText.value='新标签';fail=true;assert.equal(await restored.save(location),false);assert.equal(restored.tagsText.value,'新标签');assert.equal(restored.current.value?.configurationVersionToken,'1');restored.dispose();
});
test('a response from a previous actor or meeting cannot restore private configuration',async()=>{
 const {usePublicationConfiguration}=await import('../../src/features/minutes/usePublicationConfiguration.js');const actor=ref('owner'),id=ref(meeting);let resolve!:(x:any)=>void;
 const c=usePublicationConfiguration({configuration:async()=>new Promise(r=>resolve=r)} as any,()=>actor.value,()=>id.value);const pending=c.load();actor.value='another';resolve({meetingId:meeting,configurationVersionToken:'1',configuration:{...location,tags:['private']},tagRules:rules});await pending;assert.equal(c.current.value,null);assert.equal(c.tagsText.value,'');c.dispose();
});
