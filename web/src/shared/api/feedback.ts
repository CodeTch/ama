import {ApiError} from './client';
import type {createBusinessClient} from './business-client';
import type {CommandSnapshot} from '../composables/useCommand';
const uuid=(v:unknown):v is string=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
export type Feedback=Readonly<{id:string;meetingId:string;category:string;comment:string;state:'PENDING'|'ACCEPTED'|'REJECTED'|'NEEDS_MORE';revisionToken:string}>;
const item=(v:unknown):v is Feedback=>typeof v==='object'&&v!==null&&!Array.isArray(v)&&Object.keys(v as object).sort().join(',')==='category,comment,id,meetingId,revisionToken,state'&&uuid((v as any).id)&&uuid((v as any).meetingId)&&typeof (v as any).category==='string'&&typeof (v as any).comment==='string'&&typeof (v as any).revisionToken==='string'&&['PENDING','ACCEPTED','REJECTED','NEEDS_MORE'].includes((v as any).state);
export const FEEDBACK_WORKSPACE='a1ff6d12-4ec7-4b48-914a-17d196d3b1cb';
export type FeedbackHistory=Readonly<{id:string;action:'SUPPLEMENT'|'REVIEW'|'UNDO_REVIEW';actorId:string;revisionToken:string;state:Feedback['state'];text:string;createdAt:string}>;
export type FeedbackDetail=Readonly<{feedback:Feedback;submittedAt:string;reviewReason:string|null;reviewedBy:string|null;history:readonly FeedbackHistory[]}>;
export type FeedbackReceipt=Readonly<{feedbackId:string;historyId:string;revisionToken:string;state:Feedback['state'];operation:FeedbackHistory['action']}>;
export type FeedbackSummary=Feedback&Readonly<{submittedAt:string;reviewReason:string|null}>;
export type FeedbackOverview=Readonly<{items:readonly FeedbackSummary[];nextCursor:string|null;counts:Readonly<Record<Feedback['state']|'ALL',number>>}>;
const record=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
const fields=(v:Record<string,unknown>,names:string)=>Object.keys(v).sort().join(',')===names;
const token=(v:unknown):v is string=>typeof v==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(v)&&BigInt(v)<=9223372036854775807n;
const bounded=(v:unknown,n:number):v is string=>typeof v==='string'&&v.trim().length>0&&v.length<=n&&!v.includes('\0');
const state=(v:unknown):v is Feedback['state']=>typeof v==='string'&&['PENDING','ACCEPTED','REJECTED','NEEDS_MORE'].includes(v);
const timestamp=(v:unknown):v is string=>typeof v==='string'&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{6}Z$/.test(v);
const suffixes:Record<string,string>={FEEDBACK_SUPPLEMENT:'/supplements',FEEDBACK_REVIEW:'/review',FEEDBACK_UNDO_REVIEW:'/review/undo'};
export function validFeedbackAction(value:unknown,actor:string,workspace:string):value is CommandSnapshot{
 if(workspace!==FEEDBACK_WORKSPACE||!record(value)||!fields(value,'actor,body,key,operation,path,resource')||value.actor!==actor||!uuid(value.resource)||!uuid(value.key)||typeof value.operation!=='string'||!Object.hasOwn(suffixes,value.operation)||value.path!==`/api/v1/feedback/${value.resource}${suffixes[value.operation]}`||typeof value.body!=='string'||value.body.length>32768)return false;
 try{const b=JSON.parse(value.body);if(!record(b)||!token(b.expectedRevisionToken))return false;
  if(value.operation==='FEEDBACK_SUPPLEMENT')return fields(b,'expectedRevisionToken,text')&&bounded(b.text,4096);
  if(value.operation==='FEEDBACK_REVIEW')return fields(b,'decision,expectedRevisionToken,reason')&&['ACCEPTED','REJECTED','NEEDS_MORE'].includes(String(b.decision))&&bounded(b.reason,2048);
  return fields(b,'expectedRevisionToken');
 }catch{return false;}
}
function historyItem(v:unknown):v is FeedbackHistory{return record(v)&&fields(v,'action,actorId,createdAt,id,revisionToken,state,text')&&uuid(v.id)&&['SUPPLEMENT','REVIEW','UNDO_REVIEW'].includes(String(v.action))&&bounded(v.actorId,255)&&token(v.revisionToken)&&state(v.state)&&typeof v.text==='string'&&v.text.length<=4096&&timestamp(v.createdAt);}
function detailItem(v:unknown):v is FeedbackDetail{return record(v)&&fields(v,'feedback,history,reviewReason,reviewedBy,submittedAt')&&item(v.feedback)&&token(v.feedback.revisionToken)&&timestamp(v.submittedAt)&&(v.reviewReason===null||bounded(v.reviewReason,2048))&&(v.reviewedBy===null||bounded(v.reviewedBy,255))&&Array.isArray(v.history)&&v.history.length<=50&&v.history.every(historyItem);}
export function createFeedbackApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 function owner(){const who=actor();if(!who)throw new ApiError('http',401,'请先登录读取反馈。');return who;}
 function current(who:string,signal?:AbortSignal){if(actor()!==who||signal?.aborted)throw new DOMException('Feedback scope changed','AbortError');}
 async function page(path:string,cursor:string|null,signal:AbortSignal){const who=owner();if(cursor!==null&&!uuid(cursor))throw new TypeError('Invalid feedback cursor');const q=new URLSearchParams({limit:'20'});if(cursor)q.set('cursor',cursor);const v=await client.json<unknown>(path+'?'+q,{signal,expectedStatus:200,maxResponseBytes:1048576});current(who,signal);if(!record(v)||!fields(v,'items,nextCursor')||!Array.isArray(v.items)||v.items.length>20||!v.items.every(item)||!(v.nextCursor===null||uuid(v.nextCursor)))throw new ApiError('response',200,'反馈列表响应暂无法确认。');return Object.freeze({items:Object.freeze(v.items.map(row=>Object.freeze(row))),nextCursor:v.nextCursor});}
 return {
 async create(value:{meetingId:string;category:string;comment:string},key:string=crypto.randomUUID()){const who=owner();if(!uuid(key)||!uuid(value.meetingId)||!value.category||!value.comment.trim()||value.comment.length>4096)throw new TypeError('Invalid feedback');const v=await client.json<unknown>('/api/v1/feedback',{method:'POST',body:JSON.stringify(value),headers:{'Idempotency-Key':key},expectedStatus:201});current(who);if(!item(v))throw new ApiError('response',201,'反馈结果暂无法确认。');return Object.freeze(v);},
 list:(cursor:string|null,signal:AbortSignal)=>page('/api/v1/feedback',cursor,signal),
 reviewQueue:(cursor:string|null,signal:AbortSignal)=>page('/api/v1/feedback/review-queue',cursor,signal),
 async summaries(cursor:string|null,selectedState:Feedback['state']|null,reviewing:boolean,signal:AbortSignal):Promise<FeedbackOverview>{
  const who=owner();if(cursor!==null&&!uuid(cursor)||selectedState!==null&&!state(selectedState)||typeof reviewing!=='boolean')throw new TypeError('Invalid feedback summary filter');const q=new URLSearchParams({limit:'20',reviewing:String(reviewing)});if(cursor)q.set('cursor',cursor);if(selectedState)q.set('state',selectedState);
  const v=await client.json<unknown>('/api/v1/feedback/summaries?'+q,{signal,expectedStatus:200,maxResponseBytes:1048576});current(who,signal);
  if(!record(v)||!fields(v,'counts,items,nextCursor')||!record(v.counts)||!fields(v.counts,'ACCEPTED,ALL,NEEDS_MORE,PENDING,REJECTED')||!Object.values(v.counts).every(n=>typeof n==='number'&&Number.isSafeInteger(n)&&n>=0&&n<=1000000000)||!(v.nextCursor===null||uuid(v.nextCursor))||!Array.isArray(v.items)||v.items.length>20||!v.items.every(row=>{if(!record(row)||!fields(row,'category,comment,id,meetingId,reviewReason,revisionToken,state,submittedAt'))return false;const {submittedAt,reviewReason,...base}=row;return item(base)&&timestamp(submittedAt)&&(reviewReason===null||bounded(reviewReason,2048));}))throw new ApiError('response',200,'反馈摘要响应暂无法确认。');
  if(v.counts.ALL!==Number(v.counts.PENDING)+Number(v.counts.ACCEPTED)+Number(v.counts.REJECTED)+Number(v.counts.NEEDS_MORE))throw new ApiError('response',200,'反馈计数尚未确认。');return Object.freeze({items:Object.freeze(v.items.map(row=>Object.freeze(row as FeedbackSummary))),nextCursor:v.nextCursor,counts:Object.freeze(v.counts as Record<Feedback['state']|'ALL',number>)});
 },
 async detail(id:string,signal:AbortSignal){const who=owner();if(!uuid(id))throw new TypeError('Invalid feedback ID');const v=await client.json<unknown>('/api/v1/feedback/'+id,{signal,expectedStatus:200,maxResponseBytes:1048576});current(who,signal);if(!detailItem(v)||v.feedback.id!==id)throw new ApiError('response',200,'反馈详情响应暂无法确认。');return Object.freeze({...v,feedback:Object.freeze(v.feedback),history:Object.freeze(v.history.map(row=>Object.freeze(row)))});},
 async send(command:CommandSnapshot){const who=owner();if(!validFeedbackAction(command,who,FEEDBACK_WORKSPACE))throw new TypeError('Invalid original feedback action');const v=await client.json<unknown>(command.path,{method:'POST',body:command.body,headers:{'Idempotency-Key':command.key},expectedStatus:200,maxResponseBytes:16384});current(who);if(!record(v)||!fields(v,'feedbackId,historyId,operation,revisionToken,state')||v.feedbackId!==command.resource||!uuid(v.historyId)||!token(v.revisionToken)||v.revisionToken==='0'||BigInt(v.revisionToken)!==BigInt(JSON.parse(command.body).expectedRevisionToken)+1n||!state(v.state)||v.operation!==command.operation.slice('FEEDBACK_'.length))throw new ApiError('response',200,'反馈处理结果尚未确认。');return Object.freeze(v as FeedbackReceipt);}
 };
}
