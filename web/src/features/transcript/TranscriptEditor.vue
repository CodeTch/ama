<script setup lang="ts">
import {computed,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useTranscriptEditor} from './useTranscriptEditor';
import TranscriptForm from './TranscriptForm.vue';
import {ApiError} from '../../shared/api/client';
import type {TranscriptDetail,TranscriptHistory} from '../../shared/api/transcripts';
import type {TranscriptEdits} from './transcript-journal';
const props=defineProps<{meetingId:string}>();const {session,transcripts,workspace,journals,meetingScope,transcriptScopeReady}=useApplication();
const actor=()=>session.current.value?.actor.enterpriseId??null,eligible=()=>meetingScope.value===props.meetingId&&transcriptScopeReady.value;
const editor=useTranscriptEditor({...transcripts,authorize:async(id,signal)=>(await workspace.navigation(id,signal)).relation==='OWNER'},actor,()=>props.meetingId,eligible,journals),localError=ref<string|null>(null),history=shallowRef<TranscriptHistory|null>(null),selected=shallowRef<TranscriptDetail|null>(null),historyBusy=ref(false);
let epoch=0,controller:AbortController|null=null;
function clearHistory(){epoch++;controller?.abort();controller=null;history.value=null;selected.value=null;historyBusy.value=false;localError.value=null;}
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{clearHistory();if(actor()&&eligible())void editor.reload();},{immediate:true,flush:'sync'});
const permissionStop=watch(()=>editor.saved.value?.capabilities.canReadHistory,allowed=>{if(!allowed)clearHistory();},{flush:'sync'});
const stopLogout=session.onLogout(editor.logout);
const message=computed(()=>localError.value??(editor.error.value instanceof Error?editor.error.value.message:null));
const readonlyContent=computed(()=>editor.saved.value?.saved?.segments.map(({segmentId,body,marks})=>({segmentId,body,marks}))??[]);
function edit(value:TranscriptEdits){try{localError.value=null;editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂不可用。';}}
function resolve(action:'reapply'|'discard'){try{localError.value=null;editor[action]();}catch(cause){localError.value=cause instanceof Error?cause.message:'请先处理原操作。';}}
async function inspect(id:string|null,more=false){
 if(!actor()||!eligible()||!editor.saved.value?.capabilities.canReadHistory)return;const turn=++epoch,who=actor(),meeting=props.meetingId;controller?.abort();const request=new AbortController();controller=request;historyBusy.value=true;localError.value=null;
 const active=()=>turn===epoch&&who===actor()&&meeting===props.meetingId&&eligible()&&editor.saved.value?.capabilities.canReadHistory;
 try{if(id){const result=await transcripts.revision(meeting,id,request.signal);if(active())selected.value=result;}else{const result=await transcripts.history(meeting,more?history.value?.nextCursor??null:null,request.signal);if(active()){history.value=result;selected.value=null;}}}
 catch(cause){if(active()){clearHistory();localError.value=cause instanceof Error?cause.message:'历史读取暂不可用。';if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0))editor.deny(cause);else void editor.reload();}}
 finally{if(active())historyBusy.value=false;}
}
onUnmounted(()=>{stop();permissionStop();stopLogout();clearHistory();editor.dispose();});
</script>
<template>
 <TranscriptForm v-if="editor.draft.value||editor.saved.value?.saved" :value="editor.draft.value?.value.value??readonlyContent" :editable="!!editor.draft.value&&!!editor.saved.value?.capabilities.canSave" :busy="editor.busy.value" :dirty="editor.draft.value?.dirty.value??false" :conflict="editor.draft.value?.conflict.value?.value??null" :conflict-token="editor.draft.value?.conflict.value?.token??null" :state="editor.command.state.value" :blocked="editor.recoveryBlocked.value" :storage-status="editor.storageStatus.value" :error="message" :confirmed="!!editor.saved.value?.confirmation?.isCurrent" :can-confirm="!!editor.saved.value?.capabilities.canConfirm" @edit="edit" @save="editor.save" @confirm="editor.confirm" @retry="editor.retry" @reload="editor.reload" @discard="resolve('discard')" @reapply="resolve('reapply')"/>
 <section v-else class="card transcript-feedback"><h2>转写正文</h2><p v-if="editor.loading.value" role="status">正在读取转写…</p><p v-else-if="message" role="alert">{{message}}</p><p v-else>尚无已保存转写。导入任务完成后重新读取。</p><button class="btn" :disabled="editor.loading.value" @click="editor.reload">重新读取转写</button></section>
 <section v-if="editor.saved.value?.capabilities.canReadHistory" class="card transcript-feedback"><h2>转写历史</h2><button class="btn" :disabled="historyBusy" @click="inspect(null)">读取历史版本</button><ul v-if="history"><li v-for="item in history.items" :key="item.id"><button class="btn" :disabled="historyBusy" @click="inspect(item.id)">{{item.kind==='RAW'?'原始转写 RAW':'人工工作版本 WORKING'}} · {{item.revisionToken}} · {{item.createdAt}}</button></li></ul><button v-if="history?.hasMore" class="btn" :disabled="historyBusy" @click="inspect(null,true)">下一页历史</button><article v-if="selected"><h3>{{selected.selected.kind==='RAW'?'原始转写':'历史工作版本'}} · {{selected.selected.revisionToken}}</h3><p v-for="s in selected.selected.segments.slice(0,40)" :key="s.segmentId" class="text">{{s.body}}</p><p v-if="selected.selected.segments.length>40">显示前 40 段；恢复会使用所选完整版本。</p><button class="btn" :disabled="editor.busy.value||editor.draft.value?.dirty.value||!!editor.draft.value?.conflict.value||!selected.capabilities.canRestore" @click="editor.restore(selected.selected.id)">恢复为新的工作版本</button></article></section>
</template>
<style scoped>.transcript-feedback{padding:24px;margin-bottom:20px}.transcript-feedback h2{font-size:18px}.text{white-space:pre-wrap;overflow-wrap:anywhere}li{margin:8px 0}button:disabled{opacity:.5}</style>
