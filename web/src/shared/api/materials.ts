import {ApiError} from './client';
import type {createBusinessClient} from './business-client';
import {createUploadsApi} from './uploads';
import {revisionToken} from './intent-contract';
import type {CommandSnapshot} from '../composables/useCommand';
import {fitsUtf8} from '../bounded-text';
import {UPLOAD_FILE_BYTES} from '../../features/import/upload-bytes';

export type MaterialItem=Readonly<{id:string;materialRevisionId:string;parsedDocumentId:string;fileId:string;name:string;source:'LOCAL'|'KNOWLEDGE';sourceVersion:string|null;sizeBytesToken:string;sha256:string;state:'READY'|'FAILED';error:string|null}>;
export type MaterialListing=Readonly<{items:readonly MaterialItem[];materialSnapshotId:string|null;materialsPending:boolean;meetingRevision:string}>;
export type MaterialLibrary=Readonly<{id:string;name:string}>;
export type KnowledgeDocument=Readonly<{id:string;libraryId:string;name:string;version:string;sha256:string;sizeBytesToken:string}>;
export type MaterialPage<T>=Readonly<{items:readonly T[];cursor:string|null}>;
export const materialUuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const exact=(v:Record<string,unknown>,keys:string)=>Object.keys(v).sort().join(',')===keys;
const text=(v:unknown,max=4096):v is string=>typeof v==='string'&&!!v.trim()&&fitsUtf8(v,max);
const hash=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{64}$/.test(v);
const providerId=(v:unknown):v is string=>typeof v==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(v);
export const materialFormat=(name:string)=>/\.(pdf|pptx|docx)$/i.test(name);
export const knowledgeKey=(d:KnowledgeDocument)=>JSON.stringify([d.libraryId,d.id,d.version]);
export function validKnowledgeDocument(v:unknown):v is KnowledgeDocument{return object(v)&&exact(v,'id,libraryId,name,sha256,sizeBytesToken,version')&&providerId(v.id)&&providerId(v.libraryId)&&text(v.name)&&hash(v.version)&&v.sha256===v.version&&revisionToken(v.sizeBytesToken);}
function item(v:unknown):v is MaterialItem{return object(v)&&exact(v,'error,fileId,id,materialRevisionId,name,parsedDocumentId,sha256,sizeBytesToken,source,sourceVersion,state')&&materialUuid(v.id)&&materialUuid(v.materialRevisionId)&&materialUuid(v.parsedDocumentId)&&materialUuid(v.fileId)&&text(v.name)&&['LOCAL','KNOWLEDGE'].includes(String(v.source))&&(v.source==='LOCAL'?v.sourceVersion===null:hash(v.sourceVersion))&&revisionToken(v.sizeBytesToken)&&hash(v.sha256)&&(v.state==='READY'?v.error===null:v.state==='FAILED'&&text(v.error));}
const invalid=()=>new ApiError('response',200,'材料响应暂无法确认，请保留原操作并重新查询。');
function freeze<T>(v:T):T{if(typeof v==='object'&&v!==null){Object.values(v).forEach(freeze);Object.freeze(v);}return v;}
function snapshot(v:unknown,actor:string,meeting:string):v is CommandSnapshot{return object(v)&&exact(v,'actor,body,key,operation,path,resource')&&v.actor===actor&&v.resource===meeting&&materialUuid(v.key)&&typeof v.operation==='string'&&typeof v.path==='string'&&typeof v.body==='string'&&fitsUtf8(v.body,2*1024*1024);}
export function validMaterialBegin(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!snapshot(v,actor,meeting)||v.operation!=='UPLOAD_BEGIN'||v.path!==`/api/v1/meetings/${meeting}/uploads`)return false;
 try{const b:unknown=JSON.parse(v.body);return object(b)&&exact(b,'name,purpose,sha256,size')&&b.purpose==='MATERIAL_SOURCE'&&text(b.name,1024*1024)&&materialFormat(b.name)&&typeof b.size==='number'&&Number.isSafeInteger(b.size)&&b.size>=0&&b.size<=UPLOAD_FILE_BYTES&&hash(b.sha256);}catch{return false;}
}
export function validMaterialCommand(v:unknown,actor:string,meeting:string):v is CommandSnapshot{
 if(!snapshot(v,actor,meeting)||!materialUuid(meeting))return false;const base=`/api/v1/meetings/${meeting}/materials`;
 if(v.operation==='MATERIAL_RETRY'||v.operation==='MATERIAL_REMOVE'){const suffix=v.path.slice(base.length).split('/');return v.path.startsWith(base+'/')&&materialUuid(suffix[1])&&v.body===''&&(v.operation==='MATERIAL_RETRY'?suffix.length===3&&suffix[2]==='retry':suffix.length===2);}
 if(v.operation!=='MATERIAL_ADD'||v.path!==base)return false;
 try{const b:unknown=JSON.parse(v.body);return object(b)&&(exact(b,'fileId')?materialUuid(b.fileId):exact(b,'documentId,libraryId,version')&&providerId(b.libraryId)&&providerId(b.documentId)&&hash(b.version));}catch{return false;}
}

