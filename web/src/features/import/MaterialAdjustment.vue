<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,shallowRef} from 'vue';
import {useApplication} from '../../application';
import {useKnowledgeMaterials} from './useMaterials';
import KnowledgeMaterialPicker from './KnowledgeMaterialPicker.vue';
import {knowledgeKey,materialUuid,type KnowledgeDocument,type MaterialListing} from '../../shared/api/materials';
import {prepareUpload,UPLOAD_FILE_BYTES} from './upload-bytes';
import {createMaterialEnhancementApi,type MaterialEnhancement} from '../transcript/material-enhancement-api';
import {ApiError} from '../../shared/api/client';
const props=defineProps<{meetingId:string;processing?:boolean;initialRecovery?:boolean}>(),emit=defineEmits<{busy:[value:boolean];applied:[]}>();
const app=useApplication(),actor=()=>app.session.current.value?.actor.enterpriseId??null,meeting=props.meetingId;
const picker=useKnowledgeMaterials(app.materials,actor,()=>meeting,app.journals);
const enhancements=createMaterialEnhancementApi(app.session.client,actor);
const listing=shallowRef<MaterialListing|null>(null),removed=ref<string[]>([]),files=shallowRef<File[]>([]),docs=shallowRef<KnowledgeDocument[]>([]);
const error=ref(''),busy=ref(false),pickerOpen=ref(false),input=ref<HTMLInputElement>(),pendingKey=ref<string|null>(null);
const candidate=shallowRef<MaterialEnhancement|null>(null),job=ref<{stage:string;id?:string;candidateId?:string;errorCode?:string}|null>(null);
const visible=computed(()=>listing.value?.items.filter(i=>!removed.value.includes(i.materialRevisionId))??[]);
const changed=computed(()=>removed.value.length+files.value.length+docs.value.length>0);
const canApply=computed(()=>!props.processing&&!busy.value&&!!listing.value&&(changed.value||!!pendingKey.value));
let timer:ReturnType<typeof setTimeout>|undefined;const controller=new AbortController(),signal=controller.signal;
const path='/api/v1/meetings/'+meeting,cache='ama.material-apply.'+actor()+'.'+meeting;
const uploaded=new Map<File,string>();
function fail(cause:unknown){if(!signal.aborted)error.value=cause instanceof Error?cause.message:'操作失败，请重试';}
function lock(value:boolean){busy.value=value;emit('busy',value);}
async function refresh(){
 clearTimeout(timer);
 const value=await app.session.client.json<NonNullable<typeof job.value>>(path+'/material-adjustments/latest',{signal,expectedStatus:200});
 if(signal.aborted)return;if(job.value?.stage==='PENDING'&&value.stage==='SUBMITTED')emit('applied');job.value=value;candidate.value=null;
 if(value.stage==='PENDING')timer=setTimeout(()=>refresh().catch(fail),1500);
 if(value.candidateId&&materialUuid(value.candidateId)){const result=await enhancements.read(meeting,value.candidateId);if(!signal.aborted)candidate.value=result;}
}
async function load(){listing.value=await app.materials.list(meeting,signal);await refresh();}
onMounted(()=>{try{const cached=sessionStorage.getItem(cache);if(cached)pendingKey.value=JSON.parse(cached).key;}catch(e){fail(e);}void load().catch(fail);});
onUnmounted(()=>{controller.abort();clearTimeout(timer);if(picker.opened.value)picker.clear();picker.dispose();});
function addFiles(list:FileList|null){if(!list||pendingKey.value)return;for(const f of Array.from(list)){if(!/\.(pdf|pptx|docx)$/i.test(f.name)||f.size>UPLOAD_FILE_BYTES){error.value='请选择支持的 PDF、PPTX 或 DOCX 文件';continue;}files.value=[...files.value,f];}}
function selectDocs(selected:readonly KnowledgeDocument[]){const all=new Map(docs.value.map(d=>[knowledgeKey(d),d]));for(const d of selected)all.set(knowledgeKey(d),d);docs.value=[...all.values()];picker.clear();picker.close();pickerOpen.value=false;}
async function upload(file:File){
 const known=uploaded.get(file);if(known)return known;
 const who=actor();if(!who)throw Error('请重新登录');
 const data=await prepareUpload(file,signal);
 const cmd=(operation:string,resource:string,url:string,body:unknown)=>({operation,actor:who,resource,path:url,key:crypto.randomUUID(),body:JSON.stringify(body)});
 const ticket=await app.materials.begin(cmd('UPLOAD_BEGIN',meeting,path+'/uploads',{...data.begin,purpose:'MATERIAL_SOURCE'}));
 const plan=await data.plan([],ticket.partSizeToken);
 for(const part of plan)await app.materials.part(who,ticket.uploadId,part.part,data.body(part),part.sha256,signal);
 await app.materials.complete(cmd('UPLOAD_COMPLETE',ticket.uploadId,'/api/v1/uploads/'+ticket.uploadId+'/completion',{parts:plan.map(p=>({part:p.part,sha256:p.sha256})),sha256:data.begin.sha256}));
 const receipt=await app.materials.receipt(meeting,ticket.uploadId,signal);
 if(!receipt.completed||receipt.completed.originalPurpose!=='MATERIAL_SOURCE'||receipt.completed.sha256!==data.begin.sha256)throw Error('文件回执与选定材料不符');
 uploaded.set(file,receipt.completed.fileId);return receipt.completed.fileId;
}
async function apply(){
 if(!canApply.value||!listing.value)return;lock(true);error.value='';
 try{
  let saved=sessionStorage.getItem(cache);
  if(!saved){
   const items:Array<Record<string,string>>=docs.value.map(d=>({libraryId:d.libraryId,documentId:d.id,version:d.version}));
   for(const file of files.value)items.push({fileId:await upload(file)});
   saved=JSON.stringify({key:crypto.randomUUID(),body:JSON.stringify({expectedRevisionToken:listing.value.meetingRevision,removed:removed.value,items})});sessionStorage.setItem(cache,saved);
  }
  const command=JSON.parse(saved);pendingKey.value=command.key;
  await app.session.client.json(path+'/materials/batches',{method:'POST',headers:{'Idempotency-Key':command.key},body:command.body,expectedStatus:201,maxResponseBytes:262144});
  sessionStorage.removeItem(cache);pendingKey.value=null;files.value=[];docs.value=[];removed.value=[];emit('applied');await load();
 }catch(e){if(e instanceof ApiError&&[400,403,404,409,422].includes(e.status)){sessionStorage.removeItem(cache);pendingKey.value=null;if(e.status===409){listing.value=await app.materials.list(meeting,signal);const active=new Set(listing.value.items.map(item=>item.materialRevisionId));removed.value=removed.value.filter(id=>active.has(id));}}fail(e);}
 finally{lock(false);}
}
async function decide(){
 if(!candidate.value||!job.value?.id||busy.value)return;lock(true);
 try{await enhancements.adopt(meeting,candidate.value.candidateId,job.value.id);await load();emit('applied');}catch(e){fail(e);}finally{lock(false);}
}
async function retry(){if(!job.value?.id)return;try{await app.session.client.json(path+'/material-adjustments/'+job.value.id+'/retry',{method:'POST',expectedStatus:200});await refresh();}catch(e){fail(e);}}
defineExpose({apply,canApply});
</script>
<template><div class="mt16" @dragover.prevent @drop.prevent="addFiles($event.dataTransfer?.files??null)">
 <p class="small muted mb12">{{processing?'当前任务正在处理，完成后可应用材料。':initialRecovery?'应用后将使用原会议主文件和本次材料重新处理，保留此前的原始结果。':'应用后生成增强候选，核对并采用后更新工作稿。'}}</p>
 <fieldset :disabled="busy||!!pendingKey" style="border:0;padding:0"><div class="flex wrap"><button class="btn" @click="input?.click()">上传本地文件</button><button class="btn" @click="pickerOpen=true">从知识库选择</button><input ref="input" aria-label="选择背景材料" hidden multiple type="file" accept=".pdf,.docx,.pptx" @change="addFiles(input?.files??null);if(input)input.value=''"></div>
 <div v-for="item in visible" :key="item.id" class="between file-item mt8"><b>{{item.name}}</b><button class="btn sm" @click="removed.push(item.materialRevisionId)">移除</button></div>
 <div v-for="(file,n) in files" :key="n" class="between file-item mt8">{{file.name}} · 待添加<button class="btn sm" @click="files=files.filter((_,i)=>i!==n)">移除</button></div>
 <div v-for="d in docs" :key="knowledgeKey(d)" class="between file-item mt8">{{d.name}} · {{d.version.slice(0,12)}} · 待添加<button class="btn sm" @click="docs=docs.filter(x=>knowledgeKey(x)!==knowledgeKey(d))">移除</button></div>
 <p v-if="listing&&!visible.length&&!files.length&&!docs.length" class="aci-empty-material">本次背景材料为空</p></fieldset>
 <p v-if="error" role="alert" class="ac-error">{{error}}</p><p v-if="job?.stage==='PENDING'" role="status">正在提交材料处理…</p>
 <p v-if="job?.stage==='FAILED'||job?.stage==='STALE'" role="alert">增强未完成 · {{job.errorCode}} <button v-if="job.stage==='FAILED'" class="btn sm" @click="retry">重试增强</button></p>
 <p v-if="job?.stage==='SUBMITTED'" role="status">已按新材料提交处理，可返回查看进度。</p>
 <section v-if="candidate" class="mt16"><b>增强候选核对</b><p v-if="candidate.state==='APPLIED'">已采用</p><p v-else-if="candidate.state==='STALE'">原工作稿或材料版本已变化</p><template v-else><p v-for="c in candidate.changes" :key="c.segmentId">{{c.before}} → {{c.after}}</p><p v-if="!candidate.changes.length">候选与工作稿一致</p><button v-if="candidate.canApply" class="btn primary" :disabled="busy" @click="decide">采用增强候选</button></template></section>
 <KnowledgeMaterialPicker :open="pickerOpen" :picker="picker" :added="visible" :busy="busy" @close="pickerOpen=false" @confirm="selectDocs"/>
</div></template>
