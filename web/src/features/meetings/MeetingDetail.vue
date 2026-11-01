<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch,nextTick} from 'vue';
import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {useApplication} from '../../application';
import {usePublicationReadLease} from '../minutes/usePublicationReadLease';
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
import MeetingPrototypeStage from './MeetingPrototypeStage.vue';
import {DEMO_MEETING_ID} from './meeting-prototype-demo';
import {useSavedMeeting} from './useSavedMeeting';
import {ApiError} from '../../shared/api/client';
import Icon from '../../shared/Icon.vue';
import WorkspaceTopbar from '../../shared/WorkspaceTopbar.vue';
import PublishedMaterialsDrawer from '../minutes/PublishedMaterialsDrawer.vue';
const localDevelopment=import.meta.env.DEV&&import.meta.env.VITE_LOCAL_DEV_LOGIN==='1';
const route=useRoute(),router=useRouter(),{session,info,workspace,options,infoScopeReady,transcriptScopeReady,generationScopeReady,minutesScopeReady,setMeetingScope,governanceCommands,deletionCommands}=useApplication();
const meeting=computed(()=>typeof route.params.id==='string'?route.params.id:'');
const isDemoMeeting=computed(()=>meeting.value===DEMO_MEETING_ID);
const state=useSavedMeeting(info.saved,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const categoryLabels=ref<Record<string,string>>({});
watch(()=>session.current.value?.actor.enterpriseId,(actor,_old,cleanup)=>{categoryLabels.value={};if(!actor||!options?.categories)return;const request=new AbortController();cleanup(()=>request.abort());void options.categories(null,request.signal).then(value=>{if(!request.signal.aborted&&session.current.value?.actor.enterpriseId===actor)categoryLabels.value=Object.fromEntries(value.items.map(c=>[c.id,c.label]));}).catch(()=>{});},{immediate:true});
const autoGenerate=ref(false),publishedObserved=ref(false),futurePanels=ref<InstanceType<typeof FuturePanels>>();
const infoEditorRef=ref<InstanceType<typeof MeetingInfoEditor>>();
function closeInfoDrawer(){if(infoEditorRef.value?.cancel?.()??true)infoDrawerOpen.value=false;}
const infoDrawerOpen=ref(false),uploadDialog=ref<InstanceType<typeof UploadImportPanel>>(),advanced=ref(false);
const materialsDrawer=ref<InstanceType<typeof PublishedMaterialsDrawer>>();
const mediaPlayer=ref<InstanceType<typeof TranscriptPlayer>>(),transcriptRef=ref<InstanceType<typeof TranscriptEditor>>(),minutesRef=ref<InstanceType<typeof MinutesWorkspace>>();
const availableStages=computed(()=>{const n=navigation.value.value,s=n?.saved,raw=n?.privateSections?.tasks?.items.some(t=>t.kind==='TRANSCRIPT_IMPORT_V1'&&t.state==='SUCCEEDED');return{processing:true,transcript:!!s?.transcriptRevisionId||!!raw,minutes:!!s?.transcriptConfirmationId,confirm:!!s?.minutesRevisionId&&!!s?.elementSetId,publish:!!n?.privateSections?.finalConfirmationId,published:effectivePublished.value};});
const activeStage=computed(()=>{if(isDemoMeeting.value){return route.params.stage==='confirm'?'confirm':route.params.stage==='minutes'?'minutes':'transcript';}if(effectivePublished.value)return 'published';const requested=route.params.stage;return typeof requested==='string'&&availableStages.value[requested as keyof typeof availableStages.value]?requested:effectivePublished.value?'published':availableStages.value.transcript?'transcript':'processing';});
async function goStage(stage:string){if(isDemoMeeting.value){if(stage==='transcript'||stage==='minutes'||stage==='confirm')void router.push(`/meeting/${meeting.value}/${stage}`);return;}if(activeStage.value==='minutes'&&stage!==activeStage.value&&!(await minutesRef.value?.beforeLeave()))return;void router.push(`/meeting/${meeting.value}/${effectivePublished.value&&stage==='publish'?'published':stage}`);}
function saveVisible(){if(activeStage.value==='transcript')void transcriptRef.value?.save();else if(activeStage.value==='minutes')void minutesRef.value?.save();else infoDrawerOpen.value=true;}
function published(){publishedObserved.value=true;infoDrawerOpen.value=false;uploadDialog.value?.close();goStage('published');}
const stageLabel=computed(()=>effectivePublished.value?'已发布':navigation.value.value?.privateSections?.finalConfirmationId?'待发布':navigation.value.value?.saved.minutesRevisionId?'纪要待处理':navigation.value.value?.saved.transcriptConfirmationId?'待生成纪要':availableStages.value.transcript?'转写待处理':'会议采集');
const navigation=useWorkspaceNavigation(workspace.navigation,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const effectivePublished=computed(()=>publishedObserved.value||state.saved.value?.stage==='PUBLISHED'||navigation.value.value?.stage==='PUBLISHED');
const saved=state.saved,loading=computed(()=>state.loading.value||navigation.loading.value);
const navigationMessage=computed(()=>navigation.error.value instanceof ApiError?navigation.error.value.message:'会议导航暂不可用，请重试。');
const governanceBlocked=computed(()=>governanceCommands?.blockedMeeting.value===meeting.value||deletionCommands?.blockedMeeting.value===meeting.value);
watch(governanceBlocked,blocked=>{if(blocked){state.invalidate();navigation.invalidate();infoDrawerOpen.value=false;advanced.value=false;uploadDialog.value?.forceClose();}},{flush:'sync'});
const publicationReadError=ref<string|null>(null);
function loseContent(cause:unknown){publicationReadError.value=cause instanceof Error?cause.message:'会议内容暂不可访问，请重新读取。';state.invalidate();navigation.invalidate();infoDrawerOpen.value=false;advanced.value=false;}
const publicationLease=usePublicationReadLease(signal=>workspace.navigation(meeting.value,signal),()=>`${session.current.value?.actor.enterpriseId}/${meeting.value}`,loseContent);
// Unpublished meetings can retain knowledge sources whose authority is later revoked.
const canRenewContent=computed(()=>!!session.current.value&&saved.value?.id===meeting.value&&!isDemoMeeting.value&&!governanceBlocked.value&&!publicationReadError.value);
watch(()=>[session.current.value?.actor.enterpriseId,meeting.value,canRenewContent.value],()=>{publicationLease.dispose();if(canRenewContent.value)publicationLease.start();},{flush:'sync',immediate:true});
function reload(){setMeetingScope(meeting.value);if(governanceBlocked.value){state.invalidate();navigation.invalidate();return;}publicationReadError.value=null;void state.refresh();void navigation.refresh();}
const message=computed(()=>state.error.value instanceof ApiError?state.error.value.message:'会议信息暂不可用，请稍后重试。');
const stop=watch(()=>[session.current.value,meeting.value],()=>{publishedObserved.value=false;if(session.current.value&&!isDemoMeeting.value)reload();},{immediate:true});
async function refreshSession(){try{await session.refresh();}catch{/* Session state explains the failure. */}}
watch(()=>[navigation.value.value?.relation,saved.value?.id,loading.value],async ([relation,id,busy])=>{if(isDemoMeeting.value)return;if(relation==='OWNER'&&id&&!busy&&route.query.import==='1'){await nextTick();if(uploadDialog.value&&!uploadDialog.value.open){uploadDialog.value.showModal();await router.replace({query:{...route.query,import:undefined}});}}});
async function beforeMeetingLeave(){if(isDemoMeeting.value)return true;if(uploadDialog.value?.open&&!uploadDialog.value.close())return false;if(transcriptRef.value&&!await transcriptRef.value.beforeLeave())return false;return minutesRef.value?.beforeLeave()??true;}
onBeforeRouteLeave(beforeMeetingLeave);
onBeforeRouteUpdate((to,from)=>to.params.id!==from.params.id||to.params.stage!==from.params.stage?beforeMeetingLeave():true);
onMounted(()=>{if(!isDemoMeeting.value&&!session.current.value)void refreshSession();});
onUnmounted(()=>{publicationLease.dispose();stop();state.dispose();navigation.dispose();});
</script>
<template><MeetingPrototypeStage v-if="isDemoMeeting" :stage="activeStage==='confirm'?'confirm':activeStage==='minutes'?'minutes':'transcript'" :meeting-id="meeting" @navigate="goStage"/><div v-else class="layout"><div class="main">
 <WorkspaceTopbar :breadcrumb="activeStage==='published'?'会议详情':'会议工作台'" :role-name="navigation.value.value?.relation==='MEMBER'?'会议参与人':'会议负责人'"/>
 <main class="content detail-page" :class="{'minutes-stage':activeStage==='minutes'}"><div :class="activeStage==='published'?'m19-published-page':'aci-detail ac-related'">
  <div class="page-head aci-detail-head"><div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表" title="返回列表"><Icon name="arrowL" :size="18"/></RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title">{{saved?.name??'未命名会议'}}</h1><span class="tag" :class="effectivePublished?'green m19-published-status':'blue'">{{stageLabel}}</span></div><div v-if="activeStage==='published'" class="m19-published-meta"><span><b>会议分类：</b>{{categoryLabels[saved?.category??'']??saved?.category??'其他'}}</span><span><b>参会人：</b>{{saved?.confirmedRoster.map(p=>p.displayName).join('、')||'--'}}</span><span><b>会议日期：</b>{{saved?.occurredOn??'--'}}</span></div><div v-else class="page-desc">{{meeting}} · {{saved?.confirmedRosterId?'会议信息已确认':'待补充会议信息'}}</div></div></div><div v-if="activeStage!=='published'" class="flex ac-workspace-actions"><span class="small muted">{{activeStage==='minutes'?minutesRef?.saveMessage:transcriptRef?.dirty?'有未保存修改':''}}</span><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&activeStage!=='confirm'" class="btn" @click="infoDrawerOpen=true">编辑会议信息</button><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&!['confirm','publish'].includes(activeStage)" class="btn primary" @click="saveVisible">保存</button><button class="btn sm ghost" aria-label="更多操作" @click="advanced=!advanced">···</button></div></div>
  <section v-if="!session.current.value" class="card empty"><h2>{{session.status.value==='LOADING'?'正在检查登录':'企业登录暂不可用'}}</h2><button class="btn" @click="refreshSession">重新检查登录</button></section>
  <section v-else-if="governanceBlocked" class="card card-p" role="status"><h2>会议内容当前不可访问</h2><p class="mt12">已提交删除、撤回或废止请求。可通过对应状态窗口查回原请求及处理进度。</p><RouterLink class="btn mt16" to="/meetings">返回会议列表</RouterLink></section>
  <section v-else-if="publicationReadError" class="card card-p" role="alert"><p>{{publicationReadError}}</p><button class="btn mt12" @click="reload">重新读取权限与内容</button></section>
  <p v-else-if="loading&&!saved" role="status" class="card card-p">正在加载会议信息…</p>
  <section v-else-if="state.error.value&&!saved" role="alert" class="card card-p"><p>{{message}}</p><button class="btn" @click="reload">重试</button></section>
  <template v-else-if="saved">
   <MeetingProgress v-if="navigation.value.value&&activeStage!=='published'" :value="navigation.value.value" :published-observed="effectivePublished" :active="activeStage" @select="goStage"/>
   <p v-if="navigation.error.value" role="alert">{{navigationMessage}} <button class="btn" @click="navigation.reload">重新读取导航</button></p>
   <section v-if="navigation.value.value?.relation==='OWNER'&&(!infoScopeReady||!transcriptScopeReady||!generationScopeReady||!minutesScopeReady)" class="banner error" role="alert">本地恢复记录暂无法读取或清理，已暂停编辑。<button class="btn" @click="setMeetingScope(meeting)">重新检查恢复记录</button></section>
   <template v-if="navigation.value.value?.relation==='OWNER'">
    <MeetingInfoDrawer v-if="!effectivePublished" :open="infoDrawerOpen" :prevent-close="infoEditorRef?.closeBlocked??false" @close="closeInfoDrawer"><MeetingInfoEditor ref="infoEditorRef" v-if="activeStage!=='confirm'&&infoScopeReady&&navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" :key="'drawer-'+meeting" :meeting-id="meeting" :eligible="true" @saved="state.refresh" @close="infoDrawerOpen=false"/></MeetingInfoDrawer>
    <UploadImportPanel v-if="navigation.value.value.navigationState==='CHECK_PRODUCER'&&!effectivePublished" ref="uploadDialog" :key="'upload-'+meeting" :meeting-id="meeting" @saved="router.push('/meetings')" @discarded="router.push('/meetings')" @processed="()=>{reload();goStage('processing');}" @background="kind=>futurePanels?.open(kind)"/>
   </template>
   <section v-show="activeStage==='processing'" class="aci-processing"><SavedFilesPanel v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" :meeting-id="meeting" :active="activeStage==='processing'" :task-present="navigation.value.value.privateSections?.tasks?.items.some(t=>t.kind==='TRANSCRIPT_IMPORT_V1')??false" :observed-task="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.state??null" :observed-task-id="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.taskId??null" :observed-input-id="navigation.value.value.privateSections?.tasks?.items.find(t=>t.kind==='TRANSCRIPT_IMPORT_V1')?.inputId??null" :confirmed="!!navigation.value.value.saved.transcriptConfirmationId" :auto-advance="!availableStages.transcript" @changed="navigation.refresh" @processed="navigation.refresh().then(()=>goStage('transcript'))" @edit-files="uploadDialog?.showModal()" @materials="()=>uploadDialog?.showModal()"/></section>
   <div v-show="activeStage==='transcript'||activeStage==='published'" :class="activeStage==='published'?'published-transcript card card-p m19-published-left':'aci-transcript aci-inline-workbench'"><div class="wb3"><div class="col-sticky"><div v-if="activeStage==='published'" class="between mb12 m19-audio-heading"><b>原录音</b><button class="btn sm ghost" @click="materialsDrawer?.show()"><Icon name="file" :size="13"/> 背景材料</button></div><TranscriptPlayer :published="activeStage==='published'" ref="mediaPlayer" :scope="transcriptRef?.media??null" :active="activeStage==='transcript'||activeStage==='published'"/><div v-if="activeStage==='published'" class="divider"></div><div v-if="activeStage!=='published'" class="card card-p mt16"><div class="between"><b>说话人</b><button v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" class="btn sm ghost" @click="futurePanels?.open('speaker')">编辑</button></div><p class="small muted mt12">尚未关联说话人信息。</p></div></div><div :class="activeStage==='published'?'transcript-main':'transcript-main aci-inline-transcript-column'"><div v-if="activeStage==='published'" class="between mb12"><b>会议转写</b><span class="tag green">发布绑定版本</span></div><TranscriptEditor :published="activeStage==='published'" ref="transcriptRef" @seek="(fileId,startMs)=>mediaPlayer?.seek(fileId,startMs)" v-if="navigation.value.value" :key="'transcript-'+meeting" :meeting-id="meeting" :show-footer="activeStage==='transcript'&&!effectivePublished&&navigation.value.value?.relation==='OWNER'" @changed="navigation.refresh" @confirmed="autoGenerate=true;goStage('minutes')"/></div></div></div>
   <section v-show="['minutes','confirm','publish','published'].includes(activeStage)" class="minutes-stage-content"><MinutesWorkspace ref="minutesRef" v-if="navigation.value.value" :key="'minutes-'+meeting" :meeting-id="meeting" :owner="navigation.value.value.relation==='OWNER'" :meeting-info="saved" :stage="activeStage" :auto-generate="autoGenerate" :pending-candidates="navigation.value.value?.privateSections?.candidates?.items??[]" @generation-started="autoGenerate=false" @edit-info="infoDrawerOpen=true" @changed="navigation.refresh" @published="published" @info-saved="state.refresh();navigation.refresh()" @navigate="goStage"/></section>
   <PublishedMaterialsDrawer v-if="activeStage==='published'" ref="materialsDrawer" :meeting-id="meeting" @denied="loseContent"/><section v-if="advanced&&activeStage!=='published'" class="card card-p advanced-tools"><div class="between"><h2>会议状态与处理记录</h2><button class="btn" @click="reload">刷新</button></div><div v-if="activeStage==='minutes'" class="flex mt16"><button class="btn" @click="minutesRef?.reload()">刷新纪要</button><button v-if="navigation.value.value?.relation==='OWNER'" class="btn" @click="minutesRef?.openVersions()">候选与版本</button></div><dl><dt>会议编号</dt><dd>{{saved.id}}</dd><dt>会议日期</dt><dd>{{saved.occurredOn??'未填写'}}</dd><dt>会议分类</dt><dd>{{saved.category??'未填写'}}</dd><dt>会议摘要</dt><dd>{{saved.summary??'未填写'}}</dd><dt>已确认名单</dt><dd>{{saved.confirmedRoster.map(p=>p.displayName).join('、')||'未确认'}}</dd></dl><WorkspaceOverview v-if="navigation.value.value" :value="navigation.value.value"/><WorkspaceTasks v-if="navigation.value.value?.relation==='OWNER'" :meeting-id="meeting"/></section>
  </template>
  <div v-if="localDevelopment&&activeStage==='published'" class="demo-note">当前页面数据均为产品演示用模拟数据，不代表招商基金真实业务信息。<br>当前原型为纯前端交互演示，不连接招商基金真实业务系统。当前原型仅展示会议负责人的相关功能。</div>
 </div></main></div></div><FuturePanels ref="futurePanels"/></template>
