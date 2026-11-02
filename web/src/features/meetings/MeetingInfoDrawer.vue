<script setup lang="ts">
import Icon from '../../shared/Icon.vue';
import {onMounted,onUnmounted,ref,watch} from 'vue';
const props=withDefaults(defineProps<{open:boolean;title?:string;closeLabel?:string;preventClose?:boolean}>(),{title:'会议信息',closeLabel:'关闭编辑会议信息'});
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
  <header class="info-drawer-header drawer-h"><h3 id="info-drawer-title">{{title}}</h3><button class="drawer-close" type="button" :aria-label="closeLabel" @click="requestClose" :disabled="preventClose"><Icon name="x" :size="18"/></button></header>
  <div v-if="open||openedOnce" class="info-drawer-body"><slot/></div>
 </dialog>
</template>
<style scoped>
.info-drawer{box-sizing:border-box;width:560px;height:100dvh;max-width:92vw;max-height:100dvh;margin:0 0 0 auto;padding:0;border:0;background:var(--surface,#fff);color:var(--text);box-shadow:-8px 0 30px rgba(17,24,73,.15)}
.info-drawer[open]{display:flex;flex-direction:column}.info-drawer::backdrop{background:rgba(17,24,73,.35)}
.info-drawer-header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 20px;border-bottom:1px solid var(--border);flex-shrink:0}
.info-drawer-header h3{margin:0;font-size:15px;flex:1;color:var(--navy)}.drawer-close{margin-left:auto;cursor:pointer;border:0;background:none;color:var(--muted);padding:0;border-radius:6px}.info-drawer-body{flex:1;min-height:0;overflow:auto;padding:18px 20px 94px}
.info-drawer-body :deep(.info-editor){margin:0;box-shadow:none;padding:0;border:0}.info-drawer-body :deep(#info-editor-heading){display:none}
@media(max-width:600px){.info-drawer-body{padding:16px 16px 84px}}
:global(body.drawer-open){overflow:hidden}
</style>
