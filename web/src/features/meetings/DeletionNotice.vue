<script setup lang="ts">
import {computed,ref,watch,nextTick,onUnmounted} from 'vue';import {useApplication} from '../../application';
const app=useApplication(),flow=app.deletionCommands,dialog=ref<HTMLDialogElement>(),localError=ref('');
const original=computed(()=>flow.available.value?flow.command.snapshot.value:null),labels:Record<string,string>={PENDING:'已受理，等待继续清理',RUNNING:'正在清理',FAILED:'清理暂未完成，后台将重试',COMPLETE:'在线数据已清理完成'};
async function run(action:()=>Promise<unknown>|void){localError.value='';try{await action();}catch(cause){localError.value=cause instanceof Error?cause.message:'删除结果暂不可用。';}}
watch(()=>flow.visible.value,async visible=>{if(visible){await nextTick();if(!dialog.value?.open)dialog.value?.showModal();}else dialog.value?.close();},{immediate:true});
watch(()=>[app.session.current.value?.actor.enterpriseId,flow.available.value],()=>{localError.value='';},{flush:'sync'});
let epoch=0,timer:ReturnType<typeof setTimeout>|null=null;
watch([()=>flow.job.value?.deletionTaskId,()=>flow.job.value?.state==='COMPLETE',()=>flow.available.value],()=>{const turn=++epoch;if(timer)clearTimeout(timer);if(!flow.available.value||!flow.job.value||flow.job.value.state==='COMPLETE')return;async function tick(){if(turn!==epoch)return;await run(flow.poll);if(turn===epoch)timer=setTimeout(tick,flow.job.value?.state==='FAILED'?2000:350);}timer=setTimeout(tick,200);},{immediate:true});
onUnmounted(()=>{epoch++;if(timer)clearTimeout(timer);});
</script>
<template>
 <button v-if="flow.blockedMeeting.value&&flow.job.value?.state!=='COMPLETE'&&!flow.visible.value" class="deletion-toggle btn" @click="flow.show()">查看删除进度</button>
 <dialog ref="dialog" class="notice-dialog deletion-dialog" aria-labelledby="deletion-title" @cancel.prevent="flow.hide()">
  <header class="between"><h2 id="deletion-title">{{original?'会议删除进度':'删除会议'}}</h2><button class="btn sm ghost" aria-label="关闭删除状态" @click="flow.hide()">×</button></header>
  <p v-if="localError||flow.error.value||app.deletionScopeError.value" class="banner error mt16" role="alert">{{localError||flow.error.value||app.deletionScopeError.value}}</p>
  <template v-if="flow.available.value">
   <template v-if="!original&&flow.target.value"><p class="mt16">确认删除“{{flow.target.value.name||'未命名会议'}}”？</p><p class="small muted mt12">受理后会议立即不可访问，后台将清理附件、录音、转写、纪要及其历史正文，无法恢复。正在录音或发布的会议不能删除。</p><div class="flex mt20"><button class="btn" @click="flow.hide()">取消</button><button class="btn danger" :disabled="flow.busy.value" @click="run(flow.start)">确认删除</button></div></template>
   <p v-if="original" class="banner info mt16">当前会议已暂停展示。关闭本窗口不会取消已经受理的删除。</p>
   <section v-if="flow.job.value" aria-live="polite" class="mt16"><h3>{{labels[flow.job.value.state]}}</h3><p class="small muted mt12">{{flow.job.value.state==='COMPLETE'?'本系统的受管本地文件和在线正文已清理，仅保留最小删除标识。':'清理尚未完成，系统保留原任务持续重试。'}}</p><p class="small muted mt12">知识库原件、已下载到设备的副本及备份保留不包含在本次在线清理内。</p></section>
   <button v-if="original&&['PREPARED','UNCERTAIN'].includes(flow.command.state.value)" class="btn primary mt16" :disabled="flow.busy.value" @click="run(flow.recover)">查回原删除请求</button>
   <button v-if="original&&['CONFLICT','REJECTED'].includes(flow.command.state.value)" class="btn mt16" :disabled="flow.busy.value" @click="run(flow.dismissRejected)">关闭并重新读取会议</button>
  </template>
 </dialog>
</template>
<style scoped>.deletion-dialog{width:min(560px,calc(100vw - 32px));max-height:calc(100dvh - 48px);overflow:auto}.deletion-dialog h2{font-size:20px}.deletion-toggle{position:fixed;right:24px;bottom:78px;z-index:80;background:var(--navy);color:white}.deletion-dialog .flex{justify-content:flex-end}</style>
