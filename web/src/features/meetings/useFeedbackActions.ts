import {computed,shallowRef,shallowReadonly} from 'vue';
import {useCommand} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {FEEDBACK_WORKSPACE,validFeedbackAction,type createFeedbackApi,type FeedbackDetail,type FeedbackReceipt} from '../../shared/api/feedback';
import {ApiError} from '../../shared/api/client';
export function useFeedbackActions(api:Pick<ReturnType<typeof createFeedbackApi>,'send'|'detail'>,actor:()=>string|null,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'feedback-actions',validFeedbackAction),detail=shallowRef<FeedbackDetail|null>(null),available=shallowRef(false),reading=shallowRef(false),concealed=shallowRef(false);
 let owner:string|null=null,id:string|null=null,epoch=0,read:AbortController|null=null;
 const command=useCommand<FeedbackReceipt>(async c=>{if(!owner||owner!==actor()||!validFeedbackAction(c,owner,FEEDBACK_WORKSPACE))throw new DOMException('Feedback identity changed','AbortError');return api.send(c);},32768);
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>reading.value||command.state.value==='SENDING'),original=computed<Record<string,unknown>|null>(()=>!concealed.value&&command.snapshot.value?JSON.parse(command.snapshot.value.body):null);
 function active(turn:number){return turn===epoch&&!!owner&&owner===actor();}
 function dispose(){epoch++;read?.abort();read=null;owner=null;id=null;reading.value=false;available.value=false;detail.value=null;command.invalidate();}
 function synchronize(){dispose();const who=actor();if(!who)return;owner=who;concealed.value=false;const found=journal.load(who,FEEDBACK_WORKSPACE);if(['INVALID','UNAVAILABLE'].includes(found.kind))throw Error('反馈处理原请求暂无法恢复，请保留页面重试。');available.value=true;if(found.kind==='RESTORED'){command.restore(found.value);id=found.value.resource;}}
 function deny(cause:unknown,turn:number){if(active(turn)&&cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status)){concealed.value=true;available.value=false;detail.value=null;}}
 async function open(feedbackId:string){if(!active(epoch)||!available.value||pending.value&&command.snapshot.value?.resource!==feedbackId)throw Error('请先处理原反馈操作。');if(id!==feedbackId)detail.value=null;id=feedbackId;return reload();}
 async function reload(){const turn=epoch,requested=id;if(!active(turn)||!requested||!available.value)throw Error('请先选择当前反馈。');read?.abort();const request=new AbortController();read=request;reading.value=true;try{const value=await api.detail(requested,request.signal);if(!active(turn)||request.signal.aborted||id!==requested)throw new DOMException('Feedback scope changed','AbortError');detail.value=value;return value;}catch(cause){deny(cause,turn);throw cause;}finally{if(active(turn))reading.value=false;}}
 async function retry(){const turn=epoch,c=command.snapshot.value;if(!active(turn)||!available.value||!c||busy.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw Error('请核对原反馈操作。');
  if(journal.save(owner!,FEEDBACK_WORKSPACE,c)!=='SAVED')throw Error('无法保存原反馈请求，请保留页面重试。');
  try{const receipt=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();if(!active(turn))throw new DOMException('Feedback identity changed','AbortError');id=c.resource;await reload();if(!active(turn))throw new DOMException('Feedback identity changed','AbortError');if(!journal.clear())throw Error('反馈已受理，原记录尚未清理，请重新核实。');command.invalidate();return receipt;}catch(cause){deny(cause,turn);throw cause;}
 }
 function prepare(operation:string,suffix:string,extra:Record<string,string>){if(!active(epoch)||!available.value||pending.value||busy.value||!id||detail.value?.feedback.id!==id)throw Error('请重新读取反馈并处理原请求。');command.prepare({actor:owner!,resource:id,operation,path:`/api/v1/feedback/${id}${suffix}`,key:crypto.randomUUID(),body:{expectedRevisionToken:detail.value.feedback.revisionToken,...extra}});if(!validFeedbackAction(command.snapshot.value,owner!,FEEDBACK_WORKSPACE)){command.invalidate();throw Error('请核对反馈处理内容。');}return retry();}
 return {command,detail:shallowReadonly(detail),available:shallowReadonly(available),original,pending,busy,synchronize,open,reload,retry,dispose,
 supplement:(text:string)=>prepare('FEEDBACK_SUPPLEMENT','/supplements',{text}),review:(decision:string,reason:string)=>prepare('FEEDBACK_REVIEW','/review',{decision,reason}),undo:()=>prepare('FEEDBACK_UNDO_REVIEW','/review/undo',{}),
 discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的反馈操作。');if(!journal.clear())throw Error('原反馈记录暂无法清理。');command.invalidate();},logout(){const cleared=journal.clear();dispose();return cleared;}
 };
}
