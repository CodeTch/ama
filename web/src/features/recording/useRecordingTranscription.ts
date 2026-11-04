import {createRecordingProcessIntent,type RecordingProcessIntent} from './recording-process-intent';
import {computed,readonly,shallowRef} from 'vue';
import type {createRecordingTranscriptionApi,RecordingTranscriptSource} from './transcription-api';
import type {createTranscriptImportApi,ImportAccepted} from '../../shared/api/transcript-import';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {recordingUuid} from './recording-api';
import {revisionToken} from '../../shared/api/intent-contract';
import {fitsUtf8} from '../../shared/bounded-text';
import {ApiError} from '../../shared/api/client';
type Options={context:TabJournalContext;actor:()=>string|null;meeting:()=>string;sessionId:()=>string|null;canMutate:()=>boolean};
export function validRecordingImportCommand(value:unknown,actor:string,meeting:string):value is CommandSnapshot{
 if(typeof value!=='object'||value===null||Array.isArray(value))return false;const c=value as Record<string,unknown>;if(Object.keys(c).sort().join(',')!=='actor,body,key,operation,path,resource'||c.actor!==actor||c.resource!==meeting||c.operation!=='TRANSCRIPT_IMPORT'||c.path!==`/api/v1/meetings/${meeting}/transcript-imports`||typeof c.key!=='string'||!recordingUuid(c.key)||typeof c.body!=='string'||!fitsUtf8(c.body,65536))return false;
 try{const b:unknown=JSON.parse(c.body);if(typeof b!=='object'||b===null||Array.isArray(b))return false;const body=b as Record<string,unknown>;return Object.keys(body).sort().join(',')==='expectedRevisionToken,orderedFileIds'&&revisionToken(body.expectedRevisionToken)&&Array.isArray(body.orderedFileIds)&&body.orderedFileIds.length===1&&recordingUuid(body.orderedFileIds[0]);}catch{return false;}
}
/** The technical copy is naturally idempotent. Actual import is one persisted immutable command. */
export function useRecordingTranscription(api:ReturnType<typeof createRecordingTranscriptionApi>,intake:Pick<ReturnType<typeof createTranscriptImportApi>,'capabilities'|'send'>,options:Options){
 const view=shallowRef<RecordingTranscriptSource|null>(null),accepted=shallowRef<ImportAccepted|null>(null),error=shallowRef<string|null>(null),loading=shallowRef(false),writing=shallowRef(false),available=shallowRef(false);
 const processJournal=createRecordingProcessIntent(options.context);let processIntent:RecordingProcessIntent|null=null;
 const journal=createScopedPrivateJournal(options.context,'recording-transcript-import',validRecordingImportCommand);
 let scope:{actor:string;meeting:string;session:string|null}|null=null,epoch=0,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null,flight:Promise<void>|null=null,intentSession:string|null=null;
 const active=(turn:number)=>turn===epoch&&!!scope&&scope.actor===options.actor()&&scope.meeting===options.meeting()&&scope.session===options.sessionId();
 const command=useCommand<ImportAccepted>(c=>{if(!active(epoch)||!scope||!options.canMutate()||!validRecordingImportCommand(c,scope.actor,scope.meeting))throw new DOMException('Recording import scope changed','AbortError');return intake.send(c);},65536);
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>loading.value||writing.value||command.state.value==='SENDING');
 function stopTimer(){if(timer!==null)clearTimeout(timer);timer=null;}
 function dispose(){epoch++;stopTimer();controller?.abort();controller=null;scope=null;flight=null;intentSession=null;processIntent=null;view.value=null;accepted.value=null;error.value=null;loading.value=false;writing.value=false;available.value=false;command.invalidate();}
 function synchronize(){dispose();const who=options.actor(),meeting=options.meeting(),session=options.sessionId();if(!who||!recordingUuid(meeting)||session!==null&&!recordingUuid(session))return;scope={actor:who,meeting,session};const stored=journal.load(who,meeting);if(['INVALID','UNAVAILABLE'].includes(stored.kind)){error.value='录音转写恢复存储暂不可用，请保留当前页面。';return;}const intent=processJournal.load(who,meeting);if(['INVALID','UNAVAILABLE'].includes(intent.kind)){error.value='录音处理意图暂无法读取，请保留当前页面。';return;}if(intent.kind==='RESTORED'&&(session===null||intent.value.session===session)){processIntent=intent.value;intentSession=processIntent.session;}available.value=true;if(stored.kind==='RESTORED')command.restore(stored.value);}
 function failure(cause:unknown,turn:number){if(!active(turn))return;error.value=cause instanceof Error?cause.message:'录音转写暂未完成，请重试。';if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){view.value=null;accepted.value=null;intentSession=null;stopTimer();}}
 function schedule(turn:number){stopTimer();const result=view.value;if(active(turn)&&result&&(result.state==='PENDING'||result.state==='LEASED'||result.state==='DELAYED'||result.state==='NOT_REQUESTED'&&!result.archiveAvailable))timer=setTimeout(()=>{timer=null;void load().catch(()=>{});},1000);}
 function load():Promise<void>{
  if(flight)return flight;const turn=epoch,selected=scope;if(!active(turn)||!selected)return Promise.resolve();stopTimer();loading.value=true;const request=new AbortController();controller=request;
  const run=async()=>{let submit=false,prepare=false;try{const result=await api.read(selected.meeting,selected.session,request.signal);if(!active(turn)||request.signal.aborted)return;view.value=result;error.value=null;if(intentSession!==null&&intentSession!==result.sessionId){intentSession=null;error.value='录音来源已变化，请重新核对。';}submit=intentSession!==null&&result.state==='READY'&&!pending.value&&!result.transcriptionTaskId;prepare=intentSession!==null&&result.state==='NOT_REQUESTED'&&result.archiveAvailable&&!pending.value&&!result.transcriptionTaskId;if(result.transcriptionTaskId&&!pending.value&&intentSession!==null){if(!processJournal.clear())throw Error('原处理意图尚未清理，请重试核对');intentSession=null;processIntent=null;}}catch(cause){failure(cause,turn);throw cause;}finally{if(active(turn)){loading.value=false;controller=null;flight=null;schedule(turn);}}if(submit&&active(turn))await submitReady();else if(prepare&&active(turn)&&options.canMutate())await start();};flight=run();return flight;
 }
 function canStart(){if(!active(epoch)||!scope||!available.value||!options.canMutate()||pending.value||writing.value||!view.value||!view.value.archiveAvailable||view.value.transcriptionTaskId)throw Error('请先等待归档完成，或在处理页查看已受理的转写。');return{selected:scope,source:view.value,turn:epoch};}
 async function replay(){
  const selected=scope,turn=epoch;if(!active(turn)||!selected||!available.value||!options.canMutate()||!command.snapshot.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw Error('原转写受理请求暂不可恢复。');if(journal.save(selected.actor,selected.meeting,command.snapshot.value)!=='SAVED')throw Error('无法保存原转写请求，请保留当前页面。');
  const receipt=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();if(!active(turn))throw new DOMException('Recording import scope changed','AbortError');const request=new AbortController();controller=request;const current=await api.read(selected.meeting,selected.session,request.signal);if(!active(turn)||request.signal.aborted)throw new DOMException('Recording import scope changed','AbortError');view.value=current;if(!journal.clear())throw Error('转写已受理，原请求恢复记录尚未清理，请重试核对。');if(!processJournal.clear())throw Error('转写已受理，处理意图尚待清理，请核对原请求');command.invalidate();accepted.value=receipt;intentSession=null;processIntent=null;error.value=null;return receipt;
 }
 async function submitReady(){
  const {selected,source,turn}=canStart();if(source.state!=='READY'||!source.fileId)throw Error('录音转写来源尚未就绪。');writing.value=true;
  try{const capabilities=await intake.capabilities(selected.meeting,new AbortController().signal);if(!active(turn)||!options.canMutate())throw new DOMException('Recording import scope changed','AbortError');if(!capabilities.canSubmit)throw Error('当前会议暂不允许提交转写。');if(!capabilities.businessReady)throw Error('会议处理条件尚未就绪，请检查材料和当前版本。');if(capabilities.intake!=='AVAILABLE')throw Error('转写受理服务暂不可用，请稍后重试。');if(capabilities.execution!=='INSTALLED')throw Error('转写执行服务尚未就绪，请检查处理服务。');command.prepare({operation:'TRANSCRIPT_IMPORT',actor:selected.actor,resource:selected.meeting,path:`/api/v1/meetings/${selected.meeting}/transcript-imports`,key:crypto.randomUUID(),body:{expectedRevisionToken:capabilities.revisionToken,orderedFileIds:[source.fileId]}});return await replay();}catch(cause){failure(cause,turn);throw cause;}finally{if(active(turn))writing.value=false;}
 }
 async function start(){
  const {selected,source,turn}=canStart();if(source.state==='READY')return submitReady();if(!source.sessionId)throw Error('尚无已完成录音。');writing.value=true;intentSession=source.sessionId;
  try{if(!processIntent||processIntent.session!==source.sessionId)processIntent={actor:selected.actor,meeting:selected.meeting,session:source.sessionId,requestId:crypto.randomUUID()};if(processJournal.save(selected.actor,selected.meeting,processIntent)!=='SAVED')throw Error('无法保存录音处理意图，请保留当前页面');const result=await api.prepare(selected.meeting,selected.session,processIntent.requestId,new AbortController().signal);if(!active(turn))throw new DOMException('Recording source scope changed','AbortError');if(result.sessionId!==intentSession){intentSession=null;throw Error('录音来源已变化，请重新核对。');}view.value=result;error.value=null;schedule(turn);}catch(cause){failure(cause,turn);throw cause;}finally{if(active(turn))writing.value=false;}
  if(active(turn)&&view.value?.state==='READY')return submitReady();
 }
 async function retry(){const turn=epoch;if(writing.value)throw Error('请等待原请求完成。');writing.value=true;try{return await replay();}catch(cause){failure(cause,turn);throw cause;}finally{if(active(turn))writing.value=false;}}
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的原转写请求。');if(!journal.clear())throw Error('原请求恢复记录暂未清理。');command.invalidate();error.value=null;intentSession=null;}
 return{view:readonly(view),accepted:readonly(accepted),error:readonly(error),loading:readonly(loading),available:readonly(available),busy,pending,command,synchronize,load,start,retry,discardRejected,dispose};
}
