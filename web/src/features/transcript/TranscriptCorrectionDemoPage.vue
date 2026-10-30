<script setup lang="ts">
import {ref} from 'vue';
import {RouterLink} from 'vue-router';
import Icon from '../../shared/Icon.vue';

const meetingId='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';
const historyRead=ref(false),reloaded=ref(false),notice=ref<HTMLDialogElement>();
const noticeTitle=ref(''),noticeBody=ref('');

function showNotice(title:string){
 noticeTitle.value=title;
 noticeBody.value='会议服务尚未接入，当前操作仅用于界面演示。';
 notice.value?.showModal();
}

function reloadTranscript(){reloaded.value=true;}
</script>

<template>
 <div class="layout transcript-demo-page"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span class="topbar-crumb">/ 会议工作台</span></nav></header>
  <main class="content detail-page"><div class="aci-detail ac-related transcript-demo-detail">
   <div class="page-head aci-detail-head">
    <div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表">‹</RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title">未命名会议</h1><span class="tag blue">转写待处理</span></div><div class="page-desc">{{meetingId}} · 待补充会议信息</div></div></div>
    <div class="flex ac-workspace-actions"><button class="btn" @click="showNotice('编辑会议信息')">编辑会议信息</button><button class="btn primary" @click="showNotice('保存')">保存</button><button class="btn sm ghost" aria-label="更多操作" @click="showNotice('更多操作')">···</button></div>
   </div>

   <nav class="aci-flow meeting-progress" aria-label="会议处理环节">
    <button class="aci-step done" type="button" aria-label="材料导入" @click="showNotice('材料导入')"><span class="aci-step-icon">✓</span><span>材料导入</span><i class="aci-step-line"></i></button>
    <button class="aci-step active" type="button" aria-label="转写校正" aria-current="step"><span class="aci-step-icon">2</span><span>转写校正</span><i class="aci-step-line"></i></button>
    <button class="aci-step" type="button" aria-label="AI纪要" disabled><span class="aci-step-icon">3</span><span>AI纪要</span><i class="aci-step-line"></i></button>
    <button class="aci-step" type="button" aria-label="会议确认" disabled><span class="aci-step-icon">4</span><span>会议确认</span><i class="aci-step-line"></i></button>
    <button class="aci-step" type="button" aria-label="知识库发布" disabled><span class="aci-step-icon">5</span><span>知识库发布</span></button>
   </nav>

   <div class="wb3 demo-workbench">
    <div class="col-sticky">
     <section class="card card-p demo-empty-card"><b>会议录音</b><div class="small muted mt12">本次为文本转写文件，无原录音，不虚构播放时长。</div></section>
     <section class="card card-p mt16"><div class="between"><b>说话人</b><button class="btn sm ghost" @click="showNotice('编辑说话人')">编辑</button></div><p class="small muted mt12">尚未关联说话人信息。</p></section>
    </div>
    <div class="transcript-main">
     <section class="card transcript-feedback demo-transcript-card" aria-labelledby="demo-transcript-title"><h2 id="demo-transcript-title">转写正文</h2><p>尚无已保存转写。导入任务完成后重新读取。</p><button class="btn" @click="reloadTranscript">重新读取转写</button><p v-if="reloaded" class="small muted demo-status" role="status">已重新读取转写（演示）</p></section>
     <details class="transcript-history demo-history"><summary>转写历史与原操作恢复</summary><section class="card transcript-feedback"><h2>转写历史</h2><button class="btn" @click="historyRead=true">读取历史版本</button><p v-if="historyRead" class="small muted mt12">当前演示没有可恢复的历史版本。</p></section></details>
    </div>
   </div>
  </div></main>
 </div></div>
 <dialog ref="notice" class="notice-dialog" aria-labelledby="demo-notice-title"><form method="dialog"><h2 id="demo-notice-title">{{noticeTitle}}</h2><p>{{noticeBody}}</p><button class="btn primary" autofocus>关闭</button></form></dialog>
</template>

<style scoped>
.transcript-demo-detail .wb3{grid-template-columns:minmax(250px,300px) minmax(0,1fr);gap:16px}
.transcript-demo-detail .wb3>div{min-width:0}
.transcript-demo-detail .col-sticky{top:12px}
.demo-empty-card{min-height:108px}
.demo-transcript-card{min-height:136px;padding:24px;margin-bottom:20px}
.demo-transcript-card h2{font-size:18px;margin-bottom:6px}
.demo-transcript-card p{line-height:1.8}
.demo-status{margin-top:10px}
.demo-history{margin:12px 0;color:var(--muted);font-size:12px}
.demo-history summary{cursor:pointer}
.demo-history .transcript-feedback{box-shadow:none}
@media(max-width:900px){.transcript-demo-detail .wb3{grid-template-columns:minmax(0,1fr)}.transcript-demo-detail .col-sticky{position:static;max-height:none;overflow:visible}}
</style>
