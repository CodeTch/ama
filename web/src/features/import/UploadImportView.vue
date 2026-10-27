<script setup lang="ts">
import {computed} from 'vue';
import type {CommandState} from '../../shared/composables/useCommand';
type Item={key:string;name:string;size:number;status:string;error:string|null;uploadId:string|null;fileId:string|null;canAbort?:boolean;canRemove?:boolean};
const props=defineProps<{items:readonly Item[];ready:boolean;blocked:boolean;error:string|null;importState:CommandState;importBusy:boolean;taskId:string|null}>();
const emit=defineEmits<{add:[files:File[]];retry:[key:string,file:File];discover:[key:string];open:[];submit:[];discard:[];remove:[key:string];move:[key:string,direction:-1|1];abort:[key:string];newBatch:[]}>();
const mutable=computed(()=>props.importState==='EMPTY');
const uploading=computed(()=>props.items.some(i=>['QUEUED','VERIFYING','INITIALIZING','ABORTING'].includes(i.status))),pending=computed(()=>['PREPARED','UNCERTAIN','DENIED'].includes(props.importState));
function selected(event:Event,key?:string){const input=event.target as HTMLInputElement,files=[...(input.files??[])];if(key&&files[0])emit('retry',key,files[0]);else if(files.length)emit('add',files);input.value='';}
const stages:Record<string,string>={QUEUED:'等待上传',VERIFYING:'校验文件',INITIALIZING:'上传处理中',NEEDS_FILE:'请选择原文件以恢复',UNRESOLVED:'结果待确认',OBSERVED_COMPLETED:'已查询到上传完成',COMPLETION_ACKNOWLEDGED:'已收到文件完成回执',ABORTED:'已终止',ABORTING:'正在终止',FAILED:'上传失败'};
</script>
<template>
 <section class="card upload-panel" aria-labelledby="upload-heading"><h2 id="upload-heading">上传与导入转写文件</h2><p class="muted">选择 UTF-8 TXT 文件，按列表顺序导入。最多同时上传 2 个文件，每个文件按顺序上传分片。本浏览器每批最多 16 个文件、每个文件不超过 32 MiB。</p>
  <p v-if="error" role="alert">{{error}}</p><p v-if="blocked" role="alert">上传恢复记录暂不可用，请重新检查后操作。</p>
  <div class="actions"><label class="btn">选择 TXT 文件<input type="file" accept=".txt,text/plain" multiple :disabled="!ready||blocked||importBusy||items.length>=16||!mutable" @change="selected($event)"></label><button class="btn" :disabled="uploading||importBusy" @click="emit('open')">重新读取上传记录</button></div>
  <p v-if="!mutable" class="muted">本次导入文件清单已冻结，恢复时保留原文件及顺序。</p>
  <ol class="upload-list"><li v-for="item in items" :key="item.key"><div class="file-heading"><strong>{{item.name}}</strong><span>{{item.size}} 字节 · {{stages[item.status]??item.status}}</span></div><p v-if="item.error" role="alert">{{item.error}}</p><p v-if="item.fileId" class="muted">文件已具备导入条件。</p><div class="actions"><label class="btn">重新选择原文件<input type="file" accept=".txt,text/plain" :disabled="!ready||blocked||['QUEUED','VERIFYING','INITIALIZING','ABORTING'].includes(item.status)||importBusy||!mutable" @change="selected($event,item.key)"></label><button class="btn" :disabled="!ready||blocked||['QUEUED','VERIFYING','INITIALIZING','ABORTING'].includes(item.status)" @click="emit('discover',item.key)">查询原上传</button><button class="btn" :disabled="!mutable||uploading||blocked" @click="emit('move',item.key,-1)">上移</button><button class="btn" :disabled="!mutable||uploading||blocked" @click="emit('move',item.key,1)">下移</button><button class="btn" :disabled="!mutable||blocked||!item.canRemove" @click="emit('remove',item.key)">从本批移除</button><button v-if="item.canAbort" class="btn" :disabled="!mutable||uploading||blocked" @click="emit('abort',item.key)">终止未完成上传</button></div></li></ol>
  <p v-if="importState==='ACKNOWLEDGED'" role="status">导入任务已受理（{{taskId}}），不表示转写已完成。请在任务列表查看实际处理进度。</p>
  <p v-else-if="pending" role="status">原导入结果尚未确认，请重试同一请求。</p>
  <p v-if="importState==='CONFLICT'||importState==='REJECTED'" role="alert">导入请求已被拒绝，本地记录仍保留。检查当前会议信息后，可放弃该已拒绝请求再重新提交。</p>
  <div class="actions"><button v-if="importState==='ACKNOWLEDGED'" class="btn" :disabled="importBusy||blocked" @click="emit('newBatch')">开始新一批导入</button><button v-if="importState!=='ACKNOWLEDGED'" class="btn btn-primary" :disabled="!ready||blocked||importBusy||(!pending&&(items.length===0||items.some(i=>!i.fileId)))||importState==='CONFLICT'||importState==='REJECTED'" @click="emit('submit')">{{pending?'重试原导入请求':'按列表顺序提交导入'}}</button><button v-if="importState==='CONFLICT'||importState==='REJECTED'" class="btn" :disabled="importBusy" @click="emit('discard')">放弃已拒绝的导入请求</button></div>
 </section>
</template>
<style scoped>
.upload-panel{padding:24px;margin-bottom:20px}.upload-panel h2{font-size:18px;margin:0 0 12px}.muted{color:var(--muted);line-height:1.6}.actions{display:flex;flex-wrap:wrap;gap:12px;margin:16px 0}.actions label{display:flex;align-items:center;gap:8px;max-width:100%;flex-wrap:wrap}input[type=file]{max-width:220px}.upload-list{padding-left:24px}.upload-list li{padding:16px 0;border-bottom:1px solid var(--border,#ddd);overflow-wrap:anywhere}.file-heading{display:flex;flex-wrap:wrap;gap:12px;justify-content:space-between}.file-heading span{color:var(--muted)}[role=alert]{color:#9a3412;overflow-wrap:anywhere}button:disabled{opacity:.5;cursor:not-allowed}@media(max-width:600px){.upload-panel{padding:18px}.actions .btn{white-space:normal}.actions label{padding:10px}}
</style>
