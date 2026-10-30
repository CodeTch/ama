<script setup lang="ts">
import {computed} from 'vue';
import type {WorkspaceNavigation} from '../../shared/api/workspace';
const props=defineProps<{value:WorkspaceNavigation;publishedObserved?:boolean;active?:string}>();
const emit=defineEmits<{select:[stage:string]}>();
const stages=['processing','transcript','minutes','confirm','publish'],labels=['材料导入','转写校正','AI纪要','会议确认','知识库发布'];
const complete=computed(()=>{const s=props.value.saved,p=props.publishedObserved||props.value.stage==='PUBLISHED';const imported=props.value.privateSections?.tasks?.items.some(t=>t.kind==='TRANSCRIPT_IMPORT_V1'&&t.state==='SUCCEEDED');return[p||!!s.transcriptRevisionId||!!imported,p||!!s.transcriptConfirmationId,p||!!s.minutesRevisionId&&!!s.elementSetId,p||!!props.value.privateSections?.finalConfirmationId,p];});
const enabled=computed(()=>[true,complete.value[0],complete.value[1],complete.value[2],complete.value[3]]);
</script>
<template><nav class="aci-flow meeting-progress" aria-label="会议处理环节"><button v-for="(label,index) in labels"  :aria-label="label" :key="label" class="aci-step" :class="{active:active===stages[index]||active==='published'&&index===4,done:complete[index]}" :data-step="index+1" :data-complete="complete[index]" :aria-current="active===stages[index]||active==='published'&&index===4?'step':undefined" :disabled="(publishedObserved||value.stage==='PUBLISHED')&&index<4||!enabled[index]" @click="emit('select',stages[index]!)"><span class="aci-step-icon">{{complete[index]?'✓':index+1}}</span><span>{{label}}</span><i v-if="index<4" class="aci-step-line"></i></button></nav></template>
