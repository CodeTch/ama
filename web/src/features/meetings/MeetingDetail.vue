<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch,nextTick} from 'vue';
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {useApplication} from '../../application';
import {useWorkspaceNavigation} from './useWorkspaceNavigation';
import MeetingProgress from './MeetingProgress.vue';
import WorkspaceOverview from './WorkspaceOverview.vue';
import WorkspaceTasks from './WorkspaceTasks.vue';
import MeetingInfoEditor from './MeetingInfoEditor.vue';
import MeetingInfoDrawer from './MeetingInfoDrawer.vue';
import MinutesWorkspace from '../minutes/MinutesWorkspace.vue';
import MinutesGenerationPanel from '../minutes/MinutesGenerationPanel.vue';
import TranscriptEditor from '../transcript/TranscriptEditor.vue';
import TranscriptPlayer from '../transcript/TranscriptPlayer.vue';
import UploadImportPanel from '../import/UploadImportPanel.vue';
import SavedFilesPanel from '../import/SavedFilesPanel.vue';
import FuturePanels from './FuturePanels.vue';
import {useSavedMeeting} from './useSavedMeeting';
import {ApiError} from '../../shared/api/client';
import Icon from '../../shared/Icon.vue';
const route=useRoute(),router=useRouter(),{session,info,workspace,infoScopeReady,transcriptScopeReady,generationScopeReady,minutesScopeReady,setMeetingScope}=useApplication();
const meeting=computed(()=>typeof route.params.id==='string'?route.params.id:'');
const state=useSavedMeeting(info.saved,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const autoGenerate=ref(false),publishedObserved=ref(false),futurePanels=ref<InstanceType<typeof FuturePanels>>();
const infoEditorRef=ref<InstanceType<typeof MeetingInfoEditor>>();
function closeInfoDrawer(){if(infoEditorRef.value?.cancel?.()??true)infoDrawerOpen.value=false;}
const infoDrawerOpen=ref(false),uploadDialog=ref<InstanceType<typeof UploadImportPanel>>(),advanced=ref(false);
const mediaPlayer=ref<InstanceType<typeof TranscriptPlayer>>(),transcriptRef=ref<InstanceType<typeof TranscriptEditor>>(),minutesRef=ref<InstanceType<typeof MinutesWorkspace>>();
const availableStages=computed(()=>{const n=navigation.value.value,s=n?.saved,raw=n?.privateSections?.tasks?.items.some(t=>t.kind==='TRANSCRIPT_IMPORT_V1'&&t.state==='SUCCEEDED');return{processing:true,transcript:!!s?.transcriptRevisionId||!!raw,minutes:!!s?.transcriptConfirmationId,confirm:!!s?.minutesRevisionId&&!!s?.elementSetId,publish:!!n?.privateSections?.finalConfirmationId,published:effectivePublished.value};});
const activeStage=computed(()=>{if(effectivePublished.value)return 'published';const requested=route.params.stage;return typeof requested==='string'&&availableStages.value[requested as keyof typeof availableStages.value]?requested:effectivePublished.value?'published':availableStages.value.transcript?'transcript':'processing';});
async function goStage(stage:string){if(activeStage.value==='minutes'&&stage!==activeStage.value&&!(await minutesRef.value?.beforeLeave()))return;void router.push(`/meeting/${meeting.value}/${effectivePublished.value&&stage==='publish'?'published':stage}`);}
function saveVisible(){if(activeStage.value==='transcript')void transcriptRef.value?.save();else if(activeStage.value==='minutes')void minutesRef.value?.save();else infoDrawerOpen.value=true;}
function published(){publishedObserved.value=true;infoDrawerOpen.value=false;uploadDialog.value?.close();goStage('published');}
const stageLabel=computed(()=>effectivePublished.value?'已发布':navigation.value.value?.privateSections?.finalConfirmationId?'待发布':navigation.value.value?.saved.minutesRevisionId?'纪要待处理':navigation.value.value?.saved.transcriptConfirmationId?'待生成纪要':availableStages.value.transcript?'转写待处理':'会议采集');
const navigation=useWorkspaceNavigation(workspace.navigation,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const effectivePublished=computed(()=>publishedObserved.value||state.saved.value?.stage==='PUBLISHED'||navigation.value.value?.stage==='PUBLISHED');
const saved=state.saved,loading=computed(()=>state.loading.value||navigation.loading.value);
const navigationMessage=computed(()=>navigation.error.value instanceof ApiError?navigation.error.value.message:'会议导航暂不可用，请重试。');
function reload(){setMeetingScope(meeting.value);void state.refresh();void navigation.refresh();}
const message=computed(()=>state.error.value instanceof ApiError?state.error.value.message:'会议信息暂不可用，请稍后重试。');
const stop=watch(()=>[session.current.value,meeting.value],()=>{publishedObserved.value=false;if(session.current.value)reload();},{immediate:true});
async function refreshSession(){try{await session.refresh();}catch{/* Session state explains the failure. */}}
watch(()=>[navigation.value.value?.relation,saved.value?.id,loading.value],async ([relation,id,busy])=>{if(relation==='OWNER'&&id&&!busy&&route.query.import==='1'){await nextTick();if(uploadDialog.value&&!uploadDialog.value.open){uploadDialog.value.showModal();await router.replace({query:{...route.query,import:undefined}});}}});
function beforeMeetingLeave(){if(uploadDialog.value?.open&&!uploadDialog.value.close())return false;return minutesRef.value?.beforeLeave()??true;}
onBeforeRouteLeave(beforeMeetingLeave);
onBeforeRouteUpdate((to,from)=>to.params.id!==from.params.id?beforeMeetingLeave():true);
onMounted(()=>{if(!session.current.value)void refreshSession();});
onUnmounted(()=>{stop();state.dispose();navigation.dispose();});
</script>
<template><div class="layout"><div class="main">
 <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span class="topbar-crumb">/ {{activeStage==='published'?'已发布会议':'会议工作台'}}</span></nav><div class="topbar-right"><span class="small muted">{{session.current.value?.actor.enterpriseId??'企业登录暂不可用'}}</span></div></header>
 <main class="content detail-page" :class="{'minutes-stage':activeStage==='minutes'}"><div class="aci-detail ac-related" :class="{'m19-published-page':activeStage==='published'}">
  <div class="page-head aci-detail-head"><div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表">‹</RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title">{{saved?.name??'未命名会议'}}</h1><span class="tag" :class="effectivePublished?'green':'blue'">{{stageLabel}}</span></div><div class="page-desc">{{meeting}} · {{saved?.confirmedRosterId?'会议信息已确认':'待补充会议信息'}}</div></div></div><div class="flex ac-workspace-actions"><span class="small muted">{{activeStage==='minutes'?minutesRef?.saveMessage:transcriptRef?.dirty?'有未保存修改':''}}</span><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&activeStage!=='confirm'" class="btn" @click="infoDrawerOpen=true">编辑会议信息</button><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&!['confirm','publish'].includes(activeStage)" class="btn primary" @click="saveVisible">保存</button><button class="btn sm ghost" aria-label="更多操作" @click="advanced=!advanced">···</button></div></div>
  <section v-if="!session.current.value" class="card empty"><h2>{{session.status.value==='LOADING'?'正在检查登录':'企业登录暂不可用'}}</h2><button class="btn" @click="refreshSession">重新检查登录</button></section>
  <p v-else-if="loading&&!saved" role="status" class="card card-p">正在加载会议信息…</p>
  <section v-else-if="state.error.value&&!saved" role="alert" class="card card-p"><p>{{message}}</p><button class="btn" @click="reload">重试</button></section>
  <template v-else-if="saved">
   <MeetingProgress v-if="navigation.value.value" :value="navigation.value.value" :published-observed="effectivePublished" :active="activeStage" @select="goStage"/>
   <p v-if="navigation.error.value" role="alert">{{navigationMessage}} <button class="btn" @click="navigation.reload">重新读取导航</button></p>
   <section v-if="navigation.value.value?.relation==='OWNER'&&(!infoScopeReady||!transcriptScopeReady||!generationScopeReady||!minutesScopeReady)" class="banner error" role="alert">本地恢复记录暂无法读取或清理，已暂停编辑。<button class="btn" @click="setMeetingScope(meeting)">重新检查恢复记录</button></section>
   <template v-if="navigation.value.value?.relation==='OWNER'">
    <MeetingInfoDrawer :open="infoDrawerOpen" :prevent-close="infoEditorRef?.closeBlocked??false" @close="closeInfoDrawer"><MeetingInfoEditor ref="infoEditorRef" v-if="activeStage!=='confirm'&&infoScopeReady&&navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" :key="'drawer-'+meeting" :meeting-id="meeting" :eligible="true" @saved="state.refresh" @close="infoDrawerOpen=false"/></MeetingInfoDrawer>
    <UploadImportPanel v-if="navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" ref="uploadDialog" :key="'upload-'+meeting" :meeting-id="meeting" @saved="router.push('/meetings')" @discarded="router.push('/meetings')" @processed="()=>{reload();goStage('processing');}" @background="kind=>futurePanels?.open(kind)"/>
   </template>
   <section v-show="activeStage==='processing'" class="aci-processing"><SavedFilesPanel v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" :meeting-id="meeting" :active="activeStage==='processing'" :task-present="navigation.value.value.privateSections?.tasks?.items.some(t=>t.kind==='TRANSCRIPT_IMPORT_V1')??false" :observed-task="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.state??null" :observed-task-id="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.taskId??null" :observed-input-id="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.inputId??null" :confirmed="!!navigation.value.value.saved.transcriptConfirmationId" :auto-advance="!availableStages.transcript" @changed="navigation.refresh" @processed="navigation.refresh().then(()=>goStage('transcript'))" @edit-files="uploadDialog?.showModal()" @materials="kind=>futurePanels?.open(kind)"/></section>
   <div v-show="activeStage==='transcript'||activeStage==='published'" class="aci-transcript aci-inline-workbench" :class="{'published-transcript':activeStage==='published'}"><div class="wb3"><div class="col-sticky"><TranscriptPlayer ref="mediaPlayer" :scope="transcriptRef?.media??null" :active="activeStage==='transcript'||activeStage==='published'"/><div class="card card-p mt16"><div class="between"><b>说话人</b><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" class="btn sm ghost" @click="futurePanels?.open('speaker')">编辑</button></div><p class="small muted mt12">尚未关联说话人信息。</p></div></div><div class="transcript-main aci-inline-transcript-column"><TranscriptEditor ref="transcriptRef" @seek="(fileId,startMs)=>mediaPlayer?.seek(fileId,startMs)" v-if="navigation.value.value" :key="'transcript-'+meeting" :meeting-id="meeting" :show-footer="activeStage==='transcript'&&!effectivePublished&&navigation.value.value?.relation==='OWNER'" @changed="navigation.refresh" @confirmed="autoGenerate=true;goStage('minutes')"/></div></div></div>
   <section v-show="['minutes','confirm','publish','published'].includes(activeStage)" class="minutes-stage-content"><MinutesWorkspace ref="minutesRef" v-if="navigation.value.value" :key="'minutes-'+meeting" :meeting-id="meeting" :owner="navigation.value.value.relation==='OWNER'" :meeting-info="saved" :stage="activeStage" :auto-generate="autoGenerate" :pending-candidates="navigation.value.value?.privateSections?.candidates?.items??[]" @generation-started="autoGenerate=false" @edit-info="infoDrawerOpen=true" @changed="navigation.refresh" @published="published" @info-saved="state.refresh();navigation.refresh()" @navigate="goStage"/></section>
   <section v-if="advanced" class="card card-p advanced-tools"><div class="between"><h2>会议状态与处理记录</h2><button class="btn" @click="reload">刷新</button></div><div v-if="activeStage==='minutes'" class="flex mt16"><button class="btn" @click="minutesRef?.reload()">刷新纪要</button><button v-if="navigation.value.value?.relation==='OWNER'" class="btn" @click="minutesRef?.openVersions()">候选与版本</button></div><dl><dt>会议编号</dt><dd>{{saved.id}}</dd><dt>会议日期</dt><dd>{{saved.occurredOn??'未填写'}}</dd><dt>会议分类</dt><dd>{{saved.category??'未填写'}}</dd><dt>会议摘要</dt><dd>{{saved.summary??'未填写'}}</dd><dt>已确认名单</dt><dd>{{saved.confirmedRoster.map(p=>p.displayName).join('、')||'未确认'}}</dd></dl><WorkspaceOverview v-if="navigation.value.value" :value="navigation.value.value"/><WorkspaceTasks v-if="navigation.value.value?.relation==='OWNER'" :meeting-id="meeting"/></section>
  </template>
 </div></main></div></div><FuturePanels ref="futurePanels"/></template>
