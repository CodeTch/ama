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
 const allowed=['accessRevisionToken','contentEligibility','meetingId','navigationState','relation','revisionToken','saved','stage','lifecycle','governanceState','privateSectionsState','privateSectionsReason','privateSections'];
 if(Object.keys(v).some(k=>!allowed.includes(k)))return false;
 const row=v as Record<string,unknown>;
 return navigationFields(row,meeting);
}
function navigationFields(v:Record<string,unknown>,meeting:string):v is WorkspaceNavigation{
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
export function createWorkspaceApi(client:ReturnType<typeof createBusinessClient>){return {
 async navigation(meeting:string,signal:AbortSignal):Promise<WorkspaceNavigation>{
  if(!uuid(meeting))throw new TypeError('Invalid workspace meeting');
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/workspace`,{signal,expectedStatus:200,maxResponseBytes:262144});
  if(!navigation(value,meeting))throw new ApiError('response',200,'会议导航暂无法确认，请重新加载。');
  const freeze=(v:unknown)=>{if(v!==null&&typeof v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}};freeze(value);return value;
 }
};}
