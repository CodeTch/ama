import {test} from 'node:test';import assert from 'node:assert/strict';
import {useMinutesExports} from '../../src/features/minutes/useMinutesExports.js';
import {createMinutesExportsApi,validMinutesExportCommand} from '../../src/shared/api/minutes-exports.js';
import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',revision='00000000-0000-4000-8000-000000000002',task='00000000-0000-4000-8000-000000000003',file='00000000-0000-4000-8000-000000000004';
function fixture(){
 const values=new Map<string,string>(),context={tab:'exports-test',recovery:'RESUME_NAMESPACE' as const,storage:{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);}}};
 let actor:string|null='owner',failure:unknown=null;const calls:any[]=[];let status={exportJobId:task,state:'READY',sourceRevision:revision,format:'md',fileId:null,error:null} as any;
 const api={send:async(command:any)=>{calls.push(command);if(failure)throw failure;return {...status};},read:async()=>{if(failure)throw failure;return {...status};}};
 return {context,api,calls,actor:()=>actor,setActor:(v:string|null)=>{actor=v;},fail:(v:unknown)=>{failure=v;},state:(v:unknown)=>{status=v;}};
}
test('lost export acknowledgement restores only the original key, format and saved source without draft text',async()=>{
 const f=fixture(),first=useMinutesExports(f.api as any,f.actor,()=>meeting,f.context);first.synchronize();f.fail(Error('lost ACK'));await assert.rejects(first.start(revision,'md'));const original=f.calls[0];assert.deepEqual(JSON.parse(original.body),{sourceRevision:revision,format:'md'});first.dispose();
 const next=useMinutesExports(f.api as any,f.actor,()=>meeting,f.context);next.synchronize();assert.equal(f.calls.length,1);await assert.rejects(next.start(revision,'pdf'));f.fail(null);await next.recover();assert.deepEqual(f.calls[1],original);assert.equal(next.job.value?.exportJobId,task);
});
test('accepted export polling performs no second POST and terminal retry requires a fresh explicit command',async()=>{
 const f=fixture(),tools=useMinutesExports(f.api as any,f.actor,()=>meeting,f.context);tools.synchronize();await tools.start(revision,'md');await assert.rejects(tools.start(revision,'pdf'));
 f.state({exportJobId:task,state:'SUCCEEDED',sourceRevision:revision,format:'md',fileId:file,error:null});await tools.poll();assert.equal(f.calls.length,1);assert.equal(tools.download.value,`/api/v1/exports/${task}/download`);
 const key=f.calls[0].key;await tools.start(revision,'md');assert.notEqual(f.calls[1].key,key);
});
test('denied export read conceals download and changed identity cannot recover another owner request',async()=>{
 const f=fixture(),tools=useMinutesExports(f.api as any,f.actor,()=>meeting,f.context);tools.synchronize();await tools.start(revision,'md');f.fail(new ApiError('http',403,'revoked'));await assert.rejects(tools.poll());assert.equal(tools.job.value,null);assert.equal(tools.download.value,null);assert.equal(tools.available.value,false);
 f.setActor('other');tools.synchronize();assert.equal(tools.command.snapshot.value,null);assert.equal(tools.job.value,null);
});
test('export API rejects a substituted format/source and malformed original journal before transport',async()=>{
 const original={actor:'owner',resource:meeting,operation:'MINUTES_EXPORT',path:`/api/v1/meetings/${meeting}/exports`,key:file,body:JSON.stringify({sourceRevision:revision,format:'md'})};
 assert.equal(validMinutesExportCommand(original,'owner',meeting),true);assert.equal(validMinutesExportCommand({...original,body:JSON.stringify({sourceRevision:revision,format:'md',body:'draft'})},'owner',meeting),false);
 let calls=0;const api=createMinutesExportsApi({json:async()=>{calls++;return{exportJobId:task,state:'READY',sourceRevision:revision,format:'pdf',fileId:null,error:null};}} as any,()=> 'owner');
 await assert.rejects(api.send(original),e=>e instanceof ApiError&&e.kind==='response');assert.equal(calls,1);
 await assert.rejects(api.send({...original,actor:'other'}));assert.equal(calls,1);
});
