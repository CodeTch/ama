import type {components} from './generated';
import type {createBusinessClient} from './business-client';
import {ApiError} from './client';
import {fitsUtf8} from '../bounded-text';
export type Person=components['schemas']['Person'];
export type Category=components['schemas']['CategoryItem'];
const shape=(v:unknown,keys:string):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===keys;
const text=(v:unknown):v is string=>typeof v==='string'&&fitsUtf8(v,16384);
const identity=(v:unknown):v is string=>typeof v==='string'&&v.trim().length>0&&fitsUtf8(v,1020)&&[...v].length<=255;
const cursor=(v:unknown):v is string|null=>v===null||typeof v==='string'&&v.length>0&&fitsUtf8(v,4096);
const person=(v:unknown):v is Person=>shape(v,'active,department,displayName,stableId')&&identity(v.stableId)&&text(v.displayName)&&(v.department===null||text(v.department))&&typeof v.active==='boolean';
const category=(v:unknown):v is Category=>shape(v,'id,label')&&identity(v.id)&&text(v.label);
function freeze<T>(value:T):T{if(typeof value==='object'&&value!==null){Object.values(value).forEach(freeze);Object.freeze(value);}return value;}
export function createMeetingOptionsApi(client:ReturnType<typeof createBusinessClient>){return {
 async people(query:string,next:string|null,signal:AbortSignal):Promise<components['schemas']['PeoplePage']>{
  if(!text(query)||!cursor(next))throw new TypeError('Invalid directory query');const params=new URLSearchParams({query,limit:'20'});if(next!==null)params.set('cursor',next);
  const value=await client.json<unknown>('/api/v1/directory/people?'+params,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
  if(!shape(value,'items,nextCursor')||!Array.isArray(value.items)||value.items.length>50||!value.items.every(person)||new Set(value.items.map(x=>x.stableId)).size!==value.items.length||!cursor(value.nextCursor))throw new ApiError('response',200,'人员目录响应不可用。');
  return freeze({items:value.items,nextCursor:value.nextCursor});
 },
 async categories(next:string|null,signal:AbortSignal):Promise<components['schemas']['CategoryPage']>{
  if(!cursor(next))throw new TypeError('Invalid category cursor');const params=new URLSearchParams({limit:'20'});if(next!==null)params.set('cursor',next);
  const value=await client.json<unknown>('/api/v1/meetings/categories?'+params,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
  if(!shape(value,'hasMore,items,nextCursor')||!Array.isArray(value.items)||value.items.length>50||!value.items.every(category)||new Set(value.items.map(x=>x.id)).size!==value.items.length||!cursor(value.nextCursor)||value.hasMore!==(value.nextCursor!==null))throw new ApiError('response',200,'会议类别响应不可用。');
  return freeze({items:value.items,nextCursor:value.nextCursor,hasMore:value.nextCursor!==null});
 }
};}
