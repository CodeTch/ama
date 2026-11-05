<script setup lang="ts">
import Icon from '../../shared/Icon.vue';
defineProps<{rows:readonly {id:string;index:number;elapsedMs:number;reason?:string;incomplete:boolean}[]}>();
function clock(ms:number){
 const seconds=Math.floor(ms/1000);
 return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');
}
</script>
<template><div class="acr-saved-parts" aria-label="已记录录音段">
 <span class="acr-parts-caption">已保存录音 · {{rows.length}}个文件</span>
 <div class="acr-saved-part-files"><div v-for="part in rows" :key="part.id" class="acr-saved-part">
  <Icon name="file" :size="12"/><span>第{{part.index}}段 · 录音片段 {{String(part.index).padStart(2,'0')}}.wav</span>
  <time>{{clock(part.elapsedMs)}}</time><small>{{part.reason==='SYSTEM'?'系统中断':part.reason==='MANUAL'?'中断保存':part.reason==='FINISHED'?'结束保存':'结束原因未知'}}{{part.incomplete?' · 尾段完整性未知':''}}</small>
 </div></div>
</div></template>
