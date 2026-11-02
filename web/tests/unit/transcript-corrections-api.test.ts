import {test} from 'node:test';import assert from 'node:assert/strict';
import {createTranscriptCorrectionsApi,validCorrectionCommand} from '../../src/shared/api/transcript-corrections.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',source='00000000-0000-4000-8000-000000000002',candidate='00000000-0000-4000-8000-000000000003',key='00000000-0000-4000-8000-000000000004',token='9007199254740993';
const session:any={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
const observation={meetingRevision:1,revisionToken:token,accessRevision:0,accessRevisionToken:'0',currentSavedRevisionId:source,currentConfirmationId:null};
const item={id:candidate,segmentId:source,source:'原始文字',replacement:'原始文字。',status:'PENDING',operationId:null,canAccept:true,canReject:true,canUndo:false};
const current={meetingId:meeting,sourceRevisionId:source,observation,items:[item],pendingCount:1,canPropose:true};
const proposal={actor:'owner',resource:meeting,key,operation:'TRANSCRIPT_CORRECTIONS_PROPOSE',path:`/api/v1/meetings/${meeting}/transcript/correction-proposals`,body:JSON.stringify({expectedRevisionToken:token,sourceRevisionId:source})};
const decision={...proposal,operation:'TRANSCRIPT_CORRECTION',path:`/api/v1/meetings/${meeting}/transcript/corrections/${candidate}/decisions`,body:JSON.stringify({expectedRevisionToken:token,sourceRevisionId:source,candidateId:candidate,decision:'ACCEPT'})};
const api=()=>createTranscriptCorrectionsApi(createBusinessClient('https://ama.test',()=>session,()=>{}),()=> 'owner');
test('correction original commands accept only server IDs and exact scope/path with no arbitrary model text',()=>{
 assert(validCorrectionCommand(proposal,'owner',meeting));assert(validCorrectionCommand(decision,'owner',meeting));
 for(const bad of [{...decision,actor:'other'},{...decision,path:proposal.path},{...decision,body:decision.body.replace('}',',"text":"forged"}')},{...proposal,body:proposal.body.replace('}',',"candidateId":"'+candidate+'"}')}])assert.equal(validCorrectionCommand(bad,'owner',meeting),false);
});
test('actual correction client validates scope, counts, capabilities and late identity changes',async()=>{
 const original=globalThis.fetch;try{let response:any=current;globalThis.fetch=async()=>Response.json(response);assert.deepEqual(await api().read(meeting,source,new AbortController().signal),current);
 for(const bad of [{...current,meetingId:source},{...current,pendingCount:0},{...current,items:[item,item]},{...current,items:[{...item,status:'ACCEPTED',canAccept:true}]}]){response=bad;await assert.rejects(api().read(meeting,source,new AbortController().signal),ApiError);}
 }finally{globalThis.fetch=original;}
});
test('proposal recovery resends byte-identical body and key and preserves original source receipt',async()=>{
 const original=globalThis.fetch,seen:Request[]=[];try{globalThis.fetch=async(input,init)=>{seen.push(new Request(String(input),init));return Response.json({result:{reviewBatchId:key,sourceRevisionId:source,candidateIds:[candidate],createdCount:1},replayed:true,resultIsCurrent:false,observation});};await api().send(proposal);await api().send(proposal);assert.equal(seen[0]!.headers.get('Idempotency-Key'),key);assert.equal(await seen[0]!.text(),proposal.body);assert.equal(await seen[1]!.text(),proposal.body);}finally{globalThis.fetch=original;}
});
