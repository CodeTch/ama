import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
export type MeetingPage=components['schemas']['MeetingPage'];
export type MeetingScope='owned'|'joined';
export type MeetingFilters={q?:string;stages?:string[];categories?:string[];from?:string;to?:string;topic?:string};
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}
const uuid=(v:unknown)=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const nullableText=(v:unknown)=>v===null||typeof v==='string';
function date(v:unknown){if(v===null)return true;if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v))return false;const time=Date.parse(v+'T00:00:00Z');return Number.isFinite(time)&&new Date(time).toISOString().startsWith(v+'T');}
function item(value:unknown):value is components['schemas']['MeetingItem'] {
 if(typeof value==='object'&&value!==null&&!Array.isArray(value)&&'destination' in value){
  const {ownerName,materialCount,allowedActions,destination,...core}=value as Record<string,unknown>;
  if(!item(core)||!nullableText(ownerName)||!count(materialCount)||!Array.isArray(allowedActions)||!allowedActions.includes('OPEN')||new Set(allowedActions).size!==allowedActions.length||!allowedActions.every(a=>['OPEN','DELETE','RETRY_TRANSCRIPT','RETRY_MINUTES','RETRY_PUBLISH'].includes(a))||core.relation==='MEMBER'&&allowedActions.some(a=>a!=='OPEN'))return false;
  if(destination===`/recording/${core.id}`)return core.relation==='OWNER'&&core.stage==='ENTRY'&&core.writeState==='CHECK_WORKSPACE';
  return typeof destination==='string'&&new RegExp('^/meeting/'+core.id+'/(processing|transcript|minutes|publish|published)$').test(destination);
 }
 return (shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,relation,revisionToken,stage,writeState')||(shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,participants,relation,revisionToken,stage,updatedAt,writeState')||shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,participants,relation,revisionToken,stage,updatedAt,workspaceStage,writeState')&&typeof value.workspaceStage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(value.workspaceStage))&&Array.isArray(value.participants)&&value.participants.length<=128&&value.participants.every(x=>typeof x==='string')&&(value.updatedAt===null||typeof value.updatedAt==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(value.updatedAt)))&&uuid(value.id)&&nullableText(value.name)&&nullableText(value.category)&&date(value.occurredOn)&&value.lifecycle==='ACTIVE'
  &&typeof value.stage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(value.stage)
  &&typeof value.relation==='string'&&['OWNER','MEMBER'].includes(value.relation)&&typeof value.writeState==='string'&&['MEMBER_READ_ONLY','PUBLISHED_READ_ONLY','CHECK_WORKSPACE'].includes(value.writeState)&&revisionToken(value.revisionToken)&&revisionToken(value.accessRevisionToken);
}
const stages=['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'];
const count=(v:unknown):v is number=>typeof v==='number'&&Number.isSafeInteger(v)&&v>=0;
function record(v:unknown):v is Record<string,unknown>{return typeof v==='object'&&v!==null&&!Array.isArray(v);}
function page(value:unknown,scope:MeetingScope):value is MeetingPage {
 if(!shape(value,'hasMore,items,nextCursor,stageCounts,topicGroups,total')||typeof value.hasMore!=='boolean'||!Array.isArray(value.items)||value.items.length>50||!value.items.every(item)||!count(value.total)||value.total<value.items.length||value.hasMore&&value.items.length===0)return false;
 if(!record(value.stageCounts)||Object.keys(value.stageCounts).sort().join(',')!==[...stages].sort().join(',')||!Object.values(value.stageCounts).every(count)||Object.values(value.stageCounts).reduce<number>((a,b)=>a+(b as number),0)!==value.total)return false;
 if(!record(value.topicGroups)||Object.keys(value.topicGroups).length>256||!Object.entries(value.topicGroups).every(([key,n])=>(key==='__none'||uuid(key))&&count(n)))return false;
 if(scope==='joined'?Object.keys(value.topicGroups).length!==0:Object.values(value.topicGroups).reduce<number>((a,b)=>a+(b as number),0)!==value.total)return false;
 return new Set(value.items.map(row=>row.id)).size===value.items.length&&(value.nextCursor===null||typeof value.nextCursor==='string'&&/^[A-Za-z0-9_-]{1,1536}$/.test(value.nextCursor))&&value.hasMore===(value.nextCursor!==null);
}

export function createMeetingsApi(client:ReturnType<typeof createBusinessClient>) {
 return {async list(scope:MeetingScope,cursor:string|null,signal:AbortSignal,filters:MeetingFilters={}):Promise<MeetingPage>{
  if(!['owned','joined'].includes(scope))throw new TypeError('Invalid list scope');const query=new URLSearchParams({scope,limit:'10'});if(cursor!==null)query.set('cursor',cursor);if(filters.q?.trim())query.set('q',filters.q.trim());if(filters.stages?.length)query.set('stages',[...filters.stages].sort().join(','));if(filters.categories?.length)query.set('categories',[...filters.categories].sort().join(','));if(filters.from)query.set('from',filters.from);if(filters.to)query.set('to',filters.to);if(filters.topic){if(filters.topic!=='__none'&&!uuid(filters.topic))throw new TypeError('Invalid topic filter');query.set('topic',filters.topic);}
  const result=await client.json<unknown>('/api/v1/meetings?'+query,{signal});if(!page(result,scope))throw new ApiError('response',200,'会议列表响应不可用，请重新加载。');
  Object.freeze(result.stageCounts);Object.freeze(result.topicGroups);result.items.forEach(row=>Object.freeze(row));Object.freeze(result.items);return Object.freeze(result);
 }};
}
