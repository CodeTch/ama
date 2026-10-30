<script setup lang="ts">
import {computed,onBeforeUnmount,ref} from 'vue';
import {RouterLink} from 'vue-router';
import Icon from '../../shared/Icon.vue';

type PublishPhase='idle'|'publishing'|'failed'|'succeeded';
type Folder={id:string;name:string;children?:Folder[]};
type Library={id:string;name:string;description:string;folders:Folder[]};

const meetingId='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';
const documentTitle=ref('昆仑 AI·无界浏览器智能助手系统_架构评审材料_V1.9_分析报告');
const libraryQuery=ref('');
const selectedLibrary=ref('');
const selectedFolder=ref('');
const folderParent=ref<string|null>(null);
const phase=ref<PublishPhase>('idle');
const attempts=ref(0);
const progress=ref(0);
const previewDialog=ref<HTMLDialogElement>();
const publishDialog=ref<HTMLDialogElement>();
const noticeDialog=ref<HTMLDialogElement>();
const noticeTitle=ref('');
const noticeBody=ref('');
const publishMessage=ref('正在校验确认版、证据关系与发布权限…（模拟）');
const submittedDestination=ref('');
const submittedTitle=ref('');
let publishTimer:ReturnType<typeof setInterval>|null=null;

const libraries:Library[]=[
 {id:'research',name:'投资研究知识库',description:'投研资料与经营分析会议',folders:[
  {id:'q3',name:'2026 年第三季度',children:[{id:'research-topic',name:'专题研究'},{id:'research-review',name:'架构评审'}]},
  {id:'annual',name:'年度经营分析'},
  {id:'project',name:'项目评审与复盘'},
 ]},
 {id:'risk',name:'风险管理知识库',description:'风险管理制度与专题材料',folders:[
  {id:'risk-q3',name:'风险专题'},
  {id:'risk-policy',name:'制度与流程'},
 ]},
 {id:'enterprise',name:'企业制度知识库',description:'企业制度、流程与培训资料',folders:[
  {id:'enterprise-root',name:'会议纪要'},
 ]},
];

const visibleLibraries=computed(()=>{
 const query=libraryQuery.value.trim().toLocaleLowerCase();
 return query?libraries.filter(item=>item.name.toLocaleLowerCase().includes(query)||item.description.toLocaleLowerCase().includes(query)):libraries;
});
const currentLibrary=computed(()=>libraries.find(item=>item.id===selectedLibrary.value));
function findFolder(items:Folder[],id:string):Folder|undefined{for(const item of items){if(item.id===id)return item;const nested=item.children&&findFolder(item.children,id);if(nested)return nested;}return undefined;}
function folderPath(items:Folder[],id:string,path:Folder[]=[]):Folder[]{for(const item of items){const next=[...path,item];if(item.id===id)return next;const children=item.children;if(children){const nested=folderPath(children,id,next);if(nested.length)return nested;}}return [];}
const visibleFolders=computed(()=>{if(!currentLibrary.value)return [];if(!folderParent.value)return currentLibrary.value.folders;return findFolder(currentLibrary.value.folders,folderParent.value)?.children??[];});
const currentFolder=computed(()=>currentLibrary.value&&selectedFolder.value?findFolder(currentLibrary.value.folders,selectedFolder.value):undefined);
const destination=computed(()=>{if(!currentLibrary.value||!currentFolder.value)return '请选择发布位置';return [currentLibrary.value.name,...folderPath(currentLibrary.value.folders,currentFolder.value.id).map(item=>item.name)].join(' / ');});
const locked=computed(()=>phase.value==='publishing'||phase.value==='succeeded');
const publishDisabled=computed(()=>!currentFolder.value||locked.value);
const publishLabel=computed(()=>phase.value==='failed'?'重试发布':'发布到知识库');
const statusText=computed(()=>phase.value==='failed'?'知识库服务暂时不可用（模拟 · KB-5503）':'');
const confirmedMinutes=[
 {heading:'一、会议背景',body:'本次会议围绕架构评审材料展开讨论，形成了可追溯的结论与行动建议。'},
 {heading:'二、核心结论',body:'会议确认采用统一的知识库发布路径，所有内容以确认版纪要和证据关系为准。'},
 {heading:'三、后续行动',body:'由会议负责人整理确认版本并发布到投资研究知识库，后续按照目录权限进行回看。'},
];
const formalDocument={id:'KD-2026-DEMO-001',version:'发布版 V1',publisher:'会议负责人',publishedAt:'2026-10-30 21:00'};

