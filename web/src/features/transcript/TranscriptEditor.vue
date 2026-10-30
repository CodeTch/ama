<script setup lang="ts">
import {computed,onUnmounted,ref,shallowRef,watch} from 'vue';
import {useApplication} from '../../application';
import {useTranscriptEditor} from './useTranscriptEditor';
import TranscriptForm from './TranscriptForm.vue';
import {ApiError} from '../../shared/api/client';
import type {TranscriptDetail,TranscriptHistory} from '../../shared/api/transcripts';
import type {MediaScope} from './useSourceMedia';
import type {TranscriptEdits} from './transcript-journal';
const props=withDefaults(defineProps<{meetingId:string;showFooter?:boolean}>(),{showFooter:true});const emit=defineEmits<{changed:[];confirmed:[];seek:[fileId:string,startMs:number]}>();const form=ref<InstanceType<typeof TranscriptForm>>(),confirmDialog=ref<HTMLDialogElement>();const {session,transcripts,workspace,journals,meetingScope,transcriptScopeReady}=useApplication();
const actor=()=>session.current.value?.actor.enterpriseId??null,eligible=()=>meetingScope.value===props.meetingId&&transcriptScopeReady.value;
const editor=useTranscriptEditor({...transcripts,authorize:async(id,signal)=>(await workspace.navigation(id,signal)).relation==='OWNER'},actor,()=>props.meetingId,eligible,journals),localError=ref<string|null>(null),history=shallowRef<TranscriptHistory|null>(null),selected=shallowRef<TranscriptDetail|null>(null),historyBusy=ref(false),raw=shallowRef<TranscriptDetail|null>(null);
let epoch=0,controller:AbortController|null=null;
function clearHistory(){raw.value=null;epoch++;controller?.abort();controller=null;history.value=null;selected.value=null;historyBusy.value=false;localError.value=null;}
const stop=watch(()=>[actor(),props.meetingId,eligible()],()=>{clearHistory();if(actor()&&eligible())void editor.reload();},{immediate:true,flush:'sync'});
watch(()=>editor.saved.value,async value=>{if(value)emit('changed');raw.value=null;if(value&&!value.saved&&value.capabilities.canReadHistory){await inspect(null);const original=history.value?.items.find(x=>x.kind==='RAW');if(original){await inspect(original.id);if(selected.value?.selected.kind==='RAW')raw.value=selected.value;}}});
const permissionStop=watch(()=>editor.saved.value?.capabilities.canReadHistory,allowed=>{if(!allowed)clearHistory();},{flush:'sync'});
const stopLogout=session.onLogout(editor.logout);
const message=computed(()=>localError.value??(editor.error.value instanceof Error?editor.error.value.message:null));
const readonlyContent=computed(()=>(editor.saved.value?.saved??raw.value?.selected)?.segments.map(({segmentId,body,marks})=>({segmentId,body,marks}))??[]);
const sourceDetail=computed(()=>editor.saved.value?.saved?editor.saved.value:raw.value);
const provenance=computed(()=>editor.saved.value?.saved?.segments??raw.value?.selected.segments??[]);
const media=computed<MediaScope|null>(()=>{const d=sourceDetail.value;if(!d?.capabilities.canReadSources)return null;const ids=new Set(provenance.value.filter(s=>s.startMs!==null&&s.endMs!==null).map(s=>s.sourceFileId)),revisionId=d===raw.value?raw.value!.selected.id:null;return{meeting:props.meetingId,revisionId,accessRevisionToken:d.observation.accessRevisionToken,sources:d.sources.filter(s=>ids.has(s.fileId)&&d.sourceBytes.some(b=>b.fileId===s.fileId&&b.eligibility==='ELIGIBLE')).map(s=>({fileId:s.fileId,sha256:s.sha256,sizeBytes:s.sizeBytes,ordinal:s.ordinal,revisionId}))};});
function edit(value:TranscriptEdits){try{localError.value=null;editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂不可用。';}}
function resolve(action:'reapply'|'discard'){try{localError.value=null;editor[action]();}catch(cause){localError.value=cause instanceof Error?cause.message:'请先处理原操作。';}}
async function inspect(id:string|null,more=false){
 if(!actor()||!eligible()||!editor.saved.value?.capabilities.canReadHistory)return;const turn=++epoch,who=actor(),meeting=props.meetingId;controller?.abort();const request=new AbortController();controller=request;historyBusy.value=true;localError.value=null;
 const active=()=>turn===epoch&&who===actor()&&meeting===props.meetingId&&eligible()&&editor.saved.value?.capabilities.canReadHistory;
 try{if(id){const result=await transcripts.revision(meeting,id,request.signal);if(active())selected.value=result;}else{const result=await transcripts.history(meeting,more?history.value?.nextCursor??null:null,request.signal);if(active()){history.value=result;selected.value=null;}}}
 catch(cause){if(active()){clearHistory();localError.value=cause instanceof Error?cause.message:'历史读取暂不可用。';if(cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status??0))editor.deny(cause);else void editor.reload();}}
 finally{if(active())historyBusy.value=false;}
}
async function save(){if(!form.value?.composing)await editor.save();}
async function commit(value:TranscriptEdits,expected:string):Promise<boolean>{
 if(form.value?.composing||!editor.draft.value||editor.busy.value||editor.recoveryBlocked.value)return false;
 const original=editor.command.snapshot.value,pending=editor.pendingOperation()!==null&&!['CONFLICT','REJECTED'].includes(editor.command.state.value);
 if(pending){if(original?.operation!=='TRANSCRIPT_SAVE')return false;await editor.retry();}
 else{if(expected!==editor.saved.value?.observation.revisionToken){localError.value='转写或纠错已变化，请核对后重试';return false;}try{editor.edit(value);}catch(cause){localError.value=cause instanceof Error?cause.message:'修改暂不可用。';return false;}await editor.save();}
 return !editor.error.value&&!editor.draft.value?.dirty.value&&!editor.draft.value?.conflict.value;
}
async function confirm(){if(form.value?.composing)return;if(editor.draft.value?.dirty.value)await editor.save();if(editor.error.value||editor.draft.value?.dirty.value)return;if(!editor.saved.value?.confirmation?.isCurrent)await editor.confirm();if(editor.saved.value?.confirmation?.isCurrent&&!editor.error.value)emit('confirmed');}
async function begin(){const id=editor.saved.value?.saved?.id??raw.value?.selected.id;if(!id)return;await editor.reload();if(editor.error.value||editor.draft.value?.dirty.value)return;await editor.restore(id);}
defineExpose({save,media,dirty:computed(()=>editor.draft.value?.dirty.value??false)});
onUnmounted(()=>{stop();permissionStop();stopLogout();clearHistory();editor.dispose();});
</script>
<template>
 <TranscriptForm ref="form" :pending-save="editor.pendingOperation()==='TRANSCRIPT_SAVE'&&!['CONFLICT','REJECTED'].includes(editor.command.state.value)" :revision-token="editor.saved.value?.observation.revisionToken" :commit="commit" :provenance="provenance" :playable-files="media?.sources.map(s=>s.fileId)??[]" @seek="(fileId,startMs)=>emit('seek',fileId,startMs)" :show-footer="showFooter" @begin="begin" v-if="editor.draft.value||editor.saved.value?.saved||raw" :value="editor.saved.value?.saved?editor.draft.value?.value.value??readonlyContent:readonlyContent" :editable="!!editor.draft.value&&!!editor.saved.value?.capabilities.canSave" :busy="editor.busy.value" :dirty="editor.draft.value?.dirty.value??false" :conflict="editor.draft.value?.conflict.value?.value??null" :conflict-token="editor.draft.value?.conflict.value?.token??null" :state="editor.command.state.value" :blocked="editor.recoveryBlocked.value" :storage-status="editor.storageStatus.value" :error="message" :confirmed="!!editor.saved.value?.confirmation?.isCurrent" :can-confirm="!!editor.saved.value?.capabilities.canConfirm" @edit="edit" @save="save" @confirm="confirmDialog?.showModal()" @retry="editor.retry" @reload="editor.reload" @discard="resolve('discard')" @reapply="resolve('reapply')"/>
 <section v-else class="card transcript-feedback"><h2>转写正文</h2><p v-if="editor.loading.value" role="status">正在读取转写…</p><p v-else-if="message" role="alert">{{message}}</p><p v-else>尚无已保存转写。导入任务完成后重新读取。</p><button class="btn" :disabled="editor.loading.value" @click="editor.reload">重新读取转写</button></section>
 <details v-if="editor.saved.value?.capabilities.canReadHistory" class="transcript-history"><summary>转写历史与原操作恢复</summary><section class="card transcript-feedback"><h2>转写历史</h2><button class="btn" :disabled="historyBusy" @click="inspect(null)">读取历史版本</button><ul v-if="history"><li v-for="item in history.items" :key="item.id"><button class="btn" :disabled="historyBusy" @click="inspect(item.id)">{{item.kind==='RAW'?'原始转写 RAW':'人工工作版本 WORKING'}} · {{item.revisionToken}} · {{item.createdAt}}</button></li></ul><button v-if="history?.hasMore" class="btn" :disabled="historyBusy" @click="inspect(null,true)">下一页历史</button><article v-if="selected"><h3>{{selected.selected.kind==='RAW'?'原始转写':'历史工作版本'}} · {{selected.selected.revisionToken}}</h3><p v-for="s in selected.selected.segments.slice(0,40)" :key="s.segmentId" class="text">{{s.body}}</p><p v-if="selected.selected.segments.length>40">显示前 40 段；恢复会使用所选完整版本。</p><button class="btn" :disabled="editor.busy.value||editor.draft.value?.dirty.value||!!editor.draft.value?.conflict.value||!selected.capabilities.canRestore" @click="editor.restore(selected.selected.id)">恢复为新的工作版本</button></article></section></details>
 <dialog ref="confirmDialog" class="notice-dialog" aria-labelledby="confirm-transcript-title"><h3 id="confirm-transcript-title">确认转写并生成纪要</h3><p class="mt16">确认后将使用当前已保存转写作为纪要依据。未保存的修改会先保存，生成内容仍需人工核对。</p><p class="small muted mt12">确认、模板设置和纪要生成分别记录结果；发生中断时可查回原操作。</p><div class="flex mt16"><button class="btn" @click="confirmDialog?.close()">返回校正</button><button class="btn primary" :disabled="editor.busy.value" @click="confirm().then(()=>{if(editor.saved.value?.confirmation?.isCurrent)confirmDialog?.close();})">确认并生成</button></div></dialog>
</template>
<style scoped>.transcript-feedback{padding:24px;margin-bottom:20px}.transcript-feedback h2{font-size:18px}.text{white-space:pre-wrap;overflow-wrap:anywhere}li{margin:8px 0}button:disabled{opacity:.5}</style>

<style scoped>.transcript-history{margin:12px 0;color:var(--muted);font-size:12px}.transcript-history summary{cursor:pointer}.transcript-feedback{box-shadow:none}</style>
