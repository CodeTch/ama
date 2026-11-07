<script setup lang="ts">
import {computed,onMounted,onUnmounted,nextTick,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useMeetingInfoEditor} from './useMeetingInfoEditor';
import {useInfoSuggestions} from './useInfoSuggestions';
import Icon from '../../shared/Icon.vue';
import {usePeopleDirectory} from './usePeopleDirectory';
import {useSpeakerRosterSource} from './useSpeakerRosterSource';
import MeetingInfoForm from './MeetingInfoForm.vue';
import {ApiError} from '../../shared/api/client';
import {validateFinalInformation,type InformationValidation} from '../minutes/final-information';
import type {Person,Category} from '../../shared/api/meeting-options';
import type {InfoFields} from '../../shared/api/meeting-info';
const props=defineProps<{meetingId:string;eligible:boolean;protectDirtyClose?:boolean;locked?:boolean;presentation?:'drawer'|'final'}>(),emit=defineEmits<{saved:[];close:[]}>();
const {session,info,infoSuggestions,journals,options,transcripts,transcriptCorrections,infoScopeReady,meetingScope}=useApplication(),actor=()=>session.current.value?.actor.enterpriseId??null;
const suspended=ref(false);
const eligible=()=>(props.eligible||suspended.value)&&infoScopeReady.value&&meetingScope.value===props.meetingId;
const editor=useMeetingInfoEditor(info,actor,()=>props.meetingId,eligible,journals);
const suggestions=useInfoSuggestions(infoSuggestions,actor,()=>props.meetingId,eligible,()=>!!props.locked||!!editor.draft.value?.conflict.value||editor.busy.value||!['EMPTY','ACKNOWLEDGED'].includes(editor.command.state.value),()=>editor.saved.value?.revisionToken??null,async(receipt,command,suggestion)=>{await editor.reload();if(editor.error.value)throw editor.error.value;const intent=JSON.parse(command.body);if(intent.decision==='ACCEPT'&&suggestion){const field={NAME:'name',SUMMARY:'summary',CATEGORY:'category'} as const;editor.mergeSuggestedField(field[suggestion.field],intent.expectedRevisionToken,receipt.revisionToken);}informationSaveMessage.value='';emit('saved');},journals);
const suggestionPending=computed(()=>['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(suggestions.command.state.value)),suggestionError=computed(()=>suggestions.error.value instanceof Error?suggestions.error.value.message:null);
const stopSuggestionRevision=watch(()=>editor.saved.value?.revisionToken,()=>{if(editor.saved.value)void suggestions.reload();});
async function decideSuggestion(id:string,decision:'ACCEPT'|'REJECT'){try{localError.value=null;await suggestions.decide(id,decision);}catch(cause){localError.value=cause instanceof Error?cause.message:'建议操作暂不可用。';}}
const informationSaveMessage=ref(''),infoForm=ref<InstanceType<typeof MeetingInfoForm>>(),validation=shallowRef<InformationValidation|null>(null);
const leaveDialog=ref<HTMLDialogElement>();let leavePromise:Promise<boolean>|null=null,leaveResolve:((v:boolean)=>void)|null=null;
const categories=shallowRef<readonly Category[]>([]),categoryCursor=ref<string|null>(null),optionsBusy=ref(false),optionsError=ref<string|null>(null),localError=ref<string|null>(null);
const directoryOpen=ref(false),directory=usePeopleDirectory(options,actor,()=>props.meetingId,()=>eligible()&&directoryOpen.value);
function openDirectory(open:boolean){directoryOpen.value=open;if(open)void directory.catalogue();}
let optionEpoch=0,optionController:AbortController|null=null;
function resetOptions(){informationSaveMessage.value='';optionEpoch++;optionController?.abort();optionController=null;directoryOpen.value=false;categories.value=[];categoryCursor.value=null;optionsBusy.value=false;optionsError.value=null;localError.value=null;}
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{resetOptions();if(actor()&&eligible()){void editor.reload();void loadOptions();}},{immediate:true,flush:'sync'});
const logoutStop=session.onLogout(editor.logout);
async function loadOptions(more=false){
 const who=actor(),id=props.meetingId;if(!who||!eligible())return;
 const generation=++optionEpoch;optionController?.abort();const controller=new AbortController();optionController=controller;optionsBusy.value=true;optionsError.value=null;
 const active=()=>generation===optionEpoch&&actor()===who&&props.meetingId===id&&eligible();
 try{const page=await options.categories(more?categoryCursor.value:null,controller.signal);if(active()){categories.value=more?[...new Map([...categories.value,...page.items].map(c=>[c.id,c])).values()]:page.items;categoryCursor.value=page.nextCursor;}}
 catch(cause){if(active())optionsError.value=cause instanceof ApiError?cause.message:'会议类别暂不可用，请重试。';}
 finally{if(active()){optionsBusy.value=false;optionController=null;}}
}
const speakerRoster=useSpeakerRosterSource({current:transcripts.current,speakers:transcriptCorrections.speakers,people:options.people},actor,()=>props.meetingId,()=>eligible()&&!editor.busy.value&&!editor.recoveryBlocked.value&&!suggestionPending.value&&!suggestions.busy.value&&['EMPTY','ACKNOWLEDGED'].includes(editor.command.state.value),()=>editor.draft.value?.value.value??null,edit);
const rosterPeople=shallowRef<Record<string,Person>>({});let rosterEpoch=0,rosterController:AbortController|null=null;
const stopRoster=watch(()=>[actor(),props.meetingId,eligible(),JSON.stringify(editor.draft.value?.value.value.rosterDraftIds??[])],()=>{
 const turn=++rosterEpoch;rosterController?.abort();rosterPeople.value={};const who=actor(),id=props.meetingId,ids=editor.draft.value?.value.value.rosterDraftIds??[];if(!who||!eligible()||!ids.length)return;
 const controller=new AbortController();rosterController=controller;let index=0;
 const valid=()=>turn===rosterEpoch&&!controller.signal.aborted&&actor()===who&&props.meetingId===id&&eligible();
 async function worker(){while(valid()&&index<ids.length){const key=ids[index++]!;try{let cursor:string|null=null;const seen=new Set<string>();for(let page=0;page<10;page++){const result=await options.people(key,cursor,controller.signal);if(!valid())return;const match=result.items.find(p=>p.stableId===key);if(match){rosterPeople.value={...rosterPeople.value,[key]:match};break;}if(result.nextCursor===null||seen.has(result.nextCursor))break;seen.add(result.nextCursor);cursor=result.nextCursor;}}catch{/* Display only. Missing metadata never invents a department or changes a grant. */}}}
 void Promise.all([worker(),worker()]);
},{flush:'sync'});
const rosterNames=computed(()=>Object.fromEntries([...(editor.saved.value?.confirmedRoster??[]),...(editor.saved.value?.rosterDraft??[])].filter(x=>x.enterpriseId!==null).map(x=>[x.enterpriseId!,x.displayName])));
const message=computed(()=>localError.value??(editor.error.value instanceof ApiError?editor.error.value.message:editor.error.value instanceof Error?editor.error.value.message:null));
function edit(value:InfoFields){try{if(props.locked)return;localError.value=null;validation.value=null;informationSaveMessage.value='';editor.edit(value);if(matchesSaved()&&!pendingInfo.value&&!editor.draft.value?.conflict.value)editor.discard();}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂无法保存。';}}
function resolve(action:'reapply'|'discard'){try{localError.value=null;editor[action]();}catch(cause){localError.value=cause instanceof Error?cause.message:'请先恢复原操作。';}}
async function send(kind:'save'|'confirm'|'retry'){
 localError.value=null;
 if(kind==='confirm'||kind==='retry'&&editor.command.snapshot.value?.operation==='CONFIRM_ROSTER')await editor.confirmRoster();else await editor.save();
 if(editor.command.state.value==='ACKNOWLEDGED')emit('saved');
}
function validateInformation(){if(!editor.draft.value)return false;validation.value=validateFinalInformation(editor.draft.value.value.value);if(validation.value){localError.value=null;void nextTick(()=>infoForm.value?.focusField(validation.value!.field));return false;}localError.value=null;return true;}
async function saveFinalInformation(){if(!validateInformation())return false;return saveInformation();}
async function confirmFinalRoster(){if(!editor.saved.value||editor.draft.value?.dirty.value||editor.draft.value?.conflict.value)return false;if(editor.saved.value.rosterDraftId){await send('confirm');if(editor.error.value)return false;}return !!editor.saved.value?.confirmedRosterId;}
async function prepareFinal(){return await saveFinalInformation()&&await confirmFinalRoster();}
const pendingInfo=computed(()=>['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(editor.command.state.value));
const closeBlocked=computed(()=>editor.busy.value||suggestions.busy.value||editor.recoveryBlocked.value
 ||suggestionPending.value&&(!suggestions.persisted.value||suggestions.blocked.value)
 ||pendingInfo.value&&!['SAVED','RESTORED'].includes(editor.storageStatus.value));
function cancel(){if(closeBlocked.value){localError.value='原操作结果尚未确认，请先恢复原请求。';return false;}if(pendingInfo.value||suggestionPending.value){suspended.value=true;return true;}try{if(editor.draft.value?.dirty.value||editor.draft.value?.conflict.value||['CONFLICT','REJECTED'].includes(editor.command.state.value))editor.discard();if(['INVALID','UNAVAILABLE'].includes(editor.storageStatus.value)){localError.value='恢复记录暂无法清理，请保持页面打开并重试。';return false;}localError.value=null;return true;}catch(cause){localError.value=cause instanceof Error?cause.message:'暂无法放弃修改。';return false;}}
async function close(){if(props.protectDirtyClose?await beforeLeave():cancel())emit('close');}
function matchesSaved(){const d=editor.draft.value;return !!d&&JSON.stringify(d.value.value)===JSON.stringify(d.saved.value);}
async function saveAndClose(kind:'save'|'retry'){informationSaveMessage.value='';if(kind==='save'&&!pendingInfo.value&&!editor.draft.value?.conflict.value&&matchesSaved()){if(editor.draft.value?.dirty.value)editor.discard();informationSaveMessage.value='已保存';if((props.presentation??'drawer')==='drawer')emit('close');return;}await send(kind);if(editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value)informationSaveMessage.value='已保存';if((props.presentation??'drawer')==='drawer'&&editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value)emit('close');}
const canPrepare=computed(()=>!editor.draft.value?.conflict.value&&!!editor.saved.value&&!!editor.draft.value&&!closeBlocked.value&&!pendingInfo.value&&!suggestionPending.value&&!editor.recoveryBlocked.value);
async function saveInformation(){if(!actor()||!eligible()||!canPrepare.value)return false;if(matchesSaved()){if(editor.draft.value?.dirty.value)editor.discard();informationSaveMessage.value='已保存';return true;}await saveAndClose('save');return editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value;}
watch(()=>props.eligible,open=>{if(open)suspended.value=false;},{flush:'sync'});
watch(()=>[actor(),props.meetingId],()=>{suspended.value=false;},{flush:'sync'});
const pendingRecovery=computed(()=>pendingInfo.value||suggestionPending.value);
const dirty=computed(()=>!!editor.draft.value?.dirty.value&&!matchesSaved()),conflict=computed(()=>!!editor.draft.value?.conflict.value);
function focusField(field:InformationValidation['field']){infoForm.value?.focusField(field);}
async function beforeLeave():Promise<boolean>{if(!eligible())return true;if(closeBlocked.value||pendingRecovery.value){localError.value='原操作结果尚未确认，请先恢复原请求。';return false;}if(!dirty.value&&!conflict.value)return true;if(leavePromise)return leavePromise;leavePromise=new Promise(resolve=>leaveResolve=resolve);leaveDialog.value?.showModal();return leavePromise;}
async function decideLeave(choice:'save'|'discard'|'stay'){if(choice==='save'&&!await saveInformation())return;if(choice==='discard'){try{editor.discard();}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂无法放弃';return;}}leaveDialog.value?.close();const done=leaveResolve;leavePromise=null;leaveResolve=null;done?.(choice!=='stay');}
function protectUnload(event:BeforeUnloadEvent){if(eligible()&&(dirty.value||pendingRecovery.value||closeBlocked.value)){event.preventDefault();event.returnValue='';}}
onMounted(()=>window.addEventListener('beforeunload',protectUnload));
defineExpose({reload:editor.reload,revisionToken:computed(()=>editor.saved.value?.revisionToken??null),prepareFinal,validateInformation,saveFinalInformation,confirmFinalRoster,focusField,beforeLeave,dirty,conflict,cancel,closeBlocked,canPrepare,saveInformation,informationSaveMessage,pendingRecovery});
onUnmounted(()=>{window.removeEventListener('beforeunload',protectUnload);leaveResolve?.(false);stopRoster();rosterEpoch++;rosterController?.abort();stopSuggestionRevision();suggestions.dispose();stop();logoutStop();resetOptions();editor.dispose();});
</script>
<template>
 <p v-if="editor.loading.value&&!editor.draft.value" role="status" class="card feedback">正在加载可编辑信息…</p>
 <MeetingInfoForm :mark-date="protectDirtyClose" ref="infoForm" :validation="validation" :presentation="props.presentation??'drawer'" :prevent-close="closeBlocked" @close="close" v-if="editor.draft.value" :value="editor.draft.value.value.value" :dirty="editor.draft.value.dirty.value" :busy="!!locked||editor.busy.value||suggestions.busy.value||suggestionPending" :blocked="editor.recoveryBlocked.value" :state="editor.command.state.value" :operation="editor.command.snapshot.value?.operation??null" :conflict="editor.draft.value.conflict.value!==null" :conflict-value="editor.draft.value.conflict.value?.value??null" :conflict-token="editor.draft.value.conflict.value?.token??null" :can-confirm="editor.saved.value?.rosterDraftId!=null" :categories="categories" :people="directory.people.value" :departments="directory.departments.value" :people-ready="directory.peopleReady.value" :roster-names="rosterNames" :roster-people="rosterPeople" :speaker-busy="speakerRoster.busy.value" :speaker-message="speakerRoster.message.value" :speaker-error="speakerRoster.error.value" :speaker-candidates="speakerRoster.candidates.value" @speakers="speakerRoster.load" :options-busy="optionsBusy||directory.busy.value" :options-error="directory.error.value" :category-error="optionsError" :category-more="categoryCursor!==null" :people-more="directory.more.value" :error="message" :storage-status="editor.storageStatus.value" @edit="edit" @save="saveAndClose('save')" @confirm="send('confirm')" @retry="saveAndClose('retry')" @reload="editor.reload" @reapply="resolve('reapply')" @discard="resolve('discard')" @directory="openDirectory" @directory-retry="directory.retry" @search="(q,dep)=>directory.search(q,dep)" @people="(q,dep)=>directory.search(q,dep,true)" @categories="more=>loadOptions(more)">
  <template v-for="entry in [{field:'NAME',slot:'name-suggestion',label:'名称'},{field:'CATEGORY',slot:'category-suggestion',label:'分类'},{field:'SUMMARY',slot:'summary-suggestion',label:'摘要'}]" :key="entry.field" #[entry.slot]><div v-for="suggestion in suggestions.items.value.filter(s=>s.field===entry.field)" :key="suggestion.id" class="ac-inline-suggestion" :aria-label="'AI '+entry.label+'建议'">
   <span :title="`生成时间：${new Date(suggestion.createdAt).toLocaleString('zh-CN')} · 来源转写版本 ${suggestion.transcriptConfirmationId} · ${suggestion.value}`">AI建议：{{suggestion.field==='CATEGORY'?(categories.find(c=>c.id===suggestion.value)?.label??'分类暂不可读取'):suggestion.value}}</span><em v-if="suggestion.state==='ACCEPTED'||suggestion.state==='REJECTED'" class="suggestion-decision">{{suggestion.state==='ACCEPTED'?'已采纳':'已拒绝'}}</em><button v-if="(suggestion.state??'PENDING')==='PENDING'" type="button" class="btn sm ghost ac-suggestion-icon" :aria-label="'采纳'+entry.label" title="采纳建议" :disabled="!suggestions.canDecide.value" @click="decideSuggestion(suggestion.id,'ACCEPT')"><Icon name="check" :size="14"/></button><button v-if="(suggestion.state??'PENDING')==='PENDING'" type="button" class="btn sm ghost ac-suggestion-icon" :aria-label="'拒绝'+entry.label+'建议'" title="拒绝建议" :disabled="!suggestions.canDecide.value" @click="decideSuggestion(suggestion.id,'REJECT')"><Icon name="x" :size="14"/></button>
  </div></template>
  <template #suggestions><span v-if="(props.presentation??'drawer')==='final'&&suggestions.loading.value" class="sr-only" role="status">正在核对建议来源…</span><section v-if="suggestions.loading.value&&(props.presentation??'drawer')!=='final'||suggestionError||suggestions.blocked.value||suggestionPending||['CONFLICT','REJECTED'].includes(suggestions.command.state.value)||suggestions.categoryStatus.value==='UNAVAILABLE'" class="info-suggestions" aria-label="AI 信息建议状态">
   <header><strong>AI 信息建议</strong><button type="button" class="btn sm ghost" :disabled="suggestions.loading.value||suggestions.busy.value" @click="suggestions.reload">重新读取建议</button></header>
   <p v-if="suggestions.loading.value" role="status">正在核对建议来源…</p><p v-if="suggestionError" role="alert">{{suggestionError}}</p>
   <p v-if="suggestions.blocked.value" role="alert">原建议操作记录暂无法读取，请保持页面打开并重试。</p>
   <p v-if="suggestionPending" role="status">建议操作结果尚未确认，请恢复原请求。</p><button v-if="suggestionPending||suggestions.command.state.value==='ACKNOWLEDGED'&&suggestionError" type="button" class="btn sm" :disabled="suggestions.busy.value||suggestions.blocked.value" @click="suggestions.retry">重试原建议请求</button>
   <button v-if="['CONFLICT','REJECTED'].includes(suggestions.command.state.value)" type="button" class="btn sm" @click="suggestions.reset">重新核对当前建议</button>
   <p v-if="suggestions.categoryStatus.value==='UNAVAILABLE'" class="small muted">分类建议暂不可用，可手动选择分类或重试建议。</p>
  </section></template>
 </MeetingInfoForm>
 <section v-else-if="message" role="alert" class="card feedback"><p>{{message}}</p><button class="btn" @click="editor.reload">重新加载编辑信息</button></section>
 <dialog ref="leaveDialog" class="notice-dialog" aria-labelledby="dirty-info-title" @cancel.prevent="decideLeave('stay')"><h3 id="dirty-info-title">会议信息有未保存修改</h3><p class="mt16">请选择保存、放弃修改或继续编辑。</p><div class="flex mt16"><button class="btn" @click="decideLeave('stay')">继续编辑</button><button class="btn" :disabled="editor.busy.value" @click="decideLeave('discard')">放弃修改</button><button class="btn primary" :disabled="!canPrepare||editor.busy.value" @click="decideLeave('save')">保存并继续</button></div></dialog>
</template>
<style scoped>.info-suggestions{padding:12px;margin:0 0 16px;border:1px solid var(--border);border-radius:8px}.info-suggestions header{display:flex;justify-content:space-between;align-items:center;gap:8px}.info-suggestions article{margin-top:12px;padding-top:12px;border-top:1px solid var(--border)}.proposal-value{white-space:pre-wrap;overflow-wrap:anywhere;max-height:180px;overflow:auto}.suggestion-actions{display:flex;gap:8px;flex-wrap:wrap}.feedback{padding:24px;margin-bottom:20px}</style>

<style scoped>.ac-inline-suggestion{display:flex;align-items:center;gap:6px;min-width:0;margin-top:4px;font-size:12px;color:var(--muted);height:28px}.ac-inline-suggestion>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0;flex:1}.ac-inline-suggestion>.btn{flex-shrink:0}.ac-inline-suggestion>.ac-suggestion-icon{width:26px;min-width:26px;padding:4px;display:inline-flex;align-items:center;justify-content:center}</style>

<style scoped>.suggestion-decision{font-style:normal;white-space:nowrap;color:var(--muted)}</style>
