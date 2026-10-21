<script setup lang="ts">
import { ref } from 'vue';
import Icon from './shared/Icon.vue';
const scope=ref<'owned'|'joined'>('owned');
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
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home" aria-current="page"><Icon name="list"/> 我的会议</RouterLink></nav><div class="topbar-right"><span class="session-state">企业登录尚未接入</span></div></header>
  <main class="content m19-page">
   <div class="page-head"><div><h1 class="page-title">我的会议</h1><div class="page-desc">集中管理你负责和参与的会议，从录音采集、AI增强转写到纪要生成与发布，让会议内容可核对、结论可追溯、成果随时回看。</div></div><div class="page-actions">
    <button class="btn primary" @click="show('录音服务尚未接入','录音将在后续阶段提供；未访问麦克风或创建会议。')"><Icon name="mic" :size="14"/> 开始录音</button>
    <button class="btn" @click="show('导入服务尚未接入','导入将在后续阶段提供；未上传文件或创建会议。')"><Icon name="plus" :size="14"/> 导入会议</button>
   </div></div>
   <div class="m19-scope-row"><div class="tabs" role="tablist" aria-label="会议范围" @keydown="selectScope">
    <button id="owned-tab" class="tab" :tabindex="scope==='owned'?0:-1" :class="{active:scope==='owned'}" role="tab" :aria-selected="scope==='owned'" aria-controls="meeting-panel" @click="scope='owned'">我负责的</button>
    <button id="joined-tab" class="tab" :tabindex="scope==='joined'?0:-1" :class="{active:scope==='joined'}" role="tab" :aria-selected="scope==='joined'" aria-controls="meeting-panel" @click="scope='joined'">我参与的</button>
   </div><div v-if="scope==='owned'" class="m19-view"><button class="btn sm navy" aria-label="列表" aria-pressed="true"><Icon name="list"/></button><button class="btn sm" aria-label="专题分组" @click="show('专题服务尚未接入','接入真实会议数据后提供专题分组。')"><Icon name="layers"/></button></div></div>
   <section id="meeting-panel" class="card empty" role="tabpanel" :aria-labelledby="scope==='owned'?'owned-tab':'joined-tab'">
    <Icon name="list" :size="36"/><h2>会议服务尚未接入</h2><p>企业登录与会议接口接入后，将显示你{{scope==='owned'?'负责':'参与'}}的会议。</p><p>当前无法读取会议数据。</p>
   </section>
   <p class="integration-note">导入、录音、语料反馈与企业人员目录将在后续阶段接入。</p>
  </main>
 </div></div>
 <dialog ref="dialog" aria-labelledby="notice-title" class="notice-dialog"><form method="dialog"><h2 id="notice-title">{{notice.title}}</h2><p>{{notice.body}}</p><button class="btn primary" autofocus>知道了</button></form></dialog>
</template>
