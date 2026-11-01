import {test} from 'node:test';
import assert from 'node:assert/strict';
import {usePersonalTemplateCommands} from '../../src/features/minutes/usePersonalTemplateCommands.js';
import {ApiError} from '../../src/shared/api/client.js';

const id='00000000-0000-4000-8000-000000000001',revision='00000000-0000-4000-8000-000000000002';
function fixture(){
 const values=new Map<string,string>();const context={tab:'template-tests',recovery:'RESUME_NAMESPACE' as const,storage:{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>{values.set(key,value);},removeItem:(key:string)=>{values.delete(key);}}};
 let actor:string|null='owner',failure:unknown=null,readFailure:unknown=null;const calls:any[]=[];const result={id,revisionId:revision,revisionToken:'1',name:'原模板😀',themes:['结论','行动'],deleted:false};
 const api={send:async(command:any)=>{calls.push(command);if(failure)throw failure;return result;},list:async()=>{if(readFailure)throw readFailure;return{items:[result],nextCursor:null};}};
 return {context,api,calls,actor:()=>actor,fail:(value:unknown)=>{failure=value;},failRead:(value:unknown)=>{readFailure=value;},setActor:(value:string|null)=>{actor=value;}};
}
test('unknown template save keeps original themes and key across reload and blocks a second command',async()=>{
 const f=fixture(),first=usePersonalTemplateCommands(f.api as any,f.actor,f.context);first.synchronize();f.fail(Error('ACK lost'));
 await assert.rejects(first.create('原模板😀',['结论','行动']));const original=f.calls[0];assert.equal(first.pending.value,true);await assert.rejects(first.create('第二次',['别的']));assert.equal(f.calls.length,1);first.dispose();
 const restored=usePersonalTemplateCommands(f.api as any,f.actor,f.context);restored.synchronize();assert.deepEqual(restored.original.value?.themes,['结论','行动']);f.fail(null);await restored.retry();assert.deepEqual(f.calls[1],original);assert.equal(restored.pending.value,false);assert.equal(restored.page.value?.items[0]?.name,'原模板😀');
});
test('journal refusal prevents sending and accepted reread failure retries only the fresh read',async()=>{
 const f=fixture(),tools=usePersonalTemplateCommands(f.api as any,f.actor,f.context);tools.synchronize();const set=f.context.storage.setItem;f.context.storage.setItem=()=>{throw Error('quota');};
 await assert.rejects(tools.create('原模板😀',['结论','行动']));assert.equal(f.calls.length,0);f.context.storage.setItem=set;f.failRead(Error('read unavailable'));
 await assert.rejects(tools.retry());assert.equal(tools.command.state.value,'ACKNOWLEDGED');assert.equal(f.calls.length,1);f.failRead(null);await tools.retry();assert.equal(f.calls.length,1);assert.equal(tools.pending.value,false);
});
test('denial conceals private originals and scope changes cannot restore the previous owner payload',async()=>{
 const f=fixture(),tools=usePersonalTemplateCommands(f.api as any,f.actor,f.context);tools.synchronize();f.fail(new ApiError('http',403,'denied'));await assert.rejects(tools.update(id,'1','私有更改',['私有主题']));
 assert.equal(tools.original.value,null);assert.equal(tools.page.value,null);assert.equal(tools.available.value,false);assert.throws(()=>tools.discardRejected());
 f.setActor('other');tools.synchronize();assert.equal(tools.original.value,null);assert.equal(tools.pending.value,false);
});
test('conflict requires explicit discard and an accepted late response cannot cross an identity boundary',async()=>{
 const f=fixture(),tools=usePersonalTemplateCommands(f.api as any,f.actor,f.context);tools.synchronize();f.fail(new ApiError('http',409,'stale'));await assert.rejects(tools.remove(id,'1'));tools.discardRejected();assert.equal(tools.pending.value,false);
 f.fail(null);let accept!:(value:any)=>void;f.api.send=async(command:any)=>{f.calls.push(command);return new Promise(resolve=>{accept=resolve;});};const work=tools.create('原模板😀',['结论','行动']);await new Promise(resolve=>setImmediate(resolve));f.setActor('other');tools.synchronize();accept({id,revisionId:revision,revisionToken:'1',name:'原模板😀',themes:['结论','行动'],deleted:false});await assert.rejects(work,{name:'AbortError'});assert.equal(tools.page.value,null);assert.equal(tools.original.value,null);
});
