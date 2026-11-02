<script setup lang="ts">
import {computed,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useMeetingInfoEditor} from './useMeetingInfoEditor';
import {useInfoSuggestions} from './useInfoSuggestions';
import Icon from '../../shared/Icon.vue';
import {usePeopleDirectory} from './usePeopleDirectory';
import {useSpeakerRosterSource} from './useSpeakerRosterSource';
import MeetingInfoForm from './MeetingInfoForm.vue';
import {ApiError} from '../../shared/api/client';
import {finalInformationError} from '../minutes/final-information';
import type {Person,Category} from '../../shared/api/meeting-options';
import type {InfoFields} from '../../shared/api/meeting-info';
const props=defineProps<{meetingId:string;eligible:boolean;presentation?:'drawer'|'final'}>(),emit=defineEmits<{saved:[];close:[]}>();
const {session,info,infoSuggestions,journals,options,transcripts,transcriptCorrections,infoScopeReady,meetingScope}=useApplication(),actor=()=>session.current.value?.actor.enterpriseId??null;
const eligible=()=>props.eligible&&infoScopeReady.value&&meetingScope.value===props.meetingId;
const editor=useMeetingInfoEditor(info,actor,()=>props.meetingId,eligible,journals);
const suggestions=useInfoSuggestions(infoSuggestions,actor,()=>props.meetingId,eligible,()=>!!editor.draft.value?.dirty.value||!!editor.draft.value?.conflict.value||editor.busy.value||!['EMPTY','ACKNOWLEDGED'].includes(editor.command.state.value),()=>editor.saved.value?.revisionToken??null,async()=>{await editor.reload();if(editor.error.value)throw editor.error.value;emit('saved');},journals);
const suggestionPending=computed(()=>['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(suggestions.command.state.value)),suggestionError=computed(()=>suggestions.error.value instanceof Error?suggestions.error.value.message:null);
const stopSuggestionRevision=watch(()=>editor.saved.value?.revisionToken,()=>{if(editor.saved.value)void suggestions.reload();});
async function decideSuggestion(id:string,decision:'ACCEPT'|'REJECT'){try{localError.value=null;await suggestions.decide(id,decision);}catch(cause){localError.value=cause instanceof Error?cause.message:'建议操作暂不可用。';}}
const informationSaveMessage=ref('');
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
 try{const page=await options.categories(more?categoryCursor.value:null,controller.signal);if(active()){categories.value=page.items;categoryCursor.value=page.nextCursor;}}
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
function edit(value:InfoFields){try{localError.value=null;editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂无法保存。';}}
function resolve(action:'reapply'|'discard'){try{localError.value=null;editor[action]();}catch(cause){localError.value=cause instanceof Error?cause.message:'请先恢复原操作。';}}
async function send(kind:'save'|'confirm'|'retry'){
 localError.value=null;
 if(kind==='confirm'||kind==='retry'&&editor.command.snapshot.value?.operation==='CONFIRM_ROSTER')await editor.confirmRoster();else await editor.save();
 if(editor.command.state.value==='ACKNOWLEDGED')emit('saved');
}
async function prepareFinal(){if(!editor.draft.value)return false;const validation=finalInformationError(editor.draft.value.value.value);if(validation){localError.value=validation;return false;}localError.value=null;if(editor.draft.value?.dirty.value){await send('save');if(editor.error.value||editor.draft.value?.dirty.value)return false;}if(editor.saved.value?.rosterDraftId){await send('confirm');if(editor.error.value)return false;}return !!editor.saved.value;}
const closeBlocked=computed(()=>editor.busy.value||suggestions.busy.value||suggestionPending.value||editor.recoveryBlocked.value||['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(editor.command.state.value));
function cancel(){if(closeBlocked.value){localError.value='原操作结果尚未确认，请先恢复原请求。';return false;}try{if(editor.draft.value?.dirty.value||editor.draft.value?.conflict.value||['CONFLICT','REJECTED'].includes(editor.command.state.value))editor.discard();if(['INVALID','UNAVAILABLE'].includes(editor.storageStatus.value)){localError.value='恢复记录暂无法清理，请保持页面打开并重试。';return false;}localError.value=null;return true;}catch(cause){localError.value=cause instanceof Error?cause.message:'暂无法放弃修改。';return false;}}
function close(){if(cancel())emit('close');}
async function saveAndClose(kind:'save'|'retry'){informationSaveMessage.value='';await send(kind);if(editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value)informationSaveMessage.value='已保存';if((props.presentation??'drawer')==='drawer'&&editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value)emit('close');}
const canPrepare=computed(()=>!!editor.saved.value&&!!editor.draft.value&&!closeBlocked.value&&!editor.recoveryBlocked.value);
async function saveInformation(){if(!actor()||!eligible()||!canPrepare.value)return false;if(!editor.draft.value?.dirty.value){informationSaveMessage.value='没有未保存的会议信息';return true;}await saveAndClose('save');return editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value;}
defineExpose({prepareFinal,cancel,closeBlocked,canPrepare,saveInformation,informationSaveMessage});
onUnmounted(()=>{stopRoster();rosterEpoch++;rosterController?.abort();stopSuggestionRevision();suggestions.dispose();stop();logoutStop();resetOptions();editor.dispose();});
</script>
<template>
 <p v-if="editor.loading.value&&!editor.draft.value" role="status" class="card feedback">正在加载可编辑信息…</p>
 <MeetingInfoForm :presentation="props.presentation??'drawer'" @close="close" v-if="editor.draft.value" :value="editor.draft.value.value.value" :dirty="editor.draft.value.dirty.value" :busy="editor.busy.value||suggestions.busy.value||suggestionPending" :blocked="editor.recoveryBlocked.value" :state="editor.command.state.value" :operation="editor.command.snapshot.value?.operation??null" :conflict="editor.draft.value.conflict.value!==null" :conflict-value="editor.draft.value.conflict.value?.value??null" :conflict-token="editor.draft.value.conflict.value?.token??null" :can-confirm="editor.saved.value?.rosterDraftId!=null" :categories="categories" :people="directory.people.value" :departments="directory.departments.value" :people-ready="directory.peopleReady.value" :roster-names="rosterNames" :roster-people="rosterPeople" :speaker-busy="speakerRoster.busy.value" :speaker-message="speakerRoster.message.value" :speaker-error="speakerRoster.error.value" @speakers="speakerRoster.load" :options-busy="optionsBusy||directory.busy.value" :options-error="directory.error.value??optionsError" :category-more="categoryCursor!==null" :people-more="directory.more.value" :error="message" :storage-status="editor.storageStatus.value" @edit="edit" @save="saveAndClose('save')" @confirm="send('confirm')" @retry="saveAndClose('retry')" @reload="editor.reload" @reapply="resolve('reapply')" @discard="resolve('discard')" @directory="openDirectory" @directory-retry="directory.retry" @search="(q,dep)=>directory.search(q,dep)" @people="(q,dep)=>directory.search(q,dep,true)" @categories="more=>loadOptions(more)">
  <template v-for="entry in [{field:'NAME',slot:'name-suggestion'},{field:'SUMMARY',slot:'summary-suggestion'}]" :key="entry.field" #[entry.slot]><div v-for="suggestion in suggestions.items.value.filter(s=>s.field===entry.field)" :key="suggestion.id" class="ac-inline-suggestion" :aria-label="entry.field==='NAME'?'AI 名称建议':'AI 摘要建议'">
   <span :title="`生成时间：${new Date(suggestion.createdAt).toLocaleString('zh-CN')} · 来源转写版本 ${suggestion.transcriptConfirmationId} · ${suggestion.value}`">AI建议：{{suggestion.value}}</span><button type="button" class="btn sm ghost ac-suggestion-icon" :aria-label="entry.field==='NAME'?'采纳名称':'采纳摘要'" title="采纳建议" :disabled="!suggestions.canDecide.value" @click="decideSuggestion(suggestion.id,'ACCEPT')"><Icon name="check" :size="14"/></button><button type="button" class="btn sm ghost ac-suggestion-icon" :aria-label="entry.field==='NAME'?'拒绝名称建议':'拒绝摘要建议'" title="拒绝建议" :disabled="!suggestions.canDecide.value" @click="decideSuggestion(suggestion.id,'REJECT')"><Icon name="x" :size="14"/></button>
  </div></template>
  <template #suggestions><section v-if="suggestions.loading.value||suggestionError||suggestions.blocked.value||suggestionPending||['CONFLICT','REJECTED'].includes(suggestions.command.state.value)||editor.draft.value?.dirty.value&&suggestions.items.value.length" class="info-suggestions" aria-label="AI 信息建议状态">
   <header><strong>AI 信息建议</strong><button type="button" class="btn sm ghost" :disabled="suggestions.loading.value||suggestions.busy.value" @click="suggestions.reload">重新读取建议</button></header>
   <p v-if="suggestions.loading.value" role="status">正在核对建议来源…</p><p v-if="suggestionError" role="alert">{{suggestionError}}</p>
   <p v-if="suggestions.blocked.value" role="alert">原建议操作记录暂无法读取，请保持页面打开并重试。</p>
   <p v-if="suggestionPending" role="status">建议操作结果尚未确认，请恢复原请求。</p><button v-if="suggestionPending||suggestions.command.state.value==='ACKNOWLEDGED'&&suggestionError" type="button" class="btn sm" :disabled="suggestions.busy.value||suggestions.blocked.value" @click="suggestions.retry">重试原建议请求</button>
   <button v-if="['CONFLICT','REJECTED'].includes(suggestions.command.state.value)" type="button" class="btn sm" @click="suggestions.reset">重新核对当前建议</button>
   <p v-if="editor.draft.value?.dirty.value&&suggestions.items.value.length" class="small muted">请先保存当前草稿，再采纳或拒绝建议。人工输入会保留。</p>
  </section></template>
 </MeetingInfoForm>
 <section v-else-if="message" role="alert" class="card feedback"><p>{{message}}</p><button class="btn" @click="editor.reload">重新加载编辑信息</button></section>
</template>
<style scoped>.info-suggestions{padding:12px;margin:0 0 16px;border:1px solid var(--border);border-radius:8px}.info-suggestions header{display:flex;justify-content:space-between;align-items:center;gap:8px}.info-suggestions article{margin-top:12px;padding-top:12px;border-top:1px solid var(--border)}.proposal-value{white-space:pre-wrap;overflow-wrap:anywhere;max-height:180px;overflow:auto}.suggestion-actions{display:flex;gap:8px;flex-wrap:wrap}.feedback{padding:24px;margin-bottom:20px}</style>

<style scoped>.ac-inline-suggestion{display:flex;align-items:center;gap:6px;min-width:0;margin-top:4px;font-size:12px;color:var(--muted);height:28px}.ac-inline-suggestion>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0;flex:1}.ac-inline-suggestion>.btn{flex-shrink:0}.ac-inline-suggestion>.ac-suggestion-icon{width:26px;min-width:26px;padding:4px;display:inline-flex;align-items:center;justify-content:center}</style>
