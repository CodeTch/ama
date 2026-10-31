<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue';
import type {TranscriptReplacementSearch} from '../../shared/api/transcript-operations';
const props=defineProps<{matches:(Omit<TranscriptReplacementSearch,'occurrences'>&{occurrences:ReadonlyArray<TranscriptReplacementSearch['occurrences'][number]>})|null;segments:ReadonlyArray<{segmentId:string;body:string}>;pending:boolean;busy:boolean;searching:boolean;canMutate:boolean;state:string;original:{find?:string;replacement?:string;occurrenceIds?:ReadonlyArray<string>}|null;error:string|null}>();
const emit=defineEmits<{search:[query:string];replace:[replacement:string,ids:string[]];undo:[];retry:[];discard:[];close:[]}>();
const dialog=ref<HTMLDialogElement>(),query=ref(''),replacement=ref(''),selected=ref<string[]>([]),page=ref(0),composing=ref(false);
const locked=computed(()=>props.pending||props.busy),matches=computed(()=>props.matches?.occurrences??[]),pages=computed(()=>Math.max(1,Math.ceil(matches.value.length/50)));
const bodies=computed(()=>new Map(props.segments.map(s=>[s.segmentId,s.body])));
const visible=computed(()=>matches.value.slice(page.value*50,page.value*50+50).map(item=>{const body=Array.from(bodies.value.get(item.segmentId)??'');return {...item,before:body.slice(Math.max(0,item.startScalar-24),item.startScalar).join(''),hit:body.slice(item.startScalar,item.endScalar).join(''),after:body.slice(item.endScalar,item.endScalar+24).join('')};}));
watch(()=>props.matches,()=>{selected.value=[];page.value=0;});
function close(event?:Event){event?.preventDefault();if(!locked.value)emit('close');}
function search(){if(!locked.value&&!props.searching&&props.canMutate&&!composing.value&&query.value)emit('search',query.value);}
onMounted(()=>dialog.value?.showModal());
</script>
<template><dialog ref="dialog" class="modal lg transcript-operation-dialog" aria-labelledby="manual-tools-title" @cancel="close">
 <header class="modal-h"><h3 id="manual-tools-title" class="m-t">纠错建议与候选</h3><button class="m-x" aria-label="关闭" :disabled="locked" @click="close()">×</button></header>
 <div class="modal-b"><p class="small muted">纠错建议服务尚未接入。可核对已保存工作稿中的具体位置后进行人工修订。</p><h4>按位置替换</h4>
 <p v-if="!canMutate&&!pending" role="status">请先保存或处理当前修改，再修订已保存工作稿。</p>
 <form @submit.prevent="search"><div class="field"><label for="manual-find">查找完整文本</label><input id="manual-find" v-model="query" class="input" aria-label="查找完整文本" maxlength="1024" :readonly="locked" :disabled="!canMutate&&!pending" @compositionstart="composing=true" @compositionend="composing=false"/></div><button class="btn" :disabled="locked||searching||!canMutate||composing||!query">{{searching?'查找中…':'查找全部位置'}}</button></form>
 <div v-if="pending&&original" class="original" role="status"><p v-if="original.find!==undefined">原查找：{{original.find}}</p><p v-if="original.replacement!==undefined">原替换为：{{original.replacement||'（删除匹配文本）'}}</p><p v-if="original.occurrenceIds">选中 {{original.occurrenceIds.length}} 处</p><p v-else>原操作：撤销所选工作版本</p><p>保留原请求，恢复完成后再进行其他修改。</p></div>
 <div v-if="matches.length&&!pending"><p>共 {{matches.length}} 处，已选 {{selected.length}} 处</p><button class="btn sm" :disabled="locked||!canMutate" @click="selected=matches.map(x=>x.occurrenceId)">选择全部位置</button> <button class="btn sm" :disabled="locked" @click="selected=[]">清空选择</button>
 <ul class="occurrences"><li v-for="item in visible" :key="item.occurrenceId" :data-occurrence="item.occurrenceId"><label><input v-model="selected" type="checkbox" :value="item.occurrenceId" :disabled="locked||!canMutate"/><span>{{item.before}}<mark>{{item.hit}}</mark>{{item.after}}</span></label></li></ul>
 <nav aria-label="匹配位置分页"><button class="btn sm" :disabled="page===0" @click="page--">上一页</button><span> {{page+1}} / {{pages}} </span><button class="btn sm" :disabled="page+1>=pages" @click="page++">下一页</button></nav></div>
 <p v-else-if="props.matches&&!pending" role="status">全文未找到匹配位置。</p>
 <div class="field"><label for="manual-replacement">替换文本（可留空）</label><textarea id="manual-replacement" v-model="replacement" class="input" aria-label="替换文本（可留空）" maxlength="65536" :readonly="locked" :disabled="!canMutate&&!pending" @compositionstart="composing=true" @compositionend="composing=false"/></div>
 <button class="btn primary" :disabled="locked||!canMutate||composing||!selected.length" @click="emit('replace',replacement,[...selected])">替换选中位置</button>
 <h4>撤销当前工作版本</h4><p class="small muted">恢复当前工作版本之前的已保存内容，并创建新的工作版本。原始留档保留。</p><button class="btn" :disabled="locked||!canMutate||composing" @click="emit('undo')">撤销当前工作版本</button>
 <p v-if="error" role="alert">{{error}}</p>
 </div><footer class="modal-f"><button v-if="pending&&['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(state)" class="btn primary" :disabled="busy||composing" @click="emit('retry')">{{state==='ACKNOWLEDGED'?'重新核实已受理修订':'重试原修订请求'}}</button><button v-if="pending&&['CONFLICT','REJECTED'].includes(state)" class="btn" :disabled="busy" @click="emit('discard')">放弃已拒绝的修订请求</button><button class="btn" :disabled="locked" @click="close()">关闭</button></footer>
</dialog></template>
<style scoped>.transcript-operation-dialog{border:0;padding:0;margin:auto;color:var(--text)}.transcript-operation-dialog:not([open]){display:none}.transcript-operation-dialog::backdrop{background:rgba(17,24,73,.42)}.modal-b{max-height:70vh;overflow:auto}h4{margin:20px 0 12px}.input{width:100%}.occurrences{list-style:none;padding:0}.occurrences li{padding:8px 0}.occurrences label{display:flex;gap:10px;overflow-wrap:anywhere;white-space:pre-wrap}.original{padding:12px;background:#fff5dc;margin:12px 0;overflow-wrap:anywhere;white-space:pre-wrap}[role=alert]{color:#9a3412}</style>
