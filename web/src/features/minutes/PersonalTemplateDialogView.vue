<script setup lang="ts">
import StableLabel from '../../shared/StableLabel.vue';
import {computed,ref} from 'vue';
import Icon from '../../shared/Icon.vue';
const props=defineProps<{mode:'create'|'update'|'delete';name:string;themeText:string;pending:boolean;busy:boolean;composing:boolean;state:string;error:string;concealed:boolean;accepted:boolean}>();
const emit=defineEmits<{'update:name':[value:string];'update:themeText':[value:string];'update:composing':[value:boolean];submit:[];retry:[];discard:[];close:[]}>();
const dialog=ref<HTMLDialogElement>();
const blocked=computed(()=>props.pending||props.busy),readonly=computed(()=>blocked.value||props.accepted||props.concealed);
const heading=computed(()=>props.mode==='create'?'新增总结模板':props.mode==='update'?'编辑总结模板':'删除总结模板');
function close(event?:Event){event?.preventDefault();if(!blocked.value)emit('close');}
defineExpose({show:()=>{if(dialog.value&&!dialog.value.open)dialog.value.showModal();},close:()=>dialog.value?.close()});
</script>
<template>
 <dialog ref="dialog" class="mask template-dialog-root" :aria-label="heading" @cancel="close" @click="event=>{if(event.target===event.currentTarget)close();}">
  <div class="modal">
   <header class="modal-h"><h3 class="m-t">{{heading}}</h3><button class="m-x" aria-label="关闭" :disabled="blocked" @click="close()"><Icon name="x" :size="18"/></button></header>
   <div class="modal-b">
    <template v-if="mode!=='delete'">
     <div class="field"><label for="personal-template-name">模板名称</label><input id="personal-template-name" class="input" aria-label="模板名称" :value="concealed?'':name" :readonly="readonly" @input="emit('update:name',($event.target as HTMLInputElement).value)" @compositionstart="emit('update:composing',true)" @compositionend="emit('update:composing',false)"/></div>
     <div class="field"><label for="personal-template-themes">段落主题（每行一个，1—8个）</label><textarea id="personal-template-themes" class="input" rows="5" aria-label="段落主题" :value="concealed?'':themeText" :readonly="readonly" @input="emit('update:themeText',($event.target as HTMLTextAreaElement).value)" @compositionstart="emit('update:composing',true)" @compositionend="emit('update:composing',false)"/></div>
     <div class="small muted">仅本人可见；保存并使用后，重新生成并采用才应用到正文。</div>
    </template>
    <template v-else><p>确认删除模板“{{concealed?'':name}}”？</p><p class="small muted mt12">删除模板后，已有纪要及历史版本保留。</p></template>
    <p v-if="pending&&!concealed" class="small muted mt12" role="status">正在核实原模板操作，当前内容保持只读。</p>
    <div class="ac-error" role="alert">{{error}}</div>
   </div>
   <footer class="modal-f">
    <button v-if="pending&&['CONFLICT','REJECTED'].includes(state)" class="btn" :disabled="busy" @click="emit('discard')">放弃已拒绝的原请求</button>
    <button class="btn" :disabled="blocked" @click="close()">取消</button>
    <button v-if="pending" class="btn primary" :disabled="busy||composing||concealed||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(state)" @click="emit('retry')"><StableLabel :labels="['查回原模板保存结果','重新核实已保存模板']">{{state==='ACKNOWLEDGED'?'重新核实已保存模板':'查回原模板保存结果'}}</StableLabel></button>
    <button v-else class="btn" :class="mode==='delete'?'danger':'primary'" :disabled="busy||composing||concealed" @click="emit('submit')"><StableLabel :labels="['删除','保存并使用','使用已保存模板']">{{mode==='delete'?'删除':accepted?'使用已保存模板':'保存并使用'}}</StableLabel></button>
   </footer>
  </div>
 </dialog>
</template>
<style scoped>
.template-dialog-root{inset:0;border:0;padding:0;margin:0;width:100vw;height:100dvh;max-width:none;max-height:none;color:var(--text)}
.template-dialog-root:not([open]){display:none}.template-dialog-root::backdrop{background:transparent}.m-t{margin:0}.input{width:100%}
</style>
