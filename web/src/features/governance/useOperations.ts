import {computed,shallowRef} from 'vue';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import type {createBusinessClient} from '../../shared/api/business-client';
import {ApiError} from '../../shared/api/client';
import {operationsResponse} from '../../shared/api/operations-wire';
export const OPERATIONS_SCOPE='00000000-0000-0000-0000-000000000005';
const mutation=/^\/api\/v1\/(?:operations\/(?:voiceprints(?:\/[0-9a-f-]{36}\/(?:review|revocation)|\/match)?|datasets|evaluations(?:\/[0-9a-f-]{36}\/review)?|configuration-releases|configuration-rollbacks|corpus\/[0-9a-f-]{36}\/withdrawal)|meetings\/[0-9a-f-]{36}\/(?:mail|feedback))$/;
export function validOperationsCommand(v:unknown,actor:string,scope:string):v is CommandSnapshot{
 if(typeof v!=='object'||v===null||Array.isArray(v))return false;const c=v as Record<string,unknown>;
 if(Object.keys(c).sort().join(',')!=='actor,body,key,operation,path,resource'||c.actor!==actor||scope!==OPERATIONS_SCOPE||c.resource!==scope||c.operation!=='OPERATIONS'||typeof c.path!=='string'||!mutation.test(c.path)||typeof c.key!=='string'||!/^[-A-Za-z0-9_]{1,128}$/.test(c.key)||typeof c.body!=='string'||c.body.length>8*1024*1024)return false;
 try{const b:unknown=JSON.parse(c.body);return typeof b==='object'&&b!==null&&!Array.isArray(b);}catch{return false;}
}
/** One original mutation at a time, shared across governance pages and mail dialogs. */
export function useOperations(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'operations',validOperationsCommand),available=shallowRef(false);let owner:string|null=null,epoch=0;const reads=new Set<AbortController>();
 const command=useCommand<unknown>(async c=>{if(!owner||owner!==actor()||!validOperationsCommand(c,owner,OPERATIONS_SCOPE))throw new DOMException('Operations identity changed','AbortError');const value=await client.json(c.path,{method:'POST',body:c.body,headers:{'Idempotency-Key':c.key},expectedStatus:/\/meetings\/.+\/mail$/.test(c.path)?202:200,maxResponseBytes:8*1024*1024});if(!operationsResponse(c.path,'POST',value))throw new ApiError('response',200,'治理响应暂无法核实，请保留原请求。');return value;});
 const pending=computed(()=>command.snapshot.value!==null);
 function dispose(){epoch++;for(const r of reads)r.abort();reads.clear();owner=null;available.value=false;command.invalidate();}
 function synchronize(){dispose();const who=actor();if(!who)return;owner=who;if(!journal.synchronize(who,OPERATIONS_SCOPE))throw new Error('原治理请求暂无法安全恢复，请保留页面。');const found=journal.load(who,OPERATIONS_SCOPE);if(found.kind==='INVALID'||found.kind==='UNAVAILABLE')throw new Error('原治理请求暂无法安全恢复，请保留页面。');available.value=true;if(found.kind==='RESTORED')command.restore(found.value);}
 async function retry(){const turn=epoch,c=command.snapshot.value;if(!owner||owner!==actor()||!available.value||!c||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw new Error('请先核对原治理请求。');if(journal.save(owner,OPERATIONS_SCOPE,c)!=='SAVED')throw new Error('无法保存原操作，尚未发送。');let result:unknown;try{result=command.state.value==='ACKNOWLEDGED'?command.ack.value:await command.send();}catch(cause){if(turn===epoch&&cause instanceof ApiError&&[401,403].includes(cause.status))available.value=false;throw cause;}if(turn!==epoch||owner!==actor())throw new DOMException('Operations scope changed','AbortError');if(!journal.clear())throw new Error('已受理，恢复记录尚未清理，请查回原结果。');command.invalidate();return result;}
 async function submit(path:string,body:unknown){if(!owner||owner!==actor()||!available.value||pending.value)throw new Error('请先处理原治理请求。');command.prepare({actor:owner,resource:OPERATIONS_SCOPE,operation:'OPERATIONS',path,key:crypto.randomUUID(),body});if(!validOperationsCommand(command.snapshot.value,owner,OPERATIONS_SCOPE)){command.invalidate();throw new Error('不支持的治理操作。');}return retry();}
 async function read<T=unknown>(path:string):Promise<T>{const turn=epoch,who=owner;if(!who||who!==actor()||!available.value)throw new Error('请登录当前企业账号。');const request=new AbortController();reads.add(request);try{const value=await client.json<T>(path,{signal:request.signal,expectedStatus:200,maxResponseBytes:8*1024*1024});if(turn!==epoch||who!==actor()||request.signal.aborted)throw new DOMException('Operations scope changed','AbortError');if(!operationsResponse(path,'GET',value))throw new ApiError('response',200,'治理读取响应暂无法核实。');return value;}catch(cause){if(turn===epoch&&cause instanceof ApiError&&(cause.status===401||cause.status===403&&!/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail(?:-context)?$/.test(path))){available.value=false;for(const r of reads)r.abort();}throw cause;}finally{reads.delete(request);}}
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw new Error('结果未知时不能丢弃原请求。');if(!journal.clear())throw new Error('恢复记录尚未清理。');command.invalidate();}
 function logout(){const cleared=journal.clear();dispose();return cleared;}
 return{command,pending,available,synchronize,submit,retry,discardRejected,dispose,read,logout};
}
