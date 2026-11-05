<script setup lang="ts">
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue';
import Icon from '../../shared/Icon.vue';
import type {SpeechCorrectionItem} from '../../shared/api/transcript-corrections';
import {correctionRange} from './correction-range';

const props=defineProps<{item:SpeechCorrectionItem;body:string;anchor:HTMLElement;pinned:boolean;disabled:boolean}>();
const emit=defineEmits<{close:[focus:boolean];enter:[];leave:[];decide:[decision:'ACCEPT'|'REJECT'];undo:[];edit:[text:string];feedback:[]}>();
const panel=ref<HTMLElement>(),left=ref(0),top=ref(0);
const change=computed(()=>props.item.range??correctionRange(props.item.source,props.item.replacement));
const status={PENDING:'待确认',ACCEPTED:'已接受',REJECTED:'已拒绝',STALE:'上下文已变化',UNDONE:'已撤销'};
const applicable=computed(()=>props.item.source===props.body&&props.item.status==='PENDING');
function place(){
 const el=panel.value;
 if(!el)return;
 const anchor=props.anchor.getBoundingClientRect();
 if(!props.anchor.isConnected||anchor.bottom<0||anchor.top>innerHeight){emit('close',false);return;}
 const bounds=el.getBoundingClientRect();
 left.value=Math.max(8,Math.min(anchor.left,innerWidth-bounds.width-8));
 const below=anchor.bottom+8;
 top.value=Math.max(8,Math.min(below+bounds.height<=innerHeight-8?below:anchor.top-bounds.height-8,innerHeight-bounds.height-8));
}
function outside(event:PointerEvent){
 if(event.target instanceof Node&&!panel.value?.contains(event.target)&&!props.anchor.contains(event.target))emit('close',false);
}
function escape(event:KeyboardEvent){if(event.key==='Escape'){event.preventDefault();emit('close',true);}}
let observer:ResizeObserver|undefined;
watch(()=>props.anchor,()=>nextTick(place));
watch(()=>props.pinned,value=>{if(value)panel.value?.focus({preventScroll:true});},{flush:'post'});
onMounted(()=>{
 place();
 if(props.pinned)panel.value?.focus({preventScroll:true});
 observer=new ResizeObserver(place);observer.observe(panel.value!);
 window.addEventListener('resize',place);window.addEventListener('scroll',place,true);
 document.addEventListener('pointerdown',outside,true);document.addEventListener('keydown',escape);
});
onUnmounted(()=>{
 observer?.disconnect();window.removeEventListener('resize',place);window.removeEventListener('scroll',place,true);
 document.removeEventListener('pointerdown',outside,true);document.removeEventListener('keydown',escape);
});
</script>
<template>
 <Teleport to="body"><section ref="panel" class="aci-correction-pop" role="dialog" aria-label="纠错详情" tabindex="-1"
  :style="{left:left+'px',top:top+'px'}" @pointerenter="emit('enter')" @pointerleave="emit('leave')" @focusin="emit('enter')" @focusout="emit('leave')">
  <div class="aci-correction-pop-head"><b>纠错建议</b><span class="small muted">{{status[item.status]}}</span>
   <button type="button" aria-label="关闭纠错详情" @click="emit('close',true)"><Icon name="x" :size="14"/></button></div>
  <div class="aci-correction-values"><del>{{change.original||'（插入）'}}</del> → <ins>{{(item.range?.replacement??('suggestion' in change?change.suggestion:''))||'（删除）'}}</ins></div>
  <div class="aci-correction-source">来源：本段语音增强</div>
  <p v-if="item.status==='STALE'||item.status==='PENDING'&&!applicable" class="small ac-error">正文已变化，请核对当前片段。</p>
  <div class="aci-correction-actions">
   <template v-if="item.status==='PENDING'"><button class="btn sm primary" :disabled="disabled||!applicable||!item.canAccept" @click="emit('decide','ACCEPT')">接受本段建议</button>
   <button class="btn sm" :disabled="disabled||!item.canReject" @click="emit('decide','REJECT')">拒绝本段建议</button></template>
   <button v-if="item.canUndo" class="btn sm" :disabled="disabled" @click="emit('undo')">撤销接受</button>
  </div>
  <details class="aci-correction-more"><summary>更多操作</summary>
   <p v-if="item.createdSourceRevisionId" class="small muted">来源版本 {{item.createdSourceRevisionId}}</p>
   <div class="flex wrap mt8">
    <button class="btn sm" :disabled="disabled" @click="emit('edit',applicable?item.replacement:body)">{{applicable?'自定义修订':'编辑当前正文'}}</button>
    <button v-if="item.status==='ACCEPTED'&&!item.canUndo&&body===item.replacement" class="btn sm" :disabled="disabled" @click="emit('edit',item.source)">用原文编辑本段</button>
    <button class="btn sm ghost" :disabled="disabled" @click="emit('feedback')">提交语料反馈</button>
   </div>
  </details>
 </section></Teleport>
</template>
