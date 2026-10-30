import {computed,shallowReadonly,shallowRef,watch} from 'vue';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import type {CommandSnapshot} from '../../shared/composables/useCommand';
import {fitsUtf8} from '../../shared/bounded-text';
import {prepareUpload,UPLOAD_FILE_BYTES,UPLOAD_PART_COUNT} from './upload-bytes';
import {knowledgeKey,materialFormat,materialUuid,validKnowledgeDocument,validMaterialBegin,validMaterialCommand,type createMaterialsApi,type KnowledgeDocument,type MaterialItem,type MaterialLibrary,type MaterialListing} from '../../shared/api/materials';

type Api=ReturnType<typeof createMaterialsApi>;
export type MaterialState=Readonly<{busy:boolean;blocked:boolean;failed:boolean;hasFiles:boolean}>;
type JobKind='LOCAL'|'KNOWLEDGE'|'RETRY'|'REMOVE';
type StoredJob=Readonly<{id:string;kind:JobKind;name:string;begin:CommandSnapshot|null;completion:CommandSnapshot|null;command:CommandSnapshot|null;uploadId:string|null;selection:KnowledgeDocument|null}>;
export type MaterialJob=StoredJob&Readonly<{status:string;error:string|null;needsFile:boolean;canRemove:boolean}>;
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const message=(e:unknown)=>e instanceof Error?e.message:'材料操作未完成，请重试原操作。';
const aborted=()=>new DOMException('Material scope changed','AbortError');
const freezeJob=(job:StoredJob,status='等待恢复原操作',error:string|null=null):MaterialJob=>Object.freeze({...job,status,error,needsFile:job.kind==='LOCAL'&&!job.command&&!job.completion,canRemove:!job.begin&&!job.command});
function validJobs(v:unknown,actor:string,meeting:string):v is StoredJob[]{
 if(!Array.isArray(v)||v.length>64||new Set(v.map(x=>x?.id)).size!==v.length)return false;
 return v.every(j=>{
  if(!object(j)||!exact(j,'begin,command,completion,id,kind,name,selection,uploadId')||!materialUuid(j.id)||typeof j.name!=='string'||!fitsUtf8(j.name,1024*1024)||!['LOCAL','KNOWLEDGE','RETRY','REMOVE'].includes(String(j.kind))||!(j.uploadId===null||materialUuid(j.uploadId))||!(j.selection===null||validKnowledgeDocument(j.selection)))return false;
  if(j.begin!==null&&(!validMaterialBegin(j.begin,actor,meeting)||j.kind!=='LOCAL'))return false;
  if(j.command!==null&&(!validMaterialCommand(j.command,actor,meeting)||j.command.operation!==(j.kind==='REMOVE'?'MATERIAL_REMOVE':j.kind==='RETRY'?'MATERIAL_RETRY':'MATERIAL_ADD')))return false;
  if(j.kind==='KNOWLEDGE'){
   if(!j.selection||!j.command)return false;const b=JSON.parse(j.command.body);if(b.libraryId!==j.selection.libraryId||b.documentId!==j.selection.id||b.version!==j.selection.version)return false;
  }else if(j.selection!==null)return false;
  if(j.kind==='LOCAL'&&j.command!==null&&!materialUuid(JSON.parse(j.command.body).fileId))return false;
  if(j.kind!=='LOCAL'&&(j.begin!==null||j.completion!==null||j.uploadId!==null)||['REMOVE','RETRY'].includes(String(j.kind))&&j.command===null)return false;
  if(j.completion!==null){
   const c=j.completion;if(!j.begin||!j.uploadId||!object(c)||!exact(c,'actor,body,key,operation,path,resource')||c.actor!==actor||c.resource!==j.uploadId||c.operation!=='UPLOAD_COMPLETE'||c.path!==`/api/v1/uploads/${j.uploadId}/completion`||!materialUuid(c.key)||typeof c.body!=='string'||!fitsUtf8(c.body,1024*1024))return false;
   try{const b:unknown=JSON.parse(c.body),begin=JSON.parse(j.begin.body);if(!object(b)||!exact(b,'parts,sha256')||b.sha256!==begin.sha256||!Array.isArray(b.parts)||b.parts.length<1||b.parts.length>UPLOAD_PART_COUNT||!b.parts.every((p,i)=>object(p)&&exact(p,'part,sha256')&&p.part===i+1&&typeof p.sha256==='string'&&/^[0-9a-f]{64}$/.test(p.sha256)))return false;}catch{return false;}
  }
  return true;
 });
}

