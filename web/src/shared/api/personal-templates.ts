import {ApiError} from './client';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
import {fitsUtf8} from '../bounded-text';

export const PERSONAL_TEMPLATE_WORKSPACE='6621f7bd-88eb-4212-90a5-5c87501d87a5';
export type PersonalTemplate=Readonly<{id:string;revisionId:string;revisionToken:string;name:string;themes:readonly string[];deleted:boolean}>;
export type PersonalTemplatePage=Readonly<{items:readonly PersonalTemplate[];nextCursor:string|null}>;
const root='/api/v1/templates';
const uuid=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value);
const shape=(value:unknown,keys:string):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;
const revision=(value:unknown):value is string=>typeof value==='string'&&/^[1-9][0-9]{0,18}$/.test(value)&&BigInt(value)<9223372036854775807n;
const text=(value:unknown):value is string=>typeof value==='string'&&!!value.trim()&&fitsUtf8(value,8192);
function layout(name:unknown,themes:unknown):themes is readonly string[]{
 return text(name)&&Array.isArray(themes)&&themes.length>=1&&themes.length<=8&&themes.every(text)&&fitsUtf8(JSON.stringify({schemaVersion:'ama.personal-minutes-template.v1',themes}),65536);
}
function template(value:unknown):value is PersonalTemplate{
 return shape(value,'deleted,id,name,revisionId,revisionToken,themes')&&uuid(value.id)&&uuid(value.revisionId)&&revision(value.revisionToken)&&layout(value.name,value.themes)&&typeof value.deleted==='boolean';
}
function frozen(value:PersonalTemplate):PersonalTemplate{return Object.freeze({...value,themes:Object.freeze([...value.themes])});}
export function validPersonalTemplateCommand(value:unknown,actor:string,scope:string):value is CommandSnapshot{
 if(scope!==PERSONAL_TEMPLATE_WORKSPACE||!shape(value,'actor,body,key,operation,path,resource')||value.actor!==actor||!uuid(value.resource)||!uuid(value.key)||typeof value.body!=='string'||!fitsUtf8(value.body,131072))return false;
 try{
  const body:unknown=JSON.parse(value.body);if(JSON.stringify(body)!==value.body)return false;
  if(value.operation==='PERSONAL_TEMPLATE_CREATE')return value.resource===PERSONAL_TEMPLATE_WORKSPACE&&value.path===root&&shape(body,'name,themes')&&layout(body.name,body.themes);
  if(value.path!==`${root}/${value.resource}`)return false;
  if(value.operation==='PERSONAL_TEMPLATE_DELETE')return shape(body,'expectedRevisionToken')&&revision(body.expectedRevisionToken);
  return value.operation==='PERSONAL_TEMPLATE_UPDATE'&&shape(body,'expectedRevisionToken,name,themes')&&revision(body.expectedRevisionToken)&&layout(body.name,body.themes);
 }catch{return false;}
}

export function createPersonalTemplatesApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 function current(){const who=actor();if(!who)throw new ApiError('http',401,'登录状态不可用，请重新登录。');return who;}
 function active(who:string,signal?:AbortSignal){if(who!==actor()||signal?.aborted)throw new DOMException('Template scope changed','AbortError');}
 const invalid=()=>new ApiError('response',200,'模板响应暂无法核实，请保留原操作并重新读取。');
 return {
  async list(cursor:string|null,signal:AbortSignal):Promise<PersonalTemplatePage>{
   const who=current();if(cursor!==null&&!uuid(cursor))throw new TypeError('Invalid template cursor');active(who,signal);
   const query=new URLSearchParams({limit:'20'});if(cursor)query.set('cursor',cursor);
   const value=await client.json<unknown>(`${root}?${query}`,{signal,expectedStatus:200,maxResponseBytes:4*1024*1024});active(who,signal);
   if(!shape(value,'items,nextCursor')||!Array.isArray(value.items)||value.items.length>20||!value.items.every(template)||(value.nextCursor!==null&&!uuid(value.nextCursor)))throw invalid();
   let previous=cursor;for(const item of value.items){if(item.deleted||previous!==null&&item.id<=previous)throw invalid();previous=item.id;}
   if(value.nextCursor!==null&&(value.items.length===0||value.nextCursor!==value.items.at(-1)!.id))throw invalid();
   return Object.freeze({items:Object.freeze(value.items.map(frozen)),nextCursor:value.nextCursor});
  },
  async read(id:string,signal:AbortSignal):Promise<PersonalTemplate>{
   const who=current();if(!uuid(id))throw new TypeError('Invalid template');active(who,signal);
   const value=await client.json<unknown>(`${root}/${id}`,{signal,expectedStatus:200,maxResponseBytes:131072});active(who,signal);
   if(!template(value)||value.id!==id||value.deleted)throw invalid();return frozen(value);
  },
  async send(command:CommandSnapshot):Promise<PersonalTemplate>{
   const who=current();if(!validPersonalTemplateCommand(command,who,PERSONAL_TEMPLATE_WORKSPACE))throw new TypeError('Invalid original template command');
   const create=command.operation==='PERSONAL_TEMPLATE_CREATE',remove=command.operation==='PERSONAL_TEMPLATE_DELETE',status=create?201:200;
   const value=await client.json<unknown>(command.path,{method:create?'POST':remove?'DELETE':'PUT',body:command.body,headers:{'Idempotency-Key':command.key},expectedStatus:status,maxResponseBytes:131072});active(who);
   const body=JSON.parse(command.body) as {name?:string;themes?:string[];expectedRevisionToken?:string};
   if(!template(value)||value.deleted!==remove||!create&&value.id!==command.resource||value.revisionToken!==(create?'1':String(BigInt(body.expectedRevisionToken!)+1n))||!remove&&(value.name!==body.name||JSON.stringify(value.themes)!==JSON.stringify(body.themes)))throw invalid();
   return frozen(value);
  },
 };
}
