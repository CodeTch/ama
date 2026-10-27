<script setup lang="ts">
import {computed,onUnmounted,watch} from 'vue';
import {useApplication} from '../../application';
import {ApiError} from '../../shared/api/client';
import {useWorkspaceTasks} from './useWorkspaceTasks';
import WorkspaceTaskPage from './WorkspaceTaskPage.vue';
const props=defineProps<{meetingId:string}>(),{session,tasks}=useApplication();
const state=useWorkspaceTasks(tasks.list,()=>session.current.value?.actor.enterpriseId??null,()=>props.meetingId);
const message=computed(()=>state.error.value?(state.error.value instanceof ApiError?state.error.value.message:'任务暂不可用，请重试。'):null);
const stop=watch(()=>[session.current.value,props.meetingId],()=>{if(session.current.value)void state.first();},{immediate:true});
onUnmounted(()=>{stop();state.dispose();});
</script>
<template><WorkspaceTaskPage :page="state.page.value" :kind="state.kind.value" :loading="state.loading.value" :error="message" @refresh="state.reload" @first="state.first" @next="state.next" @select="state.selectKind"/></template>
