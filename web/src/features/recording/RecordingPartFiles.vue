<script setup lang="ts">
import {ref} from 'vue';
import {useApplication} from '../../application';
import Icon from '../../shared/Icon.vue';
import type {RecordingSession} from './recording-api';
type PartRow={id:string;index:number;elapsedMs:number;savedElapsedMs:number;incomplete:boolean;state:string};
const props=defineProps<{recording:RecordingSession;phase:string;rows:readonly PartRow[]}>();
const app=useApplication(),playing=ref<string|null>(null),error=ref<string|null>(null);
function clock(ms:number){const seconds=Math.floor(ms/1000);return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');}
function play(part:PartRow){error.value=null;playing.value=playing.value===part.id?null:part.id;}
</script>
<template><div class="acr-saved-parts" aria-label="已记录录音段"><span class="acr-parts-caption">已保存录音 · {{rows.length}}个文件</span><div class="acr-saved-part-files"><template v-for="part in rows" :key="part.id"><div class="acr-saved-part"><Icon name="file" :size="12"/><button class="part-link" :aria-label="'回听第'+part.index+'段录音'" :disabled="part.savedElapsedMs<part.elapsedMs" @click="play(part)">第{{part.index}}段 · 录音片段 {{String(part.index).padStart(2,'0')}}.wav</button><time>{{clock(part.elapsedMs)}}</time><small>{{part.state==='SYSTEM_INTERRUPTED'?'系统中断':part.state==='INTERRUPTED'?'中断保存':part.state==='SAVED'?'结束保存':'结束原因未知'}}{{part.incomplete?' · 尾段完整性未知':''}}{{part.savedElapsedMs<part.elapsedMs?' · 待确认保存':''}}</small></div><audio v-if="playing===part.id" :key="part.id" :src="app.recordingApi.partAudioUrl(recording,part.id)" controls preload="metadata" :aria-label="'第'+part.index+'段录音回听'" @error="error='本段暂不可播放，请核对登录与保存状态后重新打开。'"></audio></template></div><p v-if="error" class="ac-error small" role="alert">{{error}}</p></div></template>
<style scoped>.part-link{border:0;background:transparent;padding:0;color:inherit;font:inherit;text-align:left;cursor:pointer;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.part-link:hover{text-decoration:underline}.part-link:disabled{cursor:default;opacity:.6}.acr-saved-part-files audio{width:100%;height:32px}</style>
