<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue';
const props=withDefaults(defineProps<{open:boolean;title?:string;preventClose?:boolean}>(),{title:'会议信息'});
const emit=defineEmits<{close:[]}>(),dialog=ref<HTMLDialogElement>(),openedOnce=ref(props.open);
function sync(){
 if(!dialog.value)return;
 if(props.open){openedOnce.value=true;if(!dialog.value.open)dialog.value.showModal();}
 else if(dialog.value.open)dialog.value.close();
 document.body.classList.toggle('drawer-open',props.open);
}
function requestClose(){if(!props.preventClose)emit('close');}
function backdrop(event:MouseEvent){
 if(event.target!==dialog.value)return;
 const bounds=dialog.value.getBoundingClientRect();
 if(event.clientX<bounds.left||event.clientX>bounds.right)requestClose();
}
watch(()=>props.open,sync,{flush:'post'});
onMounted(sync);
onUnmounted(()=>document.body.classList.remove('drawer-open'));
</script>
<template>
 <dialog ref="dialog" class="info-drawer ac-info-drawer" role="dialog" aria-modal="true" aria-labelledby="info-drawer-title" @cancel.prevent="requestClose" @click="backdrop">
  <header class="info-drawer-header"><h2 id="info-drawer-title">{{title}}</h2><button class="btn sm" type="button" aria-label="关闭编辑会议信息" @click="requestClose" :disabled="preventClose">✕</button></header>
  <div v-if="open||openedOnce" class="info-drawer-body"><slot/></div>
 </dialog>
</template>
<style scoped>
.info-drawer{box-sizing:border-box;width:min(560px,100vw);height:100dvh;max-width:100vw;max-height:100dvh;margin:0 0 0 auto;padding:0;border:0;background:var(--surface,#fff);color:var(--text);box-shadow:-12px 0 32px #0f172a2e}
.info-drawer[open]{display:flex;flex-direction:column}.info-drawer::backdrop{background:#0f172a61}
.info-drawer-header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 20px;border-bottom:1px solid var(--border);flex-shrink:0}
.info-drawer-header h2{margin:0;font-size:15px}.info-drawer-body{min-height:0;overflow:auto;padding:20px}
.info-drawer-body :deep(.info-editor){margin:0;box-shadow:none;padding:0;border:0}.info-drawer-body :deep(#info-editor-heading){display:none}
@media(max-width:600px){.info-drawer-header{padding:14px 16px}.info-drawer-body{padding:16px}}
:global(body.drawer-open){overflow:hidden}
</style>
