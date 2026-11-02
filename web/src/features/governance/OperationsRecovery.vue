<script setup lang="ts">
import {computed,ref} from 'vue';import {RouterLink} from 'vue-router';import {useApplication} from '../../application';
const {operationsCommands:ops,session}=useApplication(),error=ref('');
async function recheck(){error.value='';try{await session.refresh();}catch(cause){error.value=cause instanceof Error?cause.message:'请重新登录企业账号。';}}
const originalMail=computed(()=>ops.command.snapshot.value?.path.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/mail$/)?.[1]??null);
async function retry(){error.value='';try{await ops.retry();}catch(cause){error.value=cause instanceof Error?cause.message:'原请求尚未确认。';}}
function discard(){try{ops.discardRejected();error.value='';}catch(cause){error.value=cause instanceof Error?cause.message:'请保留原请求。';}}
</script>
<template><section v-if="ops.pending.value" class="banner info operations-recovery" role="status"><div><b>原操作尚待核实</b><p>请保留原请求。恢复会使用原业务键和完整内容；邮件投递结果未知时由后台查询。</p><span>状态：{{ops.command.state.value}}</span></div><button class="btn" :disabled="!ops.available.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(ops.command.state.value)" @click="retry">查回并恢复原操作</button><button v-if="['REJECTED','CONFLICT'].includes(ops.command.state.value)" class="btn" @click="discard">关闭已拒绝请求</button><button v-if="ops.command.state.value==='DENIED'||!ops.available.value" class="btn" @click="recheck">重新检查登录</button><RouterLink v-if="originalMail" class="btn" :to="{path:'/operations',query:{meetingId:originalMail}}">查看原邮件记录</RouterLink><p v-if="error" role="alert">{{error}}</p></section></template>
<style scoped>.operations-recovery{flex-wrap:wrap;align-items:center}.operations-recovery p{margin:6px 0;max-width:800px}</style>