function showNotice(title:string,body='会议服务尚未接入，当前操作仅用于界面演示。'){
 if(locked.value)return;
 noticeTitle.value=title;noticeBody.value=body;noticeDialog.value?.showModal();
}
function chooseLibrary(id:string){if(!locked.value){selectedLibrary.value=id;selectedFolder.value='';folderParent.value=null;}}
function enterFolder(id:string){if(!locked.value){folderParent.value=id;selectedFolder.value='';}}
function goRoot(){if(!locked.value){folderParent.value=null;selectedFolder.value='';}}
function chooseFolder(id:string){if(!locked.value)selectedFolder.value=id;}
function openPreview(){previewDialog.value?.showModal();}
function clearTimer(){if(publishTimer){clearInterval(publishTimer);publishTimer=null;}}
function publish(){
 if(publishDisabled.value)return;
 clearTimer();submittedDestination.value=destination.value;submittedTitle.value=documentTitle.value;phase.value='publishing';progress.value=0;publishDialog.value?.showModal();
 publishTimer=setInterval(()=>{
  progress.value=Math.min(100,progress.value+20);
  if(progress.value<40)publishMessage.value='正在校验确认版、证据关系与发布权限…（模拟）';
  else if(progress.value<80)publishMessage.value='正在生成知识文档…（模拟）';
  else publishMessage.value='正在写入发布记录…（模拟）';
  if(progress.value>=100){
   clearTimer();const failed=attempts.value===0;attempts.value+=1;phase.value=failed?'failed':'succeeded';
   window.setTimeout(()=>publishDialog.value?.close(),180);
  }
 },90);
}
onBeforeUnmount(clearTimer);
</script>

