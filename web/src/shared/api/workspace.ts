import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {workspaceTaskPage} from './workspace-tasks';
import {revisionToken} from './intent-contract';
export type WorkspaceNavigation=components['schemas']['WorkspaceNavigation'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function shape(v:unknown,keys:string):v is Record<string,unknown>{return typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;}
function navigation(v:unknown,meeting:string):v is WorkspaceNavigation{
 if(typeof v!=='object'||v===null||Array.isArray(v))return false;
 const allowed=['accessRevisionToken','contentEligibility','meetingId','navigationState','relation','revisionToken','saved','stage','lifecycle','governanceState','privateSectionsState','privateSectionsReason','privateSections','activeMinutesGeneration','temporaryTitle','processingSource','processingMaterialStage','processingMaterialsChanged'];
 if(Object.keys(v).some(k=>!allowed.includes(k)))return false;
 const row=v as Record<string,unknown>;
 return navigationFields(row,meeting);
}
function navigationFields(v:Record<string,unknown>,meeting:string):v is WorkspaceNavigation{
 if(('temporaryTitle' in v||'processingSource' in v)&&v.relation!=='OWNER')return false;
 if('processingMaterialsChanged' in v&&(v.relation!=='OWNER'||typeof v.processingMaterialsChanged!=='boolean'))return false;
 if('activeMinutesGeneration' in v&&(v.relation!=='OWNER'||typeof v.activeMinutesGeneration!=='boolean'))return false;
 if('processingMaterialStage' in v&&(v.relation!=='OWNER'||!['TRANSCRIBING','ENHANCING','ADOPTING','SUCCEEDED','FAILED','STALE','CANCELLED'].includes(String(v.processingMaterialStage))))return false;
 if(v.temporaryTitle!=null&&(typeof v.temporaryTitle!=='string'||[...v.temporaryTitle].length>256))return false;
 if('processingSource' in v&&!workspaceTaskPage({items:[v.processingSource],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'},meeting,'ALL'))return false;
 if('lifecycle' in v&&v.lifecycle!=='ACTIVE'||'governanceState' in v&&v.governanceState!=='ACTIVE')return false;
 if('privateSectionsState' in v||'privateSections' in v||'privateSectionsReason' in v){
  if(v.relation!=='OWNER')return false;
  if(v.privateSectionsState==='UNAVAILABLE'){if(v.privateSectionsReason!=='PROCESSING_RUNTIME_UNAVAILABLE'||'privateSections' in v)return false;}
  else if(v.privateSectionsState==='AVAILABLE'){if('privateSectionsReason' in v||!privateSections(v.privateSections,meeting))return false;}else return false;
 }
 if(v.meetingId!==meeting||!revisionToken(v.revisionToken)||!revisionToken(v.accessRevisionToken)||v.contentEligibility!=='RECHECK_REQUIRED'||typeof v.stage!=='string'||!['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(v.stage))return false;
 if(!(v.relation==='MEMBER'&&v.navigationState==='MEMBER_READ_ONLY'||v.relation==='OWNER'&&(v.navigationState==='CHECK_PRODUCER'||v.navigationState==='PUBLISHED_READ_ONLY')))return false;
 if(!shape(v.saved,'elementSetId,infoRevisionId,minutesRevisionId,rosterRevisionId,transcriptConfirmationId,transcriptRevisionId')||!uuid(v.saved.infoRevisionId)||!Object.values(v.saved).every(x=>x===null||uuid(x)))return false;
 return v.saved.transcriptConfirmationId===null||v.saved.transcriptRevisionId!==null;
}
function privateSections(v:unknown,meeting:string):boolean{
 if(!shape(v,'candidates,exclusionRevisionId,finalConfirmationId,publicationId,publicationState,savedGenerationInputId,savedProcessingInputId,tasks,templateRevisionId,verification')||v.verification!=='PERSISTED_REFERENCES_RECHECK_PRODUCER'||!workspaceTaskPage(v.tasks,meeting,'ALL'))return false;
 if(!['exclusionRevisionId','finalConfirmationId','publicationId','savedGenerationInputId','savedProcessingInputId','templateRevisionId'].every(k=>v[k]===null||uuid(v[k])))return false;
 if(v.publicationId===null?v.publicationState!==null:typeof v.publicationState!=='string'||!['READY','RUNNING','RECONCILING','FAILED','SUCCEEDED'].includes(v.publicationState))return false;
 const page=v.candidates;return shape(page,'detailPath,hasMore,items')&&page.detailPath===`/api/v1/meetings/${meeting}/minutes/candidates`&&typeof page.hasMore==='boolean'&&Array.isArray(page.items)&&page.items.length<=20&&page.items.every(c=>shape(c,'id,inputId,state,taskId')&&uuid(c.id)&&uuid(c.inputId)&&uuid(c.taskId)&&c.state==='PENDING')&&new Set(page.items.map(c=>c.id)).size===page.items.length;
}
export function createWorkspaceApi(client:ReturnType<typeof createBusinessClient>,scope:()=>unknown=()=>client){
 type Flight={scope:unknown;controller:AbortController;promise:Promise<WorkspaceNavigation>;subscribers:number};
 const pending=new Map<string,Flight>();
 // One page composes several editors. Share only a currently executing observation,
 // never a completed response; each producer still checks its own permissions/version.
 function observe(meeting:string,signal:AbortSignal):Promise<WorkspaceNavigation>{
  if(signal.aborted)return Promise.reject(new DOMException('Navigation cancelled','AbortError'));
  const identity=scope();let flight=pending.get(meeting);
  if(flight&&(flight.scope!==identity||flight.controller.signal.aborted)){flight.controller.abort();pending.delete(meeting);flight=undefined;}
  if(!flight){
   const controller=new AbortController();
   const promise=Promise.resolve().then(async()=>{
    if(controller.signal.aborted||scope()!==identity)throw new DOMException('Navigation scope changed','AbortError');
    const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/workspace`,{signal:controller.signal,expectedStatus:200,maxResponseBytes:262144});
    if(controller.signal.aborted||scope()!==identity)throw new DOMException('Navigation scope changed','AbortError');
    if(!navigation(value,meeting))throw new ApiError('response',200,'会议导航暂无法确认，请重新加载。');
    const freeze=(v:unknown)=>{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}};freeze(value);return value;
   });
   flight={scope:identity,controller,promise,subscribers:0};pending.set(meeting,flight);
   const current=flight,clear=()=>{if(pending.get(meeting)===current)pending.delete(meeting);};void promise.then(clear,clear);
  }
  const current=flight;current.subscribers++;
  return new Promise((resolve,reject)=>{
   let finished=false;
   const finish=(value?:WorkspaceNavigation,cause?:unknown)=>{if(finished)return;finished=true;signal.removeEventListener('abort',cancel);current.controller.signal.removeEventListener('abort',cancel);current.subscribers--;if(!current.subscribers&&pending.get(meeting)===current){pending.delete(meeting);current.controller.abort();}if(cause)reject(cause);else resolve(value!);};
   const cancel=()=>finish(undefined,new DOMException('Navigation cancelled','AbortError'));
   signal.addEventListener('abort',cancel,{once:true});current.controller.signal.addEventListener('abort',cancel,{once:true});
   void current.promise.then(value=>finish(value),cause=>finish(undefined,cause));
  });
 }
 return {
 async access(meeting:string,signal:AbortSignal):Promise<void>{
  if(!uuid(meeting))throw new TypeError('Invalid workspace meeting');
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/workspace/access`,{signal,priority:'high',expectedStatus:200,maxResponseBytes:4096});
  if(!shape(value,'allowed,meetingId')||value.meetingId!==meeting||value.allowed!==true)throw new ApiError('response',200,'会议内容的访问状态暂无法核实，请重新读取。');
 },
 async navigation(meeting:string,signal:AbortSignal):Promise<WorkspaceNavigation>{
  if(!uuid(meeting))throw new TypeError('Invalid workspace meeting');
  return observe(meeting,signal);
 }
};}
