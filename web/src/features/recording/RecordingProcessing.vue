<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch} from 'vue';
import {useApplication} from '../../application';
import {recordingProcessingApi,type RecordingProcessing} from './processing-api';
import {createRecordingProcessIntent} from './recording-process-intent';
import {materialJobsJournal} from '../import/useMaterials';
import RecordingArchivePlayback from './RecordingArchivePlayback.vue';
import {recordingDuration,type RecordingSession,type RecordingPartSource,type RecordingArchiveStatus} from './recording-api';
import {ApiError} from '../../shared/api/client';
import Icon from '../../shared/Icon.vue';
import {useFixedDockHeight} from '../../shared/composables/useFixedDockHeight';
import ProcessingMaterialRow from '../import/ProcessingMaterialRow.vue';
import {useProcessingMaterials} from '../import/useProcessingMaterials';
const props=withDefaults(defineProps<{meetingId:string;sessionId?:string|null;canMutate:boolean;autoStart?:boolean;autoResume?:boolean;docked?:boolean;materialsEditing?:boolean;existingTranscript?:boolean;confirmed?:boolean}>(),{sessionId:null,autoStart:false,autoResume:true,docked:true});
const emit=defineEmits<{accepted:[];materials:[kind:'materials'|'local'|'library']}>(),app=useApplication(),api=recordingProcessingApi(app.session.client),journal=createRecordingProcessIntent(app.journals);
const value=ref<RecordingProcessing|null>(null),error=ref<string|null>(null),busy=ref(false),sessionId=ref<string|null>(null);
const pendingMaterials=materialJobsJournal(app.journals),recording=ref<RecordingSession|null>(null);
const listening=ref<string|null>(null),archive=ref<RecordingArchiveStatus|null>(null);
const archiveSettled=computed(()=>archive.value&&['SUCCEEDED','FAILED','CANCELLED','STALE'].includes(archive.value.state));
const parts=ref<RecordingPartSource[]>([]),dock=ref<HTMLElement>();
const hasTask=computed(()=>!!value.value&&value.value.state!=='NOT_REQUESTED'),ready=computed(()=>value.value?.state==='SUCCEEDED'),failed=computed(()=>value.value?.state==='FAILED');
const materialListing=useProcessingMaterials((id,signal)=>app.materials.list(id,signal),()=>app.session.current.value?.actor.enterpriseId??null,()=>props.meetingId,()=>value.value?.state==='NOT_REQUESTED');
const materialFiles=computed(()=>hasTask.value?value.value?.materialFiles??null:materialListing.value.value?.items??null);
const archiveFailed=computed(()=>archive.value&&['FAILED','CANCELLED','STALE'].includes(archive.value.state));
const stale=computed(()=>value.value?.state==='STALE'||value.value?.state==='CANCELLED');
const complete=computed(()=>value.value?ready.value&&!value.value.materialsChanged:undefined);
const reviewPrevious=computed(()=>stale.value||ready.value&&value.value?.materialsChanged);
const footerHint=computed(()=>{
 if(value.value?.state==='CANCELLED')return '本次处理已取消，已保存录音可继续使用。';
 if(stale.value)return '本次结果已过期，请核对材料和现有版本。';
 if(failed.value)return '处理失败，请查看原因后重试；已有内容保留。';
 if(reviewPrevious.value)return '背景材料有待应用修改，已有转写使用上次提交的材料。';
 return ready.value?'材料解析处理已完成，核对后进入转写校正。':'材料尚未处理完成，请先完成解析或处理异常。';
});
const canStart=computed(()=>value.value?.state==='NOT_REQUESTED'&&!!archive.value?.available&&!error.value&&!busy.value&&!props.materialsEditing&&props.canMutate&&parts.value.length>0&&!!materialListing.value.value&&!materialListing.busy.value&&!materialListing.value.value.materialsPending&&materialListing.value.value.items.every(item=>item.state==='READY'));
useFixedDockHeight(dock);
const labels:Record<RecordingProcessing['state'],string>={NOT_REQUESTED:'录音已保存，可开始处理',WAITING_ARCHIVE:'正在保存录音',COPYING:'正在准备录音来源',TRANSCRIBING:'正在识别原始录音',ENHANCING:'正在根据背景材料增强转写',SUCCEEDED:'转写与增强完成',FAILED:'处理失败，请重试',CANCELLED:'本次处理已取消，已保存录音可继续使用。',STALE:'材料或转写版本已变化，本次结果已过期。'};
const failureReason=computed(()=>{
 if(!failed.value)return null;
 const reasons:Record<string,string>={
  RECORDING_ARCHIVE_FAILED:'录音归档未完成，已确认的音频分片保留。请重试处理。',
  RECORDING_TRANSCRIPTION_FAILED:'原始录音识别未完成，已保存录音保留。请重试处理。',
  ENHANCEMENT_RETRY_SAME_REQUEST:'背景材料增强暂未完成，原始录音和转写已保留。请重试处理，继续使用本次锁定的材料。'
 };
 return reasons[value.value?.errorCode??'']??'录音处理暂未完成，已保存内容保留。请重试处理。';
});
let timer:ReturnType<typeof setTimeout>|undefined,readRequest:AbortController|null=null,epoch=0,mounted=false,completionEmitted=false,autoAttempted=false;
const partsLoaded=ref(false),commandError=ref<string|null>(null);
function inProgress(){return !!value.value&&['WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING'].includes(value.value.state);}
function stopReading(){clearTimeout(timer);readRequest?.abort();readRequest=null;}
function schedule(){
 clearTimeout(timer);
 if(mounted&&!document.hidden&&!busy.value&&!error.value&&inProgress())timer=setTimeout(load,1500);
}
function publish(result:RecordingProcessing){
 value.value=result;
 if(result.state==='SUCCEEDED'&&!completionEmitted){completionEmitted=true;emit('accepted');}
}
async function load(){
 stopReading();
 if(!mounted||!app.session.current.value||busy.value||document.hidden)return;
 const request=new AbortController();readRequest=request;
 const signal=request.signal;
 try{
  const current=recording.value??(props.sessionId?await app.recordingApi.current(props.sessionId,signal):await app.recordingApi.currentForMeeting(props.meetingId,signal));
  if(signal.aborted)return;
  if(!current)throw Error('尚无已保存录音。');
  sessionId.value=current.sessionId;recording.value=current;
  const [result,sources]=await Promise.all([api.read(current.sessionId,signal),partsLoaded.value?null:app.recordingApi.partSources(current,signal)]);
  if(signal.aborted)return;
  if(result.meetingId!==props.meetingId)throw Error('录音归属暂无法核对');
  if(sources){parts.value=sources.filter(part=>part.sampleCount>0);partsLoaded.value=true;}
  error.value=null;publish(result);
 }catch(cause){
  if(!signal.aborted){
   error.value=cause instanceof Error?cause.message:'处理状态暂不可读取';
   if(cause instanceof ApiError&&[401,403,404].includes(cause.status)){value.value=null;parts.value=[];partsLoaded.value=false;recording.value=null;sessionId.value=null;}
  }
 }finally{if(!signal.aborted){readRequest=null;schedule();}}
}
function maybeAutoStart(){
 const actor=app.session.current.value?.actor.enterpriseId;
 if(!mounted||!actor||!sessionId.value||!value.value||error.value||props.materialsEditing||!props.canMutate||busy.value||autoAttempted)return;
 const recovered=journal.load(actor,props.meetingId);
 const replay=props.autoResume&&recovered.kind==='RESTORED';
 if(!replay&&!(props.autoStart&&canStart.value))return;
 if(replay&&!recovered.value.expectedRevisionToken&&!canStart.value)return;
 autoAttempted=true;void start();
}
watch([canStart,value,()=>props.materialsEditing,()=>props.autoStart],maybeAutoStart,{flush:'post'});
async function start(){
 const actor=app.session.current.value?.actor.enterpriseId,id=sessionId.value,meeting=props.meetingId,turn=epoch;if(!actor||!id||busy.value||props.materialsEditing||!props.canMutate)return;
 const current=()=>mounted&&turn===epoch&&actor===app.session.current.value?.actor.enterpriseId;
 stopReading();busy.value=true;commandError.value=null;
 try{
  const restored=journal.load(actor,meeting);
  if(restored.kind==='INVALID'||restored.kind==='UNAVAILABLE')throw Error('原处理请求暂无法读取，请保留页面重试');
  if(restored.kind==='RESTORED'&&restored.value.session!==id)throw Error('原处理请求属于另一段录音，请先核对。');
  let intent=restored.kind==='RESTORED'&&restored.value.expectedRevisionToken?restored.value:null;
  if(!intent){
   const pending=pendingMaterials.load(actor,meeting);
   if(pending.kind!=='EMPTY'&&(pending.kind!=='RESTORED'||pending.value.length))throw Error('背景材料有待核对的添加操作，请打开背景资料重试或移除。');
   const materials=materialListing.value.value??await app.materials.list(meeting,new AbortController().signal);if(!current()||props.materialsEditing)return;if(materials.materialsPending||materials.items.some(item=>item.state!=='READY'))throw Error('背景材料尚未就绪，请重试或移除。');intent={actor,meeting,session:id,requestId:restored.kind==='RESTORED'?restored.value.requestId:crypto.randomUUID(),expectedRevisionToken:materials.meetingRevision,materialSnapshotId:materials.materialSnapshotId};}
  if(journal.save(actor,meeting,intent)!=='SAVED')throw Error('无法保存原处理请求');
  const accepted=await api.start(id,intent.requestId,intent.expectedRevisionToken!,intent.materialSnapshotId??null);if(!current())return;
  if(accepted.meetingId!==meeting)throw Error('录音归属暂无法核对');publish(accepted);
  if(!journal.clear())throw Error('处理已受理，请重试核对原请求');
 }catch(cause){
  if(current()){
   if(cause instanceof ApiError&&cause.status===409&&cause.code==='RECORDING_MATERIALS_CHANGED'){
    // The server definitively rejected this snapshot. A later poll can bind the updated materials.
    if(journal.save(actor,meeting,{actor,meeting,session:id,requestId:crypto.randomUUID()})!=='SAVED')commandError.value='材料已变化，原请求暂无法更新。';
    else commandError.value='材料已变化，请核对最新材料后重新开始处理。';
    void materialListing.reload();
   }else commandError.value=cause instanceof Error?cause.message:'处理请求尚未确认';
  }
 }finally{if(current()){busy.value=false;schedule();}}
}
async function retry(){
 if(!sessionId.value||busy.value||!props.canMutate||!failed.value)return;
 const turn=epoch,meeting=props.meetingId;stopReading();busy.value=true;commandError.value=null;
 try{const result=await api.retry(sessionId.value,value.value?.taskId??null);if(!mounted||turn!==epoch)return;if(result.meetingId!==meeting)throw Error('录音归属暂无法核对');error.value=null;publish(result);}
 catch(cause){if(mounted&&turn===epoch)commandError.value=cause instanceof Error?cause.message:'重试未完成';}
 finally{if(mounted&&turn===epoch){busy.value=false;schedule();}}
}
async function changeRun(action:'CANCEL'|'RESTART'){
 const run=value.value?.runId,id=sessionId.value,meeting=props.meetingId,turn=epoch;
 if(!run||!id||busy.value||!props.canMutate)return;
 busy.value=true;stopReading();commandError.value=null;
 try{const revision=action==='CANCEL'?'0':(await app.materials.list(meeting,new AbortController().signal)).meetingRevision;
  if(turn!==epoch)return;const result=await api.change(id,action,run,revision,crypto.randomUUID());
  if(turn===epoch&&result.meetingId===meeting){completionEmitted=false;publish(result);emit('accepted');}
 }catch(cause){if(turn===epoch)commandError.value=cause instanceof Error?cause.message:'操作未完成，请重新核对';}
 finally{if(turn===epoch){busy.value=false;void load();}}
}
function beforeLeave(){return !busy.value;}
function visibilityChanged(){if(document.hidden)stopReading();else void load();}
function refresh(){void load();}
watch(()=>[props.meetingId,props.sessionId,app.session.current.value?.actor.enterpriseId],()=>{
 epoch++;stopReading();archive.value=null;value.value=null;error.value=null;commandError.value=null;sessionId.value=null;recording.value=null;
 listening.value=null;parts.value=[];partsLoaded.value=false;busy.value=false;completionEmitted=false;autoAttempted=false;if(mounted)void load();
},{flush:'sync'});
onMounted(()=>{mounted=true;document.addEventListener('visibilitychange',visibilityChanged);void load();});
onUnmounted(()=>{mounted=false;epoch++;stopReading();document.removeEventListener('visibilitychange',visibilityChanged);});
defineExpose({beforeLeave,state:computed(()=>value.value?.state),complete,reloadMaterials:()=>{if(value.value?.state==='NOT_REQUESTED')void materialListing.reload();refresh();}});
</script>
<template>
 <section class="card" aria-label="录音处理">
  <div class="card-h" :class="hasTask?'aci-processing-top':''"><div><div class="card-t">材料导入与解析</div><div class="small muted mt8">{{!value?'正在读取录音处理状态…':hasTask?labels[value.state]:'录音已自动关联，无需重复上传；可继续补充背景资料。'}}</div></div><span class="ml-a"></span><button v-if="value&&!hasTask" class="btn sm" :disabled="!archiveSettled" @click="emit('materials','materials')">添加背景资料</button></div>
  <div class="card-p">
   <p v-if="error" class="ac-error" role="alert">{{error}} <button class="btn sm ghost" :disabled="busy" @click="refresh">重新读取状态</button></p>
   <p v-if="commandError" class="ac-error mb12" role="alert">{{commandError}} <button v-if="hasTask&&!failed" class="btn sm" :disabled="busy" @click="start">核对原处理请求</button></p>
   <p v-if="failureReason" class="ac-error mb12" role="alert">{{failureReason}}</p>
   <div v-if="value" :class="hasTask?'aci-material-grid':''">
    <section><h3>会议主文件 <span v-if="hasTask" class="small muted">{{parts.length}} 个</span></h3><p v-if="!parts.length" class="small muted" role="status">{{partsLoaded?'录音中没有已保存的有效片段。':'正在读取录音文件…'}}</p><div v-for="part in parts" :key="part.partId" class="aci-progress-file"><div class="aci-progress-file-top"><span class="aci-file-type"><Icon name="mic" :size="18"/></span><div class="aci-progress-name"><button v-if="recording&&part.state==='AVAILABLE'" class="recording-source" :aria-label="'回听第'+part.partNo+'段录音'" @click="listening=listening===part.partId?null:part.partId">第{{part.partNo}}段录音.wav</button><b v-else>第{{part.partNo}}段录音.wav</b><div class="small muted">会议主文件 · {{recordingDuration(part.sampleCount)}} · WAV</div></div><span class="tag" :class="failed||stale?'red':ready||!hasTask?'green':'orange'">{{failed?'处理失败':value?.state==='CANCELLED'?'已取消':stale?'结果已过期':ready?'识别及转写增强完成':hasTask?labels[value.state]:'已保存'}}</span></div><audio v-if="recording&&listening===part.partId" :src="app.recordingApi.partAudioUrl(recording,part.partId)" controls preload="metadata" :aria-label="'第'+part.partNo+'段录音回听'"/><div v-if="ready" class="progress mt12" aria-hidden="true"><div class="bar" style="width:100%"></div></div></div></section>
    <section><div v-if="!hasTask" class="divider"></div><div :class="hasTask?'aci-material-section-title':'ac-material-heading'"><h3>{{hasTask?'背景材料及相关文档':'会议背景材料及相关文档'}} <span class="small muted">{{hasTask?(materialFiles?materialFiles.length+' 个':''):'（选填）'}}</span></h3><button v-if="hasTask" class="btn sm" @click="emit('materials','materials')">调整背景材料</button><div v-else class="flex wrap"><button class="btn" :disabled="!archiveSettled" @click="emit('materials','local')">上传本地文件</button><button class="btn" :disabled="!archiveSettled" @click="emit('materials','library')">从知识库选择</button></div></div>
     <p v-if="!hasTask&&materialListing.error.value" class="small ac-error" role="alert">{{materialListing.error.value}} <button class="btn sm ghost" @click="materialListing.reload">重新读取背景材料</button></p><p v-else-if="materialFiles===null" class="small muted" role="status">{{hasTask?'本次任务未返回材料明细。':'正在读取背景材料…'}}</p><template v-else><ProcessingMaterialRow v-for="item in materialFiles" :key="item.materialRevisionId" :item="item"/><div v-if="!materialFiles.length" class="aci-empty-material">{{hasTask?'未添加背景材料，将基于会议主文件处理。':'未添加背景材料，将基于录音处理。'}}</div></template>
    </section>
   </div>
   <div v-if="value?.materialsChanged" class="banner warn mt16">材料有待应用修改，当前任务仍使用提交时锁定的材料版本。请在转写校正中核对并应用新材料。</div>
   <p v-if="hasTask" class="small muted mt16">{{value?.taskId?'处理任务 '+value.taskId+' · ':''}}<template v-if="value?.materialSnapshotId">材料快照 {{value.materialSnapshotId}} · </template>{{ready?'本次录音已完成识别和增强。':stale?'请核对材料和已有转写。':failed?'已保存录音可继续回听。':'可以离开页面，处理会继续。'}}</p>
   <p v-else-if="value" class="small muted mt16" role="status">{{archiveFailed?'录音归档未完成，请先在下方重试归档。':!archiveSettled?'录音正在归档，完成后可以调整背景材料。':canStart?'录音和材料已就绪，点击开始处理继续。':'请先等待录音及背景材料就绪；解析失败的材料可重试或移除。'}}</p>
   <div v-if="value&&!hasTask" class="aci-process-actions"><button class="btn primary" :disabled="!canStart" @click="start">开始处理</button></div>
   <div v-else-if="hasTask&&!docked" class="aci-process-actions"><button v-if="failed" class="btn primary" :disabled="busy||!canMutate" @click="retry">重试处理</button><RouterLink v-else class="btn primary" :to="'/meeting/'+meetingId+'/processing'">查看处理进度</RouterLink></div>
   <RecordingArchivePlayback v-if="recording" :recording="recording" @status="archive=$event"/>
  </div>
 </section>
 <footer v-if="docked&&hasTask&&(!confirmed||!complete)" ref="dock" class="acf-dock" role="region" aria-label="当前步骤确认操作">
  <div class="acf-inner"><div class="acf-hint">{{footerHint}}</div>
   <div class="acf-actions">
    <button v-if="value?.runId&&(inProgress()||failed)" class="btn" :disabled="busy||!canMutate" @click="changeRun('CANCEL')">取消处理</button>
    <button v-if="failed" class="btn primary" :disabled="busy||!canMutate" @click="retry">重试处理</button>
    <RouterLink v-else-if="reviewPrevious&&existingTranscript" class="btn primary" :to="'/meeting/'+meetingId+'/transcript'">核对已有转写</RouterLink>
    <button v-else-if="stale&&value?.runId" class="btn primary" :disabled="busy||!canMutate" @click="changeRun('RESTART')">按当前版本重新处理</button>
    <button v-else-if="reviewPrevious" class="btn" @click="refresh">重新读取状态</button>
    <RouterLink v-else-if="complete" class="btn primary" :to="'/meeting/'+meetingId+'/transcript'">进入转写校正</RouterLink>
    <button v-else class="btn primary" disabled>进入转写校正</button>
   </div>
  </div>
 </footer>
</template>

<style scoped>.recording-source{padding:0;border:0;background:none;color:inherit;font:inherit;font-weight:600;cursor:pointer}.recording-source:hover{text-decoration:underline}.aci-progress-file audio{width:100%;margin-top:8px}</style>
