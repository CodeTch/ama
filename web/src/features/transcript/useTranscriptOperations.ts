import {computed,readonly,shallowRef} from 'vue';
import {useCommand} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {validTranscriptOperationCommand,type createTranscriptOperationsApi,type TranscriptReplacementSearch,type TranscriptOperationReceipt} from '../../shared/api/transcript-operations';
import type {TranscriptCurrent} from '../../shared/api/transcripts';
import {ApiError} from '../../shared/api/client';

type Api=ReturnType<typeof createTranscriptOperationsApi>&{current:(meeting:string,signal:AbortSignal)=>Promise<TranscriptCurrent>};
type Options={actor:()=>string|null;meeting:()=>string;eligible:()=>boolean;canMutate:()=>boolean;current:()=>TranscriptCurrent|null;context:TabJournalContext};
type Original={find?:string;replacement?:string;occurrenceIds?:string[];sourceRevisionId:string;expectedRevisionToken:string};
/** Historical command receipts never become the displayed current transcript. */
export function useTranscriptOperations(api:Api,options:Options){
 const journal=createScopedPrivateJournal(options.context,'transcript-operations',validTranscriptOperationCommand);
 const matches=shallowRef<TranscriptReplacementSearch|null>(null),available=shallowRef(false),searching=shallowRef(false),reading=shallowRef(false),concealed=shallowRef(false);
 let scope:{actor:string;meeting:string}|null=null,epoch=0,searchEpoch=0,searchRequest:AbortController|null=null,readRequest:AbortController|null=null;
 const command=useCommand<TranscriptOperationReceipt>(async c=>{if(!scope||!active(epoch)||!validTranscriptOperationCommand(c,scope.actor,scope.meeting))throw new DOMException('Transcript operation scope changed','AbortError');return api.send(c);});
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>reading.value||command.state.value==='SENDING');
 const original=computed<Original|null>(()=>!concealed.value&&command.snapshot.value?JSON.parse(command.snapshot.value.body):null);
 function conceal(cause:unknown){if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0)){concealed.value=true;available.value=false;matches.value=null;}}
 function active(turn:number){return turn===epoch&&!!scope&&scope.actor===options.actor()&&scope.meeting===options.meeting()&&options.eligible();}
 function dispose(){epoch++;searchEpoch++;searchRequest?.abort();readRequest?.abort();searchRequest=null;readRequest=null;scope=null;matches.value=null;available.value=false;searching.value=false;reading.value=false;command.invalidate();}
 function synchronize(){
  dispose();const who=options.actor(),meeting=options.meeting();if(!who||!options.eligible())return;
  scope={actor:who,meeting};concealed.value=false;const stored=journal.load(who,meeting);
  if(stored.kind==='INVALID'||stored.kind==='UNAVAILABLE')throw Error('修订恢复记录暂不可用，请保留页面并重新读取。');
  available.value=true;if(stored.kind==='RESTORED')command.restore(stored.value);
 }
 function source(){
  const current=options.current();if(!active(epoch)||!available.value||!options.canMutate()||pending.value||!current?.capabilities.canSave||current.meetingId!==scope!.meeting||current.saved?.kind!=='WORKING')throw Error('请先保存或处理当前修改，再操作已保存工作稿。');
  return current;
 }
 async function search(query:string){
  const current=source(),turn=epoch,order=++searchEpoch,revision=current.saved!.id,token=current.observation.revisionToken;
  searchRequest?.abort();const request=new AbortController();searchRequest=request;matches.value=null;searching.value=true;
  try{const result=await api.search(scope!.meeting,revision,query,request.signal);
   if(active(turn)&&order===searchEpoch&&options.current()?.saved?.id===revision&&options.current()?.observation.revisionToken===token&&options.canMutate()&&!pending.value)matches.value=result;
  }catch(cause){if(active(turn)&&order===searchEpoch){conceal(cause);throw cause;}}finally{if(active(turn)&&order===searchEpoch)searching.value=false;}
 }
 async function retry(){
  const turn=epoch,currentScope=scope;if(!active(turn)||!currentScope||!available.value||!command.snapshot.value)throw Error('原修订操作暂不可恢复。');
  if(busy.value)throw Error('正在核实原修订操作，请稍候。');
  if(!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw Error('请先核对权限或明确放弃已拒绝的操作。');
  if(journal.save(currentScope.actor,currentScope.meeting,command.snapshot.value)!=='SAVED')throw Error('无法保存修订原请求，请保留页面重试。');
  let accepted:TranscriptOperationReceipt;
  try{accepted=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();}catch(cause){if(active(turn))conceal(cause);throw cause;}
  if(!active(turn))throw new DOMException('Transcript operation scope changed','AbortError');
  reading.value=true;const request=new AbortController();readRequest=request;
  try{
   const current=await api.current(currentScope.meeting,request.signal);
   if(!active(turn)||request.signal.aborted)throw new DOMException('Transcript operation scope changed','AbortError');
   if(current.meetingId!==currentScope.meeting)throw Error('当前工作稿暂无法核实，请保留原请求。');
   if(!journal.clear())throw Error('修订已受理，恢复记录清理尚未完成，请重试确认。');
   command.invalidate();matches.value=null;return {accepted,current};
  }catch(cause){if(active(turn))conceal(cause);throw cause;}finally{if(active(turn)){reading.value=false;readRequest=null;}}
 }
 function prepare(operation:'TRANSCRIPT_REPLACE'|'TRANSCRIPT_UNDO',body:Original){
  const current=source(),who=scope!.actor,meeting=scope!.meeting;
  if(body.sourceRevisionId!==current.saved!.id||body.expectedRevisionToken!==current.observation.revisionToken)throw Error('工作稿已有变化，请重新查找并核对。');
  const path=`/api/v1/meetings/${meeting}/transcript`+(operation==='TRANSCRIPT_REPLACE'?'/replacements':`/operations/${body.sourceRevisionId}/undo`);
  command.prepare({operation,actor:who,resource:meeting,path,key:crypto.randomUUID(),body});
  if(!validTranscriptOperationCommand(command.snapshot.value,who,meeting)){command.invalidate();throw Error('请核对修订内容和选中位置。');}
  return retry();
 }
 async function replace(replacement:string,ids:string[]){
  const found=matches.value,known=new Set(found?.occurrences.map(x=>x.occurrenceId));if(!found?.complete||!ids.length||ids.some(id=>!known.has(id)))throw Error('请查找并选中需要替换的具体位置。');
  return prepare('TRANSCRIPT_REPLACE',{sourceRevisionId:found.sourceRevisionId,expectedRevisionToken:found.expectedRevisionToken,find:found.query,replacement,occurrenceIds:[...ids]});
 }
 async function undo(){const current=source();return prepare('TRANSCRIPT_UNDO',{sourceRevisionId:current.saved!.id,expectedRevisionToken:current.observation.revisionToken});}
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的修订操作。');if(!journal.clear())throw Error('原修订记录暂无法清理。');command.invalidate();matches.value=null;}
 return {matches:readonly(matches),available:readonly(available),searching:readonly(searching),pending,busy,original,command,synchronize,clearSearch(){searchEpoch++;searchRequest?.abort();matches.value=null;searching.value=false;},search,replace,undo,retry,discardRejected,dispose};
}
