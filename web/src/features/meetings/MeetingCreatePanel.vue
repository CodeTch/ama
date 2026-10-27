<script setup lang="ts">
import {computed} from 'vue';
import type {CommandState} from '../../shared/composables/useCommand';
const props=defineProps<{state:CommandState;busy:boolean;blocked:boolean;storageStatus:string;cleanupFailed:boolean;message:string|null;discovery:'FOUND'|'ABSENT'|null;discovering:boolean;discoveryError:boolean}>();
defineEmits<{submit:[];lookup:[];recover:[];session:[];close:[]}>();
const terminal=computed(()=>['CONFLICT','DENIED','REJECTED'].includes(props.state));
const label=computed(()=>props.state==='ACKNOWLEDGED'?'打开已创建会议':props.state==='UNCERTAIN'||props.state==='PREPARED'?'重试原请求':'创建并打开');
</script>
<template>
 <section class="card create-panel" aria-labelledby="meeting-create-title">
  <h2 id="meeting-create-title">新建会议</h2><p>先创建会议，再填写会议信息和参会人员。</p>
  <p v-if="busy" role="status">正在处理，请稍候…</p>
  <p v-if="blocked" role="alert">恢复记录暂无法读取。请先恢复原请求，再创建其他会议。</p>
  <p v-else-if="state==='UNCERTAIN'" role="status">尚未确认创建结果。重试会使用原请求，不会重新生成创建请求。</p>
  <p v-else-if="state==='ACKNOWLEDGED'">已确认创建，可继续打开会议。</p>
  <p v-if="terminal" role="alert">原请求被拒绝。请重新检查登录和访问权限后恢复原请求。</p>
  <p v-if="message" role="alert">{{message}}</p>
  <p v-if="cleanupFailed" role="alert">恢复记录尚未清理，请保留当前页面并重试打开已创建会议。</p>
  <p v-if="storageStatus==='UNAVAILABLE'" class="storage-note">当前浏览器无法保存恢复记录，刷新或关闭页面可能无法恢复本次操作。</p>
  <p v-if="discovery==='FOUND'">查到原请求关联的会议。请重试原请求，取得确认后打开。</p>
  <p v-else-if="discovery==='ABSENT'">暂未查到原请求关联，请继续保留并重试原请求。</p>
  <p v-if="discoveryError" role="alert">暂时无法查询创建结果，请稍后重试。</p>
  <div class="create-actions">
   <button class="btn primary" :disabled="busy||blocked||terminal" @click="$emit('submit')">{{label}}</button>
   <button v-if="state==='UNCERTAIN'" class="btn" :disabled="busy||discovering" @click="$emit('lookup')">{{discovering?'正在查询…':'查询原请求'}}</button>
   <button v-if="blocked" class="btn" :disabled="busy" @click="$emit('recover')">重新读取恢复记录</button>
   <button v-if="terminal" class="btn" :disabled="busy" @click="$emit('session')">重新检查登录</button>
   <button v-if="state==='EMPTY'&&!blocked" class="btn" :disabled="busy" @click="$emit('close')">暂不创建</button>
  </div>
 </section>
</template>
<style scoped>
.create-panel{padding:24px;margin-bottom:24px}.create-panel h2{font-size:18px;margin:0 0 12px}.create-panel p{line-height:1.6;overflow-wrap:anywhere}.storage-note{color:var(--muted)}.create-actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:20px}button:disabled{opacity:.55;cursor:default}@media(max-width:600px){.create-panel{padding:18px}}
</style>
