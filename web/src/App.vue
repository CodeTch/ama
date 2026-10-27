<script setup lang="ts">
import { computed,onMounted,onUnmounted,ref } from 'vue';
import {useApplication} from './application';
import {useRouter} from 'vue-router';
import {useMeetingCreateFlow} from './features/meetings/useMeetingCreateFlow';
import MeetingCreatePanel from './features/meetings/MeetingCreatePanel.vue';
import MeetingList from './features/meetings/MeetingList.vue';
import {ApiError} from './shared/api/client';
import Icon from './shared/Icon.vue';
const {session,list,create,createDiscovery}=useApplication(),router=useRouter(),createOpen=ref(false);
const createFlow=useMeetingCreateFlow(create,()=>session.current.value?.actor.enterpriseId??null,async id=>{const path=`/meeting/${id}`;const failure=await router.push(path);return !failure&&router.currentRoute.value.path===path;});
const createMessage=computed(()=>createFlow.error.value?(createFlow.error.value instanceof ApiError?createFlow.error.value.message:'操作暂未完成，请保留原请求并重试。'):null);
onUnmounted(createFlow.dispose);
const scope=computed({get:()=>list.scope.value,set:value=>{void list.selectScope(value);}});
const page=list.page,loading=list.loading;
const listError=computed(()=>list.error.value instanceof ApiError?list.error.value.message:'会议数据暂不可用，请稍后重试。');
async function reloadSession(){try{await session.refresh();await list.reload();}catch{/* Session state supplies the unavailable boundary. */}}
onMounted(()=>{if(session.current.value)void list.reload();else void reloadSession();});
function selectScope(event:KeyboardEvent) {
 if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
 event.preventDefault();
 scope.value=event.key==='Home'?'owned':event.key==='End'?'joined':scope.value==='owned'?'joined':'owned';
 (event.currentTarget as HTMLElement).querySelector<HTMLButtonElement>('#'+scope.value+'-tab')?.focus();
}
const dialog=ref<HTMLDialogElement>();
const notice=ref({title:'',body:''});
function show(title:string,body:string) {notice.value={title,body};dialog.value?.showModal();}
</script>
<template>
 <div class="layout"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home" aria-current="page"><Icon name="list"/> 我的会议</RouterLink></nav><div class="topbar-right"><span class="session-state">{{session.status.value==='LOADING'?'正在检查登录':session.current.value?session.current.value.actor.enterpriseId:'企业登录暂不可用'}}</span></div></header>
  <main class="content m19-page">
   <div class="page-head"><div><h1 class="page-title">我的会议</h1><div class="page-desc">集中管理你负责和参与的会议，从录音采集、AI增强转写到纪要生成与发布，让会议内容可核对、结论可追溯、成果随时回看。</div></div><div class="page-actions">
    <button class="btn primary" @click="show('录音服务尚未接入','录音将在后续阶段提供；未访问麦克风或创建会议。')"><Icon name="mic" :size="14"/> 开始录音</button>
    <button class="btn" :disabled="!session.current.value" @click="createOpen=true"><Icon name="plus" :size="14"/> 新建会议</button>
   </div></div>
   <MeetingCreatePanel v-if="session.current.value&&(createOpen||create.command.state.value!=='EMPTY'||create.recoveryBlocked.value)" :state="create.command.state.value" :busy="createFlow.busy.value" :blocked="create.recoveryBlocked.value" :storage-status="create.storageStatus.value" :cleanup-failed="create.cleanupFailed.value" :message="createMessage" :discovery="createDiscovery.result.value?.status??null" :discovering="createDiscovery.loading.value" :discovery-error="!!createDiscovery.error.value" @submit="createFlow.open" @lookup="createDiscovery.reload" @recover="create.retryRecovery" @session="reloadSession" @close="createOpen=false"/>
   <div class="m19-scope-row"><div class="tabs" role="tablist" aria-label="会议范围" @keydown="selectScope">
    <button id="owned-tab" class="tab" :tabindex="scope==='owned'?0:-1" :class="{active:scope==='owned'}" role="tab" :aria-selected="scope==='owned'" aria-controls="meeting-panel" @click="scope='owned'">我负责的</button>
    <button id="joined-tab" class="tab" :tabindex="scope==='joined'?0:-1" :class="{active:scope==='joined'}" role="tab" :aria-selected="scope==='joined'" aria-controls="meeting-panel" @click="scope='joined'">我参与的</button>
   </div><div v-if="scope==='owned'" class="m19-view"><button class="btn sm navy" aria-label="列表" aria-pressed="true"><Icon name="list"/></button><button class="btn sm" aria-label="专题分组" @click="show('专题服务尚未接入','接入真实会议数据后提供专题分组。')"><Icon name="layers"/></button></div></div>
   <section id="meeting-panel" class="card" role="tabpanel" :aria-labelledby="scope==='owned'?'owned-tab':'joined-tab'" :aria-busy="loading">
    <div v-if="!session.current.value" class="empty"><Icon name="list" :size="36"/><h2>{{session.status.value==='LOADING'?'正在检查登录':'企业登录暂不可用'}}</h2><p>请通过企业登录恢复会话后查看你{{scope==='owned'?'负责':'参与'}}的会议。</p><button v-if="session.status.value!=='LOADING'" class="btn" @click="reloadSession">重新检查登录</button></div>
    <template v-else>
     <div v-if="list.error.value" class="list-feedback" role="alert"><p>{{listError}}</p><button class="btn sm" @click="list.reload">重试当前页</button><button class="btn sm" @click="list.first">返回第一页</button></div>
     <p v-if="loading" class="list-feedback" role="status">正在加载会议…</p>
     <MeetingList v-if="page&&page.items.length" :items="page.items"/>
     <div v-else-if="!loading&&!list.error.value" class="empty"><Icon name="list" :size="36"/><h2>{{page?.hasMore?'本页没有可显示的会议':'暂无会议'}}</h2><p>{{page?.hasMore?'可以继续查看下一页。':`这里将显示你${scope==='owned'?'负责':'参与'}的会议。`}}</p></div>
     <div v-if="page" class="list-pagination"><button class="btn sm" :disabled="loading" @click="list.reload">刷新</button><button v-if="page.hasMore" class="btn sm" :disabled="loading" @click="list.next">下一页</button></div>
    </template>
   </section>
   <p class="integration-note">导入、录音、语料反馈与企业人员目录将在后续阶段接入。</p>
  </main>
 </div></div>
 <dialog ref="dialog" aria-labelledby="notice-title" class="notice-dialog"><form method="dialog"><h2 id="notice-title">{{notice.title}}</h2><p>{{notice.body}}</p><button class="btn primary" autofocus>知道了</button></form></dialog>
</template>

<style scoped>
.list-feedback{padding:16px;color:var(--muted)}.list-feedback .btn{margin-top:10px;margin-right:8px}.list-pagination{display:flex;justify-content:flex-end;gap:10px;padding:14px 16px;border-top:1px solid var(--border)}button:disabled{opacity:.55;cursor:wait}
</style>
