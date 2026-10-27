import {shallowRef,shallowReadonly,watch} from 'vue';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {validGenerationCommand,type GenerationCurrent,type GenerationTemplates,type GenerationReceipt} from '../../shared/api/minutes-generation';
import {ApiError} from '../../shared/api/client';
type Api={authorize?:(id:string,signal:AbortSignal)=>Promise<unknown>;current:(id:string,signal:AbortSignal)=>Promise<GenerationCurrent>;templates:(id:string,cursor:string|null,signal:AbortSignal)=>Promise<GenerationTemplates>;send:(c:CommandSnapshot)=>Promise<GenerationReceipt>};
const denied=(e:unknown)=>e instanceof ApiError&&e.kind==='http'&&[401,403].includes(e.status??0);
export function useMinutesGeneration(api:Api,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean,context?:TabJournalContext){
 const current=shallowRef<GenerationCurrent|null>(null),templates=shallowRef<GenerationTemplates|null>(null),loading=shallowRef(false),busy=shallowRef(false),ready=shallowRef(false),blocked=shallowRef(false),error=shallowRef<unknown>(null),command=useCommand(api.send);
 const journal=context?createScopedPrivateJournal<CommandSnapshot>(context,'minutes-generation',validGenerationCommand):null;
 let epoch=0,readTurn=0,disposed=false,controller:AbortController|null=null,flight:Promise<void>|null=null,owner=actor(),resource=meeting(),retained:CommandSnapshot|null=null;
 function invalidate(){epoch++;readTurn++;controller?.abort();controller=null;flight=null;
  if(command.state.value==='ACKNOWLEDGED'&&!blocked.value)retained=null;else if(command.snapshot.value)retained=command.snapshot.value;if(owner!==null&&actor()!==null&&actor()!==owner||meeting()!==resource)retained=null;
  if(actor()!==null)owner=actor();resource=meeting();if(journal&&!journal.synchronize(actor(),meeting()))blocked.value=true;
  current.value=null;templates.value=null;ready.value=false;command.invalidate();loading.value=false;busy.value=false;error.value=null;if(disposed)retained=null;
 }
 const stop=watch(()=>[actor(),meeting(),eligible()],invalidate,{flush:'sync'});
 async function reload(afterSend=false){
  if(disposed||flight&&!afterSend)return;const who=actor(),id=meeting(),generation=epoch,turn=++readTurn;if(!who||!eligible()){invalidate();return;}
  controller?.abort();const request=new AbortController();controller=request;const active=()=>!disposed&&generation===epoch&&turn===readTurn&&who===actor()&&id===meeting()&&eligible()&&!request.signal.aborted;
  loading.value=true;error.value=null;
  try{
   const authorized=api.authorize?await api.authorize(id,request.signal):await api.current(id,request.signal);if(!active())return;
   if(!ready.value||blocked.value){const record=journal?.load(who,id)??{kind:'EMPTY' as const};blocked.value=['INVALID','UNAVAILABLE'].includes(record.kind);if(blocked.value)throw new Error('恢复记录暂不可用，请保持页面打开并重试。');
    const original=retained??(record.kind==='RESTORED'?record.value:null);if(original&&command.state.value==='EMPTY')command.restore(original);retained=null;ready.value=true;
   }
   if(!active())return;const observed=api.authorize?await api.current(id,request.signal):authorized as GenerationCurrent;if(active())current.value=observed;
  }catch(cause){if(active()){if(denied(cause))invalidate();error.value=cause;current.value=null;}}
  finally{if(active()){loading.value=false;controller=null;}}
 }
 async function loadTemplates(more=false){if(disposed||busy.value||loading.value||!ready.value||!eligible()||!actor())return;const generation=epoch,id=meeting(),who=actor(),turn=++readTurn;controller?.abort();const request=new AbortController();controller=request;loading.value=true;error.value=null;const active=()=>!disposed&&generation===epoch&&turn===readTurn&&who===actor()&&id===meeting()&&eligible();
  try{const page=await api.templates(id,more?templates.value?.nextCursor??null:null,request.signal);if(active())templates.value=page;}catch(cause){if(active()){if(denied(cause))invalidate();error.value=cause;}}finally{if(active()){loading.value=false;controller=null;}}
 }
 function send(operation:'MINUTES_INITIAL'|'MINUTES_GENERATE',template?:string,retry=false):Promise<void>{
  if(disposed)return Promise.resolve();if(flight)return flight;const generation=epoch,who=actor(),id=meeting();const active=()=>!disposed&&generation===epoch&&who===actor()&&id===meeting()&&eligible();
  const run=async()=>{try{
   if(!who||!active()||!ready.value||blocked.value)throw new Error('请先读取当前会议并恢复原操作。');busy.value=true;error.value=null;
   if(retry){if(command.snapshot.value?.operation!==operation||!['PREPARED','UNCERTAIN'].includes(command.state.value))throw new Error('没有可重试的原请求。');}
   else{
    if(!['EMPTY','ACKNOWLEDGED'].includes(command.state.value))throw new Error('请先处理原请求。');const fresh=await api.current(id,new AbortController().signal);if(!active())return;current.value=fresh;
    if(operation==='MINUTES_GENERATE'&&!fresh.eligible)throw new Error('生成条件尚未满足，请查看当前原因。');
    if(operation==='MINUTES_INITIAL'){
     if(fresh.saved.templateRevisionId!==null||fresh.saved.exclusionRevisionId!==null)throw new Error('初始设置已存在，请重新读取。');
     if(!templates.value?.items.some(t=>t.revisionId===template&&t.selectable))throw new Error('请从真实可选模板中选择。');
    }
    const body=operation==='MINUTES_GENERATE'?{expectedRevisionToken:fresh.observation.revisionToken,scope:'FULL'}:{expectedRevisionToken:fresh.observation.revisionToken,templateRevisionId:template};
    command.prepare({actor:who,resource:id,operation,path:`/api/v1/meetings/${id}/minutes/${operation==='MINUTES_GENERATE'?'generations':'initial-settings'}`,key:crypto.randomUUID(),body});
   }
   if(!active())return;const original=command.snapshot.value;if(!original||journal&&journal.save(who,id,original)!=='SAVED')throw new Error('原请求无法保存，请保持页面打开并重试。');
   await command.send();if(!active())return;
   // A failed clear keeps the original accepted request recoverable; never invent a replacement.
   if(journal&&!journal.clear()){blocked.value=true;throw new Error('操作已收到回执，恢复记录清理失败；重新读取后查回原结果。');}
   await reload(true);
  }catch(cause){if(active()){if(denied(cause))invalidate();error.value=cause;}}finally{if(active())busy.value=false;}};
  let finish!:()=>void;const result=new Promise<void>(r=>{finish=r;});flight=result;void result.then(()=>{if(generation===epoch)flight=null;});void run().then(finish,finish);return result;
 }
 return {current:shallowReadonly(current),templates:shallowReadonly(templates),loading:shallowReadonly(loading),busy:shallowReadonly(busy),ready:shallowReadonly(ready),blocked:shallowReadonly(blocked),error:shallowReadonly(error),command,reload:()=>reload(),loadTemplates,initialize:(id:string)=>send('MINUTES_INITIAL',id),generate:()=>send('MINUTES_GENERATE'),retry:()=>send(command.snapshot.value?.operation==='MINUTES_INITIAL'?'MINUTES_INITIAL':'MINUTES_GENERATE',undefined,true),
  discardRejected(){if(flight||!['CONFLICT','REJECTED'].includes(command.state.value))throw new Error('原请求结果尚未明确。');if(journal&&!journal.clear())throw new Error('恢复记录无法清理。');command.invalidate();retained=null;},
  logout(){retained=null;if(journal&&!journal.clear())blocked.value=true;invalidate();retained=null;},dispose(){disposed=true;stop();invalidate();}
 };
}
