<script setup lang="ts">
import {onUnmounted} from 'vue';
import {useApplication} from '../../application';
import MeetingInfoDrawer from '../meetings/MeetingInfoDrawer.vue';
import Icon from '../../shared/Icon.vue';
import {usePublishedMaterials} from './usePublishedMaterials';
const props=defineProps<{meetingId:string}>(),emit=defineEmits<{denied:[cause:unknown]}>(),app=useApplication();
const view=usePublishedMaterials((id,signal)=>app.materials.list(id,signal),()=>app.session.current.value?.actor.enterpriseId??null,()=>props.meetingId,cause=>emit('denied',cause));
defineExpose({show:view.open});onUnmounted(view.dispose);
</script>
<template>
 <MeetingInfoDrawer :open="view.opened.value" title="背景材料" close-label="关闭背景材料" @close="view.close">
  <section class="card card-p m19-background-materials" aria-label="会议背景材料"><div class="between mb12"><b>会议背景材料</b><span v-if="view.value.value" class="tag gray">{{view.value.value.items.length}}份</span></div>
   <p v-if="view.busy.value" role="status">正在读取背景材料…</p>
   <div v-else-if="view.error.value" class="banner error" role="alert">{{view.error.value}}<button class="btn sm" @click="view.reload">重新读取材料</button></div>
   <div v-else-if="view.value.value" class="m19-background-file-list"><div v-for="item in view.value.value.items" :key="item.id" class="m19-background-file"><span class="m19-background-file-icon"><Icon :name="item.source==='KNOWLEDGE'?'kb':'file'" :size="18"/></span><div class="m19-background-file-info"><div class="b" :title="item.name">{{item.name}}</div><div class="small muted mt8">{{item.name.split('.').pop()?.toUpperCase()}} · {{item.source==='KNOWLEDGE'?'知识库':'本地文件'}}<template v-if="item.sourceVersion"> · {{item.sourceVersion}}</template></div></div></div><div v-if="!view.value.value.items.length" class="empty">暂无背景材料</div></div>
  </section>
 </MeetingInfoDrawer>
</template>
<style scoped>
.m19-background-file{display:flex;gap:12px;padding:14px 0;border-bottom:1px solid var(--border)}.m19-background-file:last-child{border-bottom:0}.m19-background-file-icon{width:36px;height:36px;display:flex;align-items:center;justify-content:center;border-radius:8px;background:var(--orange-bg);color:var(--orange);flex-shrink:0}.m19-background-file-info{min-width:0;flex:1;overflow-wrap:anywhere}.m19-background-file-info>.b{line-height:1.6}.m19-background-file-info .small{line-height:1.6}
</style>
