<script setup lang="ts">
import {computed,ref} from 'vue';
import {confirmationCheckPage,type FinalCheck,type FinalCheckTarget} from './final-confirmation-checks';
const props=defineProps<{checks:FinalCheck[];busy:boolean}>();
const emit=defineEmits<{process:[target:FinalCheckTarget];retry:[]}>();
const pages=ref<Record<string,number>>({});
const page=(check:FinalCheck)=>confirmationCheckPage(pages.value[check.id],check.actions.length);
const failed=computed(()=>props.checks.filter(c=>c.status!=='passed').length);
</script>
<template>
 <details class="card ac-confirm-check mt16" :open="failed>0" aria-label="最终确认检查">
  <summary class="card-h"><span class="card-t">确认检查</span><span class="small muted">{{failed?failed+' 类条件待处理':'全部检查已通过'}}</span></summary>
  <div class="card-p"><div class="between mb12"><p class="small muted">按当前已保存版本核对，最终确认时将再次检查。</p><button class="btn sm" :disabled="busy" @click="emit('retry')">重新检查</button></div>
   <ul class="final-check-list"><li v-for="check in checks" :key="check.id" :data-check="check.id" :class="{failed:check.status==='failed'}"><div class="between"><strong>{{check.label}}</strong><span :class="['tag',check.status==='passed'?'green':check.status==='failed'?'orange':'']">{{check.status==='passed'?'通过':check.status==='failed'?'未通过':'待核实'}}</span></div><p class="small" :class="check.status==='passed'?'muted':''">{{check.reason}}</p>
    <ul v-if="check.actions.length"><li v-for="(action,index) in check.actions.slice(page(check)*20,(page(check)+1)*20)" :key="index" class="final-check-action"><span v-if="action.label!=='去处理'">{{action.label}}</span><button class="btn sm" :disabled="busy" :aria-label="'去处理：'+check.label+(action.label==='去处理'?'':' · '+action.label)" @click="emit('process',action.target)">去处理</button></li></ul>
    <div v-if="check.actions.length>20" class="flex"><button class="btn sm" :disabled="!page(check)" @click="pages[check.id]=page(check)-1">上一页检查项</button><button class="btn sm" :disabled="(page(check)+1)*20>=check.actions.length" @click="pages[check.id]=page(check)+1">下一页检查项</button></div>
   </li></ul>
  </div>
 </details>
</template>
<style scoped>
summary{cursor:pointer;display:flex;justify-content:space-between;gap:16px}.final-check-list{list-style:none;padding:0;margin:0;display:block}.final-check-list>li{padding:12px 0;border-bottom:1px solid var(--border)}.final-check-list>li.failed{color:var(--red)}.final-check-list p{margin:8px 0;line-height:1.7}.final-check-list ul{list-style:none;padding:0}.final-check-action{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:8px}.final-check-action>span{overflow-wrap:anywhere;min-width:0}.final-check-action button{flex-shrink:0}
</style>
