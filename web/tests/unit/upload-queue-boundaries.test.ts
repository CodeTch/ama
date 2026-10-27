import {test} from 'node:test';import assert from 'node:assert/strict';
import {createApplication} from '../../src/application.js';
import {createJournal} from '../../src/shared/composables/journal.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const meeting='00000000-0000-4000-8000-000000000001';
test('initial application route preserves original upload and import commands across browser refresh',()=>{
 const data=new Map<string,string>(),storage={getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}},tab='refresh-tab';
 const upload:CommandSnapshot={actor:'A',resource:meeting,operation:'UPLOAD_BEGIN',path:`/api/v1/meetings/${meeting}/uploads`,key:'00000000-0000-4000-8000-000000000002',body:JSON.stringify({name:'a.txt',purpose:'TRANSCRIPT_INPUT',size:1,sha256:'a'.repeat(64)})};
 const imported:CommandSnapshot={actor:'A',resource:meeting,operation:'TRANSCRIPT_IMPORT',path:`/api/v1/meetings/${meeting}/transcript-imports`,key:'00000000-0000-4000-8000-000000000003',body:JSON.stringify({expectedRevisionToken:'1',orderedFileIds:['00000000-0000-4000-8000-000000000004']})};
 storage.setItem('ama.upload-owner.v1:'+tab,JSON.stringify({actor:'A',meeting}));const uploads=createJournal(storage,{actor:'A',meeting,kind:'upload-queue',tab},(v):v is CommandSnapshot[]=>Array.isArray(v),8388608),intake=createJournal(storage,{actor:'A',meeting,kind:'import-command',tab},(v):v is CommandSnapshot=>typeof v==='object',131072);uploads.save([upload]);intake.save(imported);
 const app=createApplication('https://ama.test',{storage,tab,recovery:'RESUME_NAMESPACE'});app.setMeetingScope(meeting);assert.deepEqual(uploads.load(),{kind:'RESTORED',value:[upload]});assert.deepEqual(intake.load(),{kind:'RESTORED',value:imported});app.dispose();
});
