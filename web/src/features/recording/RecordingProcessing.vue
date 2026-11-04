<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch} from 'vue';
import {useApplication} from '../../application';
import {recordingProcessingApi,type RecordingProcessing} from './processing-api';
import {createRecordingProcessIntent} from './recording-process-intent';
import type {RecordingPartSource} from './recording-api';
import Icon from '../../shared/Icon.vue';
import {useFixedDockHeight} from '../../shared/composables/useFixedDockHeight';
import {useProcessingMaterials} from '../import/useProcessingMaterials';
const props=withDefaults(defineProps<{meetingId:string;sessionId?:string|null;canMutate:boolean;autoStart?:boolean;autoResume?:boolean;docked?:boolean}>(),{sessionId:null,autoStart:false,autoResume:true,docked:true});
const emit=defineEmits<{accepted:[];materials:[kind:'materials'|'local'|'library']}>(),app=useApplication(),api=recordingProcessingApi(app.session.client),journal=createRecordingProcessIntent(app.journals);
const value=ref<RecordingProcessing|null>(null),error=ref<string|null>(null),busy=ref(false),sessionId=ref<string|null>(null);
const parts=ref<RecordingPartSource[]>([]),dock=ref<HTMLElement>();
const hasTask=computed(()=>!!value.value&&value.value.state!=='NOT_REQUESTED'),ready=computed(()=>value.value?.state==='SUCCEEDED'||value.value?.state==='STALE'),failed=computed(()=>value.value?.state==='FAILED');
const materialListing=useProcessingMaterials((id,signal)=>app.materials.list(id,signal),()=>app.session.current.value?.actor.enterpriseId??null,()=>props.meetingId,()=>!hasTask.value);
const materialFiles=computed(()=>hasTask.value?value.value?.materialFiles??null:materialListing.value.value?.items??null);
const canStart=computed(()=>!busy.value&&props.canMutate&&parts.value.length>0&&!!materialListing.value.value&&!materialListing.value.value.materialsPending&&materialListing.value.value.items.every(item=>item.state==='READY'));
useFixedDockHeight(dock);
const clock=(samples:number)=>{const seconds=Math.floor(samples/16000);return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');};
const labels:Record<RecordingProcessing['state'],string>={NOT_REQUESTED:'录音已保存，可开始处理',WAITING_ARCHIVE:'正在保存录音',COPYING:'正在准备录音来源',TRANSCRIBING:'正在识别原始录音',ENHANCING:'正在根据背景材料增强转写',SUCCEEDED:'转写与增强完成',FAILED:'处理失败，请重试',STALE:'材料或转写版本已变化，请核对后重新处理'};
let timer:ReturnType<typeof setTimeout>|undefined,epoch=0,mounted=false,completionEmitted=false;
function publish(result:RecordingProcessing){value.value=result;if(result.state==='SUCCEEDED'&&!completionEmitted){completionEmitted=true;emit('accepted');}}
async function load(){
 const turn=epoch,actor=app.session.current.value?.actor.enterpriseId;if(!actor)return;
 try{
  const current=props.sessionId?await app.recordingApi.current(props.sessionId):await app.recordingApi.currentForMeeting(props.meetingId),session=current?.sessionId;
  if(!mounted||turn!==epoch)return;
  if(!session){error.value='尚无已保存录音。';return;}sessionId.value=session;
  const result=await api.read(session);if(!mounted||turn!==epoch)return;
  if(result.meetingId!==props.meetingId)throw Error('录音归属暂无法核对');publish(result);error.value=null;
  if(!parts.value.length&&current){const saved=await app.recordingApi.partSources(current);if(!mounted||turn!==epoch)return;parts.value=saved.filter(part=>part.sampleCount>0);}
  const intent=journal.load(actor,props.meetingId);
  if(result.state==='NOT_REQUESTED'&&props.autoStart||props.autoResume&&intent.kind==='RESTORED')await start();
 }catch(cause){if(mounted&&turn===epoch)error.value=cause instanceof Error?cause.message:'处理状态暂不可读取';}
 finally{if(mounted&&turn===epoch&&value.value?.state!=='SUCCEEDED')timer=setTimeout(load,1500);}
}
async function start(){
 const actor=app.session.current.value?.actor.enterpriseId,id=sessionId.value,meeting=props.meetingId,turn=epoch;if(!actor||!id||busy.value||!props.canMutate)return;
 const current=()=>mounted&&turn===epoch&&actor===app.session.current.value?.actor.enterpriseId;
 busy.value=true;error.value=null;
 try{
  const restored=journal.load(actor,meeting);
  if(restored.kind==='INVALID'||restored.kind==='UNAVAILABLE')throw Error('原处理请求暂无法读取，请保留页面重试');
  if(restored.kind==='RESTORED'&&restored.value.session!==id)throw Error('原处理请求属于另一段录音，请先核对。');
  let intent=restored.kind==='RESTORED'&&restored.value.expectedRevisionToken?restored.value:null;
  if(!intent){const materials=await app.materials.list(meeting,new AbortController().signal);if(!current())return;if(materials.materialsPending||materials.items.some(item=>item.state!=='READY'))throw Error('背景材料尚未就绪，请重试或移除。');intent={actor,meeting,session:id,requestId:crypto.randomUUID(),expectedRevisionToken:materials.meetingRevision,materialSnapshotId:materials.materialSnapshotId};}
  if(journal.save(actor,meeting,intent)!=='SAVED')throw Error('无法保存原处理请求');
  const accepted=await api.start(id,intent.requestId,intent.expectedRevisionToken!,intent.materialSnapshotId??null);if(!current())return;
  if(accepted.meetingId!==meeting)throw Error('录音归属暂无法核对');publish(accepted);
  if(!journal.clear())throw Error('处理已受理，请重试核对原请求');
 }catch(cause){if(current())error.value=cause instanceof Error?cause.message:'处理请求尚未确认';}finally{if(current())busy.value=false;}
}
async function retry(){if(!sessionId.value||busy.value||!props.canMutate)return;const turn=epoch,meeting=props.meetingId;busy.value=true;try{const result=await api.retry(sessionId.value,value.value?.taskId??null);if(!mounted||turn!==epoch)return;if(result.meetingId!==meeting)throw Error('录音归属暂无法核对');publish(result);error.value=null;}catch(cause){if(mounted&&turn===epoch)error.value=cause instanceof Error?cause.message:'重试未完成';}finally{if(mounted&&turn===epoch)busy.value=false;}}
function refresh(){clearTimeout(timer);void load();}
function beforeLeave(){return !busy.value;}
watch(()=>[props.meetingId,props.sessionId,app.session.current.value?.actor.enterpriseId],()=>{epoch++;clearTimeout(timer);value.value=null;error.value=null;sessionId.value=null;parts.value=[];busy.value=false;completionEmitted=false;if(mounted)void load();});
onMounted(()=>{mounted=true;void load();});onUnmounted(()=>{mounted=false;epoch++;clearTimeout(timer);});
defineExpose({beforeLeave,reloadMaterials:()=>{void materialListing.reload();refresh();}});
</script>
<template>
 <section class="card" aria-label="录音处理">
  <div class="card-h" :class="hasTask?'aci-processing-top':''"><div><div class="card-t">材料导入与解析</div><div class="small muted mt8">{{hasTask?(value?labels[value.state]:'正在读取处理状态'):'录音已自动关联，无需重复上传；可继续补充背景资料。'}}</div></div><span class="ml-a"></span><button v-if="!hasTask" class="btn sm" @click="emit('materials','materials')">添加背景资料</button></div>
  <div class="card-p">
   <p v-if="error" class="ac-error" role="alert">{{error}} <button class="btn sm ghost" :disabled="busy" @click="refresh">重新读取状态</button></p>
   <div :class="hasTask?'aci-material-grid':''">
    <section><h3>会议主文件 <span v-if="hasTask" class="small muted">{{parts.length}} 个</span></h3><p v-if="!parts.length" class="small muted" role="status">正在读取录音文件…</p><div v-for="part in parts" :key="part.partId" class="aci-progress-file"><div class="aci-progress-file-top"><span class="aci-file-type"><Icon name="mic" :size="18"/></span><div class="aci-progress-name"><b>第{{part.partNo}}段录音.wav</b><div class="small muted">会议主文件 · {{clock(part.sampleCount)}} · WAV</div></div><span class="tag" :class="failed?'red':ready||!hasTask?'green':'orange'">{{failed?'处理失败':ready?'识别及转写完成':hasTask?'正在识别':'已保存'}}</span></div><div v-if="hasTask" class="progress mt12"><div class="bar" :style="{width:ready?'100%':'0%'}"></div></div></div></section>
    <section><div v-if="!hasTask" class="divider"></div><div :class="hasTask?'aci-material-section-title':'ac-material-heading'"><h3>{{hasTask?'背景材料及相关文档':'会议背景材料及相关文档'}} <span class="small muted">{{hasTask?(materialFiles?.length??0)+' 个':'（选填）'}}</span></h3><button v-if="hasTask" class="btn sm" @click="emit('materials','materials')">调整背景材料</button><div v-else class="flex wrap"><button class="btn" @click="emit('materials','local')">上传本地文件</button><button class="btn" @click="emit('materials','library')">从知识库选择</button></div></div>
     <p v-if="!hasTask&&materialListing.error.value" class="small ac-error" role="alert">{{materialListing.error.value}} <button class="btn sm ghost" @click="materialListing.reload">重新读取背景材料</button></p><p v-else-if="materialFiles===null" class="small muted" role="status">正在读取背景材料…</p><template v-else><div v-for="item in materialFiles" :key="item.materialRevisionId" class="aci-progress-file"><div class="aci-progress-file-top"><span class="aci-file-type"><Icon name="file" :size="18"/></span><div class="aci-progress-name"><b :title="item.name">{{item.name}}</b><div class="small muted">{{hasTask?'本次处理锁定的材料':'会议背景材料'}}</div></div><span class="tag" :class="item.state==='READY'?'green':'red'">{{item.state==='READY'?'已解析':'解析失败'}}</span></div></div><div v-if="!materialFiles.length" class="aci-empty-material">{{hasTask?'未添加背景材料，将基于会议主文件处理。':'未添加背景材料，将基于录音处理。'}}</div></template>
    </section>
   </div>
   <div v-if="value?.materialsChanged" class="banner warn mt16">材料有待应用修改，当前任务仍使用提交时锁定的材料版本。请在转写校正中核对并应用新材料。</div>
   <p v-if="hasTask" class="small muted mt16">{{value?.taskId?'处理任务 '+value.taskId+' · ':''}}原始识别、背景材料解析与增强转写完成后可进入转写校正。可以离开页面，处理会继续。</p>
   <p v-else class="small muted mt16" role="status">{{canStart?'录音和材料已就绪，点击开始处理继续。':'请先等待录音及背景材料就绪；解析失败的材料可重试或移除。'}}</p>
   <div v-if="!hasTask" class="aci-process-actions"><button class="btn primary" :disabled="!canStart" @click="start">开始处理</button></div>
   <div v-else-if="!docked" class="aci-process-actions"><button v-if="failed" class="btn primary" :disabled="busy||!canMutate" @click="retry">重试处理</button><RouterLink v-else class="btn primary" :to="'/meeting/'+meetingId+'/processing'">查看处理进度</RouterLink></div>
  </div>
 </section>
 <footer v-if="docked&&hasTask" ref="dock" class="acf-dock" role="region" aria-label="当前步骤确认操作"><div class="acf-inner"><div class="acf-hint">{{failed?'处理失败，请查看原因后重试；已有内容保留。':ready?'材料解析处理已完成，核对后进入转写校正。':'材料尚未处理完成，请先完成解析或处理异常。'}}</div><div class="acf-actions"><button v-if="failed" class="btn primary" :disabled="busy||!canMutate" @click="retry">重试处理</button><RouterLink v-else-if="ready" class="btn primary" :to="'/meeting/'+meetingId+'/transcript'">进入转写校正</RouterLink><button v-else class="btn primary" disabled>进入转写校正</button></div></div></footer>
</template>
