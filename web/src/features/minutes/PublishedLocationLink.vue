<script setup lang="ts">
import {watch,onUnmounted} from 'vue';import {RouterLink} from 'vue-router';import {useApplication} from '../../application';import Icon from '../../shared/Icon.vue';import {usePublishedLocation} from './usePublishedLocation';
const props=defineProps<{meetingId:string}>(),app=useApplication();
const location=usePublishedLocation({current:(id,signal)=>app.publications.current(id,signal),context:(id,publication,signal)=>app.meetingLibrary.context(id,publication,signal)},()=>app.session.current.value?.actor.enterpriseId??null,()=>props.meetingId);
const stop=watch(()=>[app.session.current.value?.actor.enterpriseId,props.meetingId],()=>void location.reload(),{immediate:true});onUnmounted(()=>{stop();location.dispose();});
</script>
<template><div class="m19-published-location"><span class="muted">发布知识库：</span><RouterLink v-if="location.value.value" :to="{path:'/meeting-library/'+encodeURIComponent(location.value.value.libraryId),query:{meeting:meetingId}}" class="m19-kb-link"><Icon name="kb" :size="13"/> {{location.value.value.libraryName}}</RouterLink><span v-else-if="location.busy.value" role="status">正在读取发布位置…</span><template v-else><span>原知识库暂不可用</span><button class="btn sm ghost" aria-label="重新读取知识库位置" @click="location.reload">重试</button></template></div></template>