/** Independent material intake. Journals retain original commands, never bytes or restored ACK authority. */
export function useMaterials(api:Api,actor:()=>string|null,meeting:()=>string,context:TabJournalContext,eligible:()=>boolean=()=>true){
 const items=shallowRef<readonly MaterialItem[]>([]),jobs=shallowRef<readonly MaterialJob[]>([]),ready=shallowRef(false),busy=shallowRef(false),loading=shallowRef(false),error=shallowRef<string|null>(null),storageBlocked=shallowRef(false),listing=shallowRef<MaterialListing|null>(null);
 const journal=createScopedPrivateJournal(context,'import-materials',validJobs),files=new Map<string,File>();
 let epoch=0,disposed=false,controller=new AbortController(),restored=false,reloadFlight:Promise<void>|null=null,readSequence=0;
 const state=computed<MaterialState>(()=>({busy:busy.value||loading.value,blocked:!ready.value||storageBlocked.value||busy.value||loading.value||jobs.value.length>0||!!listing.value?.materialsPending,failed:!!error.value||jobs.value.some(j=>!!j.error)||items.value.some(i=>i.state==='FAILED'),hasFiles:items.value.length>0||jobs.value.length>0}));
 const stored=(values:readonly MaterialJob[])=>values.map(({id,kind,name,begin,completion,command,uploadId,selection})=>({id,kind,name,begin,completion,command,uploadId,selection}));
 function active(turn:number){if(disposed||turn!==epoch||!actor()||!materialUuid(meeting())||!eligible())throw aborted();}
 function persist(values=jobs.value){if(!actor()||!materialUuid(meeting()))throw aborted();if(journal.save(actor()!,meeting(),stored(values))!=='SAVED'){storageBlocked.value=true;throw new Error('无法保存材料原请求，请保持页面打开并恢复存储后重试。');}storageBlocked.value=false;}
 function patch(id:string,change:Partial<MaterialJob>){jobs.value=Object.freeze(jobs.value.map(j=>j.id===id?Object.freeze({...j,...change}):j));}
 function createCommand(operation:string,path:string,body:string):CommandSnapshot{return Object.freeze({actor:actor()!,resource:meeting(),operation,path,key:crypto.randomUUID(),body});}
 async function fresh(turn:number){const serial=++readSequence,value=await api.list(meeting(),controller.signal);active(turn);if(serial===readSequence){items.value=value.items;listing.value=value;ready.value=true;}return value;}
 function invalidate(){epoch++;controller.abort();controller=new AbortController();reloadFlight=null;readSequence++;items.value=[];jobs.value=[];listing.value=null;ready.value=false;busy.value=false;loading.value=false;error.value=null;storageBlocked.value=false;restored=false;files.clear();}
 function reload():Promise<void>{
  if(reloadFlight)return reloadFlight;if(disposed||!actor()||!materialUuid(meeting())||!eligible())return Promise.resolve();const turn=epoch;loading.value=true;
  const run=async()=>{try{
   active(turn);
   if(!restored){const found=journal.load(actor()!,meeting());if(found.kind!=='EMPTY'&&found.kind!=='RESTORED')throw new Error('材料恢复记录不可用，请保留原操作并恢复存储后重试。');jobs.value=found.kind==='RESTORED'?Object.freeze(found.value.map(j=>freezeJob(j))):[];restored=true;}
   if(storageBlocked.value)persist();await fresh(turn);active(turn);error.value=null;
  }catch(cause){if(turn===epoch){ready.value=false;error.value=message(cause);}}finally{if(turn===epoch){loading.value=false;reloadFlight=null;}}};
  reloadFlight=run();return reloadFlight;
 }
 const stop=watch(()=>[actor(),meeting(),eligible()],()=>{invalidate();journal.synchronize(actor(),materialUuid(meeting())?meeting():null);void reload();},{immediate:true,flush:'sync'});
 function writable(){if(!ready.value||storageBlocked.value||!actor()||busy.value||loading.value)throw new Error('请先重新读取材料并恢复原操作。');}
 function get(id:string){const j=jobs.value.find(j=>j.id===id);if(!j)throw new Error('材料操作已变化，请重新读取。');return j;}
 async function localUpload(id:string,turn:number){
  let job=get(id),prepared:Awaited<ReturnType<typeof prepareUpload>>|undefined;
  if(!job.begin){const file=files.get(id);if(!file||!materialFormat(file.name)||file.size>UPLOAD_FILE_BYTES)throw new Error('请选择 PDF、PPTX 或 DOCX 背景材料（单文件不超过32 MiB）。');patch(id,{status:'校验文件中'});prepared=await prepareUpload(file,controller.signal);active(turn);
   const begin=createCommand('UPLOAD_BEGIN',`/api/v1/meetings/${meeting()}/uploads`,JSON.stringify({...prepared.begin,purpose:'MATERIAL_SOURCE'}));patch(id,{begin,canRemove:false});persist();job=get(id);
  }
  patch(id,{status:'恢复上传回执中'});const accepted=await api.begin(job.begin!);active(turn);
  if(job.uploadId&&accepted.uploadId!==job.uploadId)throw new Error('上传回执与原请求不一致，请保留原操作。');patch(id,{uploadId:accepted.uploadId});persist();job=get(id);
  const begin=JSON.parse(job.begin!.body) as {name:string;size:number;sha256:string};
  const receipt=async()=>{const value=await api.receipt(meeting(),accepted.uploadId,controller.signal);active(turn);if(value.purpose!=='MATERIAL_SOURCE'||value.displayName!==begin.name||value.expectedSha256!==begin.sha256||value.expectedSizeToken!==String(begin.size)||value.accepted.uploadId!==accepted.uploadId)throw new Error('背景材料来源回执不一致，请保留原操作。');return value;};
  let current=await receipt();
  if(current.state!=='COMPLETED'){
   if(job.completion){patch(id,{status:'恢复原完成请求中'});await api.complete(job.completion);active(turn);current=await receipt();}
   else{
    if(!['OPEN','FINALIZING'].includes(current.state))throw new Error('该上传当前不可继续，请保留原请求核对状态。');
    if(!prepared){const file=files.get(id);if(!file){patch(id,{needsFile:true});throw new Error('请重新选择原背景文件，校验相同字节后继续。');}if(file.name!==begin.name)throw new Error('请选择原文件名及相同字节的背景文件。');prepared=await prepareUpload(file,controller.signal,{sizeToken:String(begin.size),sha256:begin.sha256});active(turn);}
    const parts:Parameters<typeof prepared.plan>[0][number][]=[];let after='0';
    for(let pages=0;;pages++){if(pages>=10)throw new Error('分块回执超过浏览器恢复限额。');const page=await api.parts(meeting(),accepted.uploadId,after,controller.signal);active(turn);parts.push(...page.items);if(parts.length>UPLOAD_PART_COUNT)throw new Error('分块回执超过浏览器恢复限额。');if(page.nextCursor===null)break;if(BigInt(page.nextCursor)<=BigInt(after))throw new Error('分块回执未前进。');after=page.nextCursor;}
    const plan=await prepared.plan(parts,current.accepted.partSizeToken);active(turn);
    if(plan.some(p=>!p.accepted)&&!current.allowedActions.includes('UPLOAD_PARTS'))throw new Error('该材料上传当前不允许继续传输分块。');
    patch(id,{status:'上传中',needsFile:false});for(const part of plan){if(part.accepted)continue;await api.part(actor()!,accepted.uploadId,part.part,prepared.body(part),part.sha256,controller.signal);active(turn);}
    current=await receipt();if(current.state!=='COMPLETED'){
     if(!current.allowedActions.includes('COMPLETE'))throw new Error('请重新核对材料完成回执。');
     const completion=Object.freeze({...createCommand('UPLOAD_COMPLETE',`/api/v1/uploads/${accepted.uploadId}/completion`,JSON.stringify({parts:plan.map(p=>({part:p.part,sha256:p.sha256})),sha256:begin.sha256})),resource:accepted.uploadId});patch(id,{completion,status:'校验上传结果中'});persist();
     await api.complete(completion);active(turn);current=await receipt();
    }
   }
  }
  if(current.state!=='COMPLETED'||!current.completedFileId||current.completed?.originalPurpose!=='MATERIAL_SOURCE'||current.completed.sha256!==begin.sha256||current.completed.sizeBytesToken!==String(begin.size))throw new Error('背景文件完成结果尚未确认，请重试原操作。');
  job=get(id);if(job.command){if(JSON.parse(job.command.body).fileId!==current.completedFileId)throw new Error('材料关联与原完成回执不一致。');}
  else{patch(id,{command:createCommand('MATERIAL_ADD',`/api/v1/meetings/${meeting()}/materials`,JSON.stringify({fileId:current.completedFileId})),needsFile:false});persist();}
 }
 async function perform(id:string,turn:number):Promise<boolean>{
  try{active(turn);patch(id,{error:null});if(get(id).kind==='LOCAL')await localUpload(id,turn);active(turn);const job=get(id);if(!job.command)throw new Error('材料原请求缺失。');patch(id,{status:job.kind==='REMOVE'?'移除中':'解析并关联材料中'});
   if(job.kind==='REMOVE')await api.remove(job.command);else await api.send(job.command);active(turn);
   await fresh(turn);active(turn);
   const remaining=jobs.value.filter(j=>j.id!==id);persist(remaining);jobs.value=Object.freeze(remaining);files.delete(id);error.value=null;return true;
  }catch(cause){if(turn===epoch){patch(id,{status:'操作未完成',error:message(cause)});error.value=message(cause);}return false;}
 }
 async function run(ids:readonly string[]){writable();const turn=epoch;busy.value=true;try{for(const id of ids){active(turn);await perform(id,turn);}}finally{if(turn===epoch)busy.value=false;}}
 async function addLocal(selected:readonly File[]){writable();if(jobs.value.length)throw new Error('请先恢复已有材料操作。');if(items.value.length+selected.length>64)throw new Error('背景材料最多64份。');
  const added=selected.map(file=>{const id=crypto.randomUUID();files.set(id,file);return freezeJob({id,kind:'LOCAL',name:file.name,begin:null,completion:null,command:null,uploadId:null,selection:null},'等待校验');});jobs.value=Object.freeze([...jobs.value,...added]);persist();await run(added.map(j=>j.id));
 }
 async function addKnowledge(selected:readonly KnowledgeDocument[]){writable();if(selected.some(d=>!validKnowledgeDocument(d))||new Set(selected.map(knowledgeKey)).size!==selected.length)throw new Error('知识库选择缺少明确版本。');if(items.value.length+selected.length>64)throw new Error('背景材料最多64份。');
  const ids:string[]=[],byId=new Map<string,string>();for(const d of selected){const prior=jobs.value.find(j=>j.kind==='KNOWLEDGE'&&j.selection&&knowledgeKey(j.selection)===knowledgeKey(d));if(prior){ids.push(prior.id);byId.set(prior.id,knowledgeKey(d));continue;}
   if(jobs.value.some(j=>j.kind!=='KNOWLEDGE'))throw new Error('请先恢复已有材料操作。');const j=freezeJob({id:crypto.randomUUID(),kind:'KNOWLEDGE',name:d.name,begin:null,completion:null,uploadId:null,selection:Object.freeze({...d}),command:createCommand('MATERIAL_ADD',`/api/v1/meetings/${meeting()}/materials`,JSON.stringify({libraryId:d.libraryId,documentId:d.id,version:d.version}))});jobs.value=Object.freeze([...jobs.value,j]);ids.push(j.id);byId.set(j.id,knowledgeKey(d));}
  persist();await run(ids);return ids.filter(id=>!jobs.value.some(j=>j.id===id)).map(id=>byId.get(id)!);
 }
 async function retryJob(id:string,file?:File){writable();if(file)files.set(id,file);persist();await run([get(id).id]);}
 async function removeJob(id:string){writable();if(!get(id).canRemove)throw new Error('原请求结果尚未确认，不能丢弃该材料操作。');const remaining=jobs.value.filter(j=>j.id!==id);persist(remaining);jobs.value=Object.freeze(remaining);files.delete(id);}
 async function materialAction(id:string,kind:'RETRY'|'REMOVE'){writable();if(jobs.value.length)throw new Error('请先恢复已有材料操作。');const material=items.value.find(i=>i.id===id);if(!material)throw new Error('材料已变化，请重新读取。');if(kind==='RETRY'&&material.state!=='FAILED')throw new Error('仅失败材料可重试解析。');
  const j=freezeJob({id:crypto.randomUUID(),kind,name:material.name,begin:null,completion:null,uploadId:null,selection:null,command:createCommand(kind==='RETRY'?'MATERIAL_RETRY':'MATERIAL_REMOVE',`/api/v1/meetings/${meeting()}/materials/${id}`+(kind==='RETRY'?'/retry':''),'')});jobs.value=Object.freeze([...jobs.value,j]);persist();await run([j.id]);
 }
 return {items:shallowReadonly(items),jobs:shallowReadonly(jobs),ready:shallowReadonly(ready),busy:shallowReadonly(busy),loading:shallowReadonly(loading),error:shallowReadonly(error),listing:shallowReadonly(listing),state,reload,addLocal,addKnowledge,retryJob,removeJob,retry:(id:string)=>materialAction(id,'RETRY'),remove:(id:string)=>materialAction(id,'REMOVE'),async ensureReady(){await reload();return !state.value.blocked;},logout(){invalidate();journal.clear();},dispose(){disposed=true;stop();invalidate();}};
}

