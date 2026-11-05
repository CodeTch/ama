<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useFixedDockHeight} from '../../shared/composables/useFixedDockHeight';
import {materialUuid} from '../../shared/api/materials';
import type {components} from '../../shared/api/generated';
import {importFormat} from '../../shared/document-formats';
import Icon from '../../shared/Icon.vue';
import StableLabel from '../../shared/StableLabel.vue';
import {prepareUpload} from './upload-bytes';
import {useProcessingMaterials} from './useProcessingMaterials';
import {lockedImportFiles} from './locked-import-files';
import ProcessingMaterialRow from './ProcessingMaterialRow.vue';

const props=defineProps<{
 meetingId:string;active:boolean;autoAdvance?:boolean;taskPresent?:boolean;confirmed?:boolean;
 materialsChanged?:boolean;materialStage?:string|null;observedTask?:string|null;observedTaskId?:string|null;observedInputId?:string|null;observedRevision?:string|null;
}>();
const emit=defineEmits<{changed:[];processed:[];editFiles:[];materials:[kind:'materials'|'local'|'library']}>();
const {session,uploads,uploadQueue:queue,recordingStore,tasks,materials}=useApplication();
type FileItem=components['schemas']['CompletedFileSummary'];
const actor=()=>session.current.value?.actor.enterpriseId??null;
const files=shallowRef<FileItem[]>([]),loading=ref(false),error=ref<string|null>(null),more=ref(false),processing=ref(false);
const taskState=ref<string|null>(null),taskError=ref<string|null>(null),recorded=ref(false),dock=ref<HTMLElement>();
const selected=computed(()=>{
 const ack=queue.importCommand.state.value==='ACKNOWLEDGED'?queue.importCommand.ack.value:null;
 if(ack&&ack.meetingId===props.meetingId&&(!props.observedTaskId||!props.observedRevision||BigInt(ack.revisionToken)>BigInt(props.observedRevision)))
  return {task:ack.taskId,input:ack.inputId};
 if(materialUuid(props.observedTaskId)&&materialUuid(props.observedInputId))return {task:props.observedTaskId,input:props.observedInputId};
 return null;
});
const observedState=computed(()=>selected.value?.task===props.observedTaskId?props.observedTask??null:null);
const lockedMaterials=shallowRef<import('../recording/processing-api').ProcessingMaterial[]|null>(null);
const enhancementRetry=ref(false);
const materialStage=ref<string|null>(null);
const enhanced=computed(()=>materialStage.value??props.materialStage);
const effectiveState=computed(()=>taskState.value??observedState.value);
const hasTask=computed(()=>!!props.taskPresent||!!selected.value);
const failedStates=['FAILED','CANCELLED','STALE'];
const failed=computed(()=>failedStates.includes(effectiveState.value??''));
const ready=computed(()=>effectiveState.value==='SUCCEEDED');
const labels:Record<string,string>={TRANSCRIBING:'正在识别或解析主文件',ENHANCING:'正在根据锁定的背景材料增强转写',ADOPTING:'正在保存增强转写',READY:'等待处理',RUNNING:'正在识别或解析主文件',RECONCILING:'正在核对处理结果',SUCCEEDED:'主文件转写完成',CANCELLED:'处理已取消',STALE:'处理输入已过期',FAILED:'主文件处理失败'};
const reasons:Record<string,string>={ATTEMPT_LIMIT:'已达到本次任务的尝试次数。',SUBJECT_CHANGED:'会议或输入版本已变化。',LEASE_EXPIRED:'处理服务未能及时续约。',PROCESSING_INPUT_INVALID:'处理服务无法解析本次输入，请检查原文件。',PROCESSING_UNAVAILABLE:'转写服务暂不可用。'};
const statusText=computed(()=>labels[effectiveState.value??'']??'正在读取处理状态…');
const materialListing=useProcessingMaterials((id,signal)=>materials.list(id,signal),actor,()=>props.meetingId,()=>props.active&&(!enhanced.value||failed.value)&&(hasTask.value||recorded.value));
const canStart=computed(()=>files.value.length>0&&!more.value&&!loading.value&&!processing.value&&queue.ready.value&&!queue.blocked.value
 &&!!materialListing.value.value&&!materialListing.busy.value&&!materialListing.value.value.materialsPending
 &&files.value.every(f=>f.originalPurpose==='TRANSCRIPT_INPUT'&&f.intakeEligibility.state==='ELIGIBLE'&&!!importFormat(f.displayName)));