<template>
 <div class="layout knowledge-publish-demo-page"><div class="main">
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span class="topbar-crumb">/ 会议工作台</span></nav></header>
  <main class="content detail-page"><div class="aci-detail ac-related knowledge-publish-detail">
   <div class="page-head aci-detail-head">
    <div class="ac-workspace-leading"><RouterLink to="/meetings" class="btn ac-workspace-back" aria-label="返回列表">‹</RouterLink><div class="aci-heading-text"><div class="aci-title-row"><h1 class="page-title">未命名会议</h1><span class="tag orange">待发布</span></div><div class="page-desc">{{meetingId}} · 会议信息已确认</div></div></div>
    <div class="flex ac-workspace-actions"><button class="btn" :disabled="locked" @click="showNotice('编辑会议信息')">编辑会议信息</button><button class="btn primary" :disabled="locked" @click="showNotice('保存')">保存</button><button class="btn sm ghost" :disabled="locked" aria-label="更多操作" @click="showNotice('更多操作')">···</button></div>
   </div>

   <nav class="aci-flow meeting-progress" aria-label="会议处理环节">
    <button class="aci-step done" type="button" aria-label="材料导入" @click="showNotice('材料导入')"><span class="aci-step-icon">✓</span><span>材料导入</span><i class="aci-step-line"></i></button>
    <button class="aci-step done" type="button" aria-label="转写校正" @click="showNotice('转写校正')"><span class="aci-step-icon">✓</span><span>转写校正</span><i class="aci-step-line"></i></button>
    <button class="aci-step done" type="button" aria-label="AI纪要" @click="showNotice('AI纪要')"><span class="aci-step-icon">✓</span><span>AI纪要</span><i class="aci-step-line"></i></button>
    <button class="aci-step done" type="button" aria-label="会议确认" @click="showNotice('会议确认')"><span class="aci-step-icon">✓</span><span>会议确认</span><i class="aci-step-line"></i></button>
    <button class="aci-step active" type="button" aria-label="知识库发布" aria-current="step"><span class="aci-step-icon">5</span><span>知识库发布</span></button>
   </nav>

   <div class="two-col knowledge-publish-grid">
    <section class="card pub-location-card" aria-labelledby="publish-location-title">
     <div class="card-h"><h2 id="publish-location-title" class="card-t">选择发布位置</h2></div>
     <div class="card-p">
      <div class="pub-location-search"><div class="search-box"><Icon name="search" :size="13"/><input v-model="libraryQuery" class="input" :disabled="locked" aria-label="搜索发布知识库" placeholder="搜索知识库"/><button v-if="libraryQuery" type="button" class="pub-search-clear" :disabled="locked" aria-label="清空搜索" @click="libraryQuery=''">×</button></div></div>
      <div class="small muted pub-location-count">{{visibleLibraries.length}} 个知识库</div>
      <div class="pub-location-list demo-library-list" role="listbox" aria-label="知识库列表">
       <button v-for="library in visibleLibraries" :key="library.id" type="button" role="option" class="pub-library-option" :class="{selected:library.id===selectedLibrary}" :disabled="locked" :aria-label="library.name" :aria-selected="library.id===selectedLibrary" @click="chooseLibrary(library.id)"><span class="pub-library-icon">▱</span><span class="pub-library-copy"><b>{{library.name}}</b><small>{{library.description}}</small></span><span aria-hidden="true">›</span></button>
       <p v-if="!visibleLibraries.length" class="empty small">没有匹配的知识库</p>
      </div>
      <div v-if="currentLibrary" class="demo-folder-panel"><div class="demo-folder-heading"><b>文件夹</b><span class="small muted">{{currentLibrary.name}}</span><button v-if="folderParent" class="btn sm ghost" type="button" :disabled="locked" @click="goRoot">返回根目录</button></div><p v-if="!selectedFolder" class="small muted demo-folder-prompt">请选择文件夹</p><div class="pub-folder-list"><div v-for="folder in visibleFolders" :key="folder.id" class="pub-folder-option"><label><input type="radio" name="publish-folder" :aria-label="folder.name" :disabled="locked" :checked="selectedFolder===folder.id" @change="chooseFolder(folder.id)"/> <span>{{folder.name}}</span></label><button v-if="folder.children?.length" class="btn sm ghost" type="button" :disabled="locked" :aria-label="`进入 ${folder.name}`" @click="enterFolder(folder.id)">进入</button></div></div></div>
      <p v-else class="empty small demo-folder-empty">请选择知识库</p>
      <div class="small muted mt12">文件夹继承知识库权限；仅可选择已有文件夹，不能在此创建、修改或删除。</div>
     </div>
    </section>

    <div class="grid knowledge-publish-side">
     <section class="card" aria-labelledby="publish-config-title"><div class="card-h"><h2 id="publish-config-title" class="card-t">发布配置</h2></div><div class="card-p">
      <div class="field"><label>发布路径</label><div class="card card-p publish-path"><b>{{destination}}</b></div></div>
      <div class="field"><label for="demo-document-title">文档标题</label><input id="demo-document-title" v-model="documentTitle" :disabled="locked" class="input"/></div>
      <div class="banner info publish-info">▣ 知识库权限粒度在库层面，发布无需配置权限：文档自动继承目标知识库的访问权限，不额外扩大。</div>
      <div class="divider"></div>
      <div class="publish-meta"><div class="between"><span class="muted">来源会议</span><span class="mono small">{{meetingId}}</span></div><div class="between"><span class="muted">证据完整性</span><span class="tag green">校验通过 · 重要结论100%有证据</span></div><div class="between"><span class="muted">权限</span><span class="tag navy">继承知识库库级权限</span></div></div>
      <div class="mt12"><button class="btn" type="button" @click="openPreview">最终文档预览</button></div>
     </div></section>
     <section class="card demo-preview-card" aria-labelledby="minutes-preview-title"><div class="card-h between"><h2 id="minutes-preview-title" class="card-t">纪要预览</h2><span class="small muted">已最终确认的版本</span></div><div class="card-p"><h3>{{documentTitle}}</h3><div v-for="minute in confirmedMinutes" :key="minute.heading" class="confirmed-minute"><h4>{{minute.heading}}</h4><p>{{minute.body}}</p></div></div></section>
     <section class="card card-p publish-action-card"><div class="flex"><div style="flex:1"><div class="b" style="font-size:14px">{{phase==='failed'?'重试发布':'确认发布'}}</div><div class="small muted">发布以确认版纪要、证据关系和发布版本为最小单元 · 重复发布不会生成重复文档</div></div><button class="btn primary lg" :disabled="publishDisabled" type="button" @click="publish">{{publishLabel}}</button></div><div v-if="!currentFolder" class="small mt8 publish-hint">请选择发布知识库或库内文件夹</div></section>
     <section v-if="phase==='failed'" class="banner error publish-result" role="alert"><div><b>知识库发布失败：{{statusText}}</b><br/>已确认状态与发布位置均已保留，可直接重试，无需重新确认。</div></section>
     <section v-if="phase==='succeeded'" class="card publication-result-card" role="status"><div class="card-h"><div><b>发布成功，正式纪要已生成。</b><div class="small muted mt8"><span>当前会议已切换为只读</span>，重复发布不会生成重复知识文档。</div></div></div><div class="card-p"><dl class="publication-result-meta"><div><dt>文档编号</dt><dd>{{formalDocument.id}}</dd></div><div><dt>发布版本</dt><dd>{{formalDocument.version}}</dd></div><div><dt>发布位置</dt><dd>{{submittedDestination}}</dd></div><div><dt>发布人 / 时间</dt><dd>{{formalDocument.publisher}} · {{formalDocument.publishedAt}}</dd></div></dl><a class="btn" :href="`#knowledge-document-${formalDocument.id}`" aria-label="查看正式纪要 ↗">查看正式纪要 ↗</a></div></section>
    </div>
   </div>
  </div></main>
 </div></div>

 <footer class="acf-dock knowledge-publish-dock"><div class="acf-inner"><span class="acf-hint">{{phase==='succeeded'?'发布完成，当前会议已切换为只读。':'发布后会议将保持只读。'}}</span><div class="acf-actions"><button class="btn" type="button" :disabled="locked" @click="showNotice('返回会议确认')">上一步</button><button class="btn primary" :disabled="publishDisabled" type="button" @click="publish">{{publishLabel}}</button></div></div></footer>

 <dialog ref="previewDialog" class="notice-dialog preview-dialog" aria-labelledby="preview-title"><form method="dialog"><h2 id="preview-title">最终文档预览（发布版）</h2><div class="preview-copy"><h3>{{documentTitle}}</h3><div v-for="minute in confirmedMinutes" :key="minute.heading" class="confirmed-minute"><h4>{{minute.heading}}</h4><p>{{minute.body}}</p></div></div><button class="btn primary" autofocus>关闭预览</button></form></dialog>
 <dialog ref="publishDialog" class="notice-dialog publish-dialog" aria-labelledby="publishing-title" @cancel.prevent><form method="dialog"><h2 id="publishing-title">正在发布</h2><div class="publish-progress"><div class="progress"><div class="bar" :style="{width:`${progress}%`}"></div></div><p class="small muted">{{publishMessage}}</p><p class="small muted">请稍候，发布完成后会显示最终结果。</p></div></form></dialog>
 <dialog ref="noticeDialog" class="notice-dialog" aria-labelledby="demo-notice-title"><form method="dialog"><h2 id="demo-notice-title">{{noticeTitle}}</h2><p>{{noticeBody}}</p><button class="btn primary" autofocus>关闭</button></form></dialog>
