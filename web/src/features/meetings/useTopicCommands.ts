import {computed,shallowReadonly,shallowRef} from 'vue';
import {useCommand} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {TOPIC_WORKSPACE,validTopicCommand,type createTopicsApi,type Topic} from '../../shared/api/topics';
import {ApiError} from '../../shared/api/client';
/** One private original operation per tab, shared by topic creation, editing and grouping. */
export function useTopicCommands(api:Pick<ReturnType<typeof createTopicsApi>,'send'|'list'|'memberships'>,actor:()=>string|null,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'topics',validTopicCommand),topics=shallowRef<readonly Topic[]>([]),available=shallowRef(false),reading=shallowRef(false),concealed=shallowRef(false);
 let owner:string|null=null,epoch=0,read:AbortController|null=null;
 const command=useCommand<Topic|null>(async c=>{if(owner!==actor()||!owner||!validTopicCommand(c,owner,TOPIC_WORKSPACE))throw new DOMException('Topic actor changed','AbortError');return api.send(c);},32768);
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>reading.value||command.state.value==='SENDING'),original=computed<Record<string,unknown>|null>(()=>!concealed.value&&command.snapshot.value?JSON.parse(command.snapshot.value.body):null);
 function active(turn:number){return turn===epoch&&!!owner&&owner===actor();}
 function dispose(){epoch++;read?.abort();read=null;owner=null;available.value=false;reading.value=false;topics.value=[];command.invalidate();}
 function synchronize(){dispose();const who=actor();if(!who)return;owner=who;concealed.value=false;const found=journal.load(who,TOPIC_WORKSPACE);if(found.kind==='INVALID'||found.kind==='UNAVAILABLE')throw Error('专题恢复记录暂不可用，请保留页面重试。');available.value=true;if(found.kind==='RESTORED')command.restore(found.value);}
 async function reload(){const turn=epoch;if(!active(turn))return;read?.abort();const request=new AbortController();read=request;const rows=await api.list(request.signal);if(active(turn)&&!request.signal.aborted)topics.value=rows;}
 async function retry(){
  const turn=epoch,snapshot=command.snapshot.value;if(!active(turn)||!available.value||!snapshot||busy.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw Error('请先核对原专题操作和当前身份。');
  if(journal.save(owner!,TOPIC_WORKSPACE,snapshot)!=='SAVED')throw Error('专题原请求无法保存，请保留页面重试。');
  try{const receipt=command.state.value==='ACKNOWLEDGED'?command.ack.value:await command.send();if(!active(turn))throw new DOMException('Topic scope changed','AbortError');reading.value=true;const request=new AbortController();read=request;const rows=await api.list(request.signal);
   if(!active(turn)||request.signal.aborted)throw new DOMException('Topic scope changed','AbortError');
   if(['TOPIC_ASSIGN','TOPIC_UNASSIGN'].includes(snapshot.operation))await api.memberships([snapshot.resource],request.signal);
   if(snapshot.operation==='TOPIC_BATCH_ASSIGN')await api.memberships((JSON.parse(snapshot.body).meetings as Array<{meetingId:string}>).map(row=>row.meetingId),request.signal);
   if(!active(turn)||request.signal.aborted)throw new DOMException('Topic scope changed','AbortError');topics.value=rows;if(!journal.clear())throw Error('专题已受理，恢复记录尚未清理，请重新核实。');command.invalidate();return receipt;
  }catch(cause){if(active(turn)&&cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0)){concealed.value=true;topics.value=[];available.value=false;}throw cause;}finally{if(active(turn))reading.value=false;}
 }
 async function prepare(operation:string,resource:string,path:string,body:unknown){if(!active(epoch)||!available.value||pending.value)throw Error('请先处理原专题操作。');command.prepare({operation,resource,path,actor:owner!,key:crypto.randomUUID(),body});if(!validTopicCommand(command.snapshot.value,owner!,TOPIC_WORKSPACE)){command.invalidate();throw Error('请核对专题名称、说明和版本。');}return retry();}
 const root='/api/v1/topics';
 return {command,pending,busy,original,available:shallowReadonly(available),topics:shallowReadonly(topics),synchronize,reload,retry,dispose,
 create:(name:string,description:string|null)=>prepare('TOPIC_CREATE',TOPIC_WORKSPACE,root,{name,description}),
 update:(id:string,expectedRevisionToken:string,name:string,description:string|null)=>prepare('TOPIC_UPDATE',id,root+'/'+id,{expectedRevisionToken,name,description}),
 remove:(id:string,expectedRevisionToken:string)=>prepare('TOPIC_DELETE',id,root+'/'+id,{expectedRevisionToken}),
 assign:(meeting:string,topic:string,expectedRevisionToken:string)=>prepare('TOPIC_ASSIGN',meeting,`/api/v1/meetings/${meeting}/topics`,{topicId:topic,expectedRevisionToken}),
 unassign:(meeting:string,topic:string,expectedRevisionToken:string)=>prepare('TOPIC_UNASSIGN',meeting,`/api/v1/meetings/${meeting}/topics/${topic}`,{expectedRevisionToken}),
 assignMany:(meetings:Array<{meetingId:string;expectedRevisionToken:string}>,topicId:string|null)=>prepare('TOPIC_BATCH_ASSIGN',TOPIC_WORKSPACE,root+'/assignments',{meetings,topicId}),
 discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的专题操作。');if(!journal.clear())throw Error('专题恢复记录暂无法清理。');command.invalidate();},
 logout(){const cleared=journal.clear();dispose();return cleared;}
 };
}
