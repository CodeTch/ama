<script setup lang="ts">
import {computed,onMounted,ref} from 'vue';
import Icon from '../../shared/Icon.vue';
import {useApplication} from '../../application';
const props=defineProps<{meetingId:string;revisionToken:string;segmentId:string;source:string;replacement:string;initialComment?:string;initialType?:string;initialNote?:string}>();
const emit=defineEmits<{close:[]}>();
const {operationsCommands:ops}=useApplication(),dialog=ref<HTMLDialogElement>();
const types=[['PERSON','人名纠错'],['DEPARTMENT','部门纠错'],['PRODUCT','产品纠错'],['FINANCIAL_TERM','金融术语纠错'],['PRODUCT_ALIAS','产品别名'],['TERM_PRONUNCIATION','术语读音']];
const feedbackType=ref(props.initialType??'PERSON'),original=ref(props.source),suggested=ref(props.replacement),note=ref(props.initialNote??props.initialComment??''),composing=ref(false),error=ref(''),busy=ref(false),submitted=ref(false);
const locked=computed(()=>busy.value||ops.pending.value);
function close(event?:Event){event?.preventDefault();if(!busy.value)emit('close');}
async function submit(){
 if(locked.value||composing.value||!original.value.trim()||!suggested.value.trim())return;
 busy.value=true;error.value='';
 try{await ops.submit(`/api/v1/meetings/${props.meetingId}/feedback`,{sourceRevisionToken:props.revisionToken,segmentIds:[props.segmentId],feedbackType:feedbackType.value,originalValue:original.value,suggestedValue:suggested.value,note:note.value});submitted.value=true;}
 catch(cause){error.value=cause instanceof Error?cause.message:'反馈结果尚未确认，请恢复原请求。';}
 finally{busy.value=false;}
}
async function recover(){
 if(busy.value)return;
 busy.value=true;error.value='';
 try{await ops.retry();submitted.value=true;}catch(cause){error.value=cause instanceof Error?cause.message:'反馈结果尚未确认。';}finally{busy.value=false;}
}
function discard(){try{ops.discardRejected();error.value='';}catch(cause){error.value=cause instanceof Error?cause.message:'请保留原请求。';}}
onMounted(()=>dialog.value?.showModal());
</script>
<template><dialog ref="dialog" class="modal transcript-feedback-dialog" aria-label="提交语料反馈" @cancel="close">
 <header class="modal-h"><h3 class="m-t">提交语料反馈</h3><button class="m-x" aria-label="关闭语料反馈" :disabled="busy" @click="close()">×</button></header>
 <div class="modal-b" @compositionstart="composing=true" @compositionend="composing=false"><div class="banner info" style="padding:8px 14px"><Icon name="message" :size="14"/> 会议内的单次修改不会直接更新全局企业语料，需经平台管理员审核。</div>
 <div v-if="ops.pending.value" role="status"><p>原反馈请求已保留，请恢复结果后继续。</p><button class="btn" :disabled="busy||!ops.available.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(ops.command.state.value)" @click="recover">恢复原反馈</button><button v-if="['CONFLICT','REJECTED'].includes(ops.command.state.value)" class="btn" :disabled="busy" @click="discard">放弃已拒绝反馈</button></div>
 <p v-if="submitted" role="status">反馈已提交，可在“我的语料反馈”查看处理进度。</p>
 <template v-else><div class="field"><label for="feedback-type">反馈类型</label><select id="feedback-type" v-model="feedbackType" class="select" :disabled="locked"><option v-for="[value,label] in types" :key="value" :value="value">{{label}}</option></select></div>
 <div class="two-col"><div class="field"><label for="feedback-original">原始值</label><input id="feedback-original" v-model="original" class="input" maxlength="2048" :readonly="locked"/></div><div class="field"><label for="feedback-suggested">建议值</label><input id="feedback-suggested" v-model="suggested" class="input" maxlength="2048" :readonly="locked"/></div></div>
 <div class="field"><label for="feedback-note">补充说明</label><textarea id="feedback-note" v-model="note" class="input" rows="2" maxlength="4096" placeholder="可选：说明语境或读音" :readonly="locked"/></div></template>
 <p v-if="error" role="alert">{{error}}</p></div>
 <footer class="modal-f"><button class="btn" :disabled="busy" @click="close()">{{submitted?'关闭':'取消'}}</button><button v-if="!submitted" class="btn primary" :disabled="locked||!ops.available.value||composing||!original.trim()||!suggested.trim()" @click="submit">提交反馈</button></footer>
</dialog></template>
<style scoped>.transcript-feedback-dialog{border:0;padding:0;margin:auto;color:var(--text);max-width:calc(100vw - 40px)}.transcript-feedback-dialog::backdrop{background:rgba(17,24,73,.42)}.input,.select{width:100%}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:16px}.modal-b{max-height:70vh;overflow:auto}[role=alert]{color:var(--red)}</style>
