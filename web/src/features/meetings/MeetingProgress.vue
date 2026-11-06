<script setup lang="ts">
import {computed} from 'vue';import Icon from '../../shared/Icon.vue';
import {workspaceStages} from './workspace-stages';
import type {WorkspaceNavigation} from '../../shared/api/workspace';
const props=withDefaults(defineProps<{value:WorkspaceNavigation;publishedObserved?:boolean;active?:string;processingComplete?:boolean}>(),{processingComplete:undefined});
const emit=defineEmits<{select:[stage:string]}>();
const stages=['processing','transcript','minutes','confirm','publish'],labels=['材料导入','转写校正','AI纪要','会议确认','知识库发布'];
const capability=computed(()=>workspaceStages(props.value,props.publishedObserved));
const complete=computed(()=>[props.processingComplete??capability.value.complete[0],...capability.value.complete.slice(1)]);
const enabled=computed(()=>stages.map(stage=>capability.value.available[stage as keyof typeof capability.value.available]));
const explainMinutes=computed(()=>!enabled.value[2]&&!props.publishedObserved&&props.value.stage!=='PUBLISHED');
</script>
<template><nav class="aci-flow meeting-progress" aria-label="会议处理环节"><button v-for="(label,index) in labels" :aria-describedby="index===2&&explainMinutes?'minutes-step-prerequisite':undefined" :aria-disabled="index===2&&explainMinutes||undefined" :aria-label="label" :key="label" class="aci-step" :class="{active:active===stages[index]||active==='published'&&index===4,done:complete[index]}" :data-step="index+1" :data-complete="complete[index]" :aria-current="active===stages[index]||active==='published'&&index===4?'step':undefined" :disabled="(publishedObserved||value.stage==='PUBLISHED')&&index<4||!enabled[index]&&!(index===2&&explainMinutes)" @click="enabled[index]&&emit('select',stages[index]!)"><span class="aci-step-icon"><Icon v-if="complete[index]" name="check" :size="15"/><template v-else>{{index+1}}</template></span><span>{{label}}</span><span v-if="index===2&&explainMinutes" id="minutes-step-prerequisite" role="tooltip" class="step-hint">确认转写后可进入AI纪要</span><i v-if="index<4" class="aci-step-line"></i></button></nav></template>

<style scoped>.aci-step:disabled{opacity:1}.aci-step[aria-disabled=true]{position:relative;cursor:not-allowed;color:#aaa}.step-hint{position:absolute;top:100%;left:50%;transform:translateX(-50%);z-index:3;width:max-content;padding:4px 8px;background:var(--text);color:var(--surface);border-radius:4px;font-size:12px;visibility:hidden}.aci-step:hover .step-hint,.aci-step:focus-visible .step-hint{visibility:visible}</style>
