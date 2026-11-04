import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createWorkspaceApi} from '../../src/shared/api/workspace';
const id='10000000-0000-4000-8000-000000000001';
const value={meetingId:id,revisionToken:'1',accessRevisionToken:'0',stage:'ENTRY',relation:'OWNER',navigationState:'CHECK_PRODUCER',contentEligibility:'RECHECK_REQUIRED',saved:{infoRevisionId:id,rosterRevisionId:null,transcriptRevisionId:null,transcriptConfirmationId:null,minutesRevisionId:null,elementSetId:null}};
test('concurrent navigation shares only the pending read and cancellation belongs to each subscriber',async()=>{
 const calls:{signal:AbortSignal;resolve:(v:unknown)=>void}[]=[];
 const api=createWorkspaceApi({json:(_path:string,{signal}:any)=>new Promise(resolve=>calls.push({signal,resolve}))} as any);
 const a=new AbortController(),b=new AbortController();
 const first=api.navigation(id,a.signal),second=api.navigation(id,b.signal);
 await Promise.resolve();assert.equal(calls.length,1);
 a.abort();await assert.rejects(first,{name:'AbortError'});assert.equal(calls[0]!.signal.aborted,false);
 calls[0]!.resolve(value);assert.deepEqual(await second,value);
 const third=api.navigation(id,new AbortController().signal);await Promise.resolve();assert.equal(calls.length,2);calls[1]!.resolve(value);await third;
});
test('last cancellation and an identity change cannot reuse a pending private navigation read',async()=>{
 const calls:{signal:AbortSignal;resolve:(v:unknown)=>void}[]=[];let scope:object={};
 const api=createWorkspaceApi({json:(_path:string,{signal}:any)=>new Promise(resolve=>calls.push({signal,resolve}))} as any,()=>scope);
 const a=new AbortController();const first=api.navigation(id,a.signal);await Promise.resolve();a.abort();await assert.rejects(first,{name:'AbortError'});assert.equal(calls[0]!.signal.aborted,true);
 const second=api.navigation(id,new AbortController().signal);await Promise.resolve();scope={};const third=api.navigation(id,new AbortController().signal);await Promise.resolve();assert.equal(calls.length,3);assert.equal(calls[1]!.signal.aborted,true);
 calls[1]!.resolve(value);await assert.rejects(second,{name:'AbortError'});calls[2]!.resolve(value);assert.deepEqual(await third,value);
});
