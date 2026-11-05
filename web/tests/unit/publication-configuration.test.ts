import {test} from 'node:test';import assert from 'node:assert/strict';import {ref} from 'vue';
const meeting='00000000-0000-4000-8000-000000000001';
const rules={maxCount:2,maxLength:4,allowEmpty:true,trim:true,caseSensitive:true};
const location={providerId:'kb',libraryId:'library',folderId:'root'};
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
