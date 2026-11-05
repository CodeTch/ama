<script setup lang="ts">
import {computed} from 'vue';
import type {SpeechCorrectionItem} from '../../shared/api/transcript-corrections';
import TranscriptMatch from './TranscriptMatch.vue';
import {correctionRange} from './correction-range';

const props=defineProps<{body:string;items?:readonly SpeechCorrectionItem[];search:RegExp|null;openId?:string}>();
const emit=defineEmits<{show:[item:SpeechCorrectionItem,anchor:HTMLElement,pinned:boolean];leave:[]}>();
const status={PENDING:'待确认',ACCEPTED:'已接受',REJECTED:'已拒绝',STALE:'上下文已变化',UNDONE:'已撤销'};
const inline=computed(()=>{
 const item=props.items?.find(i=>i.status==='ACCEPTED'?i.replacement===props.body:i.status!=='STALE'&&i.source===props.body);
 if(!item)return null;
 const change=correctionRange(item.source,item.replacement);
 if(props.search){
  // Keep a full text match readable when a suggested edit splits it across different controls.
  const changed=item.status==='ACCEPTED'?change.suggestion:change.original;
  const boundaries=[change.prefix.length,change.prefix.length+changed.length];
  for(const match of props.body.matchAll(new RegExp(props.search,'giu'))){
   if(boundaries.some(boundary=>match.index<boundary&&boundary<match.index+match[0].length))return null;
  }
 }
 return {item,change};
});
const other=computed(()=>props.items?.filter(item=>item.id!==inline.value?.item.id));
function open(item:SpeechCorrectionItem,event:Event,pinned=false){emit('show',item,event.currentTarget as HTMLElement,pinned);}
</script>
<template><div class="s-text"><template v-if="inline"><TranscriptMatch :text="inline.change.prefix" :search="search"/><button
 :id="'issue-'+inline.item.id" type="button" class="aci-inline-issue inline-suggestion" aria-haspopup="dialog"
 :aria-label="'纠错：'+status[inline.item.status]+'，'+inline.change.original+' → '+inline.change.suggestion"
 :aria-expanded="openId===inline.item.id" :class="inline.item.status==='PENDING'?'is-pending':'is-handled'"
 @pointerenter="open(inline.item,$event)" @pointerleave="emit('leave')" @focus="open(inline.item,$event)" @blur="emit('leave')" @click="open(inline.item,$event,true)">
 <template v-if="inline.item.status==='REJECTED'||inline.item.status==='UNDONE'"><TranscriptMatch :text="inline.change.original||'（原文）'" :search="search"/></template><template v-else><del><TranscriptMatch :text="inline.change.original" :search="search"/></del><ins><TranscriptMatch :text="inline.change.suggestion||'（删除）'" :search="search"/></ins></template></button><TranscriptMatch :text="inline.change.suffix" :search="search"/></template><TranscriptMatch v-else-if="search" :text="body" :search="search"/><template v-else>{{body}}</template></div>
 <div v-if="other?.length" class="aci-unlocated-issues"><button v-for="item in other" :id="'issue-'+item.id" :key="item.id" type="button"
 class="aci-inline-issue" :class="item.status==='PENDING'?'is-pending':'is-handled'" aria-haspopup="dialog" :aria-expanded="openId===item.id"
 @pointerenter="open(item,$event)" @pointerleave="emit('leave')" @focus="open(item,$event)" @blur="emit('leave')" @click="open(item,$event,true)">{{status[item.status]}} · 核对本段建议</button></div></template>
