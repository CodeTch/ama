import {test} from 'node:test';
import assert from 'node:assert/strict';
import {operationsResponse} from '../../src/shared/api/operations-wire';
import {useOperations} from '../../src/features/governance/useOperations';

const id='12345678-1234-1234-1234-123456789012';
const paths=[
 '/api/v1/operations/datasets','/api/v1/operations/evaluations',
 `/api/v1/operations/evaluations/${id}/review`,
 '/api/v1/operations/configuration-releases','/api/v1/operations/configuration-rollbacks',
 `/api/v1/meetings/${id}/feedback`,`/api/v1/operations/corpus/${id}/withdrawal`,
 `/api/v1/meetings/${id}/voiceprint-applications`,
];
test('empty receipts never acknowledge protected governance mutations',()=>{
 for(const path of paths)assert.equal(operationsResponse(path,'POST',{}),false,path);
});
test('unknown operations are not allowed by a generic object fallback',()=>{
 assert.equal(operationsResponse('/api/v1/operations/unregistered','GET',{message:'anything'}),false);
});
test('a review receipt belongs to its requested resource and reason',()=>{
 const path=`/api/v1/operations/evaluations/${id}/review`;
 assert.equal(operationsResponse(path,'POST',{id,reviewReason:'核对完成'},{reason:'核对完成'}),true);
 assert.equal(operationsResponse(path,'POST',{id:'22345678-1234-1234-1234-123456789012',reviewReason:'核对完成'},{reason:'核对完成'}),false);
 assert.equal(operationsResponse(path,'POST',{id,reviewReason:'旧请求的意见'},{reason:'核对完成'}),false);
});
test('an empty create receipt keeps the original request recoverable',async()=>{
 const values=new Map<string,string>();
 const storage={getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);},clear:()=>values.clear(),key:(i:number)=>[...values.keys()][i]??null,get length(){return values.size;}};
 let valid=false;const calls:string[]=[];
 const ops=useOperations({json:async(_path:string,o:RequestInit)=>{calls.push(String(o.body));return valid?{id,version:'regression-v1'}:{};}} as never,()=> 'owner',{tab:'receipts',storage,recovery:'NEW_NAMESPACE'});
 ops.synchronize();await assert.rejects(ops.submit(paths[0]!,{version:'regression-v1',samples:[]}));
 assert.equal(ops.command.state.value,'UNCERTAIN');assert.equal(ops.pending.value,true);
 const key=ops.command.snapshot.value?.key;valid=true;await ops.retry();
 assert.ok(key);assert.deepEqual(calls,[calls[0],calls[0]]);assert.equal(ops.pending.value,false);
});
