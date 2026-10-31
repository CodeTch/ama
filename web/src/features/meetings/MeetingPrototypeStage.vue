<script setup lang="ts">
import {ref} from 'vue';
import {RouterLink} from 'vue-router';
import Icon from '../../shared/Icon.vue';
import {DEMO_MEETING_ID,prototypeGroups,type PrototypeStage} from './meeting-prototype-demo';

const props=defineProps<{stage:PrototypeStage;meetingId?:string}>();
const emit=defineEmits<{navigate:[stage:PrototypeStage]}>();
const meetingId=props.meetingId??DEMO_MEETING_ID;
const minutesView=ref<'elements'|'body'>('elements'),selectedCategory=ref<string>(prototypeGroups[0].name),reloaded=ref(false),historyRead=ref(false),regenerated=ref(false);
const notice=ref<HTMLDialogElement>(),noticeTitle=ref(''),noticeBody=ref('');

function showNotice(title:string,body='会议服务尚未接入，当前操作仅用于界面演示。'){
 noticeTitle.value=title;noticeBody.value=body;notice.value?.showModal();
}
function navigate(stage:PrototypeStage){emit('navigate',stage);}
function reloadTranscript(){reloaded.value=true;}
function regenerate(){regenerated.value=true;showNotice('重新生成');}
</script>

