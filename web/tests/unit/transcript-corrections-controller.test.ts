import {test} from 'node:test';import assert from 'node:assert/strict';
import {useSpeechCorrections} from '../../src/features/transcript/useSpeechCorrections.js';import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',source='00000000-0000-4000-8000-000000000002',candidate='00000000-0000-4000-8000-000000000003';
function fixture(){
 const values=new Map<string,string>(),context={tab:'speech-correction-test',recovery:'RESUME_NAMESPACE' as const,storage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);}}};
 let actor:string|null='owner',allowed=true,error:unknown=null,readError:unknown=null;const current:any={meetingId:meeting,saved:{id:source,kind:'WORKING'},observation:{revisionToken:'2'},capabilities:{canSave:true},correction:{mode:'CONTROLLED_SPEECH'}};const seen:any[]=[];
 const view:any={meetingId:meeting,sourceRevisionId:source,items:[{id:candidate,status:'PENDING',canAccept:true,canReject:true,canUndo:false,operationId:null}],pendingCount:1,canPropose:true,observation:current.observation};
 const api={speakers:async()=>({meetingId:meeting,sourceRevisionId:source,observation:current.observation,items:[]}),read:async()=>view,current:async()=>{if(readError)throw readError;return current;},send:async(c:any)=>{seen.push(c);if(error)throw error;return {result:{},replayed:false} as any;}};
 const options={actor:()=>actor,meeting:()=>meeting,eligible:()=>!!actor,canMutate:()=>allowed,current:()=>current,context};
 return{api,options,context,seen,setActor:(v:string|null)=>{actor=v;},setAllowed:(v:boolean)=>{allowed=v;},fail:(v:unknown)=>{error=v;},failRead:(v:unknown)=>{readError=v;}};
}
test('lost correction decision ACK restores exact original candidate/body/key even when the current draft changes',async()=>{
 const f=fixture(),first=useSpeechCorrections(f.api as any,f.options);first.synchronize();await first.load();f.fail(Error('lost'));await assert.rejects(first.decide(candidate,'ACCEPT'));const original=f.seen[0];first.dispose();f.setAllowed(false);
 const recovered=useSpeechCorrections(f.api as any,f.options);recovered.synchronize();assert(recovered.pending.value);f.fail(null);await recovered.retry();assert.deepEqual(f.seen[1],original);assert.equal(recovered.pending.value,false);
});
test('journal denial sends nothing and ACK is never resent while fresh read is unavailable',async()=>{
 const f=fixture(),tool=useSpeechCorrections(f.api as any,f.options);tool.synchronize();await tool.load();const save=f.context.storage.setItem;f.context.storage.setItem=()=>{throw Error('quota');};await assert.rejects(tool.propose());assert.equal(f.seen.length,0);f.context.storage.setItem=save;f.failRead(Error('offline'));await assert.rejects(tool.retry());assert.equal(tool.command.state.value,'ACKNOWLEDGED');f.failRead(null);await tool.retry();assert.equal(f.seen.length,1);assert.equal(tool.pending.value,false);
});
test('permission denial immediately hides private candidates but retains the unresolved original operation',async()=>{
 const f=fixture(),tool=useSpeechCorrections(f.api as any,f.options);tool.synchronize();await tool.load();f.fail(new ApiError('http',403,'denied'));await assert.rejects(tool.decide(candidate,'REJECT'));assert.equal(tool.view.value,null);assert.equal(tool.original.value,null);assert(tool.pending.value);assert.throws(tool.discardRejected);
});
test('late suggestions never reappear after an identity change and dirty drafts cannot create new decisions',async()=>{
 const f=fixture(),tool=useSpeechCorrections(f.api as any,f.options);tool.synchronize();let finish!:(v:any)=>void;f.api.read=async()=>new Promise(resolve=>{finish=resolve;});const loading=tool.load();f.setActor('other');tool.synchronize();finish({items:[{id:candidate}]});await loading;assert.equal(tool.view.value,null);f.setAllowed(false);await assert.rejects(tool.propose());assert.equal(f.seen.length,0);
});
