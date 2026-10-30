import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {revisionToken} from './intent-contract';
export type MeetingPage=components['schemas']['MeetingPage'];
export type MeetingScope='owned'|'joined';
export type MeetingFilters={q?:string;stages?:string[];categories?:string[];from?:string;to?:string};
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}
const uuid=(v:unknown)=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const nullableText=(v:unknown)=>v===null||typeof v==='string';
function date(v:unknown){if(v===null)return true;if(typeof v!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(v))return false;const time=Date.parse(v+'T00:00:00Z');return Number.isFinite(time)&&new Date(time).toISOString().startsWith(v+'T');}
function item(value:unknown):value is components['schemas']['MeetingItem'] {
 return (shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,relation,revisionToken,stage,writeState')||(shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,participants,relation,revisionToken,stage,updatedAt,writeState')||shape(value,'accessRevisionToken,category,id,lifecycle,name,occurredOn,participants,relation,revisionToken,stage,updatedAt,workspaceStage,writeState')&&typeof value.workspaceStage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(value.workspaceStage))&&Array.isArray(value.participants)&&value.participants.length<=128&&value.participants.every(x=>typeof x==='string')&&(value.updatedAt===null||typeof value.updatedAt==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(value.updatedAt)))&&uuid(value.id)&&nullableText(value.name)&&nullableText(value.category)&&date(value.occurredOn)&&value.lifecycle==='ACTIVE'
  &&typeof value.stage==='string'&&['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'].includes(value.stage)
  &&typeof value.relation==='string'&&['OWNER','MEMBER'].includes(value.relation)&&typeof value.writeState==='string'&&['MEMBER_READ_ONLY','PUBLISHED_READ_ONLY','CHECK_WORKSPACE'].includes(value.writeState)&&revisionToken(value.revisionToken)&&revisionToken(value.accessRevisionToken);
}
function page(value:unknown):value is MeetingPage {
 return shape(value,'hasMore,items,nextCursor')&&typeof value.hasMore==='boolean'&&Array.isArray(value.items)&&value.items.length<=50&&value.items.every(item)
  &&new Set(value.items.map(row=>row.id)).size===value.items.length
  &&(value.nextCursor===null||typeof value.nextCursor==='string'&&/^[A-Za-z0-9_-]{1,1536}$/.test(value.nextCursor))&&value.hasMore===(value.nextCursor!==null);
}
export function createMeetingsApi(client:ReturnType<typeof createBusinessClient>) {
 return {async list(scope:MeetingScope,cursor:string|null,signal:AbortSignal,filters:MeetingFilters={}):Promise<MeetingPage>{
  if(!['owned','joined'].includes(scope))throw new TypeError('Invalid list scope');const query=new URLSearchParams({scope,limit:'10'});if(cursor!==null)query.set('cursor',cursor);if(filters.q?.trim())query.set('q',filters.q.trim());if(filters.stages?.length)query.set('stages',[...filters.stages].sort().join(','));if(filters.categories?.length)query.set('categories',[...filters.categories].sort().join(','));if(filters.from)query.set('from',filters.from);if(filters.to)query.set('to',filters.to);
  const result=await client.json<unknown>('/api/v1/meetings?'+query,{signal});if(!page(result))throw new ApiError('response',200,'会议列表响应不可用，请重新加载。');
  result.items.forEach(row=>Object.freeze(row));Object.freeze(result.items);return Object.freeze(result);
 }};
}