<template>
 <div class="layout prototype-meeting-page"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span class="topbar-crumb">/ 会议工作台</span></nav></header>
  <main class="content detail-page prototype-detail-page" :class="{'prototype-minutes-page':stage==='minutes'}"><div class="aci-detail ac-related prototype-detail">
   <div class="page-head aci-detail-head">
    <div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表">‹</RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title">未命名会议</h1><span class="tag blue">{{stage==='confirm'?'会议待确认':stage==='minutes'?'纪要待处理':'转写待处理'}}</span></div><div class="page-desc">{{meetingId}} · 待补充会议信息</div></div></div>
    <div class="flex ac-workspace-actions"><button class="btn" @click="showNotice('编辑会议信息')">编辑会议信息</button><button class="btn primary" @click="showNotice('保存')">保存</button><button class="btn sm ghost" aria-label="更多操作" @click="showNotice('更多操作')">···</button></div>
   </div>

   <nav class="aci-flow meeting-progress" aria-label="会议处理环节">
    <button class="aci-step done" type="button" aria-label="材料导入" @click="showNotice('材料导入')"><span class="aci-step-icon">✓</span><span>材料导入</span><i class="aci-step-line"></i></button>
    <button class="aci-step" :class="{done:stage==='minutes'||stage==='confirm',active:stage==='transcript'}" type="button" aria-label="转写校正" :aria-current="stage==='transcript'?'step':undefined" :disabled="stage==='transcript'" @click="navigate('transcript')"><span class="aci-step-icon">{{stage==='minutes'||stage==='confirm'?'✓':'2'}}</span><span>转写校正</span><i class="aci-step-line"></i></button>
    <button class="aci-step" :class="{done:stage==='confirm',active:stage==='minutes'}" type="button" aria-label="AI纪要" :aria-current="stage==='minutes'?'step':undefined" :disabled="stage==='transcript'||stage==='confirm'" @click="navigate('minutes')"><span class="aci-step-icon">{{stage==='confirm'?'✓':'3'}}</span><span>AI纪要</span><i class="aci-step-line"></i></button>
    <button class="aci-step" :class="{active:stage==='confirm'}" type="button" aria-label="会议确认" :aria-current="stage==='confirm'?'step':undefined" :disabled="stage!=='confirm'" @click="navigate('confirm')"><span class="aci-step-icon">4</span><span>会议确认</span><i class="aci-step-line"></i></button>
    <button class="aci-step" type="button" aria-label="知识库发布" disabled @click="showNotice('知识库发布')"><span class="aci-step-icon">5</span><span>知识库发布</span></button>
   </nav>

   <template v-if="stage==='transcript'">
    <div class="prototype-transcript-grid">
     <div class="prototype-left-column">
      <section class="card card-p prototype-recording-card"><b>会议录音</b><div class="small muted mt12">本次为文本转写文件，无原录音，不虚构播放时长。</div></section>
      <section class="card card-p mt16"><div class="between"><b>说话人</b><button class="btn sm ghost" @click="showNotice('编辑说话人')">编辑</button></div><p class="small muted mt12">尚未关联说话人信息。</p></section>
     </div>
     <div class="transcript-main">
      <section class="card transcript-feedback prototype-transcript-card" aria-labelledby="prototype-transcript-title"><h2 id="prototype-transcript-title">转写正文</h2><p>尚无已保存转写。导入任务完成后重新读取。</p><button class="btn" @click="reloadTranscript">重新读取转写</button><p v-if="reloaded" class="small muted prototype-status" role="status">已重新读取转写（演示）</p></section>
      <details class="prototype-history"><summary>转写历史与原操作恢复</summary><section class="card transcript-feedback"><h2>转写历史</h2><button class="btn" @click="historyRead=true">读取历史版本</button><p v-if="historyRead" class="small muted mt12">当前演示没有可恢复的历史版本。</p></section></details>
     </div>
    </div>
   </template>

   <template v-else-if="stage==='minutes'">
    <section class="prototype-minutes-workbench">
     <nav class="tabs prototype-minutes-tabs" aria-label="纪要内容"><button class="tab" :class="{active:minutesView==='elements'}" :aria-pressed="minutesView==='elements'" @click="minutesView='elements'">核心要素</button><button class="tab" :class="{active:minutesView==='body'}" :aria-pressed="minutesView==='body'" @click="minutesView='body'">会议纪要</button></nav>
     <div v-if="minutesView==='elements'" class="prototype-minutes-layout">
      <nav class="prototype-minutes-toc" aria-label="核心要素分类"><button v-for="group in prototypeGroups" :key="group.name" class="prototype-toc-item" :class="{selected:selectedCategory===group.name}" :aria-current="selectedCategory===group.name?'location':undefined" @click="selectedCategory=group.name">{{group.name}}</button></nav>
      <div class="prototype-minutes-content"><section v-for="group in prototypeGroups" :key="group.name" class="card card-p prototype-minutes-section" :class="{selected:selectedCategory===group.name}"><div class="between"><h3>{{group.name}}</h3><button class="btn sm ghost" @click="showNotice('局部重新生成')">♧ 局部重新生成</button></div><p v-for="paragraph in group.paragraphs" :key="paragraph" class="prototype-minutes-paragraph">{{paragraph}}</p><button class="btn sm ghost" @click="showNotice('编辑核心要素')">✎ 编辑</button></section></div>
     </div>
     <section v-else class="card card-p prototype-minutes-body" aria-labelledby="prototype-minutes-body-title"><div class="between prototype-body-toolbar"><div><h2 id="prototype-minutes-body-title">AI纪要正文</h2><p class="small muted mt8">生成结果为演示数据，需人工核对。</p></div><div class="flex"><button class="btn sm ghost" @click="showNotice('选择总结模板')">总结模板</button><button class="btn sm" @click="regenerate">重新生成</button><button class="btn sm ghost" @click="showNotice('编辑正文')">✎ 编辑</button></div></div><h3 class="prototype-body-title">未命名会议 · 会议纪要</h3><p v-for="group in prototypeGroups.slice(0,5)" :key="group.name" class="prototype-body-paragraph"><b>{{group.name}}：</b>{{group.paragraphs[0]}}</p><p v-if="regenerated" class="small muted prototype-status" role="status">已重新生成纪要（演示）</p></section>
    </section>
    <footer class="prototype-dock"><span class="prototype-dock-hint">确认核心要素并生成会议纪要后进入会议确认。</span><button class="btn" @click="navigate('transcript')">返回转写校正</button><button class="btn primary" aria-label="进入会议确认" @click="navigate('confirm')">进入会议确认</button></footer>
   </template>
   <template v-else>
    <section class="prototype-confirm-grid">
     <section class="card card-p"><h2>会议确认</h2><p class="mt12">请确认会议信息、参会名单与 AI 纪要内容后继续发布。</p><div class="prototype-confirm-info"><b>会议名称</b><span>未命名会议</span><b>会议日期</b><span>待补充</span><b>参会人</b><span>尚未确认参会人员</span></div></section>
     <section class="card card-p"><h2>会议纪要</h2><p class="mt12">AI 纪要演示正文已生成，最终确认前仍可返回编辑。</p><button class="btn mt16" @click="navigate('minutes')">返回纪要编辑</button></section>
    </section>
    <footer class="prototype-dock"><span class="prototype-dock-hint">最终确认将保存会议信息、确认参会名单并更新访问权限。</span><button class="btn" @click="navigate('minutes')">返回纪要编辑</button><button class="btn primary" @click="showNotice('最终确认并继续发布')">最终确认并继续发布</button></footer>
   </template>
  </div></main>
 </div></div>
 <dialog ref="notice" class="notice-dialog" aria-labelledby="prototype-notice-title"><form method="dialog"><h2 id="prototype-notice-title">{{noticeTitle}}</h2><p>{{noticeBody}}</p><button class="btn primary" autofocus>关闭</button></form></dialog>