const lockedInput=computed(()=>selected.value?.input??null);
let fileRequest:AbortController|null=null,taskRequest:AbortController|null=null,timer:ReturnType<typeof setTimeout>|undefined;
const visible=()=>typeof document==='undefined'||!document.hidden;
function stopObservation(){clearTimeout(timer);taskRequest?.abort();taskRequest=null;}
async function observe(){
 stopObservation();
 const selection=selected.value;
 if(!props.active||!visible()||!actor()||!selection)return;
 const request=new AbortController();taskRequest=request;
 try{
  const result=await tasks.importTask(props.meetingId,selection.task,request.signal);
  if(request.signal.aborted)return;
  if(result.inputId!==selection.input)throw Error('原处理任务与锁定输入不匹配');
  const previous=effectiveState.value;
  enhancementRetry.value=!!result.errorCode&&!result.errorCode.startsWith('RAW_');lockedMaterials.value=result.materialFiles??null;materialStage.value=result.materialStage??null;taskState.value=result.state;taskError.value=result.errorCode?(reasons[result.errorCode]??'处理未完成，请查看处理记录。'):null;
  if(result.state==='SUCCEEDED'&&previous!==result.state&&props.autoAdvance)emit('processed');
  else if(previous!==result.state)emit('changed');
  if(result.state!=='SUCCEEDED'&&!failedStates.includes(result.state))timer=setTimeout(observe,2000);
 }catch(cause){if(!request.signal.aborted)taskError.value=cause instanceof Error?cause.message:'处理状态暂不可读取。';}
}
async function load(){
 fileRequest?.abort();const request=new AbortController();fileRequest=request;
 const who=actor(),id=props.meetingId,selection=selected.value;
 files.value=[];error.value=null;more.value=false;recorded.value=false;loading.value=false;
 if(!who||!props.active)return;
 loading.value=true;
 try{
  if(selection){
   const source=await uploads.input(id,selection.input,selection.task,request.signal);
   const verified=await lockedImportFiles(source.files,uploads.receipt,id,request.signal);
   if(!request.signal.aborted)files.value=verified;
  }else{
   const [page,local]=await Promise.all([uploads.completedFiles(id,null,request.signal),recordingStore.load(who,id)]);
   request.signal.throwIfAborted();
   const main=page.items.filter(f=>f.originalPurpose==='TRANSCRIPT_INPUT'),ordered:FileItem[]=[];
   if(local?.finished){
    recorded.value=true;
    for(const part of local.parts.filter(p=>p.bytes>0)){
     const file=await recordingStore.file(local,part),prepared=await prepareUpload(file,request.signal);
     const matches=main.filter(f=>f.displayName===part.name&&f.sizeBytes===part.bytes&&f.sha256===prepared.begin.sha256);
     if(matches.length!==1)throw Error('录音原文件暂无法完整核对，请保留原文件并重试');
     ordered.push(matches[0]!);
    }
   }
   request.signal.throwIfAborted();
   files.value=[...ordered,...main.filter(f=>!ordered.some(p=>p.fileId===f.fileId))];more.value=page.nextCursor!==null;
  }
  if(!request.signal.aborted&&(recorded.value||failed.value)&&!queue.ready.value)await queue.open();
 }catch(cause){if(!request.signal.aborted)error.value=cause instanceof Error?cause.message:'会议主文件暂不可用，请重试。';}
 finally{if(!request.signal.aborted)loading.value=false;}
}
async function phaseCommand(action:'retry'|'cancel'){
 const selection=selected.value;if(!selection||processing.value)return;
 processing.value=true;error.value=null;
 try{await tasks.enhancementCommand(props.meetingId,selection.task,action);await observe();emit('changed');}
 catch(cause){error.value=cause instanceof Error?cause.message:'任务操作失败';}
 finally{processing.value=false;}
}
async function start(){
 if(processing.value||!props.active)return;
 const state=queue.importCommand.state.value;
 if(state!=='UNCERTAIN'&&!canStart.value)return;
 const id=props.meetingId,who=actor();
 processing.value=true;error.value=null;
 try{
  if(failed.value&&state==='ACKNOWLEDGED')queue.newBatch();
  if(state==='REJECTED'||state==='CONFLICT')queue.discardRejectedImport();
  await queue.submitImport(files.value.map(f=>({fileId:f.fileId,name:f.displayName})));
  if(actor()!==who||props.meetingId!==id||!props.active)return;
  if(queue.importCommand.state.value!=='ACKNOWLEDGED')throw Error(queue.error.value??'原处理请求尚未确认，请重试原请求');
  emit('changed');
 }catch(cause){if(actor()===who&&props.meetingId===id)error.value=cause instanceof Error?cause.message:'处理尚未完成，请重试。';}
 finally{if(actor()===who&&props.meetingId===id)processing.value=false;}
}
watch([actor,()=>props.meetingId,()=>props.active,()=>selected.value?.task,()=>selected.value?.input,
 ()=>selected.value?'':queue.items.value.map(i=>i.fileId??'').filter(Boolean).join(',')],()=>void load(),{immediate:true});
