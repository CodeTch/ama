<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue';
import {useApplication} from '../../application';
import Icon from '../../shared/Icon.vue';
import type {MaterialState} from './useMaterials';
import MaterialImportSection from './MaterialImportSection.vue';
const props=defineProps<{meetingId:string}>(),emit=defineEmits<{closed:[]}>(),{session}=useApplication(),dialog=ref<HTMLDialogElement>(),opened=ref(false);
const empty=():MaterialState=>({busy:false,blocked:false,failed:false,hasFiles:false,closeBlocked:false}),state=ref<MaterialState>(empty()),closeBlocked=computed(()=>state.value.closeBlocked),dismissBlocked=computed(()=>state.value.dismissBlocked??closeBlocked.value),actor=()=>session.current.value?.actor.enterpriseId??null;
function show(){if(!actor()||!dialog.value)return false;if(opened.value)return true;opened.value=true;if(!dialog.value.open)dialog.value.showModal();return true;}
function forceClose(){const wasOpen=opened.value;dialog.value?.close();opened.value=false;state.value=empty();if(wasOpen)emit('closed');}
function close(){if(!actor()){forceClose();return true;}if(closeBlocked.value)return false;forceClose();return true;}
function dismiss(){
 if(dismissBlocked.value)return false;
 if(!closeBlocked.value)return close();
 // Keep the controller and selected local files alive for recovery on this meeting.
 dialog.value?.close();opened.value=false;
 return true;
}
function backdrop(event:MouseEvent){if(event.target===dialog.value)dismiss();}
const stop=watch(()=>[props.meetingId,actor()],forceClose,{flush:'sync'});onUnmounted(()=>{stop();dialog.value?.close();});
defineExpose({show,close,forceClose,get open(){return opened.value;}});
</script>
<template><dialog ref="dialog" class="mask material-only-mask" role="presentation" @cancel.self.prevent="dismiss" @click="backdrop"><section class="modal xl aci-import-modal aci-background-only" role="dialog" aria-modal="true" aria-label="会议背景材料" aria-labelledby="material-only-title"><header class="modal-h"><h2 id="material-only-title" class="m-t">会议背景材料</h2><button type="button" class="m-x" aria-label="关闭背景材料" :disabled="dismissBlocked" @click="dismiss"><Icon name="x" :size="18"/></button></header><div class="modal-b"><MaterialImportSection v-if="opened||closeBlocked" :key="props.meetingId" :meeting-id="props.meetingId" @state="state=$event"/></div><footer class="modal-f"><span v-if="closeBlocked" class="small muted" role="status">{{dismissBlocked?'请先等待上传完成或恢复存储。':'可暂时关闭，再打开背景材料恢复原操作。'}}</span><button type="button" class="btn primary" :disabled="dismissBlocked" @click="dismiss">完成</button></footer></section></dialog></template>
<style scoped>
.material-only-mask{inset:0;width:auto;height:auto;max-width:none;max-height:none;padding:0;margin:0;border:0;color:var(--text);overflow:visible}.material-only-mask:not([open]){display:none}.material-only-mask::backdrop{background:transparent}.material-only-mask .modal{margin:0;max-width:calc(100vw - 32px);height:min(640px,90dvh);max-height:90dvh;display:flex;flex-direction:column;padding:0}.modal-h{flex-shrink:0}.modal-h h2{font-size:16px}.modal-b{flex:1;min-height:0;overflow:auto;scrollbar-gutter:stable}.modal-f{flex-shrink:0;display:flex;justify-content:flex-end;gap:12px;align-items:center}.modal-f [role=status]{margin-right:auto}
</style>
