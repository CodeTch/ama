import type {createPendingImportJournal} from './pending-import-journal';
import {shallowRef,computed} from 'vue';import {useCommand} from '../../shared/composables/useCommand';import type {createImportDraftsApi,ImportDraft} from '../../shared/api/import-drafts';import type {createUploadsApi} from '../../shared/api/uploads';import type {useUploadQueue} from './useUploadQueue';import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';import {validImportDraftCommand} from '../../shared/api/import-drafts';import type {TabJournalContext} from '../../shared/composables/tab-journal';import {ApiError} from '../../shared/api/client';
export function useImportDraft(api:ReturnType<typeof createImportDraftsApi>,uploads:ReturnType<typeof createUploadsApi>,queue:ReturnType<typeof useUploadQueue>,actor:()=>string|null,meeting:()=>string,context:TabJournalContext,lookup:Parameters<ReturnType<typeof useUploadQueue>['discover']>[1],pendingImports:ReturnType<typeof createPendingImportJournal>){
 const saved=shallowRef<ImportDraft|null>(null),loading=shallowRef(false),error=shallowRef<string|null>(null),busy=shallowRef(false),cleanupPending=shallowRef(false),command=useCommand(api.send,65536),journal=createScopedPrivateJournal(context,'import-draft',validImportDraftCommand);const denied=shallowRef(false);let deferred=false;let epoch=0,controller:AbortController|null=null,scope:{actor:string;meeting:string}|null=null;
 const pending=computed(()=>cleanupPending.value||['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(command.state.value)),rejected=computed(()=>['CONFLICT','REJECTED'].includes(command.state.value));
 function active(turn:number){if(turn!==epoch||!scope||actor()!==scope.actor||meeting()!==scope.meeting)throw new DOMException('Import draft scope changed','AbortError');}
 function invalidate(clear=false){epoch++;deferred=false;denied.value=false;controller?.abort();controller=null;saved.value=null;loading.value=false;busy.value=false;cleanupPending.value=false;error.value=null;scope=null;command.invalidate();if(clear)journal.clear();}
 async function open(){if(rejected.value){
  const original=command.snapshot.value!;
  if(!(deferred?pendingImports.remove(original):journal.clear()))throw Error('无法清理已拒绝的保存请求');
 }invalidate();const who=actor(),id=meeting();if(!who)return;const turn=epoch;scope={actor:who,meeting:id};controller=new AbortController();loading.value=true;try{const retained=pendingImports.get(who,id);
   if(retained){deferred=true;denied.value=retained.denied;command.restore(retained.command);return;}
   const original=journal.load(who,id);if(original.kind==='INVALID'||original.kind==='UNAVAILABLE')throw Error('导入保存恢复记录暂不可用，请保留页面并重试。');if(original.kind==='RESTORED')command.restore(original.value);if(command.snapshot.value)return;if(!queue.ready.value)await queue.open();active(turn);for(const item of queue.items.value){if(!item.fileId&&item.status==='NEEDS_FILE'){await queue.discover(item.key,lookup);active(turn);}}const value=await api.read(id,controller.signal);active(turn);saved.value=value;
   if(queue.importCommand.state.value==='EMPTY'&&queue.items.value.length===0&&value.files.length){const files=[];for(const source of value.files){const receipt=await uploads.receipt(id,source.uploadId,controller.signal);active(turn);const file=receipt.completed;if(!file||file.fileId!==source.fileId||file.originalPurpose!=='TRANSCRIPT_INPUT'||file.fileState!=='AVAILABLE'||file.intakeEligibility.state!=='ELIGIBLE')throw Error('保存的会议主文件暂不可用，请重新核对原上传结果。');files.push(file);}active(turn);queue.restoreSelection(files);}
  }catch(cause){if(turn===epoch){error.value=cause instanceof Error?cause.message:'导入草稿读取失败';if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){saved.value=null;queue.logout();}}}finally{if(turn===epoch)loading.value=false;}}
 async function execute(operation:'IMPORT_DRAFT_SAVE'|'IMPORT_DRAFT_DISCARD'){
  const turn=epoch;active(turn);if(busy.value)throw Error('请等待当前保存操作');if(denied.value)throw Error('当前账号无法确认原导入操作。');if(rejected.value)throw Error('原保存已被拒绝，请重新读取导入草稿。');busy.value=true;error.value=null;try{
   if(!pending.value){const snapshot=await api.read(scope!.meeting,controller?.signal??new AbortController().signal);active(turn);saved.value=snapshot;const body=operation==='IMPORT_DRAFT_SAVE'?{expectedRevisionToken:snapshot.revisionToken,orderedFileIds:queue.items.value.map(f=>{if(!f.fileId)throw Error('请先完成所有文件上传');return f.fileId;})}:{expectedRevisionToken:snapshot.meetingRevisionToken};command.prepare({actor:scope!.actor,resource:scope!.meeting,operation,path:`/api/v1/meetings/${scope!.meeting}/${operation==='IMPORT_DRAFT_SAVE'?'import-draft':'import-discard'}`,key:crypto.randomUUID(),body});}
   if(command.snapshot.value?.operation!==operation)throw Error('请先恢复原保存或放弃请求');active(turn);if(!deferred&&journal.save(scope!.actor,scope!.meeting,command.snapshot.value!)!=='SAVED')throw Error('原保存请求暂无法持久化，请保留页面重试');const result=command.state.value==='ACKNOWLEDGED'?command.ack.value!:await command.send();active(turn);cleanupPending.value=true;if(!(deferred?pendingImports.remove(command.snapshot.value!):journal.clear()))throw Error('请求已确认，本地恢复记录清理失败，请重试。');cleanupPending.value=false;if('orderedFileIds'in result)saved.value=result;return result;
  }catch(cause){if(turn===epoch&&command.state.value==='DENIED'){
    denied.value=true;
    const original=command.snapshot.value!;
    const retained=deferred?pendingImports.list(original.actor).find(entry=>entry.command.key===original.key):undefined;
    if(retained)pendingImports.put({...retained,denied:true});
   }
   if(turn===epoch)error.value=cause instanceof Error?cause.message:'保存结果尚未确认，请重试原请求';throw cause;}finally{if(turn===epoch)busy.value=false;}
 }
 function defer(newImport:boolean){
  const original=command.snapshot.value;
  if(!original||busy.value||!pending.value||cleanupPending.value)return false;
  if(!pendingImports.put({command:original,newImport,denied:denied.value}))return false;
  if(!deferred&&!journal.clear()){
   // Keep both copies until the legacy owner record can be cleared safely.
   return false;
  }
  deferred=true;
  return true;
 }
 const retryable=computed(()=>pending.value&&!denied.value&&!busy.value);
 return {defer,retryable,denied,cleanupPending,saved,loading,error,busy,pending,rejected,command,open,save:()=>execute('IMPORT_DRAFT_SAVE'),discard:()=>execute('IMPORT_DRAFT_DISCARD'),invalidate};
}
