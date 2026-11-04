<script setup lang="ts">
import {computed} from 'vue';import Icon from '../../shared/Icon.vue';
import {workspaceStages} from './workspace-stages';
import type {WorkspaceNavigation} from '../../shared/api/workspace';
const props=defineProps<{value:WorkspaceNavigation;publishedObserved?:boolean;active?:string}>();
const emit=defineEmits<{select:[stage:string]}>();
const stages=['processing','transcript','minutes','confirm','publish'],labels=['材料导入','转写校正','AI纪要','会议确认','知识库发布'];
const capability=computed(()=>workspaceStages(props.value,props.publishedObserved));
const complete=computed(()=>capability.value.complete);
const enabled=computed(()=>stages.map(stage=>capability.value.available[stage as keyof typeof capability.value.available]));
</script>
<template><nav class="aci-flow meeting-progress" aria-label="会议处理环节"><button v-for="(label,index) in labels"  :aria-label="label" :key="label" class="aci-step" :class="{active:active===stages[index]||active==='published'&&index===4,done:complete[index]}" :data-step="index+1" :data-complete="complete[index]" :aria-current="active===stages[index]||active==='published'&&index===4?'step':undefined" :disabled="(publishedObserved||value.stage==='PUBLISHED')&&index<4||!enabled[index]" @click="emit('select',stages[index]!)"><span class="aci-step-icon"><Icon v-if="complete[index]" name="check" :size="15"/><template v-else>{{index+1}}</template></span><span>{{label}}</span><i v-if="index<4" class="aci-step-line"></i></button></nav></template>

<style scoped>.aci-step:disabled{opacity:1}</style>
