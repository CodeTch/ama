import {computed,shallowRef} from 'vue';
import type {Feedback} from '../../shared/api/feedback';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
export type FeedbackInput={meetingId:string;category:string;comment:string};
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function validInput(value:unknown,meeting:string):value is FeedbackInput{
 if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).sort().join(',')!=='category,comment,meetingId')return false;
 const body=value as FeedbackInput;return uuid(meeting)&&body.meetingId===meeting&&['WORDING','LABEL','ACTION_OWNER'].includes(body.category)&&typeof body.comment==='string'&&!!body.comment.trim()&&body.comment.length<=4096;
}
export function validFeedbackCommand(value:unknown,actor:string,meeting:string):value is CommandSnapshot{
 if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).sort().join(',')!=='actor,body,key,operation,path,resource')return false;
 const c=value as CommandSnapshot;if(c.actor!==actor||c.resource!==meeting||c.operation!=='FEEDBACK_CREATE'||c.path!=='/api/v1/feedback'||!uuid(c.key)||typeof c.body!=='string'||c.body.length>32768)return false;
 try{return validInput(JSON.parse(c.body),meeting);}catch{return false;}
}
export function useFeedbackSubmission(api:{create:(body:FeedbackInput,key:string)=>Promise<Feedback>},actor:()=>string|null,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'feedback-submit',validFeedbackCommand),available=shallowRef(false),cleanup=shallowRef(false);let scope:{actor:string;meeting:string}|null=null;
 const command=useCommand<Feedback>(async c=>{if(!scope||actor()!==scope.actor||!validFeedbackCommand(c,scope.actor,scope.meeting))throw Error('反馈身份已变化，请重新读取');return api.create(JSON.parse(c.body),c.key);},32768);
 const pending=computed(()=>cleanup.value||['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(command.state.value));
 function open(meeting:string):FeedbackInput|null{
  command.invalidate();available.value=false;cleanup.value=false;const who=actor();scope=who&&uuid(meeting)?{actor:who,meeting}:null;if(!scope)return null;
  const stored=journal.load(who!,meeting);if(stored.kind==='INVALID'||stored.kind==='UNAVAILABLE')throw Error('反馈恢复记录暂不可用，请保留页面重试');
  available.value=true;if(stored.kind==='RESTORED'){command.restore(stored.value);return JSON.parse(stored.value.body);}return null;
 }
 async function submit(value:FeedbackInput){
  if(!scope||actor()!==scope.actor||value.meetingId!==scope.meeting||!available.value)throw Error('请先确认反馈所属身份和会议');
  if(command.state.value==='EMPTY'){if(!validInput(value,scope.meeting))throw Error('请输入有效反馈（最多4096字）');command.prepare({operation:'FEEDBACK_CREATE',actor:scope.actor,resource:scope.meeting,path:'/api/v1/feedback',key:crypto.randomUUID(),body:value});}
  if(!command.snapshot.value||journal.save(scope.actor,scope.meeting,command.snapshot.value)!=='SAVED')throw Error('无法保存反馈原请求，请保留页面重试');
  const accepted=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();cleanup.value=true;
  if(!journal.clear())throw Error('反馈已受理，原请求清理尚未完成，请重试确认');
  cleanup.value=false;command.invalidate();return accepted;
 }
 function discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw Error('不能放弃结果未知的反馈');if(!journal.clear())throw Error('反馈原请求暂无法清理');command.invalidate();}
 return {command,pending,available,open,submit,discardRejected,dispose(){available.value=false;scope=null;command.invalidate();}};
}
