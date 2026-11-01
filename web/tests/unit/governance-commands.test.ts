import {test} from 'node:test';import assert from 'node:assert/strict';
import {useGovernance} from '../../src/features/minutes/useGovernance.js';
import {createGovernanceApi,validGovernanceCommand} from '../../src/shared/api/governance.js';
import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',publication='00000000-0000-4000-8000-000000000002',job='00000000-0000-4000-8000-000000000003';
function fixture(){
 const values=new Map<string,string>(),context={tab:'governance-test',recovery:'RESUME_NAMESPACE' as const,storage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);}}};
 let actor:string|null='owner',failure:unknown=null;const calls:any[]=[];let status:any={governanceJobId:job,meetingId:meeting,publicationId:publication,action:'WITHDRAW',reason:'保留原理由',state:'PENDING',lastError:null,createdAt:'2026-11-01T00:00:00',finishedAt:null};
 const api={send:async(original:any)=>{calls.push(original);if(failure)throw failure;return {...status};},read:async()=>{if(failure)throw failure;return {...status};},history:async()=>[status]};
 return {context,api,calls,actor:()=>actor,setActor:(v:string|null)=>actor=v,fail:(v:unknown)=>failure=v,state:(v:unknown)=>status=v};
}
test('unknown withdrawal immediately conceals content and refresh restores the exact original request',async()=>{
 const f=fixture(),first=useGovernance(f.api as any,f.actor,()=>meeting,f.context);first.synchronize();f.fail(Error('lost ACK'));
 await assert.rejects(first.start(publication,'17','WITHDRAW','保留原理由'));assert.equal(first.blockedMeeting.value,meeting);const original=f.calls[0];first.dispose();
 const recovered=useGovernance(f.api as any,f.actor,()=>meeting,f.context);recovered.synchronize();assert.equal(recovered.blockedMeeting.value,meeting);assert.equal(f.calls.length,1);await assert.rejects(recovered.start(publication,'17','OBSOLETE','新理由'));f.fail(null);await recovered.recover();assert.deepEqual(f.calls[1],original);assert.equal(recovered.job.value?.governanceJobId,job);
});
test('accepted governance polls metadata only and terminal result never reopens ordinary content',async()=>{
 const f=fixture(),flow=useGovernance(f.api as any,f.actor,()=>meeting,f.context);flow.synchronize();await flow.start(publication,'17','WITHDRAW','保留原理由');
 f.state({...flow.job.value,state:'SUCCEEDED',finishedAt:'2026-11-01T00:00:01'});await flow.poll();assert.equal(flow.job.value?.state,'SUCCEEDED');assert.equal(flow.blockedMeeting.value,meeting);assert.equal(f.calls.length,1);
});
test('denied metadata hides private reasons and account switch cannot reuse the previous command',async()=>{
 const f=fixture(),flow=useGovernance(f.api as any,f.actor,()=>meeting,f.context);flow.synchronize();await flow.start(publication,'17','WITHDRAW','保留原理由');f.fail(new ApiError('http',403,'revoked'));await assert.rejects(flow.poll());assert.equal(flow.job.value,null);assert.equal(flow.history.value.length,0);
 f.setActor('other');flow.synchronize();assert.equal(flow.command.snapshot.value,null);assert.equal(flow.blockedMeeting.value,null);
});
test('wire rejects substituted publication/action and unbounded or extra original request fields',async()=>{
 const original={actor:'owner',resource:meeting,operation:'PUBLICATION_GOVERNANCE',path:`/api/v1/meetings/${meeting}/publications/${publication}/withdrawal`,key:job,body:JSON.stringify({expectedRevisionToken:'17',reason:'保留原理由'})};
 assert.equal(validGovernanceCommand(original,'owner',meeting),true);assert.equal(validGovernanceCommand({...original,body:JSON.stringify({expectedRevisionToken:'17',reason:'x',documentId:'foreign'})},'owner',meeting),false);
 assert.equal(validGovernanceCommand({...original,body:JSON.stringify({expectedRevisionToken:'17',reason:'x'.repeat(2049)})},'owner',meeting),false);
 const f=fixture(),api=createGovernanceApi({json:async()=>({...await f.api.read(),action:'OBSOLETE'})} as any,()=> 'owner');await assert.rejects(api.send(original),e=>e instanceof ApiError&&e.kind==='response');
});
