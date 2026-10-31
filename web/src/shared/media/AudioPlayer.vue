<script setup lang="ts">
import {ref} from 'vue';
const props=defineProps<{src?:string|null;startMs?:number;disabled?:boolean}>();
const audio=ref<HTMLAudioElement>();
function seek(){if(audio.value&&props.startMs!==undefined)audio.value.currentTime=Math.max(0,props.startMs/1000);}
function play(){seek();void audio.value?.play();}
defineExpose({play,seek});
</script>
<template><div class="audio-player"><audio ref="audio" :src="src??undefined" preload="metadata" controls @loadedmetadata="seek"/><span v-if="!src" class="muted">当前片段没有可播放的原录音</span></div></template>
<style scoped>.audio-player{display:flex;align-items:center;gap:10px}.audio-player audio{max-width:100%}.muted{color:var(--muted);font-size:12px}</style>
