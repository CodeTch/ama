<script setup lang="ts">
import StableLabel from '../../shared/StableLabel.vue';
import {computed,ref,watch,onUnmounted,nextTick} from 'vue';import {useApplication} from '../../application';import Icon from '../../shared/Icon.vue';import {useSourceMedia,type MediaScope} from './useSourceMedia';
const props=defineProps<{scope:MediaScope|null;active:boolean;published?:boolean;expectRecording?:boolean;sourceState?:'loading'|'error'|'ready'|'empty'}>();const {session,transcripts}=useApplication();const state=useSourceMedia((...args)=>transcripts.source(...args),()=>session.current.value?.actor.enterpriseId??null,()=>props.scope,URL,()=>props.active,(...args)=>transcripts.playback(...args));
const emit=defineEmits<{position:[position:{fileId:string;ms:number;playing:boolean}|null]}>();
const element=ref<HTMLMediaElement>(),current=ref(0),duration=ref(0),playing=ref(false),speed=ref(1),localError=ref<string|null>(null);const selectedSource=computed(()=>state.sources.value.find(source=>source.fileId===state.selected.value));
const video=computed(()=>state.type.value.startsWith('video/'));
function clock(seconds:number){const n=Math.max(0,Math.floor(seconds));return `${String(Math.floor(n/3600)).padStart(2,'0')}:${String(Math.floor(n/60)%60).padStart(2,'0')}:${String(n%60).padStart(2,'0')}`;}
function update(){const m=element.value;if(!m)return;current.value=m.currentTime;duration.value=Number.isFinite(m.duration)?m.duration:0;playing.value=!m.paused;speed.value=m.playbackRate;const fileId=state.selected.value;if(fileId)emit('position',{fileId,ms:Math.round(current.value*1000),playing:playing.value});}
function failure(){playing.value=false;emit('position',null);localError.value='录音播放失败，请重试或核对文件编码。';}
async function toggle(){const m=element.value;if(!m)return;localError.value=null;if(m.paused){try{await m.play();}catch{failure();}}else m.pause();}
function choose(file:string){if(file!==state.selected.value||state.error.value||localError.value)void state.select(file);}
function rate(){const m=element.value;if(!m)return;const speeds=[1,1.25,1.5,2];m.playbackRate=speeds[(speeds.indexOf(m.playbackRate)+1)%speeds.length]??1;update();}
function jump(seconds:number){const m=element.value;if(m&&Number.isFinite(m.duration)){m.currentTime=Math.min(m.duration,Math.max(0,seconds));update();}}
const waveform=ref<number[]>([]);
let waveGeneration=0;
async function loadWaveform(){
 const url=state.url.value, generation=++waveGeneration;
 const source=selectedSource.value;
 if(!url||!url.startsWith('blob:')||!source||source.sizeBytes>32*1024*1024||!duration.value||duration.value>2700||typeof AudioContext==='undefined')return;
 let context:AudioContext|null=null;
 try{
  context=new AudioContext({sampleRate:8000});
  const response=await fetch(url);
  const bytes=await response.arrayBuffer();
  if(generation!==waveGeneration||bytes.byteLength>32*1024*1024)return;
  const audio=await context.decodeAudioData(bytes);
  if(generation!==waveGeneration)return;
  const channel=audio.getChannelData(0);
  waveform.value=Array.from({length:64},(_,i)=>{
   const start=Math.floor(i*channel.length/64),end=Math.floor((i+1)*channel.length/64);
   let peak=0;
   const stride=Math.max(1,Math.ceil((end-start)/256));
   for(let index=start;index<end;index+=stride)peak=Math.max(peak,Math.abs(channel[index]!));
   return Math.min(100,Math.max(3,Math.round(peak*100)));
  });
 }catch{/* Audio decoding is optional for waveform display. */}
 finally{if(context&&context.state!=='closed')await context.close().catch(()=>{});}
}
let pendingSeek:{file:string;seconds:number;play:boolean}|null=null;
watch(state.selected,file=>{if(pendingSeek&&pendingSeek.file!==file)pendingSeek=null;});
function applySeek(){
 const intent=pendingSeek,media=element.value;
 if(!intent||!media?.readyState||state.selected.value!==intent.file||!props.active)return;
 pendingSeek=null;jump(intent.seconds);
 if(intent.play&&media.paused)void media.play().catch(()=>{if(element.value===media&&props.active)failure();});
}
function metadata(){update();void loadWaveform();applySeek();}
async function seek(file:string,startMs:number,play=false){
 if(!props.active||!Number.isSafeInteger(startMs)||startMs<0||!state.sources.value.some(s=>s.fileId===file))return;
 pendingSeek={file,seconds:startMs/1000,play};
 if(state.selected.value!==file||!state.url.value&&!state.busy.value){await state.select(file);await nextTick();}
 applySeek();
}
watch(state.url,()=>{waveGeneration++;waveform.value=[];element.value?.pause();emit('position',null);current.value=0;duration.value=0;playing.value=false;speed.value=1;localError.value=null;},{flush:'sync'});watch(()=>props.active,active=>{if(!active){pendingSeek=null;element.value?.pause();}});watch(()=>[props.scope?.meeting,props.scope?.accessRevisionToken],()=>{pendingSeek=null;});const logout=session.onLogout(()=>{element.value?.pause();state.dispose();});onUnmounted(()=>{waveGeneration++;logout();element.value?.pause();});defineExpose({seek});
</script>
<template>
 <div v-if="!state.sources.value.length&&published" class="empty">录音不可用<div class="small muted mt8">原录音保存完成且可用后可回听；纯转写会议无需录音。</div></div><div v-else-if="!state.sources.value.length" class="card card-p" :class="{'recording-placeholder':expectRecording&&sourceState==='loading'}"><b>会议录音</b><div class="small muted mt12">{{sourceState==='loading'?'正在读取录音来源…':sourceState==='error'?'录音来源暂未读取成功，请重新读取转写。':sourceState==='empty'?'转写结果尚未就绪，录音来源待核对。':'本次为文本转写文件，无原录音，不虚构播放时长。'}}</div></div>
 <div v-else class="aci-player-list"><div class="player"><div class="p-top"><Icon :name="video?'play':'mic'" :size="14"/> {{video?'会议视频':'会议录音'}}<span class="aci-source-count">{{state.sources.value.findIndex(s=>s.fileId===state.selected.value)+1}} / {{state.sources.value.length}}</span></div><div class="aci-playing-name" :title="state.name.value??''">{{state.name.value??selectedSource?.name??(state.busy.value?'正在读取原文件…':'会议录音')}}</div>
  <video v-if="video&&state.url.value" ref="element" :key="state.url.value" class="aci-video" :src="state.url.value" preload="metadata" playsinline @loadedmetadata="metadata" @durationchange="update" @timeupdate="update" @play="update" @pause="update" @ended="update" @error="failure"/>
  <audio v-else-if="state.url.value" ref="element" :key="state.url.value" :src="state.url.value" preload="metadata" @loadedmetadata="metadata" @durationchange="update" @timeupdate="update" @play="update" @pause="update" @ended="update" @error="failure"/>
  <div class="p-main">
   <button type="button" class="p-speed" :disabled="!duration||!!localError" aria-label="后退 10 秒" @click="jump(current-10)">−10</button>
   <button type="button" class="p-btn" :disabled="!state.url.value||state.busy.value||!!localError" aria-label="播放或暂停录音" :aria-pressed="playing" :title="playing?'暂停录音':'播放录音'" @click="toggle"><Icon :name="playing?'pause':'play'" :size="16"/></button>
   <button type="button" class="p-speed" :disabled="!duration||!!localError" aria-label="快进 10 秒" @click="jump(current+10)">+10</button>
   <input class="media-seek" type="range" min="0" :max="duration" step="0.01" :value="current" :disabled="!duration||!!localError" aria-label="播放进度" @input="jump(Number(($event.target as HTMLInputElement).value))"/>
   <button type="button" class="p-speed" :disabled="!state.url.value||!!localError" aria-label="播放速度" @click="rate"><StableLabel :labels="['1x','1.25x','1.5x','2x']">{{speed}}x</StableLabel></button>
  </div><div class="aci-media-time"><span>{{clock(current)}}</span><span>{{duration?clock(duration):state.error.value||localError?'时长不可用':'时长加载中'}}</span></div><div class="p-wave" aria-hidden="true"><i v-for="(peak,index) in waveform" :key="index" :style="{height:peak+'%'}" :class="{on:index/64<current/duration}"/></div><div v-if="state.error.value||localError" class="ac-error small" role="alert">{{state.error.value??localError}} <button class="btn sm" @click="state.selected.value&&state.select(state.selected.value)">重试</button></div>
 </div><div class="aci-file-playlist"><button v-for="(source,index) in state.sources.value" :key="source.fileId" class="aci-playlist-row" :class="{active:state.selected.value===source.fileId}" :aria-current="state.selected.value===source.fileId?'true':undefined" @click="choose(source.fileId)"><span>{{String(index+1).padStart(2,'0')}}</span><span>{{state.names.value[source.fileId]??source.name??'会议录音 '+(index+1)}}</span><small>{{state.selected.value===source.fileId&&duration?clock(duration):'--'}}</small></button></div></div>
</template>

<style scoped>.recording-placeholder{min-height:248px}.aci-media-time span:last-child,.aci-playlist-row small{min-inline-size:5.5em;text-align:right;font-variant-numeric:tabular-nums}.media-seek{min-width:0;flex:1;accent-color:var(--orange)}.p-main{gap:6px}.p-main .p-speed{padding-inline:5px}</style>
