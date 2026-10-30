import {importFormat} from '../../shared/document-formats';
import {shallowRef,shallowReadonly,watch} from 'vue';
import type {createUploadsApi} from '../../shared/api/uploads';
import type {CommandLookup} from '../../shared/api/command-discovery';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {createJournal} from '../../shared/composables/journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {fitsUtf8} from '../../shared/bounded-text';
import {prepareUpload,UPLOAD_FILE_BYTES} from './upload-bytes';
import {useUploadRecovery} from './useUploadRecovery';
import type {createTranscriptImportApi} from '../../shared/api/transcript-import';
import {revisionToken} from '../../shared/api/intent-contract';
import {ApiError} from '../../shared/api/client';
type Api=Pick<ReturnType<typeof createUploadsApi>,'begin'|'receipt'|'parts'|'part'|'complete'|'abort'>;
type Item=Readonly<{key:string;name:string;size:number;status:string;error:string|null;uploadId:string|null;fileId:string|null;canAbort:boolean;canRemove?:boolean}>;
type Entry={view:Item;command:ReturnType<typeof useCommand<Awaited<ReturnType<Api['begin']>>>>;file?:File;running:boolean};
type Scope={actor:string;meeting:string};
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
/** Two bounded file flights. The journal stores original initialization requests, never file bytes or ACK authority. */
export function useUploadQueue(api:Api,authorize:(meeting:string,signal:AbortSignal)=>Promise<unknown>,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean,context:TabJournalContext,intake?:ReturnType<typeof createTranscriptImportApi>){
 const items=shallowRef<readonly Item[]>([]),blocked=shallowRef(true),error=shallowRef<string|null>(null),ready=shallowRef(false);
 const entries=new Map<string,Entry>(),controllers=new Set<AbortController>(),ownerKey='ama.upload-owner.v1:'+context.tab,cleanupKey='ama.upload-cleanup.v1:'+context.tab;
 const importCommand=useCommand((command:CommandSnapshot)=>{if(!intake)throw new Error('导入接口不可用');return intake.send(command);}),importBusy=shallowRef(false);
 let importFlight:Promise<void>|null=null;
 let scope:Scope|null=null,epoch=0,disposed=false,activeCount=0,cleanupRequired=false;
 let knownOwner:Scope|null=null,lastActor:string|null=actor(),lastMeeting=meeting();
 const recoveries=new Set<ReturnType<typeof useUploadRecovery>>(),waiters:Array<()=>void>=[];
 function publish(){items.value=Object.freeze([...entries.values()].map(e=>Object.freeze({...e.view,canRemove:!e.running&&(['EMPTY','CONFLICT','REJECTED'].includes(e.command.state.value)||['OBSERVED_COMPLETED','ABORTED','FAILED'].includes(e.view.status))})));}
 function update(entry:Entry,patch:Partial<Item>){entry.view=Object.freeze({...entry.view,...patch});publish();}
 function valid(v:unknown,who:string,id:string):v is CommandSnapshot[]{
  if(!Array.isArray(v)||v.length>16)return false;
  return new Set(v.map(c=>c?.key)).size===v.length&&v.every(c=>{
   if(!shape(c,'actor,body,key,operation,path,resource')||c.actor!==who||c.resource!==id||c.operation!=='UPLOAD_BEGIN'||c.path!==`/api/v1/meetings/${id}/uploads`||!uuid(c.key)||typeof c.body!=='string'||!fitsUtf8(c.body,2*1024*1024))return false;
   try{const b:unknown=JSON.parse(c.body);return shape(b,'name,purpose,sha256,size')&&b.purpose==='TRANSCRIPT_INPUT'&&typeof b.name==='string'&&fitsUtf8(b.name,1024*1024)&&importFormat(b.name)!==null&&typeof b.size==='number'&&Number.isSafeInteger(b.size)&&b.size>=0&&b.size<=UPLOAD_FILE_BYTES&&typeof b.sha256==='string'&&/^[0-9a-f]{64}$/.test(b.sha256);}catch{return false;}
  });
 }
 function journal(who:string,id:string){if(!context.storage)throw new Error('上传恢复存储不可用');return createJournal(context.storage,{actor:who,meeting:id,kind:'upload-queue',tab:context.tab},(v):v is CommandSnapshot[]=>valid(v,who,id),8*1024*1024);}
 function importJournal(who:string,id:string){if(!context.storage)throw new Error('导入恢复存储不可用');return createJournal(context.storage,{actor:who,meeting:id,kind:'import-command',tab:context.tab},(v):v is CommandSnapshot=>{
  if(!shape(v,'actor,body,key,operation,path,resource')||v.actor!==who||v.resource!==id||v.operation!=='TRANSCRIPT_IMPORT'||v.path!==`/api/v1/meetings/${id}/transcript-imports`||!uuid(v.key)||typeof v.body!=='string'||!fitsUtf8(v.body,65536))return false;
  try{const b:unknown=JSON.parse(v.body);return shape(b,'expectedRevisionToken,orderedFileIds')&&revisionToken(b.expectedRevisionToken)&&Array.isArray(b.orderedFileIds)&&b.orderedFileIds.length>0&&b.orderedFileIds.length<=16&&b.orderedFileIds.every(uuid)&&new Set(b.orderedFileIds).size===b.orderedFileIds.length;}catch{return false;}
 },131072);}
 function markCleanup(){cleanupRequired=true;try{context.storage?.setItem(cleanupKey,'required');}catch{/* Keep the live barrier. */}}
 function noteScope(){const who=actor(),id=meeting();if(who!==null&&(lastActor!==null&&who!==lastActor||knownOwner&&who!==knownOwner.actor)||lastMeeting!==''&&id!==lastMeeting||knownOwner&&id!==knownOwner.meeting)markCleanup();if(who!==null)lastActor=who;lastMeeting=id;}
 function owner():Scope|null{if(!context.storage)throw new Error('上传恢复存储不可用');const marker=context.storage.getItem(cleanupKey);if(marker!==null){if(marker!=='required')throw new Error('清理标记无效');cleanupRequired=true;}const raw=context.storage.getItem(ownerKey);if(raw===null){knownOwner=null;return null;}if(!fitsUtf8(raw,4096))throw new Error('上传恢复记录无效');const value:unknown=JSON.parse(raw);if(!(shape(value,'actor,meeting')||shape(value,'actor,meeting,revoked')&&value.revoked===true)||typeof value.actor!=='string'||!value.actor||!fitsUtf8(value.actor,1020)||!uuid(value.meeting))throw new Error('上传恢复记录无效');if(value.revoked===true)cleanupRequired=true;knownOwner={actor:value.actor,meeting:value.meeting};return knownOwner;}
 function clearStored(){markCleanup();try{const old=owner();if(old){try{context.storage!.setItem(ownerKey,JSON.stringify({...old,revoked:true}));}catch{/* In-memory cleanup remains mandatory. */}if(journal(old.actor,old.meeting).clear().kind!=='CLEARED'||importJournal(old.actor,old.meeting).clear().kind!=='CLEARED')return false;}context.storage!.removeItem(ownerKey);context.storage!.removeItem(cleanupKey);knownOwner=null;cleanupRequired=false;return true;}catch{return false;}}
 function persist(){if(!scope||blocked.value)throw new Error('请先恢复上传记录');const commands=[...entries.values()].flatMap(e=>e.command.snapshot.value?[e.command.snapshot.value]:[]);context.storage!.setItem(ownerKey,JSON.stringify(scope));if(journal(scope.actor,scope.meeting).save(commands).kind!=='SAVED')throw new Error('无法保存上传原请求，请保持页面打开并重试');}
 function invalidate(){epoch++;for(const c of controllers)c.abort();controllers.clear();for(const recovery of recoveries)recovery.dispose();recoveries.clear();for(const e of entries.values())e.command.invalidate();entries.clear();scope=null;importCommand.invalidate();importFlight=null;importBusy.value=false;ready.value=false;blocked.value=true;error.value=null;publish();}
 const stop=watch(()=>[actor(),meeting(),eligible()],()=>{noteScope();invalidate();try{const old=owner();if(cleanupRequired||old&&(old.meeting!==meeting()||actor()!==null&&old.actor!==actor()))clearStored();}catch{/* open() stays blocked until recovery and cleanup succeed. */}},{flush:'sync'});
 function active(generation:number){if(disposed||epoch!==generation||!scope||actor()!==scope.actor||meeting()!==scope.meeting||!eligible())throw new DOMException('Upload queue scope changed','AbortError');}
 function entry(command:CommandSnapshot):Entry{const b=JSON.parse(command.body) as {name:string;size:number};const value:Entry={view:Object.freeze({key:command.key,name:b.name,size:b.size,status:'NEEDS_FILE',error:null,uploadId:null,fileId:null,canAbort:false}),command:useCommand(api.begin),running:false};value.command.restore(command);return value;}
 async function open(){
  if(disposed)return;invalidate();const who=actor(),id=meeting(),generation=epoch;if(!who||!eligible())return;
  const controller=new AbortController();controllers.add(controller);
  try{await authorize(id,controller.signal);if(disposed||generation!==epoch||actor()!==who||meeting()!==id||!eligible())return;
   if(context.recovery==='UNAVAILABLE')throw new Error('无法确认原上传命名空间');noteScope();let old=owner();if(cleanupRequired||old&&(old.actor!==who||old.meeting!==id)){if(!clearStored())throw new Error('无法清理旧上传记录');old=null;}const found=journal(who,id).load();
   if(found.kind==='INVALID'||found.kind==='UNAVAILABLE'||found.kind==='EMPTY'&&old?.actor===who&&old.meeting===id||found.kind==='RESTORED'&&(!old||old.actor!==who||old.meeting!==id))throw new Error('上传恢复记录暂无法读取');
   const originalImport=importJournal(who,id).load();if(originalImport.kind==='INVALID'||originalImport.kind==='UNAVAILABLE'||originalImport.kind==='RESTORED'&&!old)throw new Error('导入恢复记录暂无法读取');
   scope={actor:who,meeting:id};if(found.kind==='RESTORED')for(const command of found.value)entries.set(command.key,entry(command));if(originalImport.kind==='RESTORED')importCommand.restore(originalImport.value);blocked.value=false;ready.value=true;publish();
  }catch(cause){if(generation===epoch)error.value=cause instanceof Error?cause.message:'上传恢复失败';}finally{controllers.delete(controller);}
 }
 async function slot(run:()=>Promise<void>){if(activeCount>=2)await new Promise<void>(resolve=>waiters.push(resolve));else activeCount++;try{await run();}finally{const next=waiters.shift();if(next)next();else activeCount--;}}
 async function run(value:Entry,file:File,generation:number){
  if(value.running)return;value.running=true;
  await slot(async()=>{
   const controller=new AbortController();controllers.add(controller);let recovery:ReturnType<typeof useUploadRecovery>|null=null;
   try{active(generation);update(value,{status:'VERIFYING',error:null});active(generation);
    const snapshot=value.command.snapshot.value,original=snapshot?JSON.parse(snapshot.body) as {size:number;sha256:string}:null;
    const prepared=await prepareUpload(file,controller.signal,original?{sizeToken:String(original.size),sha256:original.sha256}:undefined);active(generation);
    if(!snapshot){value.command.prepare({actor:scope!.actor,resource:scope!.meeting,operation:'UPLOAD_BEGIN',path:`/api/v1/meetings/${scope!.meeting}/uploads`,key:value.view.key,body:prepared.begin});update(value,{name:prepared.begin.name,size:prepared.begin.size});}
    active(generation);persist();active(generation);update(value,{status:'INITIALIZING'});active(generation);
    const accepted=value.command.state.value==='ACKNOWLEDGED'?value.command.ack.value!:await value.command.send();active(generation);update(value,{uploadId:accepted.uploadId});active(generation);
    const receipt=await api.receipt(scope!.meeting,accepted.uploadId,controller.signal);active(generation);const body=JSON.parse(value.command.snapshot.value!.body) as {name:string;size:number;sha256:string};
    if(receipt.displayName!==body.name||receipt.expectedSizeToken!==String(body.size)||receipt.expectedSha256!==body.sha256||receipt.purpose!=='TRANSCRIPT_INPUT')throw new Error('上传回执与原文件请求不一致');
    update(value,{canAbort:receipt.state==='OPEN'&&receipt.allowedActions.includes('ABORT')});active(generation);
    recovery=useUploadRecovery(api,actor,meeting,()=>accepted.uploadId);recoveries.add(recovery);await recovery.resume(file);active(generation);
    const final=recovery.receipt.value;update(value,{status:recovery.stage.value,fileId:final?.completed?.intakeEligibility.state==='ELIGIBLE'?final.completed.fileId:null,canAbort:final?.state==='OPEN'&&final.allowedActions.includes('ABORT')});
   }catch(cause){if(generation===epoch){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){invalidate();error.value=cause.message;}else update(value,{status:'UNRESOLVED',error:cause instanceof Error?cause.message:'上传失败，请重试原操作'});}}
   finally{if(recovery){recovery.dispose();recoveries.delete(recovery);}controllers.delete(controller);value.running=false;if(generation===epoch)publish();}
  });
 }
 async function add(files:readonly File[]){
  requireMutable();const generation=epoch;active(generation);if(files.length<1||files.length+entries.size>16)throw new Error('每批最多选择 16 个文件');
  const work:Promise<void>[]=[];for(const file of files){active(generation);const key=crypto.randomUUID(),value:Entry={view:Object.freeze({key,name:file.name,size:file.size,status:'QUEUED',error:null,uploadId:null,fileId:null,canAbort:false}),command:useCommand(api.begin),running:false};entries.set(key,value);work.push(run(value,file,generation));}active(generation);publish();await Promise.all(work);
 }
 function requireMutable(){if(!ready.value||blocked.value||!scope||importFlight||importCommand.state.value!=='EMPTY')throw new Error('导入文件清单已冻结或尚未恢复，请先处理原请求');}
 async function retry(key:string,file:File){requireMutable();const value=entries.get(key);if(!value)throw new Error('原上传记录不可用');await run(value,file,epoch);}
 async function discover(key:string,lookup:(command:CommandSnapshot,signal:AbortSignal)=>Promise<CommandLookup>){
  const value=entries.get(key),generation=epoch;if(!value?.command.snapshot.value||value.running)return;active(generation);const controller=new AbortController();controllers.add(controller);
  try{const snapshot=value.command.snapshot.value,result=await lookup(snapshot,controller.signal);active(generation);if(result.status!=='FOUND'||!result.association){update(value,{status:'NEEDS_FILE',error:'尚未查到已受理上传，请选择原文件重试原请求'});return;}
   const uploadId=result.association.resultId,receipt=await api.receipt(scope!.meeting,uploadId,controller.signal);active(generation);const body=JSON.parse(snapshot.body) as {name:string;size:number;sha256:string};if(receipt.displayName!==body.name||receipt.expectedSizeToken!==String(body.size)||receipt.expectedSha256!==body.sha256||receipt.purpose!=='TRANSCRIPT_INPUT')throw new Error('上传关联与原请求不一致');
   update(value,{uploadId,status:receipt.state==='COMPLETED'?'OBSERVED_COMPLETED':receipt.state==='ABORTED'?'ABORTED':receipt.state==='FAILED'?'FAILED':'NEEDS_FILE',fileId:receipt.completed?.intakeEligibility.state==='ELIGIBLE'?receipt.completed.fileId:null,canAbort:receipt.state==='OPEN'&&receipt.allowedActions.includes('ABORT'),error:null});
  }catch(cause){if(generation===epoch){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){invalidate();error.value=cause.message;}else update(value,{error:cause instanceof Error?cause.message:'上传查询失败'});}}finally{controllers.delete(controller);}
 }
 function changeOrder(ordered:Array<[string,Entry]>){const previous=[...entries.entries()];entries.clear();for(const [id,value] of ordered)entries.set(id,value);try{persist();}catch(cause){entries.clear();for(const [id,value] of previous)entries.set(id,value);throw cause;}publish();}
 function move(key:string,direction:-1|1){requireMutable();if([...entries.values()].some(e=>e.running))throw new Error('请等待当前上传操作');const ordered=[...entries.entries()],index=ordered.findIndex(([id])=>id===key),next=index+direction;if(index<0||next<0||next>=ordered.length)return;[ordered[index],ordered[next]]=[ordered[next]!,ordered[index]!];changeOrder(ordered);}
 function remove(key:string){requireMutable();const value=entries.get(key);if(!value)return;if(value.running||!(['EMPTY','CONFLICT','REJECTED'].includes(value.command.state.value)||['OBSERVED_COMPLETED','ABORTED','FAILED'].includes(value.view.status)))throw new Error('请先查询或终止原上传，不能丢弃结果未知的请求');changeOrder([...entries.entries()].filter(([id])=>id!==key));}
 async function abort(key:string){requireMutable();const value=entries.get(key),generation=epoch;if(!value?.view.uploadId||value.running)throw new Error('请先查询上传结果');value.running=true;const recovery=useUploadRecovery(api,actor,meeting,()=>value.view.uploadId!);recoveries.add(recovery);try{update(value,{status:'ABORTING',error:null});active(generation);await recovery.abort();active(generation);const receipt=recovery.receipt.value;update(value,{status:recovery.stage.value,fileId:receipt?.completed?.intakeEligibility.state==='ELIGIBLE'?receipt.completed.fileId:null,canAbort:false});}catch(cause){if(generation===epoch){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){invalidate();error.value=cause.message;}else update(value,{status:'UNRESOLVED',error:cause instanceof Error?cause.message:'终止结果尚未确认'});}}finally{value.running=false;recovery.dispose();recoveries.delete(recovery);if(generation===epoch)publish();}}
 function submitImport(existing?:readonly {fileId:string;name:string}[]):Promise<void>{
  if(importFlight)return importFlight;const generation=epoch,chosen=existing?.map(file=>Object.freeze({fileId:file.fileId,name:file.name}));
  const execute=async()=>{const controller=new AbortController();controllers.add(controller);try{
   active(generation);if(!ready.value||blocked.value||!intake)throw new Error('请先恢复导入记录');importBusy.value=true;error.value=null;active(generation);
   if(importCommand.state.value==='ACKNOWLEDGED')return;
   if(importCommand.state.value==='EMPTY'){
    const sourceFiles=chosen??[...entries.values()].map(e=>({fileId:e.view.fileId,name:e.view.name})),files=sourceFiles.map(e=>e.fileId);if(!files.length||files.length>16||files.some(x=>typeof x!=='string'||!uuid(x))||new Set(files).size!==files.length||[...entries.values()].some(e=>e.running||!e.view.fileId||!files.includes(e.view.fileId)))throw new Error('请先完成所有文件上传并查询可导入结果');
    const capabilities=await intake.capabilities(scope!.meeting,controller.signal);active(generation);const limit=capabilities.budgets.maxFiles;
    if(!capabilities.canSubmit||!capabilities.businessReady||capabilities.intake!=='AVAILABLE'||capabilities.execution!=='INSTALLED'||sourceFiles.some(e=>{const format=importFormat(e.name);return !format||!capabilities.formats.includes(format);})||typeof limit!=='number'||!Number.isSafeInteger(limit)||files.length>limit)throw new Error('当前会议暂不满足导入条件，请检查处理服务和文件数量');
    importCommand.prepare({actor:scope!.actor,resource:scope!.meeting,operation:'TRANSCRIPT_IMPORT',path:`/api/v1/meetings/${scope!.meeting}/transcript-imports`,key:crypto.randomUUID(),body:{expectedRevisionToken:capabilities.revisionToken,orderedFileIds:files}});
   }
   active(generation);persist();if(importJournal(scope!.actor,scope!.meeting).save(importCommand.snapshot.value!).kind!=='SAVED')throw new Error('无法保存原导入请求');active(generation);await importCommand.send();active(generation);
  }catch(cause){if(generation===epoch){if(cause instanceof ApiError&&[401,403].includes(cause.status??0))invalidate();error.value=cause instanceof Error?cause.message:'导入请求暂无法确认';}}finally{controllers.delete(controller);if(generation===epoch)importBusy.value=false;}};
  let finish!:()=>void;const flight=new Promise<void>(resolve=>{finish=resolve;});importFlight=flight;void flight.then(()=>{if(generation===epoch)importFlight=null;});void execute().then(finish,finish);return flight;
 }
 function discardRejectedImport(){if(!scope||!['CONFLICT','REJECTED'].includes(importCommand.state.value)||importFlight)throw new Error('原导入结果仍未确定');if(importJournal(scope.actor,scope.meeting).clear().kind!=='CLEARED')throw new Error('无法清理已拒绝的导入记录');importCommand.reconcileRejected();}
 function newBatch(){if(!scope||importCommand.state.value!=='ACKNOWLEDGED'||importFlight||[...entries.values()].some(e=>e.running))throw new Error('请先确认原导入受理结果');if(journal(scope.actor,scope.meeting).save([]).kind!=='SAVED'||importJournal(scope.actor,scope.meeting).clear().kind!=='CLEARED')throw new Error('新批次恢复记录暂无法保存');entries.clear();importCommand.invalidate();publish();}
 function restoreSelection(files:readonly {fileId:string;displayName:string;sizeBytes:number}[]){
  requireMutable();if([...entries.values()].some(e=>e.running||!e.view.fileId)||files.length>16||new Set(files.map(f=>f.fileId)).size!==files.length||files.some(f=>!uuid(f.fileId)||!importFormat(f.displayName)||!Number.isSafeInteger(f.sizeBytes)||f.sizeBytes<0||f.sizeBytes>UPLOAD_FILE_BYTES))throw new Error('当前上传尚未确认，不能替换已保存文件');
  const previous=[...entries.entries()];entries.clear();for(const f of files){const key=crypto.randomUUID();entries.set(key,{view:Object.freeze({key,name:f.displayName,size:f.sizeBytes,status:'OBSERVED_COMPLETED',error:null,uploadId:null,fileId:f.fileId,canAbort:false}),command:useCommand(api.begin),running:false});}try{persist();}catch(cause){entries.clear();for(const [key,e] of previous)entries.set(key,e);throw cause;}publish();
 }
 return {items:shallowReadonly(items),blocked:shallowReadonly(blocked),ready:shallowReadonly(ready),error:shallowReadonly(error),importCommand,importBusy:shallowReadonly(importBusy),open,add,retry,discover,abort,move,remove,submitImport,discardRejectedImport,newBatch,restoreSelection,
  logout(){invalidate();clearStored();},dispose(){disposed=true;stop();invalidate();}};
}
