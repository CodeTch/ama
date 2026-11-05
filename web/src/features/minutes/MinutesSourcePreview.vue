<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue';
import {useApplication} from '../../application';
import Icon from '../../shared/Icon.vue';
import type {Source} from '../../shared/api/minutes-inspection';
import type {MinutesSourceLabel} from './useMinutesSourceLabels';
import {useMinutesSourceMedia} from './useMinutesSourceMedia';
const props=defineProps<{meetingId:string;source:Source;active:boolean;label?:MinutesSourceLabel}>(),emit=defineEmits<{denied:[cause:unknown]}>(),app=useApplication();
const state=useMinutesSourceMedia({source:(...args)=>app.transcripts.source(...args),materials:(...args)=>app.materials.list(...args)},()=>app.session.current.value?.actor.enterpriseId??null,()=>props.meetingId,()=>props.active,()=>props.source,cause=>emit('denied',cause));
const element=ref<HTMLMediaElement>(),playing=ref(false),ready=ref(false),playError=ref('');
const video=computed(()=>state.media.type.value.startsWith('video/'));
function clock(ms:number){const seconds=Math.floor(ms/1000);return String(Math.floor(seconds/60)).padStart(2,'0')+':'+String(seconds%60).padStart(2,'0');}
const title=computed(()=>state.fileName.value??(state.loading.value||state.media.busy.value?'正在核对来源原件…':props.source.locator.kind==='TRANSCRIPT'?'转写来源':'背景材料'));
const caption=computed(()=>{if(props.source.locator.kind!=='TRANSCRIPT'){const locator=props.source.locator;return [locator.page_noToken?'第'+locator.page_noToken+'页':'',locator.paragraph_noToken?'第'+locator.paragraph_noToken+'段':'',locator.section_path??''].filter(Boolean).join(' · ');}if(state.startMs.value===null)return state.loading.value?'':'转写文件';const speaker=props.label?.kind==='recording'?props.label.label.replace(/^\d+:\d+\s+/,''):'';return [video.value?'视频':'录音',clock(state.startMs.value),speaker].filter(Boolean).join(' · ');});
const error=computed(()=>playError.value||(state.error.value instanceof Error?state.error.value.message:state.error.value?'来源原件暂不可读取。':'')||state.media.error.value);
function stop(){element.value?.pause();playing.value=false;}
function metadata(){const media=element.value;ready.value=!!media&&Number.isFinite(media.duration)&&state.startMs.value!==null&&state.startMs.value/1000<=media.duration;if(media&&!ready.value)playError.value='此段时间无法在原件中定位，请重新核对来源。';}
function failure(){stop();ready.value=false;playError.value='当前浏览器无法播放此原件，可读取原始文件核对。';}
async function play(){const media=element.value;if(!media||!state.playable.value||!ready.value||state.startMs.value===null)return;if(!media.paused){stop();return;}playError.value='';try{media.currentTime=state.startMs.value/1000;await media.play();}catch{failure();}}
watch(()=>state.media.url.value,()=>{stop();ready.value=false;playError.value='';},{flush:'sync'});
watch(()=>props.active,active=>{if(!active)stop();},{flush:'sync'});
onBeforeUnmount(stop);
</script>
<template><section class="card card-p source-original-card"><h3 class="source-original-title"><a :href="source.originalBytes.route" target="_blank" rel="noopener" title="读取原始文件">{{title}}</a></h3><div v-if="caption" class="small muted mt8">{{caption}}</div><p class="small muted mt8">来源版本：<span :title="source.sourceVersion">{{source.sourceVersion}}</span></p><p v-if="source.context" class="small muted mt8">材料版本 {{source.context.materialRevisionToken}}<template v-if="source.context.libraryId"> · 知识库 {{source.context.libraryId}} · 文档 {{source.context.documentId}} · 源版本 {{source.context.externalVersion}}</template></p><div class="mt12 source-original-quote">{{source.text}}</div>
 <div v-if="state.startMs.value!==null" class="mt12"><button type="button" class="btn sm primary" :disabled="!state.playable.value||!ready" @click="play"><Icon :name="playing?'pause':'play'" :size="13"/> {{playing?'暂停播放':'从 '+clock(state.startMs.value)+' 播放'}}</button></div>
 <video v-if="video&&state.media.url.value" ref="element" :src="state.media.url.value" :hidden="!playing" class="source-original-video mt12" preload="metadata" playsinline controls @loadedmetadata="metadata" @play="playing=true" @pause="playing=false" @ended="playing=false" @error="failure"/>
 <audio v-else-if="state.media.url.value" ref="element" :src="state.media.url.value" preload="metadata" @loadedmetadata="metadata" @play="playing=true" @pause="playing=false" @ended="playing=false" @error="failure"/>
 <p v-if="error" role="alert" class="small mt12">{{error}} <button type="button" class="btn sm" @click="state.reload">重试读取原件</button></p></section></template>
<style scoped>.source-original-card{min-width:0;overflow-wrap:anywhere}.source-original-title{font-size:16px;margin:0;overflow-wrap:anywhere}.source-original-title a{color:inherit;text-decoration:none}.source-original-title a:focus-visible{outline:1px solid var(--navy);outline-offset:3px}.source-original-quote{white-space:pre-wrap;line-height:1.8}.source-original-video{width:100%;max-height:40vh}</style>
