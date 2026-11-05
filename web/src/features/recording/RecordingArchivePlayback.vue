<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {recordingDuration,type RecordingSession,type RecordingArchiveStatus} from './recording-api';

const props=defineProps<{recording:RecordingSession}>();
const emit=defineEmits<{status:[value:RecordingArchiveStatus|null]}>();
const {recordingApi,session}=useApplication();
const status=shallowRef<RecordingArchiveStatus|null>(null),busy=ref(false),error=ref<string|null>(null),expanded=ref(false);
const names:Record<RecordingArchiveStatus['state'],string>={READY:'等待归档',RUNNING:'正在归档',RECONCILING:'正在核对归档结果',SUCCEEDED:'录音归档完成',FAILED:'归档失败，原分片保留',CANCELLED:'归档已取消',STALE:'归档状态已变化'};
const retryable=computed(()=>status.value&&['FAILED','CANCELLED','STALE'].includes(status.value.state));
let generation=0,timer:ReturnType<typeof setTimeout>|undefined,deadline=0;
function waiting(){return !status.value||['READY','RUNNING','RECONCILING'].includes(status.value.state);}
async function load(retry=false){
 if(busy.value||document.hidden)return;
 const turn=generation,actor=session.current.value?.actor.enterpriseId,recording=props.recording;
 if(!actor)return;
 const current=()=>turn===generation&&actor===session.current.value?.actor.enterpriseId&&props.recording.sessionId===recording.sessionId;
 busy.value=true;error.value=null;
 try{
  const value=await(retry?recordingApi.retryArchive(recording):recordingApi.archive(recording));
  if(!current())return;
  status.value=value;emit('status',value);
  if(waiting()&&!document.hidden){
   if(Date.now()<deadline)timer=setTimeout(load,1000);
   else error.value='归档仍未确认，请稍后刷新状态。';
  }
 }catch(cause){
  if(current()){status.value=null;expanded.value=false;emit('status',null);error.value=cause instanceof Error?cause.message:'录音归档暂不可读取';}
 }finally{if(current())busy.value=false;}
}
function refresh(retry=false){clearTimeout(timer);deadline=Date.now()+180000;void load(retry);}
function visibilityChanged(){clearTimeout(timer);if(!document.hidden&&waiting())refresh();}
const stop=watch(()=>[session.current.value?.actor.enterpriseId,props.recording.sessionId],()=>{
 generation++;busy.value=false;status.value=null;error.value=null;expanded.value=false;
 if(typeof document!=='undefined')refresh();
},{immediate:true});
onMounted(()=>document.addEventListener('visibilitychange',visibilityChanged));
onUnmounted(()=>{generation++;stop();clearTimeout(timer);document.removeEventListener('visibilitychange',visibilityChanged);});
</script>

<template>
 <section class="archive" aria-label="录音归档">
  <div class="archive-line">
   <span class="small muted" role="status">{{status?names[status.state]:error?'录音归档状态待核对':'正在读取录音归档…'}}<template v-if="status?.available"> · {{recordingDuration(status.sampleCount)}}</template></span>
   <button v-if="status?.available" type="button" class="btn sm" :aria-expanded="expanded" @click="expanded=!expanded">{{expanded?'收起回听':'回听完整录音'}}</button>
   <button v-if="retryable" type="button" class="btn sm" :disabled="busy" @click="refresh(true)">重试录音归档</button>
   <button v-if="!status?.available||expanded" type="button" class="btn sm ghost" :disabled="busy" @click="refresh()">刷新归档状态</button>
  </div>
  <audio v-if="expanded&&status?.available" :src="recordingApi.audioUrl(recording)" controls preload="metadata" aria-label="录音归档回听"
   @error="error='音频暂不可播放，请核对登录和归档状态。'"></audio>
  <p v-if="error" class="ac-error small mt8" role="alert">{{error}}</p>
 </section>
</template>

<style scoped>
.archive{margin-top:16px;padding-top:12px;border-top:1px solid var(--border)}
.archive-line{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.archive audio{display:block;width:100%;margin-top:12px}
</style>
