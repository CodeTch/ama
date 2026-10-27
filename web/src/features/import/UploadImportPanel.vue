<script setup lang="ts">
import {watch,onUnmounted,ref} from 'vue';
import {useApplication} from '../../application';
import UploadImportView from './UploadImportView.vue';
const props=defineProps<{meetingId:string}>(),{uploadQueue:queue,session,discovery}=useApplication(),localError=ref<string|null>(null);
const stop=watch(()=>[session.current.value?.actor.enterpriseId,props.meetingId],()=>{localError.value=null;if(session.current.value&&!queue.ready.value)void queue.open();},{immediate:true});
async function act(run:()=>Promise<void>|void){try{localError.value=null;await run();}catch(cause){localError.value=cause instanceof Error?cause.message:'操作未完成，请重试。';}}
onUnmounted(stop);
</script>
<template><UploadImportView :items="queue.items.value" :ready="queue.ready.value" :blocked="queue.blocked.value" :error="localError??queue.error.value" :import-state="queue.importCommand.state.value" :import-busy="queue.importBusy.value" :task-id="queue.importCommand.ack.value?.taskId??null" @add="files=>act(()=>queue.add(files))" @retry="(key,file)=>act(()=>queue.retry(key,file))" @discover="key=>act(()=>queue.discover(key,discovery.lookup))" @open="act(queue.open)" @submit="act(queue.submitImport)" @discard="act(queue.discardRejectedImport)" @remove="key=>act(()=>queue.remove(key))" @move="(key,direction)=>act(()=>queue.move(key,direction))" @abort="key=>act(()=>queue.abort(key))" @new-batch="act(queue.newBatch)"/></template>