watch([actor,()=>props.meetingId,()=>props.active,()=>selected.value?.task,()=>selected.value?.input],()=>{
 stopObservation();lockedMaterials.value=null;materialStage.value=null;taskState.value=null;taskError.value=null;processing.value=false;
 if(props.materialStage||observedState.value!=='SUCCEEDED')void observe();
},{immediate:true});
watch(failed,isFailed=>{if(isFailed&&props.active&&!loading.value&&!queue.ready.value)void queue.open();});
function visibilityChanged(){if(!visible())stopObservation();else if(!ready.value&&!failed.value)void observe();}
function reload(){void load();if(hasTask.value)void observe();}
onMounted(()=>document.addEventListener('visibilitychange',visibilityChanged));
onUnmounted(()=>{document.removeEventListener('visibilitychange',visibilityChanged);fileRequest?.abort();stopObservation();});
useFixedDockHeight(dock);
defineExpose({reloadMaterials:materialListing.reload});
</script>

<template>
 <div v-if="!hasTask&&!recorded&&!loading" class="card card-p"><div class="banner info">尚未开始处理，请先提交会议文件。</div><button class="btn primary" @click="emit('editFiles')">继续导入</button><p v-if="error" class="ac-error mt12" role="alert">{{error}} <button class="btn sm ghost" @click="reload">重新读取</button></p></div>
 <div v-else class="card">
  <div class="card-h" :class="hasTask?'aci-processing-top':''"><div><div class="card-t">材料导入与解析</div><div class="small muted mt8">{{hasTask?(ready?(enhanced?'原始转写和材料增强已完成，可进入转写校正。':'主文件解析处理已完成，可进入转写校正。'):statusText):(recorded?'录音已自动关联，可继续补充背景资料。':'请核对会议主文件，可按需补充背景资料。')}}</div></div><span class="ml-a"></span><button v-if="!hasTask" class="btn sm" @click="emit('materials','materials')">添加背景资料</button></div>
  <div class="card-p"><p v-if="taskError" class="ac-error mb12" role="alert">{{taskError}} <button class="btn sm ghost" @click="observe">重新读取状态</button></p><p v-if="error" class="ac-error" role="alert">{{error}} <button class="btn sm ghost" :disabled="processing" @click="reload">重新读取</button></p><div :class="hasTask?'aci-material-grid':''">
   <section><h3>会议主文件 <span v-if="hasTask" class="small muted">{{files.length}} 个</span></h3><p v-if="loading" class="small muted" role="status">正在读取会议主文件…</p>
    <div v-for="file in files" :key="file.fileId" class="aci-progress-file"><div class="aci-progress-file-top"><span class="aci-file-type"><Icon :name="['MP4','MOV','MKV','M4V','AVI'].includes(importFormat(file.displayName)??'')?'play':['MP3','WAV','M4A','WEBM','AAC','FLAC','OGG'].includes(importFormat(file.displayName)??'')?'mic':'file'" :size="18"/></span><div class="aci-progress-name"><b :title="file.displayName">{{file.displayName}}</b><div class="small muted">会议主文件 · {{importFormat(file.displayName)??'文件'}}</div></div><span class="tag" :class="failed?'red':ready?'green':'orange'">{{hasTask?statusText:file.intakeEligibility.state==='ELIGIBLE'?'可用':'待核对'}}</span></div><div v-if="ready" class="progress mt12" aria-hidden="true"><div class="bar" style="width:100%"></div></div></div><div v-if="!files.length&&!loading&&!error" class="aci-empty-material">尚未导入主文件</div>
   </section>
   <section :aria-busy="materialListing.busy.value"><div v-if="!hasTask" class="divider"></div><div :class="hasTask?'aci-material-section-title':'ac-material-heading'"><h3>{{hasTask?'背景材料及相关文档':'会议背景材料及相关文档'}} <span v-if="lockedMaterials||materialListing.value.value" class="small muted">{{lockedMaterials?.length??materialListing.value.value?.items.length}} 个</span><span v-else-if="!hasTask" class="small muted">（选填）</span></h3><button v-if="hasTask" class="btn sm" @click="emit('materials','materials')">调整背景材料</button><div v-else class="flex wrap"><button class="btn" @click="emit('materials','local')">上传本地文件</button><button class="btn" @click="emit('materials','library')">从知识库选择</button></div></div><template v-if="lockedMaterials"><ProcessingMaterialRow v-for="item in lockedMaterials" :key="item.materialRevisionId" :item="item"/><div v-if="!lockedMaterials.length" class="aci-empty-material">未添加背景材料，将基于会议主文件处理。</div><p v-else class="small muted">本次任务使用提交时锁定的材料版本。</p></template><p v-else-if="materialListing.busy.value&&!materialListing.value.value" class="small muted" role="status">正在读取背景材料…</p><p v-else-if="materialListing.error.value" class="small ac-error" role="alert">{{materialListing.error.value}} <button class="btn sm ghost" @click="materialListing.reload">重新读取背景材料</button></p><template v-else-if="materialListing.value.value"><ProcessingMaterialRow v-for="item in materialListing.value.value.items" :key="item.materialRevisionId" :item="item"/><div v-if="!materialListing.value.value.items.length" class="aci-empty-material">{{hasTask?'未添加背景材料，将基于会议主文件处理。':'未添加背景材料，将基于会议主文件处理。'}}</div><p v-else class="small muted mt12">此处列出会议当前的背景材料。本次离线任务只解析主文件，保存工作稿后可在转写校正中生成材料增强候选。</p><p v-if="materialListing.value.value.materialsPending" class="small ac-error mt12" role="status">材料解析尚未完成，请在背景材料窗口核对。</p></template></section>
  </div><p v-if="hasTask&&selected" class="small muted mt16" :title="'本次处理锁定输入：'+(lockedInput??'正在核对')">处理任务 {{selected.task}} · 本次提交的文件已锁定，新增文件需另行处理。</p><p v-if="more" class="small muted mt16">文件超过单批处理数量，请通过导入窗口核对本次文件。</p><div v-if="!hasTask" class="small muted mt16" role="status">{{canStart?'主文件和材料已就绪，点击开始处理继续。':'请先核对主文件和背景材料，处理失败项或等待校验完成。'}}</div><div v-if="!hasTask" class="aci-process-actions"><button class="btn primary" :disabled="!canStart" @click="start"><StableLabel :labels="['开始处理','重试原处理请求']">{{queue.importCommand.state.value==='UNCERTAIN'?'重试原处理请求':'开始处理'}}</StableLabel></button></div></div>
 </div>
 <footer v-if="active&&hasTask&&(!confirmed||materialsChanged)" ref="dock" class="acf-dock" role="region" aria-label="当前步骤确认操作"><div class="acf-inner"><div class="acf-hint">{{failed?'处理失败，请查看原因后重试；已有内容保留。':ready?(materialsChanged?'背景材料已修改，请核对候选和已有转写。':'材料解析处理已完成，核对后进入转写校正。'):'材料尚未处理完成，请先完成解析或处理异常。'}}</div><div class="acf-actions"><button v-if="enhanced&&['ENHANCING','TRANSCRIBING'].includes(enhanced)" class="btn" :disabled="processing" @click="phaseCommand('cancel')">取消处理</button><button v-if="enhanced==='FAILED'&&taskError&&!taskState?.startsWith('RAW_')&&enhancementRetry" class="btn primary" :disabled="processing" @click="phaseCommand('retry')">重试增强</button><button v-else-if="failed&&queue.importCommand.state.value!=='UNCERTAIN'" class="btn primary" :disabled="!canStart" @click="start">重试处理</button><button v-else-if="failed&&queue.importCommand.state.value==='UNCERTAIN'" class="btn primary" :disabled="processing" @click="start">重试原处理请求</button><button v-else class="btn primary" :disabled="!ready" @click="emit('processed')">{{materialsChanged&&ready?'核对已有转写':'进入转写校正'}}</button></div></div></footer>
</template>
