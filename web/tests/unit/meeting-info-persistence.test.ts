import {test} from 'node:test';
import assert from 'node:assert/strict';
import {shallowRef} from 'vue';
import {useMeetingInfoEditor} from '../../src/features/meetings/useMeetingInfoEditor.js';
import type {SavedMeeting,InfoFields} from '../../src/shared/api/meeting-info.js';
import type {CommandSnapshot} from '../../src/shared/composables/useCommand.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const saved:SavedMeeting={id,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'saved',category:null,summary:null,occurredOn:null,confirmedRosterId:null,confirmedRoster:[],rosterDraftId:null,rosterDraft:null};
const fields=(name:string):InfoFields=>({name,category:null,summary:null,occurredOn:null,rosterDraftIds:[],externalRosterNames:[]});
function storage(){const data=new Map<string,string>();return {data,getItem:(k:string)=>data.get(k)??null,setItem:(k:string,v:string)=>{data.set(k,v);},removeItem:(k:string)=>{data.delete(k);}};}
test('reload restores dirty information only after authorized read and exposes changed saved version as conflict',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const};
 const api={saved:async()=>saved,send:async()=>({id,revision:2,revisionToken:'2'})};
 const first=useMeetingInfoEditor(api,()=> 'owner',()=>id,()=>true,context);await first.reload();first.edit(fields('my draft'));first.dispose();
 const second=useMeetingInfoEditor({...api,saved:async()=>({...saved,name:'other edit',revision:2,revisionToken:'2'})},()=> 'owner',()=>id,()=>true,context);
 assert.equal(second.draft.value===null,true);await second.reload();assert.equal(second.draft.value?.value.value.name,'my draft');assert.equal(second.draft.value?.baseToken.value,'1');assert.equal(second.draft.value?.conflict.value?.token,'2');second.dispose();
});
test('refresh retries original pending bytes and preserves typing after original save',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},seen:CommandSnapshot[]=[];
 const first=useMeetingInfoEditor({saved:async()=>saved,send:async command=>{seen.push(command);throw new Error('lost');}},()=> 'owner',()=>id,()=>true,context);
 await first.reload();first.edit(fields('sent'));await first.save();first.edit(fields('later'));first.dispose();
 const second=useMeetingInfoEditor({saved:async()=>({...saved,name:'sent',revision:2,revisionToken:'2'}),send:async command=>{seen.push(command);return {id,revision:2,revisionToken:'2'};}},()=> 'owner',()=>id,()=>true,context);
 await second.reload();assert.equal(second.command.state.value,'UNCERTAIN');await second.save();assert.deepEqual(seen[1],seen[0]);assert.equal(second.draft.value?.value.value.name,'later');assert.equal(second.draft.value?.dirty.value,true);assert.equal(second.draft.value?.baseToken.value,'2');second.dispose();
});
test('cold actor change clears previous private recovery and never resurrects it on return',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},actor=shallowRef('A');
 const api={saved:async()=>saved,send:async()=>({id,revision:2,revisionToken:'2'})};
 const first=useMeetingInfoEditor(api,()=>actor.value,()=>id,()=>true,context);await first.reload();first.edit(fields('private A'));first.dispose();actor.value='B';
 const second=useMeetingInfoEditor(api,()=>actor.value,()=>id,()=>true,context);await second.reload();assert.equal(second.draft.value?.value.value.name,'saved');assert.equal([...store.data.values()].some(v=>v.includes('private A')),false);actor.value='A';await second.reload();assert.equal(second.draft.value?.value.value.name,'saved');second.dispose();
});
test('unreadable recovery blocks saving rather than inventing a new command',async()=>{
 let sends=0;const context={storage:{getItem:()=>{throw new Error('denied');},setItem:()=>{},removeItem:()=>{}},tab:'tab',recovery:'RESUME_NAMESPACE' as const};
 const editor=useMeetingInfoEditor({saved:async()=>saved,send:async()=>{sends++;return {id,revision:2,revisionToken:'2'};}},()=> 'owner',()=>id,()=>true,context);
 await editor.reload();await editor.save();assert.equal(sends,0);assert.equal(editor.recoveryBlocked.value,true);editor.dispose();
});
test('roster confirmation is separate, requires saved draft, and recovers exact original command after refresh',async()=>{
 const store=storage(),context={storage:store,tab:'tab',recovery:'RESUME_NAMESPACE' as const},seen:CommandSnapshot[]=[];
 const api={saved:async()=>({...saved,rosterDraftId:id,rosterDraft:[]}),send:async(command:CommandSnapshot)=>{seen.push(command);throw new Error('lost');}};
 const editor=useMeetingInfoEditor(api,()=> 'owner',()=>id,()=>true,context);await editor.reload();editor.edit(fields('dirty'));await editor.confirmRoster();assert.equal(seen.length,0);editor.discard();await editor.confirmRoster();assert.equal(seen.length,1);assert.equal(seen[0]!.operation,'CONFIRM_ROSTER');assert.deepEqual(JSON.parse(seen[0]!.body),{expectedRevisionToken:'1'});editor.dispose();
 const restored=useMeetingInfoEditor(api,()=> 'owner',()=>id,()=>true,context);await restored.reload();await restored.confirmRoster();assert.deepEqual(seen[1],seen[0]);restored.dispose();
});
