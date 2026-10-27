<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import type {TranscriptEdits} from './transcript-journal';
import type {TranscriptSegment} from '../../shared/api/transcripts';
const props=defineProps<{value:TranscriptEdits;editable:boolean;busy:boolean;dirty:boolean;conflict:TranscriptEdits|null;conflictToken:string|null;state:string;blocked:boolean;storageStatus:string;error:string|null;confirmed:boolean;canConfirm:boolean}>();
const emit=defineEmits<{edit:[value:TranscriptEdits];save:[];confirm:[];retry:[];reload:[];discard:[];reapply:[]}>();
const page=ref(0),pages=computed(()=>Math.max(1,Math.ceil(props.value.length/40))),visible=computed(()=>props.value.slice(page.value*40,(page.value+1)*40));
watch(pages,total=>{page.value=Math.min(page.value,total-1);});
const pending=computed(()=>!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(props.state));
const labels:Record<TranscriptSegment['marks'][number],string>={KEY:'重点',CONCLUSION:'结论',DISPUTE:'争议',GENERATION_FOCUS:'生成关注'};
function edit(index:number,body:string){emit('edit',props.value.map((s,i)=>i===index?{...s,body}:s));}
function mark(index:number,key:TranscriptSegment['marks'][number],checked:boolean){emit('edit',props.value.map((s,i)=>i===index?{...s,marks:checked?[...s.marks,key]:s.marks.filter(m=>m!==key)}:s));}
</script>
<template>
 <section class="card transcript-section" aria-labelledby="transcript-title">
  <header><h2 id="transcript-title">转写正文</h2><span>{{confirmed?'当前版本已确认':'当前版本尚未确认'}}</span></header>
  <p v-if="error" role="alert">{{error}}</p><p v-if="blocked" role="alert">本地恢复记录暂不可用，请保留页面并重新读取。</p>
  <p v-else-if="storageStatus==='INVALID'||storageStatus==='UNAVAILABLE'" role="alert">草稿暂未写入恢复记录，请保持页面打开并重试保存。</p>
  <p v-if="pending" role="status">原操作结果待恢复。重试将保留原请求；后续输入仍保留在本地草稿。</p>
  <div v-if="conflict" class="conflict" role="alert"><h3>服务器已有更新（版本 {{conflictToken}}）</h3><p>下方保留你的草稿。请比较最新内容后选择处理方式。</p><details><summary>查看最新保存内容</summary><p v-for="s in conflict.slice(page*40,(page+1)*40)" :key="s.segmentId" class="text">{{s.body}}<small>{{s.marks.map(m=>labels[m]).join('、')}}</small></p></details><button class="btn" :disabled="busy||pending||blocked" @click="emit('reapply')">将我的修改应用到最新版本</button><button class="btn" :disabled="busy||pending||blocked" @click="emit('discard')">放弃本地修改，读取最新版本</button></div>
  <ol :start="page*40+1"><li v-for="(segment,i) in visible" :key="segment.segmentId"><label :for="'segment-'+segment.segmentId">第 {{page*40+i+1}} 段</label><textarea :id="'segment-'+segment.segmentId" :value="segment.body" :readonly="!editable||blocked" rows="4" @input="edit(page*40+i,($event.target as HTMLTextAreaElement).value)"/><fieldset :disabled="!editable||blocked"><legend>段落标记</legend><label v-for="(label,key) in labels" :key="key"><input type="checkbox" :checked="segment.marks.includes(key)" @change="mark(page*40+i,key,($event.target as HTMLInputElement).checked)"/>{{label}}</label></fieldset></li></ol>
  <nav v-if="pages>1" aria-label="转写段落分页"><button class="btn" :disabled="page===0" @click="page--">上一页</button><span>{{page+1}} / {{pages}}</span><button class="btn" :disabled="page+1>=pages" @click="page++">下一页</button></nav>
  <footer><button v-if="editable" class="btn btn-primary" :disabled="busy||blocked||!!conflict||pending||!['EMPTY','ACKNOWLEDGED'].includes(state)" @click="emit('save')">保存正文与标记</button><button v-if="canConfirm" class="btn" :disabled="busy||blocked||dirty||!!conflict||pending||!['EMPTY','ACKNOWLEDGED'].includes(state)" @click="emit('confirm')">确认当前保存版本</button><button v-if="pending||state==='ACKNOWLEDGED'" class="btn" :disabled="busy||blocked" @click="emit('retry')">恢复原操作结果</button><button class="btn" :disabled="busy" @click="emit('reload')">重新读取</button><button v-if="dirty||state==='CONFLICT'||state==='REJECTED'" class="btn" :disabled="busy||pending||blocked" @click="emit('discard')">放弃本地修改／已拒绝操作</button></footer>
  <p v-if="dirty" class="muted">有尚未保存的修改。确认和生成仅使用服务器已保存的版本。</p>
 </section>
</template>
<style scoped>
.transcript-section{padding:24px;margin-bottom:20px}header,footer,nav{display:flex;align-items:center;gap:12px;flex-wrap:wrap}header{justify-content:space-between}h2{font-size:18px}ol{list-style:none;padding:0}li{padding:16px 0;border-bottom:1px solid var(--border,#ddd)}textarea{display:block;width:100%;box-sizing:border-box;margin:8px 0;padding:12px;resize:vertical;font:inherit;line-height:1.7}fieldset{border:0;padding:0;display:flex;gap:16px;flex-wrap:wrap}fieldset label{display:flex;align-items:center;gap:6px}legend{font-size:13px;color:var(--muted);margin-bottom:8px}footer,nav{margin-top:20px}.conflict{padding:16px;background:#fff5dc}.text{white-space:pre-wrap;overflow-wrap:anywhere}.text small{display:block}button:disabled{opacity:.5;cursor:not-allowed}.muted{color:var(--muted)}@media(max-width:600px){.transcript-section{padding:16px}}
</style>
