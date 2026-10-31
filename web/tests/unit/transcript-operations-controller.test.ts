import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useTranscriptOperations} from '../../src/features/transcript/useTranscriptOperations.js';
import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',source='00000000-0000-4000-8000-000000000002',operation='00000000-0000-4000-8000-000000000003',occurrence='a'.repeat(64);
function fixture(){
 const values=new Map<string,string>(),context={tab:'test-manual-tools',recovery:'RESUME_NAMESPACE' as const,storage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);}}};
 let actor:string|null='owner',allowed=true,current:any={meetingId:meeting,saved:{id:source,kind:'WORKING',segments:[]},observation:{revisionToken:'2'},capabilities:{canSave:true}};
 const seen:any[]=[],searches:any[]=[];let sendError:unknown=null,readError:unknown=null;
 const api={search:async(_m:string,_s:string,query:string)=>{searches.push(query);return {meetingId:meeting,sourceRevisionId:source,expectedRevisionToken:'2',query,complete:true,occurrences:[{occurrenceId:occurrence,segmentId:source,startScalar:1,endScalar:3}]};},send:async(c:any)=>{seen.push(c);if(sendError)throw sendError;return {result:{meetingId:meeting,revisionId:operation,operationId:operation}} as any;},current:async()=>{if(readError)throw readError;return current;}};
 const options={actor:()=>actor,meeting:()=>meeting,eligible:()=>!!actor,canMutate:()=>allowed,current:()=>current,context};
 return {context,api,options,seen,setActor:(v:string|null)=>{actor=v;},setAllowed:(v:boolean)=>{allowed=v;},setCurrent:(v:any)=>{current=v;},failSend:(v:unknown)=>{sendError=v;},failRead:(v:unknown)=>{readError=v;}};
}
test('lost replacement ACK reloads immutable original request and key even with a later readonly revision',async()=>{
 const f=fixture(),first=useTranscriptOperations(f.api as any,f.options);first.synchronize();await first.search('重名');f.failSend(Error('lost ACK'));await assert.rejects(first.replace('新名',[occurrence]));
 const original=f.seen[0];assert.equal(first.pending.value,true);first.dispose();f.setAllowed(false);f.setCurrent({...f.options.current(),saved:{id:operation,kind:'WORKING',segments:[]},observation:{revisionToken:'3'}});
 const reload=useTranscriptOperations(f.api as any,f.options);reload.synchronize();assert.equal(reload.original.value?.find,'重名');assert.equal(reload.original.value?.replacement,'新名');f.failSend(null);await reload.retry();assert.deepEqual(f.seen[1],original);assert.equal(reload.pending.value,false);
 const clean=useTranscriptOperations(f.api as any,f.options);clean.synchronize();assert.equal(clean.original.value,null);
});
test('journal denial sends nothing; definite conflicts require explicit discard',async()=>{
 const f=fixture(),tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();await tools.search('重名');const set=f.context.storage.setItem;f.context.storage.setItem=()=>{throw Error('quota');};await assert.rejects(tools.replace('新名',[occurrence]));assert.equal(f.seen.length,0);
 f.context.storage.setItem=set;f.failSend(new ApiError('http',409,'changed'));await assert.rejects(tools.retry());assert.equal(tools.command.state.value,'CONFLICT');assert.equal(tools.pending.value,true);tools.discardRejected();assert.equal(tools.pending.value,false);
});
test('ACK requires fresh read before clearing journal and never resends after read failure',async()=>{
 const f=fixture(),tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();await tools.search('重名');f.failRead(Error('offline'));await assert.rejects(tools.replace('新名',[occurrence]));assert.equal(tools.command.state.value,'ACKNOWLEDGED');assert.equal(f.seen.length,1);assert.equal(tools.pending.value,true);
 f.failRead(null);await tools.retry();assert.equal(f.seen.length,1);assert.equal(tools.pending.value,false);
});
test('stale search results and changed actors cannot expose or submit old content',async()=>{
 const f=fixture();let finish!:(v:any)=>void;f.api.search=async()=>new Promise(resolve=>{finish=resolve;});const tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();const searching=tools.search('重名');f.setActor('other');tools.synchronize();finish({meetingId:meeting,sourceRevisionId:source,expectedRevisionToken:'2',query:'重名',complete:true,occurrences:[]});await searching;assert.equal(tools.matches.value,null);assert.equal(tools.original.value,null);await assert.rejects(tools.replace('新名',[occurrence]));assert.equal(f.seen.length,0);
});
test('out of order search is ignored and changed source blocks a new operation',async()=>{
 const f=fixture(),resolvers:Array<(v:any)=>void>=[];f.api.search=async()=>new Promise(resolve=>resolvers.push(resolve));const tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();const a=tools.search('甲'),b=tools.search('乙');const result=(query:string)=>({meetingId:meeting,sourceRevisionId:source,expectedRevisionToken:'2',query,complete:true,occurrences:[{occurrenceId:occurrence,segmentId:source,startScalar:0,endScalar:1}]});resolvers[1]!(result('乙'));await b;resolvers[0]!(result('甲'));await a;assert.equal(tools.matches.value?.query,'乙');
 f.setCurrent({...f.options.current(),observation:{revisionToken:'3'}});await assert.rejects(tools.replace('丙',[occurrence]));assert.equal(f.seen.length,0);
});
test('unknown operations cannot be discarded and identity change fences a late ACK read',async()=>{
 const f=fixture(),tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();await tools.search('重名');f.failSend(Error('unknown'));await assert.rejects(tools.replace('新名',[occurrence]));assert.throws(()=>tools.discardRejected());
 let accept!:(v:any)=>void;f.failSend(null);f.api.current=async()=>new Promise(resolve=>{accept=resolve;});const retry=tools.retry();await new Promise(resolve=>setImmediate(resolve));f.setActor('other');tools.synchronize();accept(f.options.current());await assert.rejects(retry);assert.equal(tools.original.value,null);assert.equal(tools.matches.value,null);
});

test('current permission denial hides private originals and matches while preserving the unresolved journal',async()=>{
 const f=fixture(),tools=useTranscriptOperations(f.api as any,f.options);tools.synchronize();await tools.search('重名');f.failSend(new ApiError('http',403,'denied'));await assert.rejects(tools.replace('新名',[occurrence]));assert.equal(tools.original.value,null);assert.equal(tools.matches.value,null);assert.equal(tools.pending.value,true);assert.throws(()=>tools.discardRejected());
});
