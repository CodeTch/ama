<script setup lang="ts">
import { computed,nextTick,onMounted,onUnmounted,ref,watch } from 'vue';
import {useApplication} from './application';
import {useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';
import {useMeetingCreateFlow} from './features/meetings/useMeetingCreateFlow';
import MeetingCreatePanel from './features/meetings/MeetingCreatePanel.vue';
import FuturePanels from './features/meetings/FuturePanels.vue';
import TopicWorkspace from './features/meetings/TopicWorkspace.vue';
import type {components} from './shared/api/generated';
import MeetingList from './features/meetings/MeetingList.vue';
import UploadImportPanel from './features/import/UploadImportPanel.vue';
import {ApiError} from './shared/api/client';
import Icon from './shared/Icon.vue';
import MeetingDateRange from './features/meetings/MeetingDateRange.vue';
import {fitSelectedTags,positionSelectedPopover} from './features/meetings/selected-filter-tags';
const {session,list,create,createDiscovery,options:useApplicationOptions,setMeetingScope,topics,topicCommands}=useApplication(),router=useRouter(),createOpen=ref(false),importAfterCreate=ref(false);
const importMeeting=ref<string|null>(null),importOpen=ref(false),importPanel=ref<InstanceType<typeof UploadImportPanel>>();
const localDevelopment=import.meta.env.DEV&&import.meta.env.VITE_LOCAL_DEV_LOGIN==='1';
const createFlow=useMeetingCreateFlow(create,()=>session.current.value?.actor.enterpriseId??null,async id=>{if(importAfterCreate.value){setMeetingScope(id);importMeeting.value=id;const failure=await router.replace({path:'/meetings',query:{import:id,fresh:'1'}});if(failure)return false;await nextTick();return importPanel.value?.showModal()??false;}const path=`/meeting/${id}`;const failure=await router.push(path);return !failure&&router.currentRoute.value.path===path;});
function goCasLogin(){window.location.assign('/login');}
function startRecording(){if(!session.current.value){goCasLogin();return;}void router.push('/recording?start=1');}
function openImport(){if(!session.current.value){goCasLogin();return;}if(importMeeting.value){importPanel.value?.showModal();return;}importAfterCreate.value=true;void createFlow.open();}
function savedImport(){importOpen.value=false;importMeeting.value=null;void router.replace({path:'/meetings'});void list.reload();}
function closedImport(){importOpen.value=false;if(importMeeting.value&&router.currentRoute.value.query.import===importMeeting.value){importMeeting.value=null;void router.replace({path:'/meetings'});}}
function processedImport(){const id=importMeeting.value;importOpen.value=false;importMeeting.value=null;if(id)void router.push(`/meeting/${id}/processing`);}
async function meetingDeleted(){await list.reload();if(!page.value?.items.length&&list.canPrevious.value)await list.first();}
watch(()=>session.current.value?.actor.enterpriseId,()=>{importPanel.value?.forceClose();importMeeting.value=null;importOpen.value=false;},{flush:'sync'});
// Anonymous users can still see the shell and its honest login boundary. API
// reads remain 401 and protected actions navigate to CAS from their handlers;
// remounting the whole page here would make the empty/error state unreachable
// and would hide the deferred voice boundary behind a redirect.
onBeforeRouteLeave(()=>topicWorkspace.value?.beforeLeave()!==false&&(!importOpen.value||(importPanel.value?.close()??true)));
onBeforeRouteUpdate((to,from)=>from.query.import&&to.query.import!==from.query.import&&importOpen.value?(importPanel.value?.close()??true):true);
watch(()=>[router.currentRoute.value.query.import,session.current.value?.actor.enterpriseId],async ([id,who])=>{if(typeof id==='string'&&/^[0-9a-f-]{36}$/.test(id)&&who&&importMeeting.value!==id){setMeetingScope(id);importMeeting.value=id;await nextTick();importPanel.value?.showModal();}},{immediate:true});
const createMessage=computed(()=>createFlow.error.value?(createFlow.error.value instanceof ApiError?createFlow.error.value.message:'操作暂未完成，请保留原请求并重试。'):null);
onUnmounted(createFlow.dispose);
const scope=computed({get:()=>list.scope.value,set:value=>{groupView.value=false;void list.selectScope(value);}});
const page=list.page,loading=list.loading;
const keyword=ref(''),stageFilters=ref<string[]>([]),categoryFilters=ref<string[]>([]),fromDate=ref(''),toDate=ref(''),groupView=ref(false),topicFilter=ref('');
const selectedMeetingIds=ref<string[]>([]);watch(()=>[session.current.value?.actor.enterpriseId,scope.value,page.value],()=>{selectedMeetingIds.value=[];});
const topicWorkspace=ref<InstanceType<typeof TopicWorkspace>>(),topicMemberships=ref<Record<string,string|null>>({}),topicMembershipLoading=ref(false),topicMembershipError=ref<string|null>(null);let topicTurn=0,topicRequest:AbortController|null=null;
async function readTopicMemberships(){const turn=++topicTurn,who=session.current.value?.actor.enterpriseId,ids=page.value?.items.map(item=>item.id)??[];topicRequest?.abort();topicMemberships.value={};topicMembershipError.value=null;topicMembershipLoading.value=false;if(!who||scope.value!=='owned'||!ids.length)return;const request=new AbortController();topicRequest=request;topicMembershipLoading.value=true;try{const rows=await topics.memberships(ids,request.signal);if(turn===topicTurn&&who===session.current.value?.actor.enterpriseId)topicMemberships.value=Object.fromEntries(rows.map(row=>[row.meetingId,row.topicId]));}catch(cause){if(turn===topicTurn)topicMembershipError.value=cause instanceof Error?cause.message:'会议专题暂无法读取。';}finally{if(turn===topicTurn)topicMembershipLoading.value=false;}}
watch(()=>[session.current.value?.actor.enterpriseId,scope.value,page.value?.items.map(item=>item.id).join(',')],()=>{void readTopicMemberships();},{immediate:true});
const topicLabels=computed(()=>Object.fromEntries((page.value?.items??[]).map(item=>{const id=topicMemberships.value[item.id];return [item.id,id?topicCommands.topics.value.find(topic=>topic.id===id)?.name??'专题暂不可用':topicMembershipLoading.value?'正在读取专题':topicMembershipError.value?'专题读取失败':'未归入专题'];})));
const unassignedItems=computed(()=>(page.value?.items??[]).filter(item=>topicMemberships.value[item.id]===null));
const topicGroups=computed(()=>topicCommands.topics.value.filter(topic=>!topicFilter.value||topicFilter.value===topic.id).map(topic=>({topic,items:(page.value?.items??[]).filter(item=>topicMemberships.value[item.id]===topic.id)})));
function assignTopics(items:components['schemas']['MeetingItem'][]){void topicWorkspace.value?.openAssignment(items.map(item=>({...item,topicId:topicMemberships.value[item.id]??null})));}
function topicsChanged(){void list.reload();void readTopicMemberships();}
function selectTopic(){if(topicFilter.value==='__create'){topicFilter.value='';if(!session.current.value){goCasLogin();return;}void topicWorkspace.value?.openCreate();return;}applyFilters();}
onUnmounted(()=>{topicTurn++;topicRequest?.abort();});
const activeFilterMenu=ref<string|null>(null);
function redrawHome(){
 // Keep the page shell mounted while a filter read settles so native controls
 // retain their focus and the browser does not paint an empty replacement tree.
 void nextTick(()=>fitSelectedTags(document));
}
watch(groupView,()=>{activeFilterMenu.value=null;redrawHome();},{flush:'sync'});watch(importOpen,open=>{if(open)redrawHome();});watch(page,redrawHome,{flush:'sync'});
function refreshSelectedTags(){void nextTick(()=>fitSelectedTags(document));}
function selectedPopover(event:Event){const target=event.target;if(target instanceof Element){const summary=target.closest('.m19-multi-selected>summary');if(summary instanceof HTMLElement)positionSelectedPopover(summary);}}
watch([stageFilters,categoryFilters],refreshSelectedTags,{deep:true});
onMounted(()=>{window.addEventListener('resize',refreshSelectedTags);document.addEventListener('mouseenter',selectedPopover,true);document.addEventListener('focusin',selectedPopover);});
onUnmounted(()=>{window.removeEventListener('resize',refreshSelectedTags);document.removeEventListener('mouseenter',selectedPopover,true);document.removeEventListener('focusin',selectedPopover);});
const categoryOptions=ref<{id:string;label:string}[]>([]);
const stageOptions:Record<string,string>={ENTRY:'会议采集',TRANSCRIPT_PENDING:'转写待处理',TRANSCRIPT_FAILED:'转写失败',MINUTES_PENDING:'纪要待处理',MINUTES_FAILED:'纪要生成失败',READY_TO_PUBLISH:'待发布',PUBLISH_FAILED:'发布失败',PUBLISHED:'已发布'};
function filterMenuToggle(event:Event,menu:string){const details=event.target as HTMLDetailsElement;if(!details.isConnected)return;if(details.open)activeFilterMenu.value=menu;else if(activeFilterMenu.value===menu)activeFilterMenu.value=null;}
function applyFilters(_event?:Event,menu:string|null=null){activeFilterMenu.value=menu;void list.selectFilters({q:keyword.value,stages:stageFilters.value,categories:categoryFilters.value,from:fromDate.value,to:toDate.value,topic:scope.value==='owned'?topicFilter.value:undefined});}
function closeFilterMenus(event:MouseEvent){if(event.target instanceof Node&&!event.target.isConnected)return;document.querySelectorAll<HTMLDetailsElement>('.m19-dropdown[open]').forEach(menu=>{if(!event.composedPath().includes(menu))menu.open=false;});}
function escapeFilters(event:KeyboardEvent){if(event.key!=='Escape')return;activeFilterMenu.value=null;document.querySelectorAll<HTMLDetailsElement>('.m19-dropdown[open]').forEach(menu=>{menu.open=false;menu.querySelector<HTMLElement>('summary')?.focus();});}
onMounted(()=>{document.addEventListener('click',closeFilterMenus);document.addEventListener('keydown',escapeFilters);});
onUnmounted(()=>{document.removeEventListener('click',closeFilterMenus);document.removeEventListener('keydown',escapeFilters);});
function clearFilters(){topicFilter.value='';keyword.value='';stageFilters.value=[];categoryFilters.value=[];fromDate.value='';toDate.value='';applyFilters();}
const profile=ref<{displayName:string;department:string|null}|null>(null);
watch(()=>session.current.value?.actor.enterpriseId,async actor=>{profile.value=null;if(!actor)return;const signal=new AbortController().signal;await Promise.allSettled([useApplicationOptions.categories(null,signal).then(value=>{if(session.current.value?.actor.enterpriseId===actor)categoryOptions.value=[...value.items];}),useApplicationOptions.people(actor,null,signal).then(value=>{if(session.current.value?.actor.enterpriseId===actor)profile.value=value.items.find(p=>p.stableId===actor)??null;})]);},{immediate:true});
const profileName=computed(()=>profile.value?.displayName||session.current.value?.actor.enterpriseId||(session.status.value==='LOADING'?'正在检查登录':'尚未登录'));
const minimumTotal=computed(()=>(list.pageNumber.value-1)*10+(page.value?.items.length??0));


const listError=computed(()=>list.error.value instanceof ApiError?list.error.value.message:'会议数据暂不可用，请稍后重试。');
async function reloadSession(){try{await session.refresh();await list.reload();}catch{/* Session state supplies the unavailable boundary. */}}
onMounted(()=>{if(session.current.value)void list.reload();else void reloadSession();});
function selectScope(event:KeyboardEvent) {
 if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
 event.preventDefault();
 scope.value=event.key==='Home'?'owned':event.key==='End'?'joined':scope.value==='owned'?'joined':'owned';
 (event.currentTarget as HTMLElement).querySelector<HTMLButtonElement>('#'+scope.value+'-tab')?.focus();
}
const future=ref<InstanceType<typeof FuturePanels>>(),dialog=ref<HTMLDialogElement>();
const notice=ref({title:'',body:''});
function show(title:string,body:string) {notice.value={title,body};dialog.value?.showModal();}
</script>
<template>
 <div class="layout"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home" aria-current="page"><Icon name="list"/> 我的会议</RouterLink><span v-if="importOpen" class="topbar-crumb">/ 会议详情</span></nav><div class="topbar-right">
   <button class="btn sm ghost" @click="router.push('/feedback')"><Icon name="message" :size="15"/> 我的语料反馈</button>
   <button class="btn sm ghost" @click="show('演示引导','可通过“开始录音”采集会议，或通过“导入会议”上传音视频和文档；在下方按会议范围、关键词、状态、类别和日期查看会议。')"><Icon name="guide" :size="15"/> 演示引导</button>
   <button class="btn sm ghost" @click="show('重置演示数据','当前会议保存在本地数据库中。此页面不执行数据删除；如需清空首页筛选，请使用“清空筛选”。')"><Icon name="reset" :size="15"/> 重置演示数据</button>
   <details class="home-profile"><summary class="role-chip" aria-label="当前用户"><div class="avatar o">{{Array.from(profileName)[0]}}</div><div><div class="rc-name">{{profileName}}</div><div class="rc-role">会议负责人 · {{profile?.department??'--'}}</div></div></summary><div class="home-profile-menu"><RouterLink to="/meeting-library" class="btn sm ghost">会议库</RouterLink><a v-if="session.current.value" class="btn sm ghost" href="/auth/cas/logout">退出登录</a><RouterLink v-else class="btn sm ghost" to="/login">企业登录</RouterLink></div></details>
  </div></header>
  <main class="content"><div class="m19-page" :class="{'aci-import-background':importOpen}">
   <div class="page-head"><div><h1 class="page-title">我的会议</h1><div class="page-desc">集中管理你负责和参与的会议，从录音采集、AI增强转写到纪要生成与发布，让会议内容可核对、结论可追溯、成果随时回看。</div></div><div class="page-actions">
    <button class="btn primary" @click="startRecording"><Icon name="mic" :size="14"/> 开始录音</button>
    <button class="btn" @click="openImport"><Icon name="plus" :size="14"/> 导入会议</button>
   </div></div>
   <MeetingCreatePanel v-if="session.current.value&&!importMeeting&&!importAfterCreate&&(createOpen||create.command.state.value!=='EMPTY'||create.recoveryBlocked.value)" :state="create.command.state.value" :busy="createFlow.busy.value" :blocked="create.recoveryBlocked.value" :storage-status="create.storageStatus.value" :cleanup-failed="create.cleanupFailed.value" :message="createMessage" :discovery="createDiscovery.result.value?.status??null" :discovering="createDiscovery.loading.value" :discovery-error="!!createDiscovery.error.value" @submit="createFlow.open" @lookup="createDiscovery.reload" @recover="create.retryRecovery" @session="reloadSession" @close="createOpen=false"/>
   <div class="m19-scope-row"><div class="tabs" role="tablist" aria-label="会议范围" @keydown="selectScope">
    <button id="owned-tab" class="tab" :tabindex="scope==='owned'?0:-1" :class="{active:scope==='owned'}" role="tab" :aria-selected="scope==='owned'" aria-controls="meeting-panel" @click="scope='owned'">我负责的</button>
    <button id="joined-tab" class="tab" :tabindex="scope==='joined'?0:-1" :class="{active:scope==='joined'}" role="tab" :aria-selected="scope==='joined'" aria-controls="meeting-panel" @click="scope='joined'">我参与的</button>
   </div><div v-if="scope==='owned'" class="flex m19-view"><button class="btn sm" :class="{navy:!groupView}" title="列表" aria-label="列表" :aria-pressed="!groupView" @click="groupView=false"><Icon name="list"/></button><button class="btn sm" :class="{navy:groupView}" title="专题分组" aria-label="专题分组" :aria-pressed="groupView" @click="groupView=true"><Icon name="layers"/></button></div></div>
   <div class="filterbar m19-filters" role="search"><div class="search-box"><Icon name="search" :size="14"/><input v-model="keyword" class="input" placeholder="搜索会议名称/编号/人员" aria-label="关键词" maxlength="128" @keydown.enter.prevent="applyFilters" @change="applyFilters"/></div><div class="m19-filter-field"><span class="m19-filter-label">状态:</span><details class="m19-dropdown" :open="activeFilterMenu==='会议状态'" @toggle="filterMenuToggle($event,'会议状态')" :class="{'m19-multi-selected':stageFilters.length}"><summary class="select" aria-label="会议状态"><template v-if="stageFilters.length"><span class="m19-selected-main"><span class="m19-selected-track"><span v-for="key in stageFilters" :key="key" class="m19-filter-tag">{{stageOptions[key]}}</span></span><span class="m19-selected-more" hidden aria-hidden="true">...</span></span><span class="m19-selected-popover" role="tooltip" aria-label="全部已选选项"><span v-for="key in stageFilters" :key="key" class="m19-filter-tag">{{stageOptions[key]}}</span></span></template><template v-else>全部会议</template></summary><div class="m19-options" role="group" aria-label="会议状态"><label><input type="checkbox" :checked="!stageFilters.length" @change="stageFilters=[];applyFilters($event,'会议状态')"/>全部会议</label><label v-for="(name,key) in stageOptions" :key="key"><input v-model="stageFilters" type="checkbox" :value="key" @change="applyFilters($event,'会议状态')"/>{{name}}</label></div></details></div><div class="m19-filter-field"><span class="m19-filter-label">类别:</span><details class="m19-dropdown" :open="activeFilterMenu==='会议分类'" @toggle="filterMenuToggle($event,'会议分类')" :class="{'m19-multi-selected':categoryFilters.length}"><summary class="select" aria-label="会议分类"><template v-if="categoryFilters.length"><span class="m19-selected-main"><span class="m19-selected-track"><span v-for="key in categoryFilters" :key="key" class="m19-filter-tag">{{categoryOptions.find(c=>c.id===key)?.label??key}}</span></span><span class="m19-selected-more" hidden aria-hidden="true">...</span></span><span class="m19-selected-popover" role="tooltip" aria-label="全部已选选项"><span v-for="key in categoryFilters" :key="key" class="m19-filter-tag">{{categoryOptions.find(c=>c.id===key)?.label??key}}</span></span></template><template v-else>全部分类</template></summary><div class="m19-options" role="group" aria-label="会议分类"><label><input type="checkbox" :checked="!categoryFilters.length" @change="categoryFilters=[];applyFilters($event,'会议分类')"/>全部分类</label><label v-for="c in categoryOptions" :key="c.id"><input v-model="categoryFilters" type="checkbox" :value="c.id" @change="applyFilters($event,'会议分类')"/>{{c.label}}</label></div></details></div><div v-if="scope==='owned'" class="m19-filter-field"><label class="m19-filter-label" for="topic-filter">专题:</label><select id="topic-filter" class="select" aria-label="所属专题" v-model="topicFilter" @change="selectTopic"><option value="">全部专题</option><option value="__none">未归入专题</option><option v-for="topic in topicCommands.topics.value" :key="topic.id" :value="topic.id">{{topic.name}}</option><option value="__create">新建专题</option></select></div><div class="m19-filter-field"><span class="m19-filter-label">会议日期:</span><MeetingDateRange :from="fromDate" :to="toDate" @apply="(from,to)=>{fromDate=from;toDate=to;applyFilters();}"/></div><button type="button" class="btn sm ghost" @click="clearFilters"><Icon name="x" :size="13"/> 清空筛选</button></div>

   <p v-if="topicMembershipError" class="banner error" role="alert">{{topicMembershipError}} <button class="btn sm" @click="readTopicMemberships">重新读取专题</button></p><button v-if="topicCommands.pending.value" class="btn" @click="topicWorkspace?.recover()">恢复原专题请求</button>
   <div v-if="scope==='owned'&&selectedMeetingIds.length" class="banner info mb12">已选 {{selectedMeetingIds.length}} 场会议<span class="ml-a"></span><button class="btn sm" @click="assignTopics((page?.items??[]).filter(item=>selectedMeetingIds.includes(item.id)))">批量调整专题</button><button class="btn sm ghost" @click="selectedMeetingIds=[]">取消选择</button></div>
   <section id="meeting-panel" :class="{card:!groupView||scope==='joined'}" role="tabpanel" :aria-labelledby="scope==='owned'?'owned-tab':'joined-tab'" :aria-busy="loading||topicMembershipLoading">
    <div v-if="!session.current.value" class="empty"><Icon name="list" :size="36"/><h2>{{session.status.value==='LOADING'?'正在检查登录':'请登录企业账号'}}</h2><p>请通过企业 CAS 登录恢复会话后查看你{{scope==='owned'?'负责':'参与'}}的会议。</p><RouterLink v-if="session.status.value!=='LOADING'" class="btn primary" to="/login">企业 CAS 登录</RouterLink><button v-else class="btn" disabled>正在检查登录</button></div>
    <template v-else>
     <div v-if="list.error.value" class="list-feedback" role="alert"><p>{{listError}}</p><button class="btn sm" @click="list.reload">重试当前页</button><button class="btn sm" @click="list.first">返回第一页</button></div>
     <template v-if="page&&!list.error.value&&groupView&&scope==='owned'"><section v-for="group in topicGroups" :key="group.topic.id" class="topic-card m19-group"><div class="topic-head"><div class="icon-tile" style="background:var(--orange-bg);color:var(--orange)"><Icon name="layers" :size="17"/></div><div style="flex:1"><button class="m19-name" @click="topicFilter=group.topic.id;groupView=false;applyFilters()">{{group.topic.name}}</button><div class="small muted">{{group.topic.description}} · {{group.items.length}}{{page?.hasMore?'+':''}}场会议</div></div><button class="btn sm ghost" aria-label="修改专题" title="修改专题" @click="topicWorkspace?.openEdit(group.topic)"><Icon name="edit" :size="13"/></button><button class="btn sm ghost" aria-label="删除专题" title="删除空专题" @click="topicWorkspace?.openDelete(group.topic)"><Icon name="x" :size="13"/></button></div><div class="card"><MeetingList v-model:selected="selectedMeetingIds" :items="group.items" :topic-names="topicLabels" @assign="assignTopics" :owned="true" :categories="Object.fromEntries(categoryOptions.map(c=>[c.id,c.label]))" @deleted="meetingDeleted"/></div></section></template>
     <section v-if="page&&!list.error.value&&groupView&&scope==='owned'&&(!topicFilter||topicFilter==='__none')" class="topic-card m19-group"><div class="topic-head"><div class="icon-tile" style="background:var(--orange-bg);color:var(--orange)"><Icon name="layers" :size="17"/></div><div style="flex:1"><button class="m19-name" @click="topicFilter='__none';groupView=false;applyFilters()">未归入专题</button><div class="small muted"> · {{unassignedItems.length}}{{page?.hasMore?'+':''}}场会议</div></div></div><div class="card"><MeetingList v-model:selected="selectedMeetingIds" :topic-names="topicLabels" @assign="assignTopics" :items="unassignedItems" :owned="true" :categories="Object.fromEntries(categoryOptions.map(c=>[c.id,c.label]))" @deleted="meetingDeleted"/></div></section>
     <MeetingList v-model:selected="selectedMeetingIds" :topic-names="topicLabels" @assign="assignTopics" v-else-if="page&&!list.error.value&&(!groupView||scope==='joined')" :items="page.items" :owned="scope==='owned'" :categories="Object.fromEntries(categoryOptions.map(c=>[c.id,c.label]))" @deleted="meetingDeleted"/>
     <div v-if="page&&!list.error.value&&(!groupView||scope==='joined'||page.hasMore||list.canPrevious.value)" class="pager">共 {{minimumTotal}}{{page.hasMore?'+':''}} 条 · {{list.pageNumber.value}}/{{page.hasMore?'…':list.pageNumber.value}} 页 <button :disabled="loading||!list.canPrevious.value" aria-label="上一页" @click="list.previous">‹</button><button class="cur" :disabled="loading" :aria-label="'第 '+list.pageNumber.value+' 页'" @click="list.reload">{{list.pageNumber.value}}</button><button :disabled="loading||!page.hasMore" aria-label="下一页" @click="list.next">›</button></div>
    </template>
   </section>
  </div><div v-if="localDevelopment" class="demo-note">当前页面数据均为产品演示用模拟数据，不代表招商基金真实业务信息。<br>当前原型为纯前端交互演示，不连接招商基金真实业务系统。当前原型仅展示会议负责人的相关功能。</div>
  </main>
 </div></div>
 <dialog ref="dialog" aria-labelledby="notice-title" class="notice-dialog"><form method="dialog"><h2 id="notice-title">{{notice.title}}</h2><p>{{notice.body}}</p><button class="btn primary" autofocus>知道了</button></form></dialog>
 <TopicWorkspace ref="topicWorkspace" @changed="topicsChanged"/><FuturePanels ref="future"/><UploadImportPanel v-if="importMeeting" ref="importPanel" :key="importMeeting" :meeting-id="importMeeting" :new-import="router.currentRoute.value.query.fresh==='1'" @opened="importOpen=true" @closed="closedImport" @saved="savedImport" @discarded="savedImport" @processed="processedImport" @background="kind=>future?.open(kind)"/>
</template>

<style scoped>
.m19-filters :deep(summary.select:not(:focus-visible)){transition:none}


.list-feedback{padding:16px;color:var(--muted)}.list-feedback .btn{margin-top:10px;margin-right:8px}
.home-profile{position:relative}.home-profile>summary{cursor:pointer;list-style:none}.home-profile>summary::-webkit-details-marker{display:none}.home-profile-menu{position:absolute;right:0;top:calc(100% + 4px);z-index:45;display:flex;flex-direction:column;align-items:stretch;min-width:150px;padding:8px;background:white;border:1px solid var(--border);border-radius:8px;box-shadow:var(--shadow-lg)}
@media(max-width:600px){
 .topbar-right{min-width:0;max-width:55%;overflow:hidden}
 .topbar-right>.btn{display:none}
 .home-profile{min-width:0;max-width:100%}
 .home-profile>summary{display:flex;align-items:center;gap:8px;max-width:100%}
 .home-profile>summary>div:last-child{min-width:0;overflow:hidden}
 .home-profile .rc-name,.home-profile .rc-role{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
}
</style>
