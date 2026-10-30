<script setup lang="ts">
import {computed,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useMeetingInfoEditor} from './useMeetingInfoEditor';
import MeetingInfoForm from './MeetingInfoForm.vue';
import {ApiError} from '../../shared/api/client';
import {finalInformationError} from '../minutes/final-information';
import type {Person,Category} from '../../shared/api/meeting-options';
import type {InfoFields} from '../../shared/api/meeting-info';
const props=defineProps<{meetingId:string;eligible:boolean;presentation?:'drawer'|'final'}>(),emit=defineEmits<{saved:[];close:[]}>();
const {session,info,journals,options,infoScopeReady,meetingScope}=useApplication(),actor=()=>session.current.value?.actor.enterpriseId??null;
const eligible=()=>props.eligible&&infoScopeReady.value&&meetingScope.value===props.meetingId;
const editor=useMeetingInfoEditor(info,actor,()=>props.meetingId,eligible,journals);
const categories=shallowRef<readonly Category[]>([]),people=shallowRef<readonly Person[]>([]),categoryCursor=ref<string|null>(null),peopleCursor=ref<string|null>(null),optionsBusy=ref(false),optionsError=ref<string|null>(null),localError=ref<string|null>(null);
let optionEpoch=0,optionController:AbortController|null=null,peopleQuery='';
function resetOptions(){optionEpoch++;optionController?.abort();optionController=null;categories.value=[];people.value=[];categoryCursor.value=null;peopleCursor.value=null;optionsBusy.value=false;optionsError.value=null;localError.value=null;}
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{resetOptions();if(actor()&&eligible()){void editor.reload();void loadOptions('categories');}},{immediate:true,flush:'sync'});
const logoutStop=session.onLogout(editor.logout);
async function loadOptions(kind:'people'|'categories',query='',more=false){
 const who=actor(),id=props.meetingId;if(!who||!eligible())return;
 const generation=++optionEpoch;optionController?.abort();const controller=new AbortController();optionController=controller;optionsBusy.value=true;optionsError.value=null;
 const active=()=>generation===optionEpoch&&actor()===who&&props.meetingId===id&&eligible();
 try{if(kind==='people'){const next=more&&query===peopleQuery?peopleCursor.value:null;const page=await options.people(query,next,controller.signal);if(active()){people.value=page.items;peopleCursor.value=page.nextCursor;peopleQuery=query;}}
 else{const page=await options.categories(more?categoryCursor.value:null,controller.signal);if(active()){categories.value=page.items;categoryCursor.value=page.nextCursor;}}}
 catch(cause){if(active())optionsError.value=cause instanceof ApiError?cause.message:'人员或类别暂不可用，请重试。';}
 finally{if(active()){optionsBusy.value=false;optionController=null;}}
}
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
const closeBlocked=computed(()=>editor.busy.value||editor.recoveryBlocked.value||['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(editor.command.state.value));
function cancel(){if(closeBlocked.value){localError.value='原操作结果尚未确认，请先恢复原请求。';return false;}try{if(editor.draft.value?.dirty.value||editor.draft.value?.conflict.value||['CONFLICT','REJECTED'].includes(editor.command.state.value))editor.discard();if(['INVALID','UNAVAILABLE'].includes(editor.storageStatus.value)){localError.value='恢复记录暂无法清理，请保持页面打开并重试。';return false;}localError.value=null;return true;}catch(cause){localError.value=cause instanceof Error?cause.message:'暂无法放弃修改。';return false;}}
function close(){if(cancel())emit('close');}
async function saveAndClose(kind:'save'|'retry'){await send(kind);if((props.presentation??'drawer')==='drawer'&&editor.command.state.value==='ACKNOWLEDGED'&&!editor.error.value&&!editor.draft.value?.dirty.value)emit('close');}
const canPrepare=computed(()=>!!editor.saved.value&&!!editor.draft.value&&!editor.busy.value&&!editor.recoveryBlocked.value);
defineExpose({prepareFinal,cancel,closeBlocked,canPrepare});
onUnmounted(()=>{stop();logoutStop();resetOptions();editor.dispose();});
</script>
<template>
 <p v-if="editor.loading.value&&!editor.draft.value" role="status" class="card feedback">正在加载可编辑信息…</p>
 <MeetingInfoForm :presentation="props.presentation??'drawer'" @close="close" v-if="editor.draft.value" :value="editor.draft.value.value.value" :dirty="editor.draft.value.dirty.value" :busy="editor.busy.value" :blocked="editor.recoveryBlocked.value" :state="editor.command.state.value" :operation="editor.command.snapshot.value?.operation??null" :conflict="editor.draft.value.conflict.value!==null" :conflict-value="editor.draft.value.conflict.value?.value??null" :conflict-token="editor.draft.value.conflict.value?.token??null" :can-confirm="editor.saved.value?.rosterDraftId!=null" :categories="categories" :people="people" :roster-names="rosterNames" :options-busy="optionsBusy" :options-error="optionsError" :category-more="categoryCursor!==null" :people-more="peopleCursor!==null" :error="message" :storage-status="editor.storageStatus.value" @edit="edit" @save="saveAndClose('save')" @confirm="send('confirm')" @retry="saveAndClose('retry')" @reload="editor.reload" @reapply="resolve('reapply')" @discard="resolve('discard')" @search="q=>loadOptions('people',q)" @people="q=>loadOptions('people',q,true)" @categories="more=>loadOptions('categories','',more)"/>
 <section v-else-if="message" role="alert" class="card feedback"><p>{{message}}</p><button class="btn" @click="editor.reload">重新加载编辑信息</button></section>
</template>
<style scoped>.feedback{padding:24px;margin-bottom:20px}</style>
