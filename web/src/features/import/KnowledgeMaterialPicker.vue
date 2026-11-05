<script setup lang="ts">
import {computed,nextTick,ref,watch} from 'vue';
import Icon from '../../shared/Icon.vue';
import {knowledgeKey,type MaterialItem,type KnowledgeDocument,type MaterialLibrary} from '../../shared/api/materials';
import type {useKnowledgeMaterials} from './useMaterials';
const props=defineProps<{open:boolean;picker:ReturnType<typeof useKnowledgeMaterials>;busy?:boolean;keepOpenWhileSubmitting?:boolean;added:readonly MaterialItem[];error?:string|null}>();
const emit=defineEmits<{close:[];confirm:[documents:readonly KnowledgeDocument[]]}>();
const dialog=ref<HTMLDialogElement>(),issue=ref('');
const rows=computed(()=>props.picker.review.value?props.picker.selected.value:props.picker.documents.value);
const selected=computed(()=>new Set(props.picker.selected.value.map(knowledgeKey)));
const added=computed(()=>new Set(props.added.filter(i=>i.source==='KNOWLEDGE'&&i.sourceLibraryId&&i.sourceDocumentId).map(i=>JSON.stringify([i.sourceLibraryId,i.sourceDocumentId,i.sourceVersion]))));
const scopes=computed<MaterialLibrary[]>(()=>[{id:'',name:'全部知识库'},...props.picker.libraries.value]);
const libraries=computed(()=>new Map(props.picker.libraries.value.map(k=>[k.id,k.name])));
const selection=computed(()=>props.picker.selected.value.filter(d=>!added.value.has(knowledgeKey(d))));
async function act(work:()=>unknown){issue.value='';try{await work();}catch(error){issue.value=error instanceof Error?error.message:'操作失败，请重试。';}}
watch(()=>props.open,async open=>{
 if(!open){dialog.value?.close();return;}
 await nextTick();if(!props.open)return;
 if(!dialog.value?.open)dialog.value?.showModal();
 if(!props.picker.opened.value)await act(props.picker.open);
},{immediate:true});
function close(){
 if(props.busy){if(!props.keepOpenWhileSubmitting)emit('close');return;}
 if(props.picker.close())emit('close');
}
function search(event:Event){void act(()=>props.picker.search((event.target as HTMLInputElement).value));}
function date(value?:string|null){return value?new Date(value).toLocaleDateString('zh-CN'):'--';}
function size(token:string){const bytes=Number(token);return bytes>=1048576?(bytes/1048576).toFixed(1)+' MB':bytes>=1024?Math.round(bytes/1024)+' KB':bytes+' B';}
</script>
<template>
 <dialog ref="dialog" class="mask material-picker-mask" role="presentation" @cancel.prevent="close" @click.self="close">
  <div class="modal lg ac-kb-modal" role="dialog" aria-modal="true" aria-labelledby="material-picker-title">
   <div class="modal-h"><h3 id="material-picker-title" class="m-t">从知识库选择材料</h3><button class="m-x" aria-label="关闭知识库选择" :disabled="busy&&keepOpenWhileSubmitting" @click="close"><Icon name="x" :size="18"/></button></div>
   <div class="modal-b">
    <div class="ac-kb-search"><input class="input" aria-label="搜索知识库或文件" placeholder="搜索知识库或文件" maxlength="128" :disabled="busy" :value="picker.query.value" @input="search"><button class="btn sm ghost" :disabled="busy" @click="act(()=>picker.search(''))">清空搜索</button></div>
    <select class="select ac-kb-mobile" aria-label="知识库范围" :disabled="busy" :value="picker.library.value" @change="act(()=>picker.chooseLibrary(($event.target as HTMLSelectElement).value))"><option value="">全部知识库</option><option v-for="lib in picker.libraries.value" :key="lib.id" :value="lib.id">{{lib.name}}</option></select>
    <div class="ac-kb-columns">
     <aside class="ac-kb-library-pane"><div class="ac-kb-label">知识库</div><div class="kb-scroll">
      <button v-for="lib in scopes" :key="lib.id" class="ac-library" :class="{active:!picker.review.value&&lib.id===picker.library.value}" :disabled="busy" :title="lib.name" @click="act(()=>picker.chooseLibrary(lib.id))"><Icon name="kb" :size="16"/><span><b>{{lib.name}}</b><small v-if="lib.id">更新于 {{date(lib.updatedAt)}}<template v-if="lib.documentCount!==undefined"> · 共 {{lib.documentCount}} 个文档</template></small></span><Icon name="arrowR" :size="12"/></button>
      <span v-if="picker.libraryLoading.value" class="small muted">加载知识库中…</span>
      <button v-if="picker.libraryError.value" class="btn sm" :disabled="busy" @click="act(async()=>{await picker.reloadLibraries();await picker.reloadDocuments();})">重试加载</button>
      <button v-else-if="picker.libraryCursor.value" class="btn sm" :disabled="busy||picker.libraryLoading.value" @click="act(picker.moreLibraries)">加载更多知识库</button>
     </div></aside>
     <section class="ac-kb-file-pane"><div class="ac-kb-label">{{picker.review.value?'已选文件':'文件'}}</div><div class="kb-scroll">
      <label v-for="doc in rows" :key="knowledgeKey(doc)" class="ac-kb-file"><input type="checkbox" :checked="selected.has(knowledgeKey(doc))||added.has(knowledgeKey(doc))" :disabled="busy||added.has(knowledgeKey(doc))" @change="act(()=>picker.toggle(doc))">
       <span class="ac-kb-file-info"><b :title="doc.name+' · 引用版本：'+doc.version">{{doc.name}}</b><small>更新于 {{date(doc.updatedAt)}} · {{size(doc.sizeBytesToken)}} · 来自 {{libraries.get(doc.libraryId)??doc.libraryId}} · 版本 {{doc.version.slice(0,12)}}</small><small v-if="added.has(knowledgeKey(doc))">已添加</small><small v-else-if="picker.query.value&&!picker.review.value">{{doc.name.toLowerCase().includes(picker.query.value.trim().toLowerCase())?'文件名匹配':'知识库名称匹配'}}</small></span>
      </label>
      <div v-if="!picker.review.value&&picker.loading.value" class="empty">正在加载文件…</div>
      <button v-else-if="!picker.review.value&&picker.error.value" class="btn sm" :disabled="busy" @click="act(picker.reloadDocuments)">重试查询</button>
      <div v-else-if="!rows.length" class="empty">{{picker.review.value?'暂无已选文件':picker.query.value?'无搜索结果':'暂无可用文件'}}</div>
      <button v-if="!picker.review.value&&picker.cursor.value" class="btn sm" :disabled="busy||picker.loading.value" @click="act(picker.moreDocuments)">加载更多</button>
     </div></section>
    </div>
    <div class="kb-issues ac-error small" role="alert">{{issue||error||picker.error.value||picker.libraryError.value}}</div>
   </div>
   <div class="modal-f"><div class="ac-kb-footer"><div><span role="status">已选{{selection.length}}个文件</span><button class="btn sm ghost" :disabled="busy" @click="act(()=>picker.setReview(!picker.review.value))">{{picker.review.value?'返回文件列表':'查看已选'}}</button><button class="btn sm ghost" :disabled="busy" @click="act(picker.clear)">清空选择</button></div><div class="flex"><button class="btn" :disabled="busy" @click="close">取消</button><button class="btn primary" :disabled="busy||!selection.length" @click="emit('confirm',selection)">{{busy?'添加中…':'添加所选文件'}}</button></div><div class="small muted ac-kb-note">仅添加所选文件作为会议材料，不扩大知识库或源文件访问权限。</div></div></div>
  </div>
 </dialog>
</template>
<style scoped>
.material-picker-mask{inset:0;width:auto;height:auto;max-width:none;max-height:none;margin:0;padding:0;border:0;color:var(--text);overflow:visible}
.material-picker-mask:not([open]){display:none}.material-picker-mask::backdrop{background:transparent}
.kb-issues{max-height:80px;overflow:auto;margin-top:8px}.kb-scroll{overflow:auto;flex:1;padding:8px;min-height:0}.modal-h h3{margin:0}
</style>
