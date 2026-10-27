import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
export type WorkspaceNavigation=components['schemas']['WorkspaceNavigation'];
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function shape(v:unknown,keys:string):v is Record<string,unknown>{return typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;}
function navigation(v:unknown,meeting:string):v is WorkspaceNavigation{
 if(!shape(v,'accessRevisionToken,contentEligibility,meetingId,navigationState,relation,revisionToken,saved,stage')||v.meetingId!==meeting||!revisionToken(v.revisionToken)||!revisionToken(v.accessRevisionToken)||v.contentEligibility!=='RECHECK_REQUIRED'||typeof v.stage!=='string'||!['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(v.stage))return false;
 if(!(v.relation==='MEMBER'&&v.navigationState==='MEMBER_READ_ONLY'||v.relation==='OWNER'&&(v.navigationState==='CHECK_PRODUCER'||v.navigationState==='PUBLISHED_READ_ONLY')))return false;
 if(!shape(v.saved,'elementSetId,infoRevisionId,minutesRevisionId,rosterRevisionId,transcriptConfirmationId,transcriptRevisionId')||!uuid(v.saved.infoRevisionId)||!Object.values(v.saved).every(x=>x===null||uuid(x)))return false;
 return v.saved.transcriptConfirmationId===null||v.saved.transcriptRevisionId!==null;
}
export function createWorkspaceApi(client:ReturnType<typeof createBusinessClient>){return {
 async navigation(meeting:string,signal:AbortSignal):Promise<WorkspaceNavigation>{
  if(!uuid(meeting))throw new TypeError('Invalid workspace meeting');
  const value=await client.json<unknown>(`/api/v1/meetings/${meeting}/workspace`,{signal,expectedStatus:200,maxResponseBytes:16384});
  if(!navigation(value,meeting))throw new ApiError('response',200,'会议导航暂无法确认，请重新加载。');
  Object.freeze(value.saved);return Object.freeze(value);
 }
};}