type PickerDraft={search:string;library:string;review:boolean;selected:KnowledgeDocument[]};
function validPicker(v:unknown):v is PickerDraft{return object(v)&&exact(v,'library,review,search,selected')&&typeof v.search==='string'&&fitsUtf8(v.search,1024)&&typeof v.library==='string'&&/^[A-Za-z0-9_-]{0,128}$/.test(v.library)&&typeof v.review==='boolean'&&Array.isArray(v.selected)&&v.selected.length<=64&&v.selected.every(validKnowledgeDocument)&&new Set(v.selected.map(knowledgeKey)).size===v.selected.length;}
/** Picker selection is a private draft; document version comes exclusively from a validated provider page. */
export function useKnowledgeMaterials(api:Api,actor:()=>string|null,meeting:()=>string,context:TabJournalContext){
 const libraries=shallowRef<readonly MaterialLibrary[]>([]),documents=shallowRef<readonly KnowledgeDocument[]>([]),selected=shallowRef<readonly KnowledgeDocument[]>([]),query=shallowRef(''),library=shallowRef(''),review=shallowRef(false),loading=shallowRef(false),libraryLoading=shallowRef(false),error=shallowRef<string|null>(null),libraryError=shallowRef<string|null>(null),cursor=shallowRef<string|null>(null),libraryCursor=shallowRef<string|null>(null),opened=shallowRef(false);
 const journal=createScopedPrivateJournal(context,'material-picker',(v):v is PickerDraft=>validPicker(v));let epoch=0,read=0,libRead=0,controller=new AbortController(),disposed=false;
 function active(turn:number){if(disposed||turn!==epoch||!actor()||!materialUuid(meeting()))throw aborted();}
 function persist(){if(!actor()||!materialUuid(meeting()))throw aborted();if(journal.save(actor()!,meeting(),{search:query.value,library:library.value,review:review.value,selected:[...selected.value]})!=='SAVED')throw new Error('无法保存知识库选择，请恢复浏览器存储后重试。');}
 function invalidate(){epoch++;read++;libRead++;controller.abort();controller=new AbortController();libraries.value=[];documents.value=[];selected.value=[];query.value='';library.value='';review.value=false;loading.value=false;libraryLoading.value=false;error.value=null;libraryError.value=null;cursor.value=null;libraryCursor.value=null;opened.value=false;}
 const stop=watch(()=>[actor(),meeting()],()=>{invalidate();journal.synchronize(actor(),materialUuid(meeting())?meeting():null);},{flush:'sync'});
 async function loadLibraries(more=false){const turn=epoch,serial=++libRead;libraryLoading.value=true;libraryError.value=null;try{active(turn);const page=await api.libraries(meeting(),'',more?libraryCursor.value:null,controller.signal);active(turn);if(serial!==libRead)return;libraries.value=Object.freeze(more?[...libraries.value,...page.items.filter(x=>!libraries.value.some(k=>k.id===x.id))]:page.items);libraryCursor.value=page.cursor;}catch(cause){if(turn===epoch&&serial===libRead)libraryError.value=message(cause);}finally{if(turn===epoch&&serial===libRead)libraryLoading.value=false;}}
 async function loadDocuments(more=false){const turn=epoch,serial=++read,id=library.value,search=query.value;documents.value=more?documents.value:[];cursor.value=more?cursor.value:null;error.value=null;if(!id)return;loading.value=true;try{active(turn);const name=libraries.value.find(k=>k.id===id)?.name??'',q=search&&name.toLowerCase().includes(search.toLowerCase())?'':search;const page=await api.documents(meeting(),id,q,more?cursor.value:null,controller.signal);active(turn);if(serial!==read||library.value!==id||query.value!==search)return;documents.value=Object.freeze(more?[...documents.value,...page.items.filter(x=>!documents.value.some(d=>knowledgeKey(d)===knowledgeKey(x)))]:page.items);cursor.value=page.cursor;}catch(cause){if(turn===epoch&&serial===read)error.value=message(cause);}finally{if(turn===epoch&&serial===read)loading.value=false;}}
 async function open(){if(opened.value)return;const turn=epoch;active(turn);const found=journal.load(actor()!,meeting());if(found.kind!=='EMPTY'&&found.kind!=='RESTORED'){error.value='知识库选择恢复记录不可用，请保留原操作并重试。';return;}if(found.kind==='RESTORED'){query.value=found.value.search;library.value=found.value.library;review.value=found.value.review;selected.value=found.value.selected;}opened.value=true;await loadLibraries();active(turn);if(!library.value)library.value=libraries.value[0]?.id??'';if(!review.value)await loadDocuments();}
 async function chooseLibrary(id:string){if(!libraries.value.some(k=>k.id===id))return;library.value=id;review.value=false;persist();await loadDocuments();}
 async function search(value:string){if(!fitsUtf8(value,1024)){error.value='搜索内容过长。';return;}query.value=value;review.value=false;persist();await loadDocuments();}
 function toggle(d:KnowledgeDocument){if(!validKnowledgeDocument(d)||!documents.value.some(x=>knowledgeKey(x)===knowledgeKey(d))&&!selected.value.some(x=>knowledgeKey(x)===knowledgeKey(d)))throw new Error('请重新读取知识库文件。');const key=knowledgeKey(d),prior=selected.value;selected.value=Object.freeze(prior.some(x=>knowledgeKey(x)===key)?prior.filter(x=>knowledgeKey(x)!==key):[...prior,Object.freeze({...d})]);try{if(selected.value.length>64)throw new Error('最多选择64份背景材料。');persist();}catch(cause){selected.value=prior;throw cause;}}
 function settle(added:readonly string[]){selected.value=Object.freeze(selected.value.filter(d=>!added.includes(knowledgeKey(d))));persist();}
 function clear(){selected.value=[];persist();}
 function setReview(value:boolean){review.value=value;persist();if(!value)void loadDocuments();}
 function close(){opened.value=false;persist();}
 return {libraries,documents,selected,query,library,review,loading,libraryLoading,error,libraryError,cursor,libraryCursor,opened,open,close,chooseLibrary,search,toggle,settle,clear,setReview,reloadLibraries:()=>loadLibraries(),reloadDocuments:()=>loadDocuments(),moreLibraries:()=>loadLibraries(true),moreDocuments:()=>loadDocuments(true),logout(){invalidate();journal.clear();},dispose(){disposed=true;stop();invalidate();}};
}
