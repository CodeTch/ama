<script setup lang="ts">
import {dialogueLabels, originalSpeakerLabel, unassignedSpeakerGroups} from './dialogue-labels';
import SpeakerNamingDialog from './SpeakerNamingDialog.vue';
import type {SpeakerCandidates} from '../governance/workbench-types';
import SpeakerAssignmentDialog from './SpeakerAssignmentDialog.vue';
import {useTranscriptActivation} from './useTranscriptActivation';
import {useSpeakerUndo} from './useSpeakerUndo';
import Icon from '../../shared/Icon.vue';
import {computed,onUnmounted,ref,shallowRef,watch,nextTick} from 'vue';
import {useApplication} from '../../application';
import {useTranscriptEditor} from './useTranscriptEditor';
import TranscriptSpeakerPanel from './TranscriptSpeakerPanel.vue';
import CorrectionPanel from './CorrectionPanel.vue';
import MaterialEnhancementPanel from './MaterialEnhancementPanel.vue';
const enhancementPending=ref(false);
import {useSpeechCorrections} from './useSpeechCorrections';
import TranscriptForm from './TranscriptForm.vue';
import TranscriptFeedbackDialog from './TranscriptFeedbackDialog.vue';
import TranscriptOperationsDialog from './TranscriptOperationsDialog.vue';
import {useTranscriptOperations} from './useTranscriptOperations';
import {ApiError} from '../../shared/api/client';
import type {TranscriptDetail,TranscriptHistory} from '../../shared/api/transcripts';
import type {MediaScope} from './useSourceMedia';
import type {TranscriptEdits} from './transcript-journal';
const speakerPanel=ref<InstanceType<typeof TranscriptSpeakerPanel>>();
const props=withDefaults(defineProps<{meetingId:string;active?:boolean;resultKey?:string;playback?:{fileId:string;ms:number;playing:boolean}|null;showFooter?:boolean;published?:boolean}>(),{showFooter:true,active:true,resultKey:''});const emit=defineEmits<{changed:[];confirmed:[];seek:[fileId:string,startMs:number]}>();const form=ref<InstanceType<typeof TranscriptForm>>(),confirmDialog=ref<HTMLDialogElement>();const {session,options,transcripts,transcriptOperations,transcriptCorrections,workspace,journals,meetingScope,transcriptScopeReady,operationsCommands}=useApplication();
const actor=()=>session.current.value?.actor.enterpriseId??null,eligible=()=>meetingScope.value===props.meetingId&&transcriptScopeReady.value;
const editor=useTranscriptEditor({...transcripts,authorize:async(id,signal)=>(await workspace.navigation(id,signal)).relation==='OWNER'},actor,()=>props.meetingId,eligible,journals),localError=ref<string|null>(null),history=shallowRef<TranscriptHistory|null>(null),selected=shallowRef<TranscriptDetail|null>(null),historyBusy=ref(false),raw=shallowRef<TranscriptDetail|null>(null);
const leaveDialog=ref<HTMLDialogElement>();let leaveDecision:((allowed:boolean)=>void)|null=null;
const feedbackItem=shallowRef<{segmentId:string;source:string;replacement:string;revisionToken:string;initialComment?:string;initialType?:string;initialNote?:string}|null>(null);
function openFeedback(id:string){
 const item=corrections.view.value?.items.find(item=>item.id===id),current=editor.saved.value;
 if(!item||!current||!canUseTools()||operationsCommands.pending.value)return;
 feedbackItem.value={segmentId:item.segmentId,source:item.source,replacement:item.replacement,revisionToken:current.observation.revisionToken};
}
function openTools(){if(tools.pending.value||canUseTools()&&!corrections.pending.value)toolsOpen.value=true;}
const toolsOpen=ref(false),toolsError=ref<string|null>(null);
const canUseTools=()=>!enhancementPending.value&&!!editor.saved.value?.capabilities.canSave&&!editor.draft.value?.dirty.value&&!editor.draft.value?.conflict.value&&!editor.busy.value&&!editor.loading.value&&!editor.recoveryBlocked.value&&editor.pendingOperation()===null&&['EMPTY','ACKNOWLEDGED'].includes(editor.command.state.value);
const tools=useTranscriptOperations({...transcriptOperations,current:transcripts.current},{actor,meeting:()=>props.meetingId,eligible,canMutate:()=>canUseTools()&&!corrections.pending.value,current:()=>editor.saved.value,context:journals});
const correctionError=ref<string|null>(null),bulkBusy=ref(false),correctionInFlight=ref(false);
const corrections=useSpeechCorrections({...transcriptCorrections,current:transcripts.current},{actor,meeting:()=>props.meetingId,eligible,canMutate:()=>canUseTools()&&!tools.pending.value,current:()=>editor.saved.value,context:journals});
const stopCorrections=watch(()=>[actor(),props.meetingId,eligible()],()=>{correctionError.value=null;feedbackItem.value=null;try{corrections.synchronize();}catch(cause){correctionError.value=cause instanceof Error?cause.message:'纠错恢复记录暂不可用。';}},{immediate:true,flush:'sync'});
function restoreFeedback(){
 const snapshot=operationsCommands.command.snapshot.value,who=actor(),meeting=props.meetingId,ready=eligible();
 if(!ready||!who||snapshot?.actor!==who||snapshot.path!==`/api/v1/meetings/${meeting}/feedback`)return;
 const body=JSON.parse(snapshot.body);
 if(!Array.isArray(body.segmentIds)||typeof body.segmentIds[0]!=='string'||typeof body.sourceRevisionToken!=='string'||(typeof body.comment!=='string'&&typeof body.feedbackType!=='string'))return;
 feedbackItem.value={segmentId:body.segmentIds[0],source:body.originalValue??'',replacement:body.suggestedValue??'',revisionToken:body.sourceRevisionToken,initialComment:body.comment,initialType:body.feedbackType,initialNote:body.note};
}
watch(()=>[operationsCommands.command.snapshot.value,actor(),props.meetingId,eligible()] as const,restoreFeedback,{immediate:true});
const voicePending=computed(()=>operationsCommands.pending.value&&operationsCommands.command.snapshot.value?.path===`/api/v1/meetings/${props.meetingId}/voiceprint-applications`);
async function recoverVoice(){try{await operationsCommands.retry();namingNotice.value='声纹已提交审核，可在“我的语料反馈”跟踪。';}catch(cause){namingNotice.value=cause instanceof Error?cause.message:'声纹申请结果尚未确认。';}}
const feedbackPending=computed(()=>operationsCommands.pending.value&&operationsCommands.command.snapshot.value?.path===`/api/v1/meetings/${props.meetingId}/feedback`);
async function loadCorrections(){const who=actor(),id=props.meetingId;try{correctionError.value=null;await corrections.load();}catch(cause){if(who===actor()&&id===props.meetingId&&eligible()&&!denyReview(cause))correctionError.value=cause instanceof Error?cause.message:'纠错建议暂不可用。';}}
async function correctionAction(action:()=>Promise<unknown>){if(bulkBusy.value||correctionInFlight.value)return;correctionInFlight.value=true;const who=actor(),id=props.meetingId;try{correctionError.value=null;await action();if(who!==actor()||id!==props.meetingId||!eligible())return;if(!corrections.pending.value){localError.value=null;await editor.reload();await loadCorrections();}}catch(cause){if(who===actor()&&id===props.meetingId&&eligible()&&!denyReview(cause))correctionError.value=cause instanceof Error?cause.message:'纠错暂无法核实，请保留原请求。';}finally{correctionInFlight.value=false;}}
const bulkDialog=ref<HTMLDialogElement>();
const batch=shallowRef<{decision:'ACCEPT'|'REJECT';ids:string[];token:string}|null>(null);
function prepareBatch(decision:'ACCEPT'|'REJECT'){
 const view=corrections.view.value;
 if(!view||!canUseTools()||bulkBusy.value||tools.pending.value||corrections.pending.value)return;
 const ids=view.items.filter(item=>decision==='ACCEPT'?item.canAccept:item.canReject).map(item=>item.id);
 if(!ids.length)return;
 batch.value={decision,ids,token:view.observation.revisionToken};
 bulkDialog.value?.showModal();
}
async function bulkCorrections(){
 const plan=batch.value;
 bulkDialog.value?.close();
 batch.value=null;
 if(!plan||bulkBusy.value||!canUseTools()||tools.pending.value||corrections.pending.value)return;
 if(plan.token!==editor.saved.value?.observation.revisionToken){
  localError.value='转写已更新，请重新核对处理范围。';
  return;
 }
 const who=actor(),meeting=props.meetingId;
 let completed=0;
 bulkBusy.value=true;
 correctionError.value=null;
 try{
  await corrections.decideBatch(plan.ids,plan.decision);
  completed=plan.ids.length;
  await editor.reload();
  if(editor.error.value)throw editor.error.value;
  await corrections.load();
  if(who===actor()&&meeting===props.meetingId&&eligible())batchStatus.value=`已处理 ${completed} 条纠错建议。`;
 }catch(cause){
  if(who===actor()&&meeting===props.meetingId&&eligible()&&!denyReview(cause)){
   const reason=cause instanceof Error?cause.message:'请核实原操作。';
   correctionError.value=`本次批量包含 ${plan.ids.length} 条建议，${completed?"已提交，请重新读取核实结果":"尚未核实结果，请恢复原操作"}。${reason}`;
  }
 }finally{bulkBusy.value=false;}
}
const batchStatus=ref('');
const reviewDialog=ref<HTMLDialogElement>();
const canReview=computed(()=>!!editor.saved.value?.capabilities.canReadHistory&&!editor.loading.value&&!editor.busy.value);
const speakerUndo=useSpeakerUndo({revision:transcripts.revision},{actor,meeting:()=>props.meetingId,active:()=>eligible()&&props.active,canMutate:()=>canUseTools()&&!tools.pending.value&&!corrections.pending.value,current:()=>editor.saved.value,undo:()=>toolAction(tools.undo),denied:denyReview});
const canEditSpeakers=computed(()=>canReview.value&&!!(editor.saved.value?.saved||raw.value?.selected)&&!speakerUndo.checking.value&&!historyBusy.value&&!bulkBusy.value&&!editor.busy.value&&!tools.pending.value&&!corrections.pending.value);
function openReview(){if(!actor()||!eligible()||!canReview.value)return;if(!reviewDialog.value?.open)reviewDialog.value?.showModal();}
function closeReview(event?:Event){if(!actor()||!eligible()){speakerUndo.cancel();reviewDialog.value?.close();return;}if(bulkBusy.value||editor.busy.value||tools.busy.value||corrections.busy.value){event?.preventDefault();return;}speakerUndo.cancel();reviewDialog.value?.close();}
async function undoLatest(){localError.value=null;await speakerUndo.run();if(canReview.value&&tools.pending.value)toolsOpen.value=true;}
watch(()=>[corrections.pending.value,corrections.busy.value,correctionInFlight.value,canReview.value,actor(),eligible()],()=>{if(corrections.pending.value&&!corrections.busy.value&&!correctionInFlight.value&&canReview.value&&!namingTarget.value&&!assignmentMode.value)void nextTick().then(()=>{if(corrections.pending.value&&!corrections.busy.value&&!correctionInFlight.value&&!namingTarget.value&&!assignmentMode.value)openReview();});},{flush:'post'});
const stopTools=watch(()=>[actor(),props.meetingId,eligible()],()=>{speakerUndo.cancel();reviewDialog.value?.close();toolsOpen.value=false;toolsError.value=null;try{tools.synchronize();if(tools.pending.value)toolsOpen.value=true;}catch(cause){toolsError.value=cause instanceof Error?cause.message:'修订恢复记录暂不可用。';}},{immediate:true,flush:'sync'});
watch(()=>[editor.saved.value?.saved?.id,editor.saved.value?.observation.revisionToken].join(':'),()=>{tools.clearSearch();corrections.clearView();if(editor.saved.value?.saved&&!corrections.pending.value)void loadCorrections();});
function denyReview(cause:unknown){if(!(cause instanceof ApiError)||![401,403].includes(cause.status??0))return false;clearHistory();editor.deny(cause);closeNaming();assignmentMode.value=null;speakerUndo.cancel();reviewDialog.value?.close();toolsOpen.value=false;emit('changed');return true;}
async function toolAction(action:()=>Promise<unknown>,refresh=true){if(bulkBusy.value||corrections.pending.value)return;const who=actor(),id=props.meetingId;toolsError.value=null;try{await action();if(who!==actor()||id!==props.meetingId||!eligible())return;if(!tools.pending.value){localError.value=null;if(refresh)await editor.reload();}}catch(cause){if(who===actor()&&id===props.meetingId&&eligible()&&!denyReview(cause))toolsError.value=cause instanceof Error?cause.message:'修订暂无法核实，请保留原请求。';}}
function discardTool(){try{tools.discardRejected();toolsError.value=null;void editor.reload();}catch(cause){toolsError.value=cause instanceof Error?cause.message:'原修订暂无法清理。';}}
let epoch=0,controller:AbortController|null=null;
function clearHistory(){raw.value=null;epoch++;controller?.abort();controller=null;history.value=null;selected.value=null;historyBusy.value=false;localError.value=null;}
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{clearHistory();if(actor()&&eligible())void editor.reload();},{immediate:true,flush:'sync'});
watch(()=>editor.saved.value,async value=>{if(value)emit('changed');raw.value=null;if(value&&!value.saved&&value.capabilities.canReadHistory){await inspect(null);const original=history.value?.items.find(x=>x.kind==='RAW');if(original){await inspect(original.id);if(selected.value?.selected.kind==='RAW')raw.value=selected.value;}}});
const permissionStop=watch(()=>editor.saved.value?.capabilities.canReadHistory,allowed=>{if(!allowed)clearHistory();},{flush:'sync'});
const stopActivation=useTranscriptActivation(()=>props.active,()=>props.resultKey,()=>!eligible()||editor.busy.value||!['EMPTY','ACKNOWLEDGED'].includes(editor.command.state.value)||!!editor.draft.value?.dirty.value||editor.pendingOperation()!==null||tools.pending.value||corrections.pending.value,()=>editor.reload());
const stopLogout=session.onLogout(()=>{corrections.dispose();correctionError.value=null;tools.dispose();toolsOpen.value=false;toolsError.value=null;editor.logout();});
const message=computed(()=>localError.value??(editor.error.value instanceof Error?editor.error.value.message:null));
const readonlyContent=computed(()=>(editor.saved.value?.saved??raw.value?.selected)?.segments.map(({segmentId,body,marks})=>({segmentId,body,marks}))??[]);
const sourceDetail=computed(()=>editor.saved.value?.saved?editor.saved.value:raw.value);
const provenance=computed(()=>editor.saved.value?.saved?.segments??raw.value?.selected.segments??[]);
const fileLabels=computed(()=>dialogueLabels(provenance.value));
const speakerGroups=computed(()=>[
 ...(corrections.speakers.value?.items.map(item=>({
  id:item.id,label:item.label,enterpriseId:item.enterpriseId,
  segments:item.segmentIds.length,segmentIds:item.segmentIds
 }))??[]),
 ...fileLabels.value.items,
 ...unassignedSpeakerGroups(provenance.value,fileLabels.value.labels,(corrections.speakers.value?.items.length??0)+fileLabels.value.items.length)
]);
const speakerColors=computed(()=>Object.fromEntries(speakerGroups.value.flatMap(group=>
 group.segmentIds.map(segmentId=>[segmentId,group.segmentIds[0]??group.id])
)));
const media=computed<MediaScope|null>(()=>{const d=sourceDetail.value;if(!d?.capabilities.canReadSources)return null;const ids=new Set(provenance.value.filter(s=>s.startMs!==null&&s.endMs!==null).map(s=>s.sourceFileId)),revisionId=d===raw.value?raw.value!.selected.id:null;return{meeting:props.meetingId,revisionId,accessRevisionToken:d.observation.accessRevisionToken,sources:d.sources.filter(s=>ids.has(s.fileId)&&(s.kind==='AUDIO'||s.kind==='VIDEO')&&d.sourceBytes.some(b=>b.fileId===s.fileId&&b.eligibility==='ELIGIBLE')).map(s=>({fileId:s.fileId,sha256:s.sha256,sizeBytes:s.sizeBytes,ordinal:s.ordinal,name:s.name,revisionId,recordingWav:d.parserVersion==='ama.recording-wav.v1'}))};});
function edit(value:TranscriptEdits){if(enhancementPending.value||tools.pending.value||corrections.pending.value)return;try{localError.value=null;editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂不可用。';}}
function resolve(action:'reapply'|'discard'){try{localError.value=null;editor[action]();}catch(cause){localError.value=cause instanceof Error?cause.message:'请先处理原操作。';}}
async function inspect(id:string|null,more=false){
 if(!actor()||!eligible()||!editor.saved.value?.capabilities.canReadHistory)return;const turn=++epoch,who=actor(),meeting=props.meetingId;controller?.abort();const request=new AbortController();controller=request;historyBusy.value=true;localError.value=null;
 const active=()=>turn===epoch&&who===actor()&&meeting===props.meetingId&&eligible()&&editor.saved.value?.capabilities.canReadHistory;
 try{if(id){const result=await transcripts.revision(meeting,id,request.signal);if(active())selected.value=result;}else{const result=await transcripts.history(meeting,more?history.value?.nextCursor??null:null,request.signal);if(active()){history.value=result;selected.value=null;}}}
 catch(cause){if(active()){clearHistory();localError.value=cause instanceof Error?cause.message:'历史读取暂不可用。';if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0))editor.deny(cause);}}
 finally{if(active())historyBusy.value=false;}
}
async function save(){if(!enhancementPending.value&&!bulkBusy.value&&!tools.pending.value&&!corrections.pending.value&&!form.value?.composing)await editor.save();}
async function commit(value:TranscriptEdits,expected:string):Promise<boolean>{
 if(bulkBusy.value||tools.pending.value||corrections.pending.value||form.value?.composing||!editor.draft.value||editor.busy.value||editor.recoveryBlocked.value)return false;
 const original=editor.command.snapshot.value,pending=editor.pendingOperation()!==null&&!['CONFLICT','REJECTED'].includes(editor.command.state.value);
 if(pending){if(original?.operation!=='TRANSCRIPT_SAVE')return false;await editor.retry();}
 else{if(expected!==editor.saved.value?.observation.revisionToken){localError.value='转写或纠错已变化，请核对后重试';return false;}try{editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂不可用。';return false;}await editor.save();}
 return !editor.error.value&&!editor.draft.value?.dirty.value&&!editor.draft.value?.conflict.value;
}
async function confirm(){if(enhancementPending.value||bulkBusy.value||tools.pending.value||corrections.pending.value||form.value?.composing)return;if(editor.draft.value?.dirty.value)await editor.save();if(editor.error.value||editor.draft.value?.dirty.value)return;if(!editor.saved.value?.confirmation?.isCurrent)await editor.confirm();if(editor.saved.value?.confirmation?.isCurrent&&!editor.error.value)emit('confirmed');}
async function begin(){if(bulkBusy.value||tools.pending.value||corrections.pending.value)return;const id=editor.saved.value?.saved?.id??raw.value?.selected.id;if(!id)return;await editor.reload();if(editor.error.value||editor.draft.value?.dirty.value)return;await editor.restore(id);}
async function beforeLeave(){if(voicePending.value){localError.value='请先恢复原声纹申请。';return false;}if(enhancementPending.value){localError.value='请先恢复原材料增强操作。';return false;}if(feedbackPending.value){restoreFeedback();return false;}if(bulkBusy.value)return false;if(corrections.pending.value){openReview();localError.value='请先恢复或处理原纠错操作。';return false;}if(tools.pending.value){toolsOpen.value=true;return false;}if(editor.pendingOperation()!==null&&!['CONFLICT','REJECTED'].includes(editor.command.state.value)){localError.value='请先恢复原转写操作结果。';return false;}if(!editor.draft.value?.dirty.value)return true;if(leaveDecision)return false;return new Promise<boolean>(resolve=>{leaveDecision=resolve;leaveDialog.value?.showModal();});}
async function decideLeave(choice:'save'|'discard'|'stay'){if(choice==='save'){await save();if(editor.error.value||editor.draft.value?.dirty.value)return;}if(choice==='discard'){try{editor.discard();}catch(cause){localError.value=cause instanceof Error?cause.message:'草稿暂无法处理。';return;}}leaveDialog.value?.close();leaveDecision?.(choice!=='stay');leaveDecision=null;}
const namingTarget=shallowRef<{
 group:(typeof speakerGroups.value)[number]; actor:string; meeting:string; revision:string; token:string; originalLabel:string;
}|null>(null);
const assignmentMode=ref<'merge'|'split'|null>(null);
let assignmentSource:{id:string;token:string}|null=null;
const namingBusy=ref(false),namingError=ref<string|null>(null),namingNotice=ref('');
const voiceCandidates=ref<SpeakerCandidates['groups'][number]['candidates']>([]),candidateError=ref('');
function closeNaming(){namingTarget.value=null;namingError.value=null;voiceCandidates.value=[];candidateError.value='';}
watch(()=>[actor(),props.meetingId,eligible()],()=>{closeNaming();assignmentMode.value=null;namingNotice.value='';},{flush:'sync'});
function namingContext(target:NonNullable<typeof namingTarget.value>){
 return namingTarget.value===target&&target.actor===actor()&&target.meeting===props.meetingId&&eligible();
}
async function submitNaming(name:string,enterpriseId:string|null,voice:boolean,voiceprintId?:string){
 const target=namingTarget.value;
 if(!target||namingBusy.value||!namingContext(target))return;
 namingBusy.value=true;namingError.value=null;
 try{
  if(corrections.pending.value){
   const original=corrections.original.value;
   if(original?.label!==name||original?.enterpriseId!==enterpriseId||original?.voiceprintId!==voiceprintId||
      JSON.stringify(original?.segmentIds)!==JSON.stringify(target.group.segmentIds))throw Error('请先恢复原说话人操作。');
   await corrections.retry();
  }else{
   const current=editor.saved.value;
   const original=current?.saved??raw.value?.selected;
   if(original?.id!==target.revision||current?.observation.revisionToken!==target.token)
    throw Error('转写版本已变化，请重新打开说话人编辑。');
   if(editor.draft.value?.dirty.value||editor.draft.value?.conflict.value||enhancementPending.value||tools.pending.value)
    throw Error('请先处理当前转写修改。');
   if(current?.saved?.kind!=='WORKING'){
    if(editor.pendingOperation()==='TRANSCRIPT_RESTORE')await editor.retry();
    else await editor.restore(target.revision);
   }
   if(!namingContext(target))return;
   if(editor.error.value||!canUseTools())throw editor.error.value??Error('当前工作版本不可修改。');
   const available=new Set(editor.saved.value?.saved?.segments.map(segment=>segment.segmentId));
   if(!target.group.segmentIds.every(id=>available.has(id)))throw Error('说话人片段已变化，请重新核对。');
   if(voiceprintId||target.group.label!==name||target.group.enterpriseId!==enterpriseId)await corrections.label([...target.group.segmentIds],name,enterpriseId,voiceprintId);
  }
  if(!namingContext(target))return;
  closeNaming();
  await editor.reload();
  if(target.actor!==actor()||target.meeting!==props.meetingId||!eligible())return;
  namingNotice.value=`说话人已命名 · ${name}（仅本次会议）`;
  if(voice){
   try{
    if(!enterpriseId)throw Error('缺少可核实的人员授权身份，未提交声纹审核。');
    const token=editor.saved.value?.observation.revisionToken;if(!token)throw Error('当前转写版本暂不可用，未提交声纹审核。');
    await operationsCommands.submit(`/api/v1/meetings/${props.meetingId}/voiceprint-applications`,{sourceRevisionToken:token,segmentIds:[...target.group.segmentIds],subjectId:enterpriseId,subjectName:name});
    if(target.actor!==actor()||target.meeting!==props.meetingId||!eligible())return;
    namingNotice.value=`说话人已命名 · ${name}；声纹已提交审核，可在“我的语料反馈”跟踪。`;
   }catch(cause){if(target.actor!==actor()||target.meeting!==props.meetingId||!eligible())return;namingNotice.value=`说话人已命名 · ${name}；${operationsCommands.pending.value?'声纹申请结果尚未确认，请恢复原申请。':cause instanceof Error?cause.message:'声纹暂未提交。'}`;}
  }
 }catch(cause){
  if(namingContext(target)&&!denyReview(cause))namingError.value=cause instanceof Error?cause.message:'说话人操作暂无法核实。';
 }finally{namingBusy.value=false;}
}
async function openSpeakers(mode:'edit'|'merge'|'split'='edit',id?:string){
 if(bulkBusy.value||tools.pending.value||corrections.pending.value||namingBusy.value)return;
 if(mode==='edit'){
  const group=speakerGroups.value.find(item=>item.id===id),who=actor();
  const current=editor.saved.value,revision=current?.saved??raw.value?.selected;
  if(!group||!who||!revision||!current||!canEditSpeakers.value||!eligible())return;
  namingNotice.value='';namingError.value=null;
  namingTarget.value={group,actor:who,meeting:props.meetingId,revision:revision.id,token:current.observation.revisionToken,originalLabel:originalSpeakerLabel(group,provenance.value)};
  voiceCandidates.value=[];candidateError.value='';const target=namingTarget.value;
  try{const value=await operationsCommands.read<SpeakerCandidates>(`/api/v1/meetings/${props.meetingId}/speaker-candidates`);if(!namingContext(target))return;
   const segments=provenance.value.filter(s=>group.segmentIds.includes(s.segmentId)),matching=value.groups.filter(g=>segments.some(s=>s.sourceFileId===g.fileId&&s.startMs!==null&&BigInt(g.startSample)===BigInt(s.startMs)*16n));
   const unambiguous=matching.filter(g=>matching.filter(other=>other.fileId===g.fileId&&other.startSample===g.startSample).length===1);
   const unique=new Map(unambiguous.flatMap(g=>g.candidates).map(c=>[c.voiceprintId,c]));voiceCandidates.value=[...unique.values()].filter(c=>segments.length>0&&segments.every(s=>unambiguous.some(g=>g.fileId===s.sourceFileId&&s.startMs!==null&&BigInt(g.startSample)===BigInt(s.startMs)*16n&&g.candidates.some(v=>v.voiceprintId===c.voiceprintId))));
  }catch{if(namingContext(target))candidateError.value='候选暂不可用，可继续手动核对人员或保留匿名。';}
  return;
 }
 if(!canEditSpeakers.value||!eligible())return;
 namingError.value=null;namingNotice.value='';
 const current=editor.saved.value,revision=current?.saved??raw.value?.selected;
 if(!current||!revision)return;
 assignmentSource={id:revision.id,token:current.observation.revisionToken};
 assignmentMode.value=mode;
}
async function assignSpeakers(sourceId:string,targetOrSegments:string|string[]){
 const mode=assignmentMode.value;
 const from=speakerGroups.value.find(group=>group.id===sourceId);
 const to=typeof targetOrSegments==='string'?speakerGroups.value.find(group=>group.id===targetOrSegments):null;
 if(!mode||!from||namingBusy.value)return;
 const who=actor(),meeting=props.meetingId;
 const valid=()=>who===actor()&&meeting===props.meetingId&&eligible()&&assignmentMode.value===mode;
 namingBusy.value=true;namingError.value=null;
 try{
  let ids:string[],name:string,enterpriseId:string|null;
  if(typeof targetOrSegments==='string'){
   if(!to||from.id===to.id)throw Error('来源与目标不能相同');
   ids=[...new Set([...from.segmentIds,...to.segmentIds])];name=to.label;enterpriseId=to.enterpriseId;
  }else{
   ids=targetOrSegments;
   if(!ids.length||!ids.every(id=>from.segmentIds.includes(id)))throw Error('请选择来源说话人的片段');
   if(ids.length===from.segmentIds.length)throw Error('当前转写版本无法保留零片段说话人，暂不能拆分全部片段。');
   let number=1;
   const names=new Set(speakerGroups.value.map(item=>item.label));
   while(names.has('说话人 '+number))number++;
   name='说话人 '+number;enterpriseId=null;
  }
  if(corrections.pending.value){
   const original=corrections.original.value;
   if(original?.label!==name||original?.enterpriseId!==enterpriseId||original?.voiceprintId!==undefined||
      JSON.stringify(original?.segmentIds)!==JSON.stringify(ids))throw Error('请先恢复原说话人操作。');
   await corrections.retry();
  }else{
   const current=editor.saved.value,revision=current?.saved??raw.value?.selected;
   if(!assignmentSource||assignmentSource.id!==revision?.id||assignmentSource.token!==current?.observation.revisionToken)
    throw Error('转写版本已变化，请重新打开说话人操作。');
   if(editor.draft.value?.dirty.value||editor.draft.value?.conflict.value||enhancementPending.value||tools.pending.value)
    throw Error('请先处理当前转写修改。');
   if(current?.saved?.kind!=='WORKING'){
    if(editor.pendingOperation()==='TRANSCRIPT_RESTORE')await editor.retry();
    else await editor.restore(revision.id);
   }
   if(!valid())return;
   if(editor.error.value||!canUseTools())throw editor.error.value??Error('当前工作版本不可修改。');
   await corrections.label(ids,name,enterpriseId);
  }
  if(!valid())return;
  assignmentMode.value=null;
  namingNotice.value=mode==='merge'?'合并完成 · 可点击「撤销」回退':'拆分完成 · 新说话人已生成，请命名';
  await editor.reload();
 }catch(cause){
  if(valid()&&!denyReview(cause))namingError.value=cause instanceof Error?cause.message:'说话人操作暂无法核实。';
 }finally{namingBusy.value=false;}
}
defineExpose({beforeLeave,save,media,openSpeakers,openReview,undoLatest,canReview,canEditSpeakers,sourceState:computed(()=>editor.loading.value||historyBusy.value?'loading':message.value?'error':sourceDetail.value?'ready':'empty'),speakerLabels:speakerGroups,dirty:computed(()=>!!editor.draft.value?.dirty.value||tools.pending.value||corrections.pending.value)});
onUnmounted(()=>{stopActivation();leaveDecision?.(false);leaveDecision=null;stop();stopTools();stopCorrections();corrections.dispose();tools.dispose();permissionStop();stopLogout();clearHistory();editor.dispose();});
</script>
<template><div v-if="voicePending" class="banner info" role="status">原声纹申请已保留。<button v-if="['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(operationsCommands.command.state.value)" class="btn" @click="recoverVoice">恢复原声纹申请</button><button v-if="['CONFLICT','REJECTED'].includes(operationsCommands.command.state.value)" class="btn" @click="operationsCommands.discardRejected">放弃已拒绝声纹申请</button></div>
 <MaterialEnhancementPanel v-if="!published&&editor.saved.value?.saved?.kind==='WORKING'&&editor.saved.value?.capabilities.canReadHistory" :meeting-id="meetingId" :active="active" :allowed="canUseTools()&&!tools.pending.value&&!corrections.pending.value" @pending="enhancementPending=$event" @adopted="editor.reload().then(loadCorrections)"/>
 <TranscriptForm :playback="playback" :simulated-speech="sourceDetail?.sources.some(source=>source.recognitionOrigin==='LOCAL_HARNESS')??false" :file-labels="fileLabels.labels" :speaker-color-keys="speakerColors" :speaker-labels="Object.fromEntries(corrections.speakers.value?.items.map(x=>[x.id,x.label])??[])" :corrections-ready="!!corrections.view.value" :correction-count="corrections.view.value?.pendingCount??0" :pending-correction-segments="corrections.view.value?.items.filter(x=>x.status==='PENDING').map(x=>x.segmentId)??[]" @accept-all="prepareBatch('ACCEPT')" @reject-all="prepareBatch('REJECT')" :corrections="corrections.view.value?.items" :can-decide="canUseTools()&&!corrections.pending.value&&!tools.pending.value" @decide="(id,decision)=>correctionAction(()=>corrections.decide(id,decision))" @feedback="openFeedback" @undo-correction="id=>correctionAction(()=>corrections.undo(id))" :published="published" @tools="openTools" @review="openReview()" :can-review="canReview" ref="form" :pending-save="editor.pendingOperation()==='TRANSCRIPT_SAVE'&&!['CONFLICT','REJECTED'].includes(editor.command.state.value)" :revision-token="editor.saved.value?.observation.revisionToken" :commit="commit" :provenance="provenance" :playable-files="media?.sources.map(s=>s.fileId)??[]" @seek="(fileId,startMs)=>emit('seek',fileId,startMs)" :show-footer="showFooter" @begin="begin" v-if="editor.draft.value||editor.saved.value?.saved||raw" :value="editor.saved.value?.saved?editor.draft.value?.value.value??readonlyContent:readonlyContent" :editable="!!editor.draft.value&&!!editor.saved.value?.capabilities.canSave" :busy="bulkBusy||editor.busy.value||tools.busy.value||corrections.busy.value" :dirty="editor.draft.value?.dirty.value??false" :conflict="editor.draft.value?.conflict.value?.value??null" :conflict-token="editor.draft.value?.conflict.value?.token??null" :state="editor.command.state.value" :blocked="enhancementPending||editor.recoveryBlocked.value||tools.pending.value||corrections.pending.value" :blocked-reason="enhancementPending?'请先恢复原材料增强操作。':corrections.pending.value?'请先恢复或处理原纠错操作。':tools.pending.value?'请先恢复或处理原修订请求。':undefined" :storage-status="editor.storageStatus.value" :error="message??correctionError" :confirmed="!!editor.saved.value?.confirmation?.isCurrent" :can-confirm="!!editor.saved.value?.capabilities.canConfirm" @edit="edit" @save="save" @confirm="confirmDialog?.showModal()" @retry="editor.retry" @reload="editor.reload" @discard="resolve('discard')" @reapply="resolve('reapply')"/>
 <section v-else class="card transcript-feedback"><h2>转写正文</h2><p v-if="editor.loading.value" role="status">正在读取转写…</p><p v-else-if="message" role="alert">{{message}}</p><p v-else>尚无已保存转写。导入任务完成后重新读取。</p><button class="btn" :disabled="editor.loading.value" @click="editor.reload">重新读取转写</button></section>
 <p v-if="batchStatus&&!published" class="small muted" role="status">{{batchStatus}}</p>
 <dialog v-if="!published" ref="bulkDialog" class="notice-dialog" aria-labelledby="correction-batch-title">
  <h3 id="correction-batch-title">{{batch?.decision==='ACCEPT'?'批量接受纠错':'批量拒绝纠错'}}</h3>
  <p class="mt16">处理整份工作稿中可执行此操作的 {{batch?.ids.length}} 条建议，一次提交并整体撤销。搜索和筛选不会改变处理范围。</p>
  <div class="flex mt16"><button class="btn" @click="bulkDialog?.close()">取消</button><button class="btn primary" @click="bulkCorrections">确认处理</button></div>
 </dialog>
 <dialog v-if="!published" ref="reviewDialog" class="transcript-review-dialog" aria-labelledby="transcript-review-title" @cancel="closeReview($event)"><header class="review-header"><h3 id="transcript-review-title">转写版本与语音审阅</h3><button type="button" class="btn sm ghost" aria-label="关闭转写审阅" @click="closeReview"><Icon name="x" :size="18"/></button></header><div class="review-body"><p v-if="speakerUndo.checking.value" role="status">正在核对当前说话人变更…</p><p v-if="speakerUndo.message.value" role="status">{{speakerUndo.message.value}}</p><p v-if="localError" role="alert">{{localError}}</p><div class="flex mb16"><button type="button" class="btn sm" @click="openTools">批量替换与撤销</button><button type="button" class="btn sm" :disabled="editor.busy.value||editor.recoveryBlocked.value" @click="editor.retry">恢复原操作结果</button><button v-if="editor.draft.value?.dirty.value||['CONFLICT','REJECTED'].includes(editor.command.state.value)" type="button" class="btn sm" @click="resolve('discard')">放弃本地修改／已拒绝操作</button></div>
 <details v-if="!published&&editor.saved.value?.capabilities.canReadHistory" class="transcript-history"><summary>转写历史与原操作恢复</summary><section class="card transcript-feedback"><h2>转写历史</h2><button class="btn" :disabled="historyBusy" @click="inspect(null)">读取历史版本</button><ul v-if="history"><li v-for="item in history.items" :key="item.id"><button class="btn" :disabled="historyBusy" @click="inspect(item.id)">{{item.kind==='RAW'?'原始转写 RAW':'人工工作版本 WORKING'}} · {{item.revisionToken}} · {{item.createdAt}}</button></li></ul><button v-if="history?.hasMore" class="btn" :disabled="historyBusy" @click="inspect(null,true)">下一页历史</button><article v-if="selected"><h3>{{selected.selected.kind==='RAW'?'原始转写':'历史工作版本'}} · {{selected.selected.revisionToken}}</h3><p v-for="s in selected.selected.segments" :key="s.segmentId" class="text">{{s.body}}</p><button class="btn" :disabled="bulkBusy||tools.pending.value||corrections.pending.value||editor.busy.value||editor.draft.value?.dirty.value||!!editor.draft.value?.conflict.value||!selected.capabilities.canRestore" @click="editor.restore(selected.selected.id)">恢复为新的工作版本</button></article></section></details>
 <section v-if="!published&&editor.saved.value?.capabilities.canReadHistory" class="card transcript-feedback" aria-label="说话人与语音增强建议"><section v-if="editor.saved.value?.correction.mode==='CONTROLLED_SPEECH'" aria-label="语音增强建议"><h2>语音增强建议</h2><p class="small muted">建议来自本次语音处理结果，接受后建立新的人工工作版本；原始转写始终保留。</p><div class="flex"><button class="btn sm" :disabled="bulkBusy||corrections.busy.value" @click="loadCorrections">读取纠错建议</button><button class="btn sm" :disabled="bulkBusy||!canUseTools()||tools.pending.value||corrections.pending.value||corrections.busy.value||!corrections.view.value?.canPropose" @click="correctionAction(corrections.propose)">检查语音增强建议</button><button v-if="corrections.pending.value" class="btn sm" :disabled="bulkBusy||corrections.busy.value" @click="correctionAction(corrections.retry)">恢复原纠错操作</button><button v-if="['CONFLICT','REJECTED'].includes(corrections.command.state.value)" class="btn sm" @click="corrections.discardRejected();loadCorrections()">放弃已拒绝的纠错请求</button></div><p v-if="corrections.loading.value" role="status">正在核实语音来源…</p><p v-if="correctionError" role="alert">{{correctionError}}</p></section><p v-else-if="correctionError" role="alert">{{correctionError}}</p><TranscriptSpeakerPanel ref="speakerPanel" v-if="corrections.speakers.value" :snapshot="corrections.speakers.value" :file-labels="fileLabels.items" :lookup-people="options.people" :segments="editor.saved.value?.saved?.segments??[]" :busy="bulkBusy||corrections.busy.value||corrections.pending.value||tools.pending.value" :editable="canUseTools()" @label="(segments,label,enterpriseId)=>correctionAction(()=>corrections.label(segments,label,enterpriseId))" @merge="(segments,label,enterpriseId)=>correctionAction(()=>corrections.label(segments,label,enterpriseId))"/><CorrectionPanel v-if="corrections.view.value" :corrections="corrections.view.value.items" :busy="bulkBusy||!canUseTools()||tools.pending.value||corrections.pending.value||corrections.busy.value" @decision="(id,decision)=>correctionAction(()=>corrections.decide(id,decision==='accept'?'ACCEPT':'REJECT'))" @undo="id=>correctionAction(()=>corrections.undo(id))"/></section>
 </div></dialog>
 <div v-if="!published&&(feedbackPending||tools.pending.value||corrections.pending.value)" class="flex wrap" role="group" aria-label="待确认的转写操作">
  <button v-if="feedbackPending" type="button" class="btn sm" @click="restoreFeedback">恢复原纠错反馈</button>
  <button v-if="tools.pending.value" type="button" class="btn sm" @click="openTools">恢复原批量修订</button>
  <button v-if="corrections.pending.value" type="button" class="btn sm" :disabled="!canReview" @click="openReview">恢复原纠错操作</button>
 </div>
 <p v-if="speakerUndo.message.value" class="small muted" role="status">{{ speakerUndo.message.value }}</p>
 <SpeakerAssignmentDialog v-if="assignmentMode" :mode="assignmentMode" :groups="speakerGroups"
  :segments="provenance" :playable="media?.sources.map(source=>source.fileId)??[]" :busy="namingBusy"
  :error="namingError" @close="assignmentMode=null" @merge="assignSpeakers" @split="assignSpeakers"
  @seek="(fileId,ms)=>emit('seek',fileId,ms)"/>
 <p v-if="namingNotice" class="small muted" role="status">{{ namingNotice }}</p>
 <SpeakerNamingDialog v-if="namingTarget" :original-label="namingTarget.originalLabel"
  :name="namingTarget.group.label" :enterprise-id="namingTarget.group.enterpriseId"
  :external="!('source' in namingTarget.group)&&!namingTarget.group.enterpriseId&&!/^说话人 \d+$/.test(namingTarget.group.label)"
  :candidates="voiceCandidates" :candidates-error="candidateError" :lookup="options.people" :busy="namingBusy" :locked="corrections.pending.value" :error="namingError"
  @close="closeNaming" @submit="submitNaming"/>
 <TranscriptFeedbackDialog v-if="feedbackItem" :meeting-id="meetingId" v-bind="feedbackItem" @close="feedbackItem=null"/>
 <TranscriptOperationsDialog :suggestions-available="!!corrections.view.value" v-if="toolsOpen&&actor()&&eligible()" :matches="tools.matches.value" :segments="editor.saved.value?.saved?.segments??[]" :pending="tools.pending.value" :busy="bulkBusy||tools.busy.value" :searching="tools.searching.value" :can-mutate="canUseTools()&&!corrections.pending.value" :state="tools.command.state.value" :original="tools.original.value" :error="toolsError" @search="query=>toolAction(()=>tools.search(query),false)" @replace="(replacement,ids)=>toolAction(()=>tools.replace(replacement,ids))" @undo="toolAction(tools.undo)" @retry="toolAction(tools.retry)" @discard="discardTool" @close="toolsOpen=false"/>
 <dialog ref="leaveDialog" class="notice-dialog" aria-labelledby="transcript-leave-title" @cancel.prevent="decideLeave('stay')"><h3 id="transcript-leave-title">处理未保存的转写修改</h3><p>离开前可保存当前工作稿，或放弃本地修改。</p><div class="flex mt16"><button class="btn" @click="decideLeave('stay')">继续核对</button><button class="btn" :disabled="editor.busy.value" @click="decideLeave('discard')">放弃修改并离开</button><button class="btn primary" :disabled="editor.busy.value" @click="decideLeave('save')">保存并离开</button></div></dialog>
 <dialog ref="confirmDialog" class="notice-dialog" aria-labelledby="confirm-transcript-title"><h3 id="confirm-transcript-title">确认转写并生成纪要</h3><p class="mt16">确认后将使用当前已保存转写作为纪要依据。未保存的修改会先保存，生成内容仍需人工核对。</p><ul class="small mt12">
  <li>当前稿件 {{editor.saved.value?.saved?.segments.length??0}} 段</li>
  <li>{{editor.draft.value?.dirty.value?'存在待保存修改，确认前将先保存。':'工作稿已保存。'}}</li>
  <li v-if="corrections.view.value">待处理纠错 {{corrections.view.value.pendingCount}} 条</li>
 </ul><p class="small muted mt12">确认、模板设置和纪要生成分别记录结果；发生中断时可查回原操作。</p><div class="flex mt16"><button class="btn" @click="confirmDialog?.close()">返回校正</button><button class="btn primary" :disabled="editor.busy.value" @click="confirm().then(()=>{if(editor.saved.value?.confirmation?.isCurrent)confirmDialog?.close();})">确认并生成</button></div></dialog>
</template>
<style scoped>.transcript-feedback{padding:24px;margin-bottom:20px}.transcript-feedback h2{font-size:18px}.text{white-space:pre-wrap;overflow-wrap:anywhere}li{margin:8px 0}button:disabled{opacity:.5}</style>

<style scoped>.transcript-history{margin:12px 0;color:var(--muted);font-size:12px}.transcript-history summary{cursor:pointer}.transcript-feedback{box-shadow:none}</style>

<style scoped>.transcript-review-dialog{margin:auto;width:min(920px,calc(100vw - 32px));max-height:88dvh;overflow:auto;padding:0;border:0;border-radius:10px;color:var(--text)}.transcript-review-dialog::backdrop{background:rgba(17,24,73,.35)}.review-header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid var(--border)}.review-header h3{font-size:15px;margin:0}.review-body{padding:20px}.review-body>p[role=alert]{color:var(--red);margin-bottom:12px}</style>
