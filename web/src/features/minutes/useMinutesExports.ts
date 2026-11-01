import {computed,shallowReadonly,shallowRef} from 'vue';import {useCommand} from '../../shared/composables/useCommand';import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';import type {TabJournalContext} from '../../shared/composables/tab-journal';import {ApiError} from '../../shared/api/client';import {validMinutesExportCommand,type ExportFormat,type ExportJob,type createMinutesExportsApi} from '../../shared/api/minutes-exports';
const terminal=(job:ExportJob|null)=>!!job&&['SUCCEEDED','FAILED','CANCELLED','STALE'].includes(job.state);
export function useMinutesExports(api:ReturnType<typeof createMinutesExportsApi>,actor:()=>string|null,meeting:()=>string,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'minutes-exports',validMinutesExportCommand),job=shallowRef<ExportJob|null>(null),available=shallowRef(false),reading=shallowRef(false),error=shallowRef<string|null>(null);
 let owner:string|null=null,scope='',epoch=0,request:AbortController|null=null;
 const active=(turn:number)=>turn===epoch&&owner!==null&&owner===actor()&&scope===meeting();
 const command=useCommand<ExportJob>(async original=>{if(!active(epoch)||!validMinutesExportCommand(original,owner!,scope))throw new DOMException('Export scope changed','AbortError');return api.send(original);},4096);
 const busy=computed(()=>reading.value||command.state.value==='SENDING'),pending=computed(()=>command.snapshot.value!==null&&!terminal(job.value));
 const download=computed(()=>active(epoch)&&available.value&&job.value?.state==='SUCCEEDED'&&job.value.fileId?`/api/v1/exports/${job.value.exportJobId}/download`:null);
 function dispose(){epoch++;request?.abort();request=null;owner=null;scope='';job.value=null;available.value=false;reading.value=false;error.value=null;command.invalidate();}
 function synchronize(){dispose();const who=actor(),id=meeting();if(!who||!id)return;owner=who;scope=id;const found=journal.load(who,id);if(found.kind==='INVALID'||found.kind==='UNAVAILABLE')throw new Error('导出恢复记录暂不可用，请保留页面重试。');available.value=true;if(found.kind==='RESTORED')command.restore(found.value);}
 function failed(cause:unknown,turn:number){if(!active(turn))return;error.value=cause instanceof Error?cause.message:'导出暂不可用，请重试。';if(cause instanceof ApiError&&cause.kind==='http'&&[401,403,404].includes(cause.status)){available.value=false;job.value=null;}}
 async function recover(){
  const turn=epoch,original=command.snapshot.value;if(!active(turn)||!available.value||busy.value||!original||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw new Error('请核对原导出请求。');
  if(journal.save(owner!,scope,original)!=='SAVED')throw new Error('导出请求无法保存，请保留页面重试。');error.value=null;
  try{const accepted=command.state.value==='ACKNOWLEDGED'?command.ack.value:await command.send();if(!active(turn)||!accepted)throw new DOMException('Export scope changed','AbortError');job.value=accepted;return accepted;}catch(cause){failed(cause,turn);throw cause;}
 }
 async function start(sourceRevision:string,format:ExportFormat){
  if(!active(epoch)||!available.value||busy.value||pending.value)throw new Error('请先查回当前导出任务。');
  if(command.snapshot.value){if(!journal.clear())throw new Error('导出恢复记录暂无法清理。');command.invalidate();}job.value=null;
  command.prepare({actor:owner!,operation:'MINUTES_EXPORT',resource:scope,path:`/api/v1/meetings/${scope}/exports`,key:crypto.randomUUID(),body:{sourceRevision,format}});
  if(!validMinutesExportCommand(command.snapshot.value,owner!,scope)){command.invalidate();throw new Error('请选择已保存版本和导出格式。');}return recover();
 }
 async function poll(){
  const turn=epoch,original=job.value;if(!active(turn)||!available.value||busy.value||!original)return;request?.abort();request=new AbortController();const signal=request.signal;reading.value=true;error.value=null;
  try{const fresh=await api.read(original.exportJobId,signal);if(!active(turn)||signal.aborted)throw new DOMException('Export scope changed','AbortError');if(fresh.exportJobId!==original.exportJobId||fresh.sourceRevision!==original.sourceRevision||fresh.format!==original.format)throw new ApiError('response',200,'导出任务与原请求不匹配。');job.value=fresh;}catch(cause){failed(cause,turn);throw cause;}finally{if(active(turn))reading.value=false;}
 }
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw new Error('请先查回原导出结果。');if(!journal.clear())throw new Error('导出恢复记录暂无法清理。');command.invalidate();job.value=null;error.value=null;}
 return{command,job:shallowReadonly(job),available:shallowReadonly(available),error:shallowReadonly(error),pending,busy,download,synchronize,start,recover,poll,discardRejected,dispose,logout(){const cleared=journal.clear();dispose();return cleared;}};
}