/** Bounded real product routes. Command bytes are journalled by the caller and forwarded unchanged. */
export function createMaterialsApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 const uploads=createUploadsApi(client,actor);
 function active(expected:string){if(!expected||expected!==actor())throw new DOMException('Material actor changed','AbortError');}
 function meeting(id:string){if(!materialUuid(id))throw new TypeError('Invalid material meeting');}
 async function page<T>(id:string,library:string|null,search:string,cursor:string|null,signal:AbortSignal,validate:(v:unknown)=>v is T):Promise<MaterialPage<T>>{
  meeting(id);if(library!==null&&!providerId(library)||typeof search!=='string'||!fitsUtf8(search,1024)||cursor!==null&&(!text(cursor,8192)))throw new TypeError('Invalid material query');
  const query=new URLSearchParams({meetingId:id,limit:'20'});if(search)query.set('search',search);if(cursor!==null)query.set('cursor',cursor);
  const value=await client.json<unknown>('/api/v1/material-libraries'+(library===null?'':'/'+encodeURIComponent(library)+'/documents')+'?'+query,{signal,expectedStatus:200,maxResponseBytes:262144});
  if(!object(value)||!exact(value,'cursor,items')||!Array.isArray(value.items)||value.items.length>20||!value.items.every(validate)||new Set(value.items.map(v=>(v as {id:string}).id)).size!==value.items.length||!(value.cursor===null||text(value.cursor,8192)&&value.cursor!==cursor&&value.items.length>0))throw invalid();
  return freeze(value as unknown as MaterialPage<T>);
 }
 return {
  async list(id:string,signal:AbortSignal):Promise<MaterialListing>{meeting(id);const value=await client.json<unknown>(`/api/v1/meetings/${id}/materials`,{signal,expectedStatus:200,maxResponseBytes:1024*1024});
   if(!object(value)||!exact(value,'items,materialSnapshotId,materialsPending,meetingRevision')||!Array.isArray(value.items)||value.items.length>64||!value.items.every(item)||new Set(value.items.map(x=>x.id)).size!==value.items.length||!(value.materialSnapshotId===null||materialUuid(value.materialSnapshotId))||typeof value.materialsPending!=='boolean'||value.materialsPending!==value.items.some(x=>x.state!=='READY')||!revisionToken(value.meetingRevision)||!value.materialsPending&&value.items.length>0&&value.materialSnapshotId===null)throw invalid();
   return freeze(value as unknown as MaterialListing);
  },
  async send(command:CommandSnapshot):Promise<MaterialItem>{active(command.actor);if(!validMaterialCommand(command,command.actor,command.resource)||command.operation==='MATERIAL_REMOVE')throw new TypeError('Invalid material command');
   const value=await client.json<unknown>(command.path,{method:'POST',headers:{'Idempotency-Key':command.key},...(command.body?{body:command.body}:{}),expectedStatus:command.operation==='MATERIAL_ADD'?201:200,maxResponseBytes:16384});if(!item(value))throw invalid();
   if(command.operation==='MATERIAL_RETRY'&&!command.path.endsWith('/'+value.id+'/retry'))throw invalid();
   if(command.operation==='MATERIAL_ADD'){const b=JSON.parse(command.body);if(b.fileId!==undefined?b.fileId!==value.fileId||value.source!=='LOCAL':value.source!=='KNOWLEDGE'||value.sourceVersion!==b.version)throw invalid();}return freeze(value);
  },
  async remove(command:CommandSnapshot):Promise<void>{active(command.actor);if(!validMaterialCommand(command,command.actor,command.resource)||command.operation!=='MATERIAL_REMOVE')throw new TypeError('Invalid material removal');await client.json<void>(command.path,{method:'DELETE',headers:{'Idempotency-Key':command.key},expectedStatus:204});},
  libraries:(id:string,search:string,cursor:string|null,signal:AbortSignal)=>page(id,null,search,cursor,signal,(v):v is MaterialLibrary=>object(v)&&exact(v,'id,name')&&providerId(v.id)&&text(v.name)),
  documents:(id:string,library:string,search:string,cursor:string|null,signal:AbortSignal)=>page(id,library,search,cursor,signal,(v):v is KnowledgeDocument=>validKnowledgeDocument(v)&&v.libraryId===library),
  async begin(command:CommandSnapshot){active(command.actor);meeting(command.resource);if(!validMaterialBegin(command,command.actor,command.resource))throw new TypeError('Invalid material upload');const value=await client.json<unknown>(command.path,{method:'POST',body:command.body,headers:{'Idempotency-Key':command.key},expectedStatus:201,maxResponseBytes:16384});if(!object(value)||!exact(value,'partSize,partSizeToken,uploadId')||!materialUuid(value.uploadId)||typeof value.partSize!=='number'||!Number.isInteger(value.partSize)||value.partSize<=0||!revisionToken(value.partSizeToken)||value.partSizeToken==='0')throw invalid();return freeze(value as {uploadId:string;partSize:number;partSizeToken:string});},
  receipt:uploads.receipt,parts:uploads.parts,part:uploads.part,complete:uploads.complete,abort:uploads.abort,
  async read(source:MaterialItem,signal:AbortSignal){if(!item(source)||BigInt(source.sizeBytesToken)>BigInt(UPLOAD_FILE_BYTES))throw new Error('材料超过当前浏览器预览限额。');const result=await client.bytes(`/api/v1/files/${source.fileId}/content`,{signal,size:Number(source.sizeBytesToken)});const sha=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',result.bytes)),b=>b.toString(16).padStart(2,'0')).join('');if(signal.aborted)throw new DOMException('Material preview cancelled','AbortError');if(sha!==source.sha256)throw invalid();return result;}
 };
}
