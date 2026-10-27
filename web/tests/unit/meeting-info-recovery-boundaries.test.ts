import {test} from 'node:test';import assert from 'node:assert/strict';
import {useMeetingInfoEditor} from '../../src/features/meetings/useMeetingInfoEditor.js';
import {createApplication} from '../../src/application.js';
import {ApiError} from '../../src/shared/api/client.js';
import {createInfoJournal} from '../../src/features/meetings/info-journal.js';
import type {SavedMeeting,InfoFields} from '../../src/shared/api/meeting-info.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',other='00000000-0000-4000-8000-000000000002';
const saved:SavedMeeting={id,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'saved',category:null,summary:null,occurredOn:null,confirmedRosterId:null,confirmedRoster:[],rosterDraftId:null,rosterDraft:null};
const fields=(name:string):InfoFields=>({name,category:null,summary:null,occurredOn:null,rosterDraftIds:[],externalRosterNames:[]});
function storage(){const data=new Map<string,string>();let fail=false;return {data,setFail:(v:boolean)=>{fail=v;},getItem:(k:string)=>{if(fail)throw new Error('unreadable');return data.get(k)??null;},setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}};}
test('failed restoration followed by unmount must never overwrite the original pending command',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const api={saved:async()=>saved,send:async()=>{throw new Error('lost');}};
 const original=useMeetingInfoEditor(api,()=> 'A',()=>id,()=>true,context);await original.reload();original.edit(fields('private pending'));await original.save();const key=original.command.snapshot.value!.key;original.dispose();const before=[...store.data.entries()];
 store.setFail(true);const failed=useMeetingInfoEditor(api,()=> 'A',()=>id,()=>true,context);await failed.reload();store.setFail(false);failed.dispose();assert.deepEqual([...store.data.entries()],before);
 const restored=useMeetingInfoEditor(api,()=> 'A',()=>id,()=>true,context);await restored.reload();assert.equal(restored.command.snapshot.value?.key,key);restored.dispose();
});
test('retry reading storage recovers original command without requiring another page reload',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},api={saved:async()=>saved,send:async()=>{throw new Error('lost');}};
 const original=useMeetingInfoEditor(api,()=> 'A',()=>id,()=>true,context);await original.reload();original.edit(fields('pending'));await original.save();const key=original.command.snapshot.value!.key;original.dispose();store.setFail(true);
 const editor=useMeetingInfoEditor(api,()=> 'A',()=>id,()=>true,context);await editor.reload();assert.equal(editor.recoveryBlocked.value,true);store.setFail(false);await editor.reload();assert.equal(editor.recoveryBlocked.value,false);assert.equal(editor.command.snapshot.value?.key,key);editor.dispose();
});
test('application account changes clear an unmounted information editor journal',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('private A'));editor.dispose();
 const old=fetch;globalThis.fetch=async()=>Response.json({actor:{enterpriseId:'B',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'});const app=createApplication('https://ama.test',context);
 try{await app.session.refresh();assert.equal([...store.data.values()].some(v=>v.includes('private A')),false);}finally{app.dispose();globalThis.fetch=old;}
});
test('application meeting switch clears recovery even when next meeting has no editor',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},app=createApplication('https://ama.test',context);
 const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('private first meeting'));editor.dispose();app.setMeetingScope(other);assert.equal([...store.data.values()].some(v=>v.includes('private first meeting')),false);app.dispose();
});
test('403 on save hides private fields; authorized reload restores original request and enables exact retry',async()=>{
 let deny=true;const seen:CommandSnapshot[]=[];const editor=useMeetingInfoEditor({saved:async()=>saved,send:async request=>{seen.push(request);if(deny)throw new ApiError('http',403,'forbidden');return {id,revision:2,revisionToken:'2'};}},()=> 'A',()=>id,()=>true);
 await editor.reload();editor.edit(fields('private'));await editor.save();assert.equal(editor.draft.value===null,true);deny=false;await editor.reload();assert.equal(editor.command.state.value,'UNCERTAIN');await editor.save();assert.deepEqual(seen[1],seen[0]);editor.dispose();
});
for(const transition of ['actor','meeting','logout'] as const)test(`failed ${transition} cleanup remains required on returning to old scope, including a fresh journal instance`,async()=>{
 const base=storage();let removalFails=false;
 const store={...base,removeItem:(key:string)=>{if(removalFails)throw new Error('locked');base.removeItem(key);}},context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};
 const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('must be cleared'));await editor.save();editor.dispose();
 const journal=createInfoJournal(context);removalFails=true;
 assert.equal(transition==='actor'?journal.synchronize('B'):transition==='meeting'?journal.synchronize('A',other):journal.clear(),false);
 assert.equal(journal.synchronize('A',id),false);const cold=createInfoJournal(context);assert.notEqual(cold.load('A',id).kind,'RESTORED');removalFails=false;assert.equal(journal.synchronize('A',id),true);assert.equal([...base.data.values()].some(v=>v.includes('must be cleared')),false);assert.equal(cold.load('A',id).kind,'EMPTY');
});
test('definite account transition retains cleanup intent even when reading owner metadata fails',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('private before transition'));editor.dispose();
 const journal=createInfoJournal(context);assert.equal(journal.synchronize('A',id),true);store.setFail(true);assert.equal(journal.synchronize('B',id),false);store.setFail(false);assert.equal(journal.synchronize('A',id),true);assert.equal(journal.load('A',id).kind,'EMPTY');
});
test('same-scope temporary metadata failure does not destroy the original pending recovery',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('keep pending'));await editor.save();editor.dispose();
 const journal=createInfoJournal(context);assert.equal(journal.synchronize('A',id),true);store.setFail(true);assert.equal(journal.synchronize('A',id),false);store.setFail(false);assert.equal(journal.synchronize('A',id),true);assert.equal(journal.load('A',id).kind,'RESTORED');
});
test('cleanup intent survives refresh when metadata reads fail but storage writes remain available',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{throw new Error('lost');}},()=> 'A',()=>id,()=>true,context);await editor.reload();editor.edit(fields('must not return'));editor.dispose();const journal=createInfoJournal(context);journal.synchronize('A',id);store.setFail(true);assert.equal(journal.synchronize('B',id),false);store.setFail(false);const cold=createInfoJournal(context);assert.equal(cold.load('A',id).kind,'EMPTY');
});
