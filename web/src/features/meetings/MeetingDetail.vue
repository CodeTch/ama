<script setup lang="ts">
import SendMinutesDialog from '../mail/SendMinutesDialog.vue';
import {defineAsyncComponent,computed,onMounted,onUnmounted,ref,watch,nextTick} from 'vue';
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {useApplication} from '../../application';
import {transientAccessFailure,usePublicationReadLease} from '../minutes/usePublicationReadLease';
import {useWorkspaceNavigation} from './useWorkspaceNavigation';
import MeetingProgress from './MeetingProgress.vue';
import type {FinalCheckTarget} from '../minutes/final-confirmation-checks';
import {workspaceStages,resolveMeetingStage} from './workspace-stages';
import WorkspaceOverview from './WorkspaceOverview.vue';
import WorkspaceTasks from './WorkspaceTasks.vue';
import TranscriptSpeakerSummary from '../transcript/TranscriptSpeakerSummary.vue';
import MeetingInfoEditor from './MeetingInfoEditor.vue';
import MeetingInfoDrawer from './MeetingInfoDrawer.vue';
import MinutesWorkspace from '../minutes/MinutesWorkspace.vue';
import type {MinutesReadingPosition} from '../minutes/minutes-reading-position';
const TranscriptEditor=defineAsyncComponent(()=>import('../transcript/TranscriptEditor.vue'));
import TranscriptPlayer from '../transcript/TranscriptPlayer.vue';
import UploadImportPanel from '../import/UploadImportPanel.vue';
const LegacyRecordingFiles=defineAsyncComponent(()=>import('../recording/LegacyRecordingFiles.vue'));
const RecordingTranscription=defineAsyncComponent(()=>import('../recording/RecordingProcessing.vue'));
import MaterialOnlyDialog from '../import/MaterialOnlyDialog.vue';
import SavedFilesPanel from '../import/SavedFilesPanel.vue';
import FuturePanels from './FuturePanels.vue';
const MeetingPrototypeStage=defineAsyncComponent(()=>import('./MeetingPrototypeStage.vue'));
import {DEMO_MEETING_ID} from './meeting-prototype-demo';
import {useSavedMeeting} from './useSavedMeeting';
import {ApiError} from '../../shared/api/client';
import Icon from '../../shared/Icon.vue';
import PublishedMaterialsDrawer from '../minutes/PublishedMaterialsDrawer.vue';
import type {MinutesCurrent} from '../../shared/api/minutes-saved';
const recordingTranscriptionRef=ref<InstanceType<typeof RecordingTranscription>>();
const route=useRoute(),router=useRouter(),{claimMinutesEntry,claimNavigation,claimInformation,session,info,workspace,options,minutesSaved,infoScopeReady,transcriptScopeReady,generationScopeReady,minutesScopeReady,setMeetingScope,governanceCommands,deletionCommands}=useApplication();
const meeting=computed(()=>typeof route.params.id==='string'?route.params.id:'');
const isDemoMeeting=computed(()=>meeting.value===DEMO_MEETING_ID);
// The current-content endpoint has its own server READ gate. Overlap it with navigation
// on a direct minutes entry, but display only the result matching the authoritative references.
let entryRead:{id:string;actor:string;request:AbortController;result:Promise<MinutesCurrent|null>}|undefined;
watch(()=>[meeting.value,session.current.value?.actor.enterpriseId,route.params.stage] as const,([id,actor,stage],old,cleanup)=>{
 if(stage!=='minutes'||!actor||!id||isDemoMeeting.value||old?.[0]===id&&old[1]===actor)return;
 const request=new AbortController();
 const flight={id,actor,request,result:(claimMinutesEntry?.(id,request.signal)??minutesSaved.current(id,request.signal)).catch(()=>null)};entryRead=flight;
 cleanup(()=>{request.abort();if(entryRead===flight)entryRead=undefined;});
},{immediate:true});
async function readEntry(id:string,signal:AbortSignal){
 const flight=entryRead;entryRead=undefined;
 if(flight&&flight.id===id&&flight.actor===session.current.value?.actor.enterpriseId){
  const current=await flight.result;signal.throwIfAborted();
  const nav=navigation.value.value;
  if(current&&nav?.meetingId===id&&nav.accessRevisionToken===current.observation.accessRevisionToken&&
   nav.revisionToken===current.observation.revisionToken&&nav.saved.minutesRevisionId===(current.body?.id??null)&&nav.saved.elementSetId===current.setId)return current;
 }
 return minutesSaved.current(id,signal);
}
const state=useSavedMeeting((id,signal)=>claimInformation?.(id,signal)??info.saved(id,signal),()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const categoryLabels=ref<Record<string,string>>({});
watch(()=>session.current.value?.actor.enterpriseId,(actor,_old,cleanup)=>{categoryLabels.value={};if(!actor||!options?.categories)return;const request=new AbortController();cleanup(()=>request.abort());void options.categories(null,request.signal).then(value=>{if(!request.signal.aborted&&session.current.value?.actor.enterpriseId===actor)categoryLabels.value=Object.fromEntries(value.items.map(c=>[c.id,c.label]));}).catch(()=>{});},{immediate:true});
const mailDialog=ref<InstanceType<typeof SendMinutesDialog>>();
const autoGenerate=ref(false),publishedObserved=ref(false),futurePanels=ref<InstanceType<typeof FuturePanels>>();
const infoEditorRef=ref<InstanceType<typeof MeetingInfoEditor>>(),materialsOnly=ref<InstanceType<typeof MaterialOnlyDialog>>(),processingFiles=ref<InstanceType<typeof SavedFilesPanel>>();
function closeAdvanced(){advanced.value=false;void nextTick(()=>meetingRoot.value?.querySelector<HTMLElement>('.meeting-status-control')?.focus());}
function closeInfoDrawer(){if(infoEditorRef.value?.cancel?.()??true)infoDrawerOpen.value=false;}
const infoDrawerOpen=ref(false),uploadDialog=ref<InstanceType<typeof UploadImportPanel>>(),advanced=ref(false);
const materialsDrawer=ref<InstanceType<typeof PublishedMaterialsDrawer>>();
const meetingRoot=ref<HTMLElement>();
const mediaPlayer=ref<InstanceType<typeof TranscriptPlayer>>(),transcriptRef=ref<InstanceType<typeof TranscriptEditor>>(),minutesRef=ref<InstanceType<typeof MinutesWorkspace>>();
const importTask=computed(()=>navigation.value.value?.processingSource?.kind==='TRANSCRIPT_IMPORT_V1' ? navigation.value.value.processingSource : navigation.value.value?.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1'));
const hasRecording=computed(()=>navigation.value.value?.processingSource?.kind==='RECORDING_ARCHIVE_V1' || navigation.value.value?.privateSections?.tasks?.items.some(t=>t.kind==='RECORDING_ARCHIVE_V1'));
const processingSaveMessage=ref('');
const participantReview=computed(()=>navigation.value.value?.relation==='MEMBER'&&!effectivePublished.value);
const availableStages=computed(()=>workspaceStages(navigation.value.value,effectivePublished.value).available);
const activeStage=computed(()=>{if(isDemoMeeting.value){return route.params.stage==='confirm'?'confirm':route.params.stage==='minutes'?'minutes':'transcript';}return resolveMeetingStage(route.params.stage,availableStages.value);});
async function goStage(stage:string){if(isDemoMeeting.value){if(stage==='transcript'||stage==='minutes'||stage==='confirm')void router.push(`/meeting/${meeting.value}/${stage}`);return;}void router.push(`/meeting/${meeting.value}/${effectivePublished.value&&stage==='publish'?'published':stage}`);}
async function processFinalCheck(target:FinalCheckTarget){if(target.kind==='transcript'){await router.push(`/meeting/${meeting.value}/transcript`);await nextTick();if(activeStage.value==='transcript'&&target.corrections)transcriptRef.value?.openReview();}else if(target.kind==='tasks'){advanced.value=true;await nextTick();meetingRoot.value?.querySelector('.advanced-tools')?.scrollIntoView({block:'center'});}}
function saveVisible(){
 if(activeStage.value==='processing'){
  if(infoEditorRef.value?.pendingRecovery||infoEditorRef.value?.dirty){infoDrawerOpen.value=true;return;}
  processingSaveMessage.value='当前页面没有待保存的修改。';return;
 }
 if(activeStage.value==='transcript')void transcriptRef.value?.save();else if(activeStage.value==='minutes')void minutesRef.value?.save();else if(activeStage.value==='confirm')void minutesRef.value?.saveInformation();else if(activeStage.value==='publish')minutesRef.value?.savePublicationConfiguration();else infoDrawerOpen.value=true;}
function published(){publishedObserved.value=true;infoDrawerOpen.value=false;uploadDialog.value?.close();goStage('published');}
const stageLabel=computed(()=>{
 const workspace=navigation.value.value,serverStage=workspace?.stage,recordingState=recordingTranscriptionRef.value?.state;
 if(effectivePublished.value)return '已发布';
 if(activeStage.value==='transcript'&&workspace)return transcriptRef.value?.reviewStatus||(workspace.saved.transcriptConfirmationId?'已确认':workspace.saved.transcriptRevisionId?'校正中':'待校正');
 if(serverStage==='MINUTES_FAILED')return '纪要生成失败';
 if(workspace?.privateSections?.finalConfirmationId)return '待发布';
 if(workspace?.saved.minutesRevisionId)return '纪要待处理';
 if(workspace?.saved.transcriptConfirmationId)return '待生成纪要';
 if(activeStage.value==='processing'){
  if(recordingState==='FAILED'||serverStage==='TRANSCRIPT_FAILED'||['FAILED','CANCELLED'].includes(importTask.value?.state??''))return '转写失败';
  if(serverStage==='TRANSCRIPT_PENDING'||recordingState&&recordingState!=='NOT_REQUESTED'||importTask.value)return '转写待处理';
 }
 return availableStages.value.transcript?'转写待处理':'会议采集';});
const navigation=useWorkspaceNavigation((id,signal)=>claimNavigation?.(id,signal)??workspace.navigation(id,signal),()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const effectivePublished=computed(()=>publishedObserved.value||state.saved.value?.stage==='PUBLISHED'||navigation.value.value?.stage==='PUBLISHED');
const saved=state.saved,loading=computed(()=>state.loading.value||navigation.loading.value);
// Initialize an editor on its first visit, then retain its draft across stage changes.
const editorScope=computed(()=>`${session.current.value?.actor.enterpriseId??''}/${meeting.value}`);
const transcriptOpened=ref(''),minutesOpened=ref(''),importOpened=ref(''),processingOpened=ref('');
watch([editorScope,activeStage,()=>navigation.value.value?.meetingId],()=>{if(activeStage.value==='processing'&&navigation.value.value)processingOpened.value=editorScope.value;},{immediate:true});
watch(()=>[editorScope.value,activeStage.value],()=>{if(participantReview.value||['transcript','published'].includes(activeStage.value))transcriptOpened.value=editorScope.value;if(['minutes','confirm','publish','published'].includes(activeStage.value))minutesOpened.value=editorScope.value;},{immediate:true});
const transcriptInitialized=computed(()=>transcriptOpened.value===editorScope.value);
const minutesInitialized=computed(()=>minutesOpened.value===editorScope.value);
watch([editorScope,activeStage,infoDrawerOpen],()=>{processingSaveMessage.value='';});
const importInitialized=computed(()=>importOpened.value===editorScope.value);
const navigationMessage=computed(()=>navigation.error.value instanceof ApiError?navigation.error.value.message:'会议导航暂不可用，请重试。');
const governanceBlocked=computed(()=>governanceCommands?.blockedMeeting.value===meeting.value||deletionCommands?.blockedMeeting.value===meeting.value);
watch(governanceBlocked,blocked=>{if(blocked){state.invalidate();navigation.invalidate();infoDrawerOpen.value=false;advanced.value=false;uploadDialog.value?.forceClose();materialsOnly.value?.forceClose();}},{flush:'sync'});
const publicationReadError=ref<string|null>(null);
const retryDisplayAccess=ref(false),readingPosition=ref<MinutesReadingPosition>();
watch([editorScope,()=>route.params.stage],()=>{readingPosition.value=undefined;},{flush:'sync'});
watch(()=>[retryDisplayAccess.value,meeting.value,session.current.value?.actor.enterpriseId] as const,([retry,id,who],_,cleanup)=>{
 if(!retry||!who)return;
 let delay=500,timer:ReturnType<typeof setTimeout>,stopped=false;const controller=new AbortController();
 async function probe(){
  try{
   await workspace.access(id,controller.signal);
   if(!stopped)reload();
  }catch(cause){
   if(stopped)return;
   if(!transientAccessFailure(cause)){retryDisplayAccess.value=false;loseContent(cause);return;}
   delay=Math.min(4000,delay*2);timer=setTimeout(probe,delay);
  }
 }
 timer=setTimeout(probe,delay);
 cleanup(()=>{stopped=true;clearTimeout(timer);controller.abort();});
});
function loseContent(cause:unknown){retryDisplayAccess.value=transientAccessFailure(cause);
 readingPosition.value=retryDisplayAccess.value?(minutesRef.value?.readingPosition?.()??readingPosition.value):undefined;publicationReadError.value=cause instanceof Error?cause.message:'会议内容暂不可访问，请重新读取。';state.invalidate();navigation.invalidate();infoDrawerOpen.value=false;advanced.value=false;}
const publicationLease=usePublicationReadLease(signal=>workspace.access(meeting.value,signal),()=>`${session.current.value?.actor.enterpriseId}/${meeting.value}`,loseContent);
// Unpublished meetings can retain knowledge sources whose authority is later revoked.
const canRenewContent=computed(()=>!!session.current.value&&saved.value?.id===meeting.value&&!isDemoMeeting.value&&!governanceBlocked.value&&!publicationReadError.value);
watch(()=>[session.current.value?.actor.enterpriseId,meeting.value,canRenewContent.value],()=>{publicationLease.dispose();if(canRenewContent.value)publicationLease.start();},{flush:'sync',immediate:true});
function reload(){retryDisplayAccess.value=false;setMeetingScope(meeting.value);if(governanceBlocked.value){state.invalidate();navigation.invalidate();return;}publicationReadError.value=null;void state.refresh();void navigation.refresh();}
const message=computed(()=>state.error.value instanceof ApiError?state.error.value.message:'会议信息暂不可用，请稍后重试。');
const stop=watch(()=>[session.current.value,meeting.value],()=>{publishedObserved.value=false;if(session.current.value&&!isDemoMeeting.value)reload();},{immediate:true});
async function refreshSession(){try{await session.refresh();}catch{/* Session state explains the failure. */}}
watch(()=>[navigation.value.value?.relation,saved.value?.id,loading.value],async ([relation,id,busy])=>{if(isDemoMeeting.value)return;if(relation==='OWNER'&&id&&!busy&&route.query.import==='1'){await nextTick();if(!uploadDialog.value?.open){if(!await showImportFiles())return;await router.replace({query:{...route.query,import:undefined}});}}});
async function showImportFiles(){if(materialsOnly.value&&!materialsOnly.value.close()){materialsOnly.value.show();return false;}importOpened.value=editorScope.value;await nextTick();return uploadDialog.value?.showModal()??false;}
async function beforeMeetingLeave(){if(isDemoMeeting.value)return true;if(infoDrawerOpen.value&&!await(infoEditorRef.value?.beforeLeave()??true))return false;if(infoEditorRef.value?.pendingRecovery){infoDrawerOpen.value=true;return false;}if(materialsOnly.value&&!materialsOnly.value.close()){materialsOnly.value.show();return false;}if(recordingTranscriptionRef.value&&!recordingTranscriptionRef.value.beforeLeave())return false;if(uploadDialog.value?.open&&!uploadDialog.value.close())return false;if(transcriptRef.value&&!await transcriptRef.value.beforeLeave())return false;const allowed=await(minutesRef.value?.beforeLeave()??true);if(allowed)for(const dialog of meetingRoot.value?.querySelectorAll<HTMLDialogElement>('dialog[open]')??[])dialog.close();return allowed;}
onBeforeRouteLeave(beforeMeetingLeave);
onBeforeRouteUpdate((to,from)=>to.params.id!==from.params.id||to.params.stage!==from.params.stage?beforeMeetingLeave():true);
onMounted(()=>{if(!isDemoMeeting.value&&!session.current.value)void refreshSession();});
onUnmounted(()=>{publicationLease.dispose();stop();state.dispose();navigation.dispose();});
</script>
<template><MeetingPrototypeStage v-if="isDemoMeeting" :stage="activeStage==='confirm'?'confirm':activeStage==='minutes'?'minutes':'transcript'" :meeting-id="meeting" @navigate="goStage"/><div v-else ref="meetingRoot" class="layout"><div class="main">

 <main class="content detail-page" :class="{'minutes-stage':activeStage==='minutes'}"><div :class="activeStage==='published'?'m19-published-page':'aci-detail ac-related'">
  <div class="page-head aci-detail-head"><div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表" title="返回列表"><Icon name="arrowL" :size="18"/></RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title" :class="{'heading-pending':loading&&!saved}">{{saved?.name||navigation.value.value?.temporaryTitle||'未命名会议'}}</h1><span v-if="activeStage==='published'" class="tag green m19-published-status">{{stageLabel}}</span><span v-else-if="navigation.value.value&&saved" :class="['tag meeting-status-control',navigation.value.value?.privateSections?.finalConfirmationId?'amber':'blue']" role="button" tabindex="0" aria-label="查看会议状态与处理记录" title="查看会议状态与处理记录" @click="advanced=!advanced" @keydown.enter.prevent="advanced=!advanced" @keydown.space.prevent="advanced=!advanced">{{stageLabel}}</span></div><div v-if="activeStage==='published'" class="m19-published-meta"><span><b>会议分类：</b>{{categoryLabels[saved?.category??'']??saved?.category??'其他'}}</span><span><b>参会人：</b>{{saved?.confirmedRoster.map(p=>p.displayName).join('、')||'--'}}</span><span><b>会议日期：</b>{{saved?.occurredOn??'--'}}</span></div><div v-else class="page-desc">{{meeting}} · {{navigation.value.value?.privateSections?.finalConfirmationId?'当前版本已最终确认':saved?.name?.trim()&&saved?.category?'会议信息已保存':'待补充会议信息'}}</div></div></div><button v-if="activeStage==='published'&&navigation.value.value?.relation==='OWNER'" class="btn" @click="mailDialog?.open(meeting)">发送纪要邮件</button><div v-if="activeStage!=='published'" class="flex ac-workspace-actions"><span class="small muted" :class="{'transcript-save-state':activeStage==='transcript'}" role="status">{{activeStage==='processing'?processingSaveMessage:activeStage==='minutes'?minutesRef?.saveMessage:activeStage==='confirm'?minutesRef?.informationSaveMessage:activeStage==='publish'?minutesRef?.publicationSaveMessage:activeStage==='transcript'?transcriptRef?.saveStatus:''}}</span><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&!['confirm','publish'].includes(activeStage)" class="btn" @click="infoDrawerOpen=true">编辑会议信息</button><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" class="btn primary" :disabled="activeStage==='minutes'&&!minutesRef?.canSave||activeStage==='transcript'&&!transcriptRef?.canSave||activeStage==='confirm'&&!minutesRef?.canSaveInformation||activeStage==='publish'&&!minutesRef?.canSavePublicationConfiguration" @click="saveVisible">保存</button></div></div>
  <section v-if="!session.current.value" class="card empty"><h2>{{session.status.value==='LOADING'?'正在检查登录':'企业登录暂不可用'}}</h2><button class="btn" @click="refreshSession">重新检查登录</button></section>
  <section v-else-if="governanceBlocked" class="card card-p" role="status"><h2>会议内容当前不可访问</h2><p class="mt12">已提交删除、撤回或废止请求。可通过对应状态窗口查回原请求及处理进度。</p><RouterLink class="btn mt16" to="/meetings">返回会议列表</RouterLink></section>
  <section v-else-if="publicationReadError" class="card card-p" role="alert"><p>{{publicationReadError}}</p><button class="btn mt12" @click="reload">重新读取权限与内容</button></section>
  <div v-else-if="loading&&(!saved||!navigation.value.value)&&route.params.stage==='transcript'" class="transcript-initial" role="status" aria-label="正在读取转写工作台">
   <div class="card transcript-stage-placeholder" aria-hidden="true"></div><div class="wb3"><div class="card transcript-audio-placeholder" aria-hidden="true"></div><div class="card card-p">正在读取转写…</div></div>
  </div>
  <section v-else-if="loading&&(!saved||!navigation.value.value)&&route.params.stage==='minutes'" class="minutes-initial" role="status"><div class="card transcript-stage-placeholder" aria-hidden="true"></div><div class="card card-p">正在读取已保存纪要…</div></section>
  <p v-else-if="loading&&(!saved||!navigation.value.value)" role="status" class="card card-p">正在加载会议信息…</p>
  <section v-else-if="state.error.value&&!saved" role="alert" class="card card-p"><p>{{message}}</p><button class="btn" @click="reload">重试</button></section>
  <template v-else-if="saved">
   <nav v-if="participantReview" class="flex mb12" aria-label="参会人回看"><button class="btn" :aria-pressed="activeStage==='transcript'" @click="goStage('transcript')">会议转写</button><button class="btn" :aria-pressed="activeStage==='minutes'" @click="goStage('minutes')">AI纪要</button></nav>
   <MeetingProgress v-if="!participantReview&&navigation.value.value&&activeStage!=='published'" :value="navigation.value.value" :published-observed="effectivePublished" :active="activeStage" :processing-complete="recordingTranscriptionRef?.complete" @select="goStage"/>
   <p v-if="route.params.stage==='confirm'&&!availableStages.confirm&&!loading" class="banner error" role="status">请先保存纪要并处理待生成任务与候选，再进入会议确认。会议信息可以在确认页补充。</p>
   <p v-if="navigation.error.value" role="alert">{{navigationMessage}} <button class="btn" @click="navigation.refresh">重新读取导航</button></p>
   <section v-if="navigation.value.value?.relation==='OWNER'&&(!infoScopeReady||!transcriptScopeReady||!generationScopeReady||!minutesScopeReady)" class="banner error" role="alert">本地恢复记录暂无法读取或清理，已暂停编辑。<button class="btn" @click="setMeetingScope(meeting)">重新检查恢复记录</button></section>
   <template v-if="navigation.value.value?.relation==='OWNER'">
    <MeetingInfoDrawer v-if="!effectivePublished" :open="infoDrawerOpen" :prevent-close="infoEditorRef?.closeBlocked??false" @close="closeInfoDrawer"><MeetingInfoEditor ref="infoEditorRef" v-if="activeStage!=='confirm'&&infoScopeReady&&navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" :key="'drawer-'+meeting" :meeting-id="meeting" :eligible="infoDrawerOpen" @saved="state.refresh" @close="infoDrawerOpen=false"/></MeetingInfoDrawer>
    <UploadImportPanel v-if="importInitialized&&navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" ref="uploadDialog" :key="'upload-'+meeting" :meeting-id="meeting" @saved="router.push('/meetings')" @discarded="router.push('/meetings')" @processed="()=>{reload();goStage('processing');}" @background="kind=>futurePanels?.open(kind)"/>
   </template>
   <section v-show="activeStage==='processing'" class="aci-processing">
    <RecordingTranscription v-if="activeStage==='processing'&&navigation.value.value?.relation==='OWNER'&&!effectivePublished&&hasRecording"
     ref="recordingTranscriptionRef" :key="'recording-transcription-'+meeting" :meeting-id="meeting"
     :can-mutate="navigation.value.value.navigationState==='CHECK_PRODUCER'" :materials-editing="materialsOnly?.open"
     :confirmed="!!navigation.value.value.saved.transcriptConfirmationId" :existing-transcript="!!navigation.value.value.saved.transcriptRevisionId"
     :auto-start="route.query.transcribe==='1'" @materials="kind=>materialsOnly?.show(kind)"
     @accepted="()=>{reload();if(route.query.transcribe)router.replace({query:{...route.query,transcribe:undefined}});}"/>
    <SavedFilesPanel v-if="processingOpened===editorScope&&navigation.value.value?.relation==='OWNER'&&!effectivePublished&&!hasRecording" ref="processingFiles"
     :meeting-id="meeting" :active="activeStage==='processing'" :task-present="!!importTask" :observed-task="navigation.value.value?.processingMaterialStage??importTask?.state" :material-stage="navigation.value.value?.processingMaterialStage" :materials-changed="navigation.value.value?.processingMaterialsChanged"
     :observed-task-id="importTask?.taskId" :observed-input-id="importTask?.inputId" :observed-revision="importTask?.businessRevisionToken"
     :confirmed="!!navigation.value.value.saved.transcriptConfirmationId" :auto-advance="!route.params.stage&&!availableStages.transcript"
     @changed="navigation.refresh" @processed="navigation.refresh().then(()=>goStage('transcript'))"
     @edit-files="showImportFiles" @materials="kind=>materialsOnly?.show(kind)"/>
    <LegacyRecordingFiles v-if="activeStage==='processing'&&navigation.value.value?.relation==='OWNER'" :meeting-id="meeting"/>
   </section>
   <div :class="participantReview?'participant-review':'workspace-results'"><div v-show="participantReview||activeStage==='transcript'||activeStage==='published'" :class="activeStage==='published'?'published-transcript card card-p m19-published-left':'aci-transcript aci-inline-workbench'"><div class="wb3"><div class="col-sticky"><div v-if="activeStage==='published'" class="between mb12 m19-audio-heading"><b>原录音</b><button class="btn sm ghost" @click="materialsDrawer?.show()"><Icon name="file" :size="13"/> 背景材料</button></div><TranscriptPlayer v-if="transcriptInitialized" :source-state="transcriptRef?.sourceState??'loading'" :published="activeStage==='published'" ref="mediaPlayer" @position="transcriptRef?.position($event)" :expect-recording="!!hasRecording" :scope="transcriptRef?.media??null" :active="participantReview||activeStage==='transcript'||activeStage==='published'"/><div v-if="activeStage==='published'" class="divider"></div><TranscriptSpeakerSummary v-if="activeStage!=='published'&&navigation.value.value?.relation==='OWNER'&&transcriptRef?.sourceState==='ready'" :items="transcriptRef?.speakerLabels??[]" :loading="transcriptRef?.speakersLoading??true" :disabled="navigation.value.value?.relation!=='OWNER'||effectivePublished||!transcriptRef?.canEditSpeakers||!!transcriptRef?.dirty" @edit="id=>transcriptRef?.openSpeakers('edit',id)" @merge="transcriptRef?.openSpeakers('merge')" @split="transcriptRef?.openSpeakers('split')" @undo="transcriptRef?.undoLatest()"/></div><div v-show="!participantReview||activeStage==='transcript'" :class="activeStage==='published'?'transcript-main':'transcript-main aci-inline-transcript-column'"><div v-if="activeStage==='published'" class="between mb12"><b>会议转写</b><span class="tag green">发布绑定版本</span></div><TranscriptEditor :workspace-revision="navigation.value.value?.revisionToken" :active="participantReview||activeStage==='transcript'||activeStage==='published'" :result-key="navigation.value.value?.privateSections?.tasks?.items.filter(t=>t.kind==='TRANSCRIPT_IMPORT_V1'&&t.state==='SUCCEEDED').map(t=>t.taskId).sort().join(',')??''" :published="activeStage==='published'" ref="transcriptRef" @seek="(fileId,startMs,play)=>mediaPlayer?.seek(fileId,startMs,play)" v-if="navigation.value.value&&transcriptInitialized" :key="[meeting,navigation.value.value.relation,navigation.value.value.accessRevisionToken].join('/')" :meeting-id="meeting" :show-footer="activeStage==='transcript'&&!effectivePublished&&navigation.value.value?.relation==='OWNER'" @changed="navigation.refresh" @confirmed="autoGenerate=true;goStage('minutes')"/></div></div></div>
   <section v-show="['minutes','confirm','publish','published'].includes(activeStage)" class="minutes-stage-content"><div v-if="participantReview&&!navigation.value.value?.saved.minutesRevisionId" class="card empty" role="status">会议纪要尚未生成。</div><MinutesWorkspace v-else ref="minutesRef" :resume-position="readingPosition" @position-consumed="readingPosition=undefined" :category-labels="categoryLabels" @process="processFinalCheck" v-if="navigation.value.value&&minutesInitialized" :read-entry="readEntry" :navigation="navigation.value.value" :key="[meeting,navigation.value.value.relation,navigation.value.value.accessRevisionToken].join('/')" :meeting-id="meeting" :owner="navigation.value.value.relation==='OWNER'" :meeting-info="saved" :stage="activeStage" :auto-generate="autoGenerate" :can-enter-confirmation="availableStages.confirm" :pending-candidates="navigation.value.value?.privateSections?.candidates?.items??[]" @generation-started="autoGenerate=false" @edit-info="infoDrawerOpen=true" @changed="navigation.refresh" @published="published" @info-saved="state.refresh();navigation.refresh()" @navigate="goStage"/></section></div>
   <MaterialOnlyDialog v-if="(processingOpened===editorScope||!navigation.value.value?.saved.transcriptRevisionId)&&navigation.value.value?.relation==='OWNER'&&!effectivePublished" ref="materialsOnly" :key="'materials-only-'+meeting" :meeting-id="meeting" :initial-recovery="!navigation.value.value?.saved.transcriptRevisionId" :processing="['TRANSCRIBING','ENHANCING','ADOPTING','COPYING','WAITING_ARCHIVE','RUNNING','READY'].includes(recordingTranscriptionRef?.state??navigation.value.value?.processingMaterialStage??importTask?.state??'')" :after-start="!!importTask||!!recordingTranscriptionRef?.state&&recordingTranscriptionRef.state!=='NOT_REQUESTED'" @closed="recordingTranscriptionRef?.reloadMaterials();processingFiles?.reloadMaterials();state.refresh();navigation.refresh()"/>
   <PublishedMaterialsDrawer v-if="activeStage==='published'" ref="materialsDrawer" :meeting-id="meeting" @denied="loseContent"/><section v-if="advanced&&activeStage!=='published'" class="card card-p advanced-tools" aria-label="会议状态与处理记录" @keydown.esc="closeAdvanced"><div class="between"><h2>会议状态与处理记录</h2><button class="btn sm" @click="closeAdvanced">关闭</button><button class="btn" @click="reload">刷新</button></div><div v-if="activeStage==='minutes'" class="flex mt16"><button class="btn" @click="minutesRef?.reload()">刷新纪要</button><button v-if="navigation.value.value?.relation==='OWNER'" class="btn" @click="minutesRef?.openVersions()">候选与版本</button></div><div v-if="activeStage==='transcript'&&navigation.value.value?.relation==='OWNER'&&!effectivePublished" class="flex mt16"><button class="btn" :disabled="!transcriptRef?.canReview" @click="transcriptRef?.openReview()">转写版本与语音审阅</button></div><dl><dt>会议编号</dt><dd>{{saved.id}}</dd><dt>会议日期</dt><dd>{{saved.occurredOn??'未填写'}}</dd><dt>会议分类</dt><dd>{{saved.category??'未填写'}}</dd><dt>会议摘要</dt><dd>{{saved.summary??'未填写'}}</dd><dt>已确认名单</dt><dd>{{saved.confirmedRoster.map(p=>p.displayName).join('、')||'未确认'}}</dd></dl><WorkspaceOverview v-if="navigation.value.value" :value="navigation.value.value"/><WorkspaceTasks v-if="navigation.value.value?.relation==='OWNER'" :meeting-id="meeting"/></section>
  </template>
 </div></main></div></div><FuturePanels ref="futurePanels"/><SendMinutesDialog v-if="activeStage==='published'" ref="mailDialog"/>
</template>

<style scoped>.meeting-status-control{cursor:pointer}.meeting-status-control:focus-visible{outline:1px solid var(--navy);outline-offset:3px}</style>

<style scoped>
.heading-pending{visibility:hidden}
.transcript-save-state{min-inline-size:3em;text-align:right}
.transcript-stage-placeholder{height:70px;margin-bottom:24px}
.transcript-audio-placeholder{min-height:248px}
.transcript-initial .wb3>.card:last-child{min-height:226px}
</style>

<style scoped>.workspace-results{display:contents}.participant-review{display:grid;grid-template-columns:minmax(240px,30%) minmax(0,1fr);gap:16px;align-items:start}.participant-review .aci-transcript,.participant-review .wb3{display:contents}.participant-review .minutes-stage-content{grid-column:2}.participant-review nav button[aria-pressed=true]{font-weight:700}@media(max-width:800px){.participant-review{display:block}.participant-review .col-sticky{position:static;margin-bottom:12px}}</style>

<style scoped>
.minutes-stage .advanced-tools{position:fixed;z-index:20;inset:64px 12px 80px auto;width:min(620px,calc(100vw - 24px));overflow:auto;overscroll-behavior:contain;box-shadow:var(--shadow);scrollbar-gutter:stable}
.minutes-stage .advanced-tools>.between{position:sticky;top:-16px;background:white;padding-block:8px;gap:8px}.advanced-tools>.between h2{flex:1}
</style>
