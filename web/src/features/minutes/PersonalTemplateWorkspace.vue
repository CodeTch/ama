<script setup lang="ts">
import {computed,nextTick,onMounted,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import type {PersonalTemplate} from '../../shared/api/personal-templates';
import PersonalTemplateDialogView from './PersonalTemplateDialogView.vue';
import Icon from '../../shared/Icon.vue';

const props=defineProps<{meetingId:string;locked:boolean;apply:(revision:string)=>Promise<boolean>}>();
const emit=defineEmits<{changed:[]}>();
const app=useApplication(),manager=app.personalTemplateCommands;
const view=ref<InstanceType<typeof PersonalTemplateDialogView>>(),library=ref<HTMLDialogElement>();
const mode=ref<'create'|'update'|'delete'>('create'),name=ref(''),themeText=ref(''),composing=ref(false),error=ref(''),localBusy=ref(false),visible=ref(false);
const selected=shallowRef<PersonalTemplate|null>(null),accepted=shallowRef<PersonalTemplate|null>(null);
let epoch=0;
const actor=()=>app.session.current.value?.actor.enterpriseId??null;
const busy=computed(()=>localBusy.value||manager.busy.value),pending=computed(()=>manager.pending.value||busy.value);
const concealed=computed(()=>!manager.available.value||!actor());
function clear(){name.value='';themeText.value='';selected.value=null;accepted.value=null;composing.value=false;}
function close(){if(busy.value)return;view.value?.close();visible.value=false;}
function restore(){
 const original=manager.original.value,snapshot=manager.command.snapshot.value;
 if(!original||!snapshot)return;
 mode.value=snapshot.operation==='PERSONAL_TEMPLATE_DELETE'?'delete':snapshot.operation==='PERSONAL_TEMPLATE_UPDATE'?'update':'create';
 name.value=typeof original.name==='string'?original.name:'';
 themeText.value=Array.isArray(original.themes)?original.themes.join('\n'):'';
 accepted.value=null;
}
async function show(){await nextTick();visible.value=true;view.value?.show();}
async function showRecovery(){restore();await show();}
async function openCreate(){
 if(manager.pending.value)return showRecovery();if(busy.value)return;
 clear();error.value=app.personalTemplateScopeError.value??'';mode.value='create';await show();
}
async function openManage(){
 if(manager.pending.value)return showRecovery();if(busy.value)return;
 error.value=app.personalTemplateScopeError.value??'';await nextTick();library.value?.showModal();
 try{await manager.reload();}catch(cause){error.value=cause instanceof Error?cause.message:'模板暂不可用。';}
}
async function loadPage(cursor:string|null){try{error.value='';await manager.reload(cursor);}catch(cause){error.value=cause instanceof Error?cause.message:'模板暂不可用。';}}
async function inspect(item:PersonalTemplate,next:'update'|'delete'){
 if(manager.pending.value)return showRecovery();if(busy.value||props.locked||concealed.value)return;
 const turn=epoch,who=actor(),meeting=props.meetingId;localBusy.value=true;error.value='';
 try{const current=await app.personalTemplates.read(item.id,new AbortController().signal);if(turn!==epoch||who!==actor()||meeting!==props.meetingId)return;
  clear();selected.value=current;name.value=current.name;themeText.value=current.themes.join('\n');mode.value=next;await show();
 }catch(cause){if(turn===epoch)error.value=cause instanceof Error?cause.message:'当前模板暂不可读取。';}finally{if(turn===epoch)localBusy.value=false;}
}
async function applySaved(value:PersonalTemplate,turn:number){
 if(value.deleted||turn!==epoch)return false;
 const applied=await props.apply(value.revisionId);if(turn!==epoch)return false;
 if(applied){view.value?.close();library.value?.close();visible.value=false;emit('changed');return true;}
 error.value='模板已保存，本会议尚未使用。请核对当前会议状态后再选择。';return false;
}
async function submit(){
 if(pending.value||props.locked||concealed.value||composing.value)return;
 const turn=epoch;localBusy.value=true;error.value='';
 try{
  if(accepted.value){await applySaved(accepted.value,turn);return;}
  let result:PersonalTemplate;
  if(mode.value==='delete'){
   if(!selected.value)throw new Error('请先读取当前模板。');
   result=await manager.remove(selected.value.id,selected.value.revisionToken);
  }else{
   const themes=themeText.value.split('\n').map(line=>line.trim()).filter(Boolean);
   result=mode.value==='create'?await manager.create(name.value.trim(),themes):await manager.update(selected.value!.id,selected.value!.revisionToken,name.value.trim(),themes);
  }
  if(turn!==epoch)return;
  emit('changed');if(result.deleted){view.value?.close();visible.value=false;clear();return;}
  accepted.value=result;name.value=result.name;themeText.value=result.themes.join('\n');await applySaved(result,turn);
 }catch(cause){if(turn===epoch)error.value=cause instanceof Error?cause.message:'模板操作尚未确认，请保留原请求。';}finally{if(turn===epoch)localBusy.value=false;}
}
async function retry(){
 if(busy.value||concealed.value||composing.value)return;const turn=epoch;localBusy.value=true;error.value='';
 try{const result=await manager.retry();if(turn!==epoch||!result)return;emit('changed');
  if(result.deleted){clear();view.value?.close();visible.value=false;return;}
  // Recovery confirms only the original personal mutation; applying to this meeting stays explicit.
  accepted.value=result;name.value=result.name;themeText.value=result.themes.join('\n');error.value='原模板保存已核实，可选择使用已保存模板。';
 }catch(cause){if(turn===epoch)error.value=cause instanceof Error?cause.message:'原模板结果暂无法核实。';}finally{if(turn===epoch)localBusy.value=false;}
}
function discard(){try{manager.discardRejected();clear();error.value='';mode.value='create';}catch(cause){error.value=cause instanceof Error?cause.message:'原请求尚不能放弃。';}}
async function useSaved(item:PersonalTemplate){if(pending.value||props.locked||concealed.value)return;const turn=epoch;localBusy.value=true;error.value='';try{await applySaved(item,turn);}catch(cause){if(turn===epoch)error.value=cause instanceof Error?cause.message:'会议模板尚未选择。';}finally{if(turn===epoch)localBusy.value=false;}}
watch(()=>[actor(),props.meetingId],()=>{epoch++;localBusy.value=false;clear();view.value?.close();library.value?.close();visible.value=false;error.value='';},{immediate:true,flush:'sync'});
watch(()=>manager.command.snapshot.value,snapshot=>{if(snapshot)restore();},{immediate:true,flush:'sync'});
watch(()=>manager.available.value,available=>{if(!available){clear();view.value?.close();library.value?.close();visible.value=false;}},{flush:'sync'});
onMounted(()=>{if(manager.pending.value)void showRecovery();});
onUnmounted(()=>{epoch++;});
defineExpose({openCreate,openManage,showRecovery,pending});
</script>
<template>
 <PersonalTemplateDialogView ref="view" v-model:name="name" v-model:theme-text="themeText" v-model:composing="composing" :mode="mode" :pending="manager.pending.value" :busy="busy" :state="manager.command.state.value" :error="error" :concealed="concealed" :accepted="!!accepted" @submit="submit" @retry="retry" @discard="discard" @close="close"/>
 <dialog ref="library" class="mask personal-library-root" aria-label="管理个人模板" @cancel="event=>{if(busy)event.preventDefault();}" @click="event=>{if(event.target===event.currentTarget&&!busy)library?.close();}">
  <div class="modal">
   <header class="modal-h"><h3 class="m-t">管理个人模板</h3><button class="m-x" aria-label="关闭模板管理" :disabled="busy" @click="library?.close()"><Icon name="x" :size="18"/></button></header>
   <div class="modal-b">
    <p class="small muted">模板编辑形成新版本；已有纪要继续保留原模板版本。</p>
    <p v-if="!manager.page.value?.items.length" class="empty">{{busy?'正在读取模板…':'暂无个人模板'}}</p>
    <article v-for="item in manager.page.value?.items" :key="item.id" class="personal-template-row">
     <div><b>{{item.name}}</b><p class="small muted">{{item.themes.join(' · ')}}</p></div>
     <div class="flex"><button class="btn sm" :disabled="pending||locked" :aria-label="'编辑模板 '+item.name" @click="inspect(item,'update')">编辑</button><button class="btn sm danger" :disabled="pending||locked" :aria-label="'删除模板 '+item.name" @click="inspect(item,'delete')">删除</button><button class="btn sm" :disabled="pending||locked" :aria-label="'使用模板 '+item.name" @click="useSaved(item)">使用</button></div>
    </article>
    <p v-if="error" role="alert" class="ac-error">{{error}}</p>
   </div>
   <footer class="modal-f"><button class="btn" :disabled="pending" @click="loadPage(null)">返回首页</button><button v-if="manager.page.value?.nextCursor" class="btn" :disabled="pending" @click="loadPage(manager.page.value.nextCursor)">下一页</button><button class="btn" :disabled="busy" @click="library?.close()">关闭</button></footer>
  </div>
 </dialog>
 <button v-if="manager.pending.value&&!visible" class="btn sm" type="button" @click="showRecovery">恢复原模板操作</button>
 <p v-if="error&&!visible&&!library?.open" class="small" role="alert">{{error}}</p>
</template>
<style scoped>
.personal-library-root{inset:0;border:0;padding:24px;margin:0;width:100vw;height:100dvh;max-width:none;max-height:none;color:var(--text)}.personal-library-root:not([open]){display:none}.personal-library-root::backdrop{background:transparent}.m-t{margin:0}.personal-template-row{padding:14px 0;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;gap:16px}.personal-template-row>div{min-width:0}.personal-template-row p{overflow-wrap:anywhere;margin-top:4px}.personal-template-row .flex{flex-shrink:0}@media(max-width:700px){.personal-template-row{align-items:flex-start;flex-direction:column}}
</style>
