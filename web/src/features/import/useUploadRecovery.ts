import {shallowReadonly,shallowRef,watch} from 'vue';
import type {components} from '../../shared/api/generated';
import type {createUploadsApi,UploadReceipt} from '../../shared/api/uploads';
import {useCommand} from '../../shared/composables/useCommand';
import {prepareUpload,UPLOAD_PART_COUNT} from './upload-bytes';
type Api=Pick<ReturnType<typeof createUploadsApi>,'receipt'|'parts'|'part'|'complete'|'abort'>;
type Stage='IDLE'|'CHECKING'|'NEEDS_FILE'|'VERIFYING'|'UPLOADING'|'COMPLETING'|'UNRESOLVED'|'BLOCKED'|'ABORTED'|'OBSERVED_COMPLETED'|'COMPLETION_ACKNOWLEDGED';
/** One already-discovered upload. Begin-key journaling and the multi-file queue are separate coordinators.
 * Navigation cancels browser work, never assumes durable server work was cancelled.
 */
export function useUploadRecovery(api:Api,actor:()=>string|null,meeting:()=>string,upload:()=>string){
 const stage=shallowRef<Stage>('IDLE'),receipt=shallowRef<UploadReceipt|null>(null),error=shallowRef<unknown>(null),busy=shallowRef(false);
 const command=useCommand(api.complete);let epoch=0,controller:AbortController|null=null,flight:Promise<void>|null=null,flightKind:string|null=null,flightFile:File|undefined,disposed=false;
 function invalidate(){const generation=++epoch,previous=controller;controller=null;flight=null;flightKind=null;flightFile=undefined;command.invalidate();previous?.abort();if(generation!==epoch)return;receipt.value=null;if(generation!==epoch)return;error.value=null;if(generation!==epoch)return;busy.value=false;if(generation!==epoch)return;stage.value='IDLE';}
 const stop=watch(()=>[actor(),meeting(),upload()],invalidate,{flush:'sync'});
 function run(kind:'inspect'|'resume'|'abort',file?:File):Promise<void>{
  if(disposed)return Promise.reject(new Error('Upload coordinator disposed'));
  if(flight)return flightKind===kind&&(kind!=='resume'||flightFile===file)?flight:Promise.reject(new Error('An upload action is already running'));
  const who=actor(),meetingId=meeting(),uploadId=upload();if(!who)return Promise.reject(new Error('Active session required'));
  const generation=++epoch;controller=new AbortController();const signal=controller.signal;flightKind=kind;flightFile=file;
  function active(){if(disposed||generation!==epoch||actor()!==who||meeting()!==meetingId||upload()!==uploadId||signal.aborted)throw new DOMException('Upload scope changed','AbortError');}
  function setStage(value:Stage){active();stage.value=value;active();}
  function blocked(message:string):never{setStage('BLOCKED');throw new Error(message);}
  async function fresh(){active();const value=await api.receipt(meetingId,uploadId,signal);active();if(command.ack.value&&value.completedFileId!==command.ack.value.fileId)throw new Error('Completion observation differs from acknowledged result');receipt.value=value;active();return value;}
  function observed(value:UploadReceipt){if(value.state==='COMPLETED'){setStage('OBSERVED_COMPLETED');return true;}if(value.state==='ABORTED'){setStage('ABORTED');return true;}return false;}
  const execute=async()=>{
   try{
    active();busy.value=true;active();error.value=null;active();receipt.value=null;active();setStage('CHECKING');
    const initial=await fresh();
    if(kind==='inspect'){if(!observed(initial))setStage(initial.state==='OPEN'||initial.state==='FINALIZING'?'NEEDS_FILE':'BLOCKED');return;}
    if(kind==='abort'){
     const allowed=initial.state==='OPEN'&&initial.allowedActions.includes('ABORT')||(initial.state==='ABORTED'||initial.state==='COMPLETED')&&initial.idempotentRetries.includes('ABORT');
     if(!allowed)blocked('This upload cannot be aborted');active();await api.abort(who,uploadId);active();const current=await fresh();if(!observed(current))throw new Error('Abort observation unresolved');return;
    }
    if(observed(initial))return;
    if(!file||!['OPEN','FINALIZING'].includes(initial.state)||initial.purpose!=='TRANSCRIPT_INPUT'||initial.expectedSha256===null)blocked('Select the original supported file before recovery');
    setStage('VERIFYING');const prepared=await prepareUpload(file,signal,{sizeToken:initial.expectedSizeToken,sha256:initial.expectedSha256});active();
    const receipts:components['schemas']['PartSummary'][]=[];let after='0';
    for(let pages=0;;pages++){
     if(pages>=10)blocked('Part recovery exceeds browser bound');active();const page=await api.parts(meetingId,uploadId,after,signal);active();
     if(receipts.length+page.items.length>UPLOAD_PART_COUNT)blocked('Part recovery exceeds browser bound');receipts.push(...page.items);
     if(page.nextCursor===null)break;if(BigInt(page.nextCursor)<=BigInt(after))blocked('Part recovery cursor did not advance');after=page.nextCursor;
    }
    const plan=await prepared.plan(receipts,initial.accepted.partSizeToken);active();
    if(plan.some(part=>!part.accepted)&&(initial.state!=='OPEN'||!initial.allowedActions.includes('UPLOAD_PARTS')))blocked('Upload does not permit additional parts');
    for(const part of plan){if(part.accepted)continue;active();setStage('UPLOADING');await api.part(who,uploadId,part.part,prepared.body(part),part.sha256,signal);active();}
    const current=await fresh();if(observed(current))return;
    if(current.expectedSizeToken!==initial.expectedSizeToken||current.expectedSha256!==initial.expectedSha256||current.purpose!==initial.purpose||!current.allowedActions.includes('COMPLETE')||!['OPEN','FINALIZING'].includes(current.state))blocked('Upload completion must be rechecked');
    const body={parts:plan.map(part=>({part:part.part,sha256:part.sha256})),sha256:prepared.begin.sha256};
    if(command.state.value==='EMPTY')command.prepare({actor:who,operation:'UPLOAD_COMPLETE',resource:uploadId,path:`/api/v1/uploads/${uploadId}/completion`,key:crypto.randomUUID(),body});
    else if(command.snapshot.value?.body!==JSON.stringify(body))blocked('Original completion manifest remains unresolved');
    active();setStage('COMPLETING');await command.send();active();setStage('COMPLETION_ACKNOWLEDGED');const final=await fresh();if(!observed(final))throw new Error('Completion observation unresolved');
   }catch(cause){if(generation===epoch){error.value=cause;if(generation!==epoch)throw new DOMException('Upload scope changed','AbortError');if(command.ack.value)setStage('COMPLETION_ACKNOWLEDGED');else if(stage.value!=='BLOCKED')setStage('UNRESOLVED');}throw cause;}
   finally{if(generation===epoch){busy.value=false;if(generation===epoch)controller=null;}}
  };
  let accept!:()=>void,decline!:(cause:unknown)=>void;const currentFlight=new Promise<void>((resolve,reject)=>{accept=resolve;decline=reject;});flight=currentFlight;void currentFlight.then(clear,clear);void execute().then(accept,decline);function clear(){if(generation===epoch){flight=null;flightKind=null;flightFile=undefined;}}
  return currentFlight;
 }
 return {stage:shallowReadonly(stage),receipt:shallowReadonly(receipt),error:shallowReadonly(error),busy:shallowReadonly(busy),completion:{state:command.state,ack:command.ack,snapshot:command.snapshot},inspect:()=>run('inspect'),resume:(file:File)=>run('resume',file),abort:()=>run('abort'),dispose(){disposed=true;stop();invalidate();}};
}