</template>

<style scoped>
.prototype-detail-page{padding-top:24px}
.prototype-detail .page-head{margin-bottom:18px}
.prototype-detail .wb3>div{min-width:0}
.prototype-transcript-grid{display:grid;grid-template-columns:minmax(250px,300px) minmax(0,1fr);gap:16px;align-items:start}
.prototype-left-column{min-width:0}
.prototype-left-column .card{min-height:108px}
.prototype-transcript-card{min-height:136px;padding:24px;margin-bottom:20px}
.prototype-transcript-card h2{font-size:18px;margin-bottom:6px}
.prototype-transcript-card p{line-height:1.8}
.prototype-status{margin-top:10px}
.prototype-history{margin:12px 0;color:var(--muted);font-size:12px}
.prototype-history summary{cursor:pointer}
.prototype-history .transcript-feedback{box-shadow:none}
.prototype-minutes-workbench{background:#fff;border:1px solid var(--border);border-radius:8px;overflow:hidden}
.prototype-minutes-tabs{display:flex;align-items:center;padding:0 16px;border-bottom:1px solid var(--border)}
.prototype-minutes-layout{display:grid;grid-template-columns:164px minmax(0,1fr);gap:16px;padding:16px;min-height:460px}
.prototype-minutes-toc{display:flex;flex-direction:column;border-right:1px solid var(--border);padding-right:10px;overflow:auto}
.prototype-toc-item{background:none;border:0;text-align:left;color:var(--muted);font:inherit;padding:10px 8px;cursor:pointer;border-radius:6px}
.prototype-toc-item.selected{background:var(--orange-bg);color:var(--orange);font-weight:600}
.prototype-minutes-content{min-width:0;overflow:auto;padding-right:4px}
.prototype-minutes-section{margin-bottom:12px;scroll-margin-top:16px}
.prototype-minutes-section.selected{border-color:#ffd7ae}
.prototype-minutes-section h3{font-size:15px;color:var(--navy)}
.prototype-minutes-paragraph{line-height:1.8;margin:12px 0;white-space:pre-wrap}
.prototype-minutes-body{min-height:460px;margin:16px;padding:24px}
.prototype-body-toolbar{gap:16px;border-bottom:1px solid var(--border);padding-bottom:16px}
.prototype-body-toolbar h2{font-size:18px;color:var(--navy)}
.prototype-body-title{font-size:20px;color:var(--navy);margin:22px 0 16px}
.prototype-body-paragraph{line-height:1.9;margin:14px 0;white-space:pre-wrap}
.prototype-confirm-grid{display:grid;grid-template-columns:minmax(0,.9fr) minmax(0,1.3fr);gap:16px;align-items:start}
.prototype-confirm-grid h2{font-size:18px;color:var(--navy)}
.prototype-confirm-info{display:grid;grid-template-columns:110px minmax(0,1fr);gap:12px 16px;margin-top:22px;color:var(--muted);font-size:13px}
.prototype-confirm-info span{color:var(--text)}
.prototype-dock{position:fixed;left:0;right:0;bottom:0;z-index:80;background:#fff;border-top:1px solid var(--border);box-shadow:0 -3px 12px #00000008;padding:14px 24px max(14px,env(safe-area-inset-bottom));display:flex;align-items:center;gap:12px}
.prototype-dock-hint{flex:1;color:var(--muted);font-size:12px}
.prototype-minutes-page{height:calc(100dvh - 56px);flex:none;overflow:hidden;display:flex;flex-direction:column;padding-block:16px}
.prototype-minutes-page .prototype-detail{display:flex;flex-direction:column;min-height:0;flex:1}
.prototype-minutes-page .prototype-detail>.aci-detail-head,.prototype-minutes-page .prototype-detail>.meeting-progress{flex-shrink:0}
.prototype-minutes-page .prototype-minutes-workbench{display:flex;flex-direction:column;flex:1;min-height:0}
.prototype-minutes-page .prototype-minutes-layout{flex:1;min-height:0}
@media(max-width:900px){.prototype-transcript-grid{grid-template-columns:minmax(0,1fr)}.prototype-minutes-layout{grid-template-columns:minmax(0,1fr);min-height:0}.prototype-minutes-toc{display:flex;flex-direction:row;overflow:auto;border-right:0;border-bottom:1px solid var(--border);padding:0 0 8px;gap:4px}.prototype-toc-item{white-space:nowrap}.prototype-dock{flex-wrap:wrap}.prototype-dock-hint{flex-basis:100%}.prototype-confirm-grid{grid-template-columns:minmax(0,1fr)}}
</style>
