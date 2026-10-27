<script setup lang="ts">
import {computed,onMounted,onUnmounted,watch} from 'vue';
import {useRoute} from 'vue-router';
import {useApplication} from '../../application';
import {useWorkspaceNavigation} from './useWorkspaceNavigation';
import WorkspaceOverview from './WorkspaceOverview.vue';
import WorkspaceTasks from './WorkspaceTasks.vue';
import MeetingInfoEditor from './MeetingInfoEditor.vue';
import MinutesWorkspace from '../minutes/MinutesWorkspace.vue';
import MinutesGenerationPanel from '../minutes/MinutesGenerationPanel.vue';
import TranscriptEditor from '../transcript/TranscriptEditor.vue';
import UploadImportPanel from '../import/UploadImportPanel.vue';
import {useSavedMeeting} from './useSavedMeeting';
import {ApiError} from '../../shared/api/client';
import Icon from '../../shared/Icon.vue';
const route=useRoute(),{session,info,workspace,infoScopeReady,transcriptScopeReady,generationScopeReady,minutesScopeReady,setMeetingScope}=useApplication();
const meeting=computed(()=>typeof route.params.id==='string'?route.params.id:'');
const state=useSavedMeeting(info.saved,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const navigation=useWorkspaceNavigation(workspace.navigation,()=>session.current.value?.actor.enterpriseId??null,()=>meeting.value);
const saved=state.saved,loading=computed(()=>state.loading.value||navigation.loading.value);
const navigationMessage=computed(()=>navigation.error.value instanceof ApiError?navigation.error.value.message:'会议导航暂不可用，请重试。');
function reload(){setMeetingScope(meeting.value);void state.reload();void navigation.reload();}
const message=computed(()=>state.error.value instanceof ApiError?state.error.value.message:'会议信息暂不可用，请稍后重试。');
const stop=watch(()=>[session.current.value,meeting.value],()=>{if(session.current.value)reload();},{immediate:true});
async function refreshSession(){try{await session.refresh();}catch{/* Session state explains the failure. */}}
onMounted(()=>{if(!session.current.value)void refreshSession();});
onUnmounted(()=>{stop();state.dispose();navigation.dispose();});
</script>
<template>
 <div class="layout"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span aria-hidden="true">/</span><span>会议信息</span></nav><div class="topbar-right"><span>{{session.current.value?.actor.enterpriseId??'企业登录暂不可用'}}</span></div></header>
  <main class="content detail-page">
   <div class="page-head"><div><h1 class="page-title">{{saved?.name??'会议信息'}}</h1><p class="page-desc">查看已保存的信息和已确认的参会人员。</p></div><button v-if="session.current.value" class="btn" :disabled="loading" @click="reload">刷新</button></div>
   <section v-if="!session.current.value" class="card empty"><h2>{{session.status.value==='LOADING'?'正在检查登录':'企业登录暂不可用'}}</h2><p>恢复登录后重新读取会议信息。</p><button class="btn" :disabled="session.status.value==='LOADING'" @click="refreshSession">重新检查登录</button></section>
   <p v-else-if="loading" role="status" class="card detail-feedback">正在加载会议信息…</p>
   <section v-else-if="state.error.value" role="alert" class="card detail-feedback"><p>{{message}}</p><button class="btn" @click="reload">重试</button></section>
   <template v-else-if="saved">
    <section class="card detail-section" aria-labelledby="meeting-info-title"><h2 id="meeting-info-title">会议信息</h2><dl><dt>会议编号</dt><dd class="meeting-number">{{saved.id}}</dd><dt>会议日期</dt><dd>{{saved.occurredOn??'未填写'}}</dd><dt>会议类别</dt><dd>{{saved.category??'未填写'}}</dd><dt>会议简介</dt><dd class="saved-text">{{saved.summary??'未填写'}}</dd></dl></section>
    <section class="card detail-section" aria-labelledby="confirmed-roster-title"><h2 id="confirmed-roster-title">已确认参会人员</h2><p v-if="saved.confirmedRosterId===null" class="muted">尚未确认参会人员。</p><p v-else-if="saved.confirmedRoster.length===0" class="muted">已确认本次会议没有参会人员。</p><ul v-else class="roster"><li v-for="member in saved.confirmedRoster" :key="member.memberId"><span>{{member.displayName}}</span><small>{{member.memberKind==='EXTERNAL'?'外部参会人员':member.enterpriseId}}</small></li></ul></section>
    <p v-if="navigation.loading.value" role="status" class="card detail-feedback">正在读取会议导航…</p>
    <section v-else-if="navigation.error.value" role="alert" class="card detail-feedback"><p>{{navigationMessage}}</p><button class="btn" @click="navigation.reload">重新读取导航</button></section>
    <WorkspaceOverview v-if="navigation.value.value" :value="navigation.value.value"/>
    <template v-if="navigation.value.value?.relation==='OWNER'">
     <section v-if="!infoScopeReady||!transcriptScopeReady||!generationScopeReady||!minutesScopeReady" class="card detail-feedback" role="alert"><p>本地恢复记录暂无法读取或清理，已暂停编辑。请保持页面打开并重试。</p><button class="btn" @click="setMeetingScope(meeting)">重新检查恢复记录</button></section>
     <MeetingInfoEditor v-if="infoScopeReady&&navigation.value.value.navigationState==='CHECK_PRODUCER'&&saved.stage!=='PUBLISHED'" :key="meeting" :meeting-id="meeting" :eligible="true" @saved="state.reload"/>
     <UploadImportPanel v-if="navigation.value.value.navigationState==='CHECK_PRODUCER'&&saved.stage!=='PUBLISHED'" :key="'upload-'+meeting" :meeting-id="meeting"/>

    </template>
    <p v-else-if="navigation.value.value?.relation==='MEMBER'" class="muted">当前以参会人员身份查看已保存内容。</p>
    <TranscriptEditor v-if="navigation.value.value" :key="'transcript-'+meeting" :meeting-id="meeting"/>
    <MinutesWorkspace v-if="navigation.value.value" :key="'minutes-'+meeting" :meeting-id="meeting" :owner="navigation.value.value.relation==='OWNER'"/>
    <template v-if="navigation.value.value?.relation==='OWNER'">
     <MinutesGenerationPanel v-if="navigation.value.value.navigationState==='CHECK_PRODUCER'&&saved.stage!=='PUBLISHED'" :key="'generation-'+meeting" :meeting-id="meeting"/>
     <WorkspaceTasks :meeting-id="meeting"/>
    </template>
   </template>
  </main>
 </div></div>
</template>
<style scoped>
.detail-page{max-width:1100px;margin:0 auto}.detail-section{padding:24px;margin-bottom:20px}.detail-section h2{font-size:18px;margin:0 0 20px}.detail-section dl{display:grid;grid-template-columns:110px minmax(0,1fr);gap:18px 24px;margin:0}.detail-section dt,.muted{color:var(--muted)}.detail-section dd{margin:0;overflow-wrap:anywhere}.saved-text{white-space:pre-wrap;line-height:1.7}.meeting-number{font-family:monospace}.roster{list-style:none;margin:0;padding:0;display:flex;flex-wrap:wrap;gap:16px}.roster li{display:flex;flex-direction:column;gap:6px;min-width:140px;overflow-wrap:anywhere}.roster small{color:var(--muted)}.detail-feedback{padding:24px}.detail-feedback .btn{margin-top:16px}.page-title{overflow-wrap:anywhere}.topbar-heading{gap:12px}@media(max-width:600px){.detail-section{padding:18px}.detail-section dl{grid-template-columns:1fr;gap:8px}.detail-section dd{margin-bottom:14px}.page-head{gap:12px}.topbar-right{max-width:35%;overflow-wrap:anywhere}}button:disabled{opacity:.55;cursor:wait}
</style>
