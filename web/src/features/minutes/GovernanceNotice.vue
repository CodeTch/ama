<script setup lang="ts">
import {computed,onUnmounted,ref,watch,nextTick} from 'vue';import {useApplication} from '../../application';import {validGovernanceReason,type GovernanceAction} from '../../shared/api/governance';
const app=useApplication(),flow=app.governanceCommands,dialog=ref<HTMLDialogElement>(),reason=ref(''),action=ref<GovernanceAction>('WITHDRAW'),localError=ref('');
const stateLabels:Record<string,string>={PENDING:'已受理，正在处理',RECONCILING:'外部结果待确认，持续核对中',SUCCEEDED:'已完成',CONFLICT:'文档已进入其他终止状态'};
const original=computed(()=>flow.available.value?flow.command.snapshot.value:null),canSend=computed(()=>flow.available.value&&!flow.busy.value&&!original.value&&!!flow.target.value&&validGovernanceReason(reason.value));
let epoch=0,timer:ReturnType<typeof setTimeout>|null=null;
async function run(fn:()=>Promise<unknown>){localError.value='';try{await fn();}catch(cause){localError.value=cause instanceof Error?cause.message:'操作暂未完成，请查回原请求。';}}
function send(){const target=flow.target.value;if(target&&canSend.value)void run(()=>flow.start(target.publicationId,target.revisionToken,action.value,reason.value));}
watch(()=>flow.visible.value,async visible=>{if(visible){await nextTick();if(!dialog.value?.open)dialog.value?.showModal();if(flow.available.value)void run(flow.loadHistory);}else dialog.value?.close();},{immediate:true});
watch(()=>[app.session.current.value?.actor.enterpriseId,app.meetingScope.value,flow.available.value],()=>{reason.value='';localError.value='';},{flush:'sync'});
watch(()=>[flow.job.value?.governanceJobId,flow.job.value?.state,flow.available.value],()=>{const turn=++epoch;if(timer)clearTimeout(timer);if(!flow.available.value||!flow.job.value||!['PENDING','RECONCILING'].includes(flow.job.value.state))return;async function tick(){if(turn!==epoch)return;await run(flow.poll);if(turn===epoch)timer=setTimeout(tick,2000);}timer=setTimeout(tick,1500);},{immediate:true});
onUnmounted(()=>{epoch++;if(timer)clearTimeout(timer);});
</script>
<template>
 <button v-if="flow.blockedMeeting.value&&!flow.visible.value" class="governance-notice-toggle btn" @click="flow.show()">查看撤回 / 废止进度</button>
 <dialog ref="dialog" class="notice-dialog governance-dialog" aria-labelledby="governance-title" @cancel.prevent="flow.hide()">
  <div class="between"><h2 id="governance-title">已发布文档治理</h2><button class="btn sm ghost" aria-label="关闭治理状态" @click="flow.hide()">×</button></div>
  <p class="small muted mt12">撤回或废止后，会议内容、来源、下载和引用将停止开放，不能重新发布或恢复。已下载到设备的文件无法远程收回。</p>
  <p v-if="flow.blockedMeeting.value" class="banner info mt16" role="status">当前会议内容已暂停展示。外部结果未知时仍保持不可访问。</p>
  <p v-if="localError||flow.error.value" role="alert" class="mt12">{{localError||flow.error.value}}</p>
  <template v-if="flow.available.value">
   <form v-if="!original&&!flow.history.value.length" class="mt16" @submit.prevent="send"><fieldset :disabled="flow.busy.value"><legend>操作类型</legend><label><input v-model="action" type="radio" value="WITHDRAW"/> 撤回</label><label class="ml12"><input v-model="action" type="radio" value="OBSOLETE"/> 废止</label></fieldset><label class="form-label mt16" for="governance-reason">理由</label><textarea id="governance-reason" v-model="reason" class="input" rows="4" maxlength="4096" :disabled="flow.busy.value" placeholder="填写本次操作的理由（最多 2048 字）"/><div class="flex mt16"><button type="button" class="btn" @click="flow.hide()">暂不操作</button><button type="submit" class="btn danger" :disabled="!canSend">确认{{action==='WITHDRAW'?'撤回':'废止'}}</button></div></form>
   <section v-if="flow.job.value" class="mt16" aria-live="polite"><h3>{{flow.job.value.action==='WITHDRAW'?'撤回':'废止'}} · {{stateLabels[flow.job.value.state]}}</h3><p class="mt12 governance-reason">{{flow.job.value.reason}}</p><p v-if="flow.job.value.state==='RECONCILING'" class="small muted mt12">请求已持久保存，后台会用原请求持续查回结果。关闭页面不会取消处理。</p><button class="btn mt12" :disabled="flow.busy.value" @click="run(flow.poll)">刷新处理状态</button></section>
   <button v-if="original&&['PREPARED','UNCERTAIN'].includes(flow.command.state.value)" class="btn primary mt16" :disabled="flow.busy.value" @click="run(flow.recover)">查回原请求</button>
   <details class="mt16"><summary @click="run(flow.loadHistory)">治理历史</summary><p v-if="!flow.history.value.length" class="small muted mt12">暂无治理操作记录。</p><article v-for="row in flow.history.value" :key="row.governanceJobId" class="governance-history"><b>{{row.action==='WITHDRAW'?'撤回':'废止'}} · {{stateLabels[row.state]}}</b><p class="governance-reason mt8">{{row.reason}}</p><time class="small muted">{{row.createdAt}}</time></article></details>
  </template>
 </dialog>
</template>
<style scoped>.governance-dialog{width:min(580px,calc(100vw - 32px));max-height:calc(100dvh - 48px);overflow:auto}.governance-dialog h2{font-size:20px}.governance-dialog fieldset{border:0;padding:0}.governance-dialog legend{margin-bottom:10px;font-weight:600}.governance-dialog textarea{width:100%;margin-top:8px}.governance-history{border-top:1px solid var(--border);padding:14px 0}.governance-reason{white-space:pre-wrap;overflow-wrap:anywhere}.governance-notice-toggle{position:fixed;right:24px;bottom:24px;z-index:80;background:var(--navy);color:white}.ml12{margin-left:12px}</style>
