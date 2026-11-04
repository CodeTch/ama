import {test} from 'node:test';import assert from 'node:assert/strict';
import {useOperations,OPERATIONS_SCOPE,validOperationsCommand} from '../../src/features/governance/useOperations';
import {ApiError} from '../../src/shared/api/client';
const memory=()=>{const values=new Map<string,string>();return{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>{values.set(k,v);},removeItem:(k:string)=>{values.delete(k);},clear:()=>values.clear(),key:(i:number)=>[...values.keys()][i]??null,get length(){return values.size;}};};
test('mail unknown response survives refresh and replays exact original key and body',async()=>{
 const store=memory(),context={tab:'operations-test',storage:store,recovery:'NEW_NAMESPACE' as const};let who:string|null='owner';const calls:{path:string;key:string;body:string}[]=[];
 const client={json:async(path:string,options:RequestInit)=>{calls.push({path,key:new Headers(options.headers).get('Idempotency-Key')!,body:String(options.body)});if(calls.length===1)throw new ApiError('network',0,'lost');return{id:'12345678-1234-1234-1234-123456789012',meetingId:'12345678-1234-1234-1234-123456789012',publicationId:'12345678-1234-1234-1234-123456789012',sourceRevisionToken:'9007199254740993',state:'PENDING',messageId:null,lastError:null,createdAt:'2026-11-02T00:00:00Z'};}};
 const first=useOperations(client as never,()=>who,context);first.synchronize();const path='/api/v1/meetings/12345678-1234-1234-1234-123456789012/mail';const body={sourceRevisionToken:'9007199254740993',publicationId:'12345678-1234-1234-1234-123456789012',recipientEnterpriseIds:['owner']};
 await assert.rejects(first.submit(path,body));assert.equal(first.command.state.value,'UNCERTAIN');assert.throws(()=>first.discardRejected());first.dispose();
 const recovered=useOperations(client as never,()=>who,context);recovered.synchronize();assert.equal(recovered.command.state.value,'UNCERTAIN');await recovered.retry();assert.deepEqual(calls[0],calls[1]);assert.equal(recovered.pending.value,false);
});
test('new actor clears old payload and late read cannot restore private data',async()=>{
 let who:string|null='first',resolve!:(v:unknown)=>void;const context={tab:'ops-scope',storage:memory(),recovery:'NEW_NAMESPACE' as const};const client={json:async()=>new Promise(r=>{resolve=r;})};const ops=useOperations(client as never,()=>who,context);ops.synchronize();const reading=ops.read('/api/v1/operations/voiceprints');who='second';ops.synchronize();resolve([{consentReference:'old private'}]);await assert.rejects(reading);assert.equal(ops.pending.value,false);
});
test('restoration validator rejects external and unrelated production mutations',()=>{
 const base={actor:'a',resource:OPERATIONS_SCOPE,operation:'OPERATIONS',key:'original-key',body:'{}'};
 for(const path of ['https://evil.invalid','/api/v1/meetings/x/delete','/api/v1/operations/evaluations?url=evil','/api/v1/operations/configuration-releases#x'])assert.equal(validOperationsCommand({...base,path},'a',OPERATIONS_SCOPE),false);
 assert.equal(validOperationsCommand({...base,path:'/api/v1/operations/configuration-releases'},'a',OPERATIONS_SCOPE),true);
});
test('closed governance response schemas reject vector and private payload additions',async()=>{
 const {operationsResponse}=await import('../../src/shared/api/operations-wire');
 const voice={id:'12345678-1234-1234-1234-123456789012',subjectId:'owner',consentReference:'本人同意',purpose:'SPEAKER_CANDIDATE',modelVersion:'v1',dimension:2,sampleCount:48000,expiresAt:'2099-01-01T00:00:00Z',state:'PENDING',revisionToken:'0',reviewReason:null};
 assert.equal(operationsResponse('/api/v1/operations/voiceprints','GET',[voice]),true);
 assert.equal(operationsResponse('/api/v1/operations/voiceprints','GET',[{...voice,values:[1,0]}]),false);
});
test('a revoked mail source preserves owner metadata access while role denial clears governance availability',async()=>{
 const context={tab:'ops-source-denial',storage:memory(),recovery:'NEW_NAMESPACE' as const};const client={json:async(path:string)=>{if(path.endsWith('/mail-context')||path.endsWith('/configuration'))throw new ApiError('http',403,'source or role denied');return[];}};const ops=useOperations(client as never,()=> 'owner',context);ops.synchronize();const root='/api/v1/meetings/12345678-1234-1234-1234-123456789012';
 await assert.rejects(ops.read(root+'/mail-context'));assert.equal(ops.available.value,true);assert.deepEqual(await ops.read(root+'/mail'),[]);await assert.rejects(ops.read('/api/v1/operations/configuration'));assert.equal(ops.available.value,false);
});
test('same-actor permission recovery restores availability and retains the original mail command',async()=>{
 let permitted=false;
 const context={tab:'ops-recheck',storage:memory(),recovery:'NEW_NAMESPACE' as const};
 const client={json:async(path:string)=>{if(path.endsWith('/configuration'))throw new ApiError('http',403,'role denied');if(!permitted)throw new ApiError('network',0,'lost');return{id:'12345678-1234-1234-1234-123456789012',meetingId:'12345678-1234-1234-1234-123456789012',publicationId:'12345678-1234-1234-1234-123456789012',sourceRevisionToken:'1',state:'PENDING',messageId:null,lastError:null,createdAt:'2026-11-03T00:00:00Z'};}};
 const ops=useOperations(client as never,()=> 'owner',context);ops.synchronize();
 await assert.rejects(ops.submit('/api/v1/meetings/12345678-1234-1234-1234-123456789012/mail',{publicationId:'12345678-1234-1234-1234-123456789012',sourceRevisionToken:'1',recipientEnterpriseIds:['owner']}));
 const original=ops.command.snapshot.value;
 await assert.rejects(ops.read('/api/v1/operations/configuration'));assert.equal(ops.available.value,false);
 await ops.recheck(async()=>{permitted=true;});
 assert.equal(ops.available.value,true);assert.deepEqual(ops.command.snapshot.value,original);
 await ops.retry();assert.equal(ops.pending.value,false);
});
