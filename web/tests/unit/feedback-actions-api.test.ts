import {test} from 'node:test';import assert from 'node:assert/strict';
import {createFeedbackApi,FEEDBACK_WORKSPACE,validFeedbackAction} from '../../src/shared/api/feedback.js';
const id='00000000-0000-4000-8000-000000000001',meeting='00000000-0000-4000-8000-000000000002',history='00000000-0000-4000-8000-000000000003',key='00000000-0000-4000-8000-000000000004';
const feedback={id,meetingId:meeting,category:'WORDING',comment:'原建议',state:'PENDING',revisionToken:'0'},detail={feedback,submittedAt:'2026-10-31T00:00:00.000000Z',reviewReason:null,reviewedBy:null,history:[]};
test('feedback original action preserves exact route body key and validates its historical receipt',async()=>{
 const calls:any[]=[];const api=createFeedbackApi({json:async(path:string,options:any)=>{calls.push({path,...options});return {feedbackId:id,historyId:history,revisionToken:'1',state:'PENDING',operation:'SUPPLEMENT'};}} as any,()=> 'owner');
 const c={actor:'owner',resource:id,operation:'FEEDBACK_SUPPLEMENT',path:`/api/v1/feedback/${id}/supplements`,key,body:JSON.stringify({expectedRevisionToken:'0',text:'补充😀'})};
 assert.equal(validFeedbackAction(c,'owner',FEEDBACK_WORKSPACE),true);await api.send(c);assert.equal(calls[0].body,c.body);assert.equal(calls[0].headers['Idempotency-Key'],key);assert.equal(calls[0].method,'POST');
 await assert.rejects(api.send({...c,path:`/api/v1/feedback/${meeting}/supplements`}));assert.equal(calls.length,1);await assert.rejects(api.send({...c,body:JSON.stringify({expectedRevisionToken:'4',text:'补充😀'})}));
});
test('detail enforces complete bounded private history and current identity after a read',async()=>{
 let actor:string|null='owner',response:any=detail;const api=createFeedbackApi({json:async()=>response} as any,()=>actor);
 assert.equal((await api.detail(id,new AbortController().signal)).feedback.comment,'原建议');response={...detail,history:Array(51).fill({})};await assert.rejects(api.detail(id,new AbortController().signal));
 const late=createFeedbackApi({json:async()=>{actor='other';return detail;}} as any,()=>actor);actor='owner';await assert.rejects(late.detail(id,new AbortController().signal),{name:'AbortError'});
});
test('review commands require exact fields revision and bounded reasons before transport',()=>{
 const c={actor:'owner',resource:id,operation:'FEEDBACK_REVIEW',path:`/api/v1/feedback/${id}/review`,key,body:JSON.stringify({expectedRevisionToken:'9007199254740993',decision:'ACCEPTED',reason:'本地审核'})};
 assert.equal(validFeedbackAction(c,'owner',FEEDBACK_WORKSPACE),true);
 for(const body of [{expectedRevisionToken:'0',decision:'UNKNOWN',reason:'说明'},{expectedRevisionToken:'0',decision:'ACCEPTED',reason:'x'.repeat(2049)},{expectedRevisionToken:0,decision:'ACCEPTED',reason:'说明'}])assert.equal(validFeedbackAction({...c,body:JSON.stringify(body)},'owner',FEEDBACK_WORKSPACE),false);
});
test('summary reads use server state filters and complete private counts with bounded metadata',async()=>{
 const calls:any[]=[];let value:any={items:[{...feedback,submittedAt:detail.submittedAt,reviewReason:null}],nextCursor:null,counts:{ALL:1,PENDING:1,ACCEPTED:0,REJECTED:0,NEEDS_MORE:0}};
 const api=createFeedbackApi({json:async(path:string,options:any)=>{calls.push({path,options});return value;}} as any,()=> 'owner');
 const page=await api.summaries(null,'PENDING',false,new AbortController().signal);assert.equal(page.counts.ALL,1);assert.match(calls[0].path,/state=PENDING/);assert.match(calls[0].path,/reviewing=false/);
 value={...value,counts:{...value.counts,ALL:-1}};await assert.rejects(api.summaries(null,null,false,new AbortController().signal));
});