</template>

<style scoped>
.knowledge-publish-detail{padding-bottom:30px}
.knowledge-publish-grid{grid-template-columns:minmax(0,1fr) minmax(0,1.2fr);align-items:start}
.knowledge-publish-grid>section,.knowledge-publish-side{min-width:0}
.knowledge-publish-side{gap:16px}
.pub-location-card .card-h,.knowledge-publish-side .card-h{align-items:flex-start}
.pub-location-card .card-p{padding-top:16px}
.pub-location-search{margin-bottom:8px}.pub-location-search .search-box{width:100%}.pub-location-search .input{padding-right:34px}
.pub-search-clear{position:absolute;right:5px;top:50%;transform:translateY(-50%);border:0;background:transparent;color:var(--muted);font-size:18px;line-height:1;cursor:pointer;padding:3px 7px}
.pub-location-count{margin:0 0 10px}.demo-library-list{min-height:188px;max-height:250px}
.pub-library-option{display:flex;align-items:center;gap:10px;width:100%;padding:12px;border:1px solid transparent;border-radius:6px;background:#fff;color:var(--text);font:inherit;text-align:left;cursor:pointer}.pub-library-option:hover{background:#fafaf6}.pub-library-option.selected{border-color:var(--orange);background:var(--orange-bg);color:var(--orange)}
.pub-library-icon{font-size:18px;line-height:1;color:currentColor}.pub-library-copy{flex:1;min-width:0}.pub-library-copy>b,.pub-library-copy>small{display:block;overflow-wrap:anywhere}.pub-library-copy>small{font-size:12px;color:var(--muted);margin-top:3px}
.demo-folder-panel{border-top:1px solid var(--border);margin-top:14px;padding-top:14px}.demo-folder-heading{display:flex;align-items:center;gap:10px;margin-bottom:8px}.demo-folder-heading button{margin-left:auto}.pub-folder-list{display:flex;flex-direction:column;gap:4px}.pub-folder-option{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 10px;border-radius:6px;color:var(--text)}.pub-folder-option:hover{background:var(--bg)}.pub-folder-option>label{display:flex;align-items:center;gap:8px;cursor:pointer;min-width:0}.pub-folder-option input{accent-color:var(--orange)}.demo-folder-empty{min-height:72px;margin-top:14px;padding:20px 12px}
.demo-folder-p{margin:0 0 4px}
.publish-path{padding:10px 14px;background:var(--orange-bg);border-color:#ffd9be}.publish-path b{color:var(--orange);overflow-wrap:anywhere}.publish-info{margin:0 0 14px}.publish-meta{display:grid;gap:8px;font-size:13px}.publish-meta .between{gap:10px}.publish-meta .mono{overflow-wrap:anywhere;text-align:right}.publish-action-card{padding:16px 20px}.publish-action-card .flex{gap:12px}.publish-hint{color:var(--amber)}.publish-result{margin:0;padding:14px 16px;line-height:1.75}.knowledge-publish-dock{z-index:90}
.demo-preview-card h3{font-size:16px;color:var(--navy);margin:0 0 16px;overflow-wrap:anywhere}.confirmed-minute{margin:0 0 16px}.confirmed-minute:last-child{margin-bottom:0}.confirmed-minute h4{font-size:14px;color:var(--navy);margin:0 0 6px}.confirmed-minute p{line-height:1.8;color:var(--muted);margin:0}.publication-result-card{margin:0}.publication-result-meta{display:grid;grid-template-columns:minmax(100px,.4fr) minmax(0,1fr);gap:10px 18px;margin:0 0 16px;font-size:13px}.publication-result-meta>div{display:contents}.publication-result-meta dt{color:var(--muted)}.publication-result-meta dd{margin:0;overflow-wrap:anywhere}
.preview-dialog,.publish-dialog{width:min(760px,calc(100vw - 40px));max-width:760px;padding:22px;border:1px solid var(--border);border-radius:10px;box-shadow:var(--shadow-lg);color:var(--text)}.preview-dialog::backdrop,.publish-dialog::backdrop,.notice-dialog::backdrop{background:rgba(17,24,73,.35)}.preview-dialog h2,.publish-dialog h2{font-size:17px;color:var(--navy);margin-bottom:16px}.preview-dialog form,.publish-dialog form{display:flex;flex-direction:column}.preview-copy{max-height:54vh;overflow:auto;padding:14px 16px;background:#fafaf7;border:1px solid var(--border);border-radius:8px;line-height:1.85}.preview-copy h3{color:var(--navy);font-size:16px;margin-bottom:14px}.preview-copy p{margin:8px 0}.preview-dialog .btn,.publish-dialog .btn{align-self:flex-end;margin-top:16px}.publish-progress{padding:22px 8px 8px}.publish-progress .progress{height:8px}.publish-dialog form>.btn{align-self:flex-end}
@media(max-width:900px){.knowledge-publish-grid{grid-template-columns:minmax(0,1fr)}.knowledge-publish-detail{padding-bottom:18px}.knowledge-publish-side{gap:12px}.publish-meta .between{align-items:flex-start;flex-wrap:wrap}.knowledge-publish-dock .acf-hint{flex-basis:100%}}
</style>
