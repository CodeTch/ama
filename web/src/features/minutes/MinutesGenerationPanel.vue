<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue';
import {useApplication} from '../../application';
import {useMinutesGeneration} from './useMinutesGeneration';
import {ApiError} from '../../shared/api/client';
const props=defineProps<{meetingId:string}>(),{session,minutesGeneration,workspace,journals,generationScopeReady,meetingScope}=useApplication();
const actor=()=>session.current.value?.actor.enterpriseId??null,eligible=()=>generationScopeReady.value&&meetingScope.value===props.meetingId;
const state=useMinutesGeneration({...minutesGeneration,authorize:async(id,signal)=>{const nav=await workspace.navigation(id,signal);if(nav.relation!=='OWNER'||nav.navigationState!=='CHECK_PRODUCER')throw new ApiError('http',403,'当前身份或会议状态不允许生成。');return nav;}},actor,()=>props.meetingId,eligible,journals);
const selection=ref(''),localError=ref<string|null>(null),message=computed(()=>localError.value??(state.error.value instanceof Error?state.error.value.message:null));
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{selection.value='';localError.value=null;if(actor()&&eligible())void state.reload();},{immediate:true,flush:'sync'}),stopLogout=session.onLogout(state.logout);
const pending=computed(()=>!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(state.command.state.value));
const reasons:Record<string,string>={TRANSCRIPT_CONFIRMATION_REQUIRED:'请先确认已保存转写',SETTINGS_REQUIRED:'请先选择初始模板与设置',MINUTES_COMBINED_NOT_INSTALLED:'纪要生成服务尚不可用',GENERATION_REQUIRED:'当前内容需要重新生成',PUBLISHED:'会议已经发布'};
function discard(){try{localError.value=null;state.discardRejected();void state.reload();}catch(cause){localError.value=cause instanceof Error?cause.message:'原操作暂无法清理。';}}
onUnmounted(()=>{stop();stopLogout();state.dispose();});
</script>
<template>
 <section class="card generation-panel"><h2>生成纪要</h2><p v-if="message" role="alert">{{message}}</p><p v-if="state.loading.value" role="status">正在读取生成条件…</p>
  <p v-if="pending" role="status">原请求结果待确认。恢复会重试原请求，不会重新创建生成任务。</p>
  <template v-if="state.current.value"><p v-if="state.current.value.saved.templateRevisionId">已选择模板版本：{{state.current.value.saved.templateRevisionId}}</p><div v-else><button class="btn" :disabled="state.loading.value||state.busy.value||pending" @click="state.loadTemplates(false)">读取可选模板</button><label v-if="state.templates.value">初始模板<select v-model="selection" :disabled="state.busy.value||pending"><option value="">请选择模板</option><option v-for="item in state.templates.value.items" :key="item.revisionId" :value="item.revisionId" :disabled="!item.selectable">{{item.name}}{{item.default?'（默认）':''}}</option></select></label><button v-if="state.templates.value?.nextCursor" class="btn" :disabled="state.loading.value||pending" @click="state.loadTemplates(true)">下一页模板</button><button class="btn" :disabled="!selection||state.busy.value||pending||state.blocked.value" @click="state.initialize(selection)">保存初始模板与设置</button></div>
   <p v-if="state.current.value.eligible">当前服务器检查允许生成。</p><ul v-else><li v-for="reason in state.current.value.reasons" :key="reason">{{reasons[reason]??reason}}</li></ul><p v-if="state.current.value.activeTaskIds.length">服务器当前观察到 {{state.current.value.activeTaskIds.length}} 个活动任务。</p>
   <button class="btn btn-primary" :disabled="!state.current.value.eligible||state.busy.value||pending||state.blocked.value||!['EMPTY','ACKNOWLEDGED'].includes(state.command.state.value)" @click="state.generate">生成完整纪要候选</button>
  </template>
  <p v-if="state.command.ack.value?.result.kind==='GENERATION_TASK'" role="status">生成任务已受理：{{state.command.ack.value.result.taskId}}。任务完成后再查看并采用候选。</p>
  <p v-else-if="state.command.ack.value?.result.kind==='INITIAL_MINUTES_SETTINGS'" role="status">初始设置操作已收到回执，请以重新读取的当前条件为准。</p>
  <button v-if="pending" class="btn" :disabled="state.busy.value||state.blocked.value" @click="state.retry">恢复原请求</button><button v-if="['CONFLICT','REJECTED'].includes(state.command.state.value)" class="btn" :disabled="state.busy.value" @click="discard">清除已拒绝请求并重新读取</button><button class="btn" :disabled="state.loading.value||state.busy.value" @click="state.reload">刷新生成条件</button>
 </section>
</template>
<style scoped>.generation-panel{padding:24px;margin-bottom:20px}.generation-panel h2{font-size:18px}.generation-panel .btn{margin:8px 8px 0 0}label{display:block;margin:16px 0}select{display:block;width:100%;max-width:500px;margin-top:8px;padding:10px;font:inherit}p{overflow-wrap:anywhere}button:disabled{opacity:.5}</style>
