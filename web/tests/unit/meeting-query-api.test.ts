import {test} from 'node:test';import assert from 'node:assert/strict';import {createMeetingsApi} from '../../src/shared/api/meetings.js';
const stages=['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'];
const counts=(n:number)=>Object.fromEntries(stages.map(s=>[s,s==='ENTRY'?n:0]));
const id='00000000-0000-4000-8000-000000000001';
const row={id,name:'A%_=',category:null,occurredOn:null,stage:'ENTRY',lifecycle:'ACTIVE',relation:'OWNER',writeState:'CHECK_WORKSPACE',revisionToken:'0',accessRevisionToken:'0'};
const good=()=>({items:[row],nextCursor:'signed-cursor',hasMore:true,total:12,stageCounts:counts(12),topicGroups:{__none:12}});
test('meeting list accepts same-filter server totals and preserves literal query text',async()=>{
 let url='';const api=createMeetingsApi({json:async(path:string)=>{url=path;return good();}} as any);const result=await api.list('owned',null,new AbortController().signal,{q:'A%_='});assert.equal(result.total,12);assert.equal(result.stageCounts.ENTRY,12);assert.equal(new URL(url,'https://local.invalid').searchParams.get('q'),'A%_=');
});
test('meeting aggregates reject inconsistent totals and leaked or malformed groups',async()=>{
 for(const invalid of [{...good(),total:-1},{...good(),stageCounts:counts(11)},{...good(),topicGroups:{__none:13}},{...good(),topicGroups:{'foreign-text-metadata':12}},{...good(),items:[]}]){
  const api=createMeetingsApi({json:async()=>invalid} as any);await assert.rejects(()=>api.list('owned',null,new AbortController().signal));
 }
});

test('meeting routes are exact local meeting paths and member actions cannot imply owner commands',async()=>{
 const details={...row,ownerName:'负责人',materialCount:3,allowedActions:['OPEN','DELETE'],destination:`/meeting/${id}/processing`};
 for(const bad of [null,'https://foreign.invalid/meeting/'+id,'/meeting/00000000-0000-4000-8000-000000000002/processing']){const api=createMeetingsApi({json:async()=>({...good(),items:[{...details,destination:bad}]})} as any);await assert.rejects(()=>api.list('owned',null,new AbortController().signal));}
 const api=createMeetingsApi({json:async()=>({...good(),items:[details]})} as any);assert.equal((await api.list('owned',null,new AbortController().signal)).items[0]?.materialCount,3);
 const member=createMeetingsApi({json:async()=>({...good(),topicGroups:{},items:[{...details,relation:'MEMBER',writeState:'MEMBER_READ_ONLY'}]})} as any);await assert.rejects(()=>member.list('joined',null,new AbortController().signal));
});

 test('owner recording destination restores the same meeting and cannot grant a member recording access',async()=>{
 const details={...row,ownerName:'负责人',materialCount:0,allowedActions:['OPEN'],destination:`/recording/${id}`};
 const api=createMeetingsApi({json:async()=>({...good(),items:[details]})} as any);
 assert.equal((await api.list('owned',null,new AbortController().signal)).items[0]!.destination,`/recording/${id}`);
 for(const bad of [{...details,relation:'MEMBER',writeState:'MEMBER_READ_ONLY'},{...details,destination:'/recording/00000000-0000-4000-8000-000000000002'}]){
  const invalid=createMeetingsApi({json:async()=>({...good(),items:[bad]})} as any);
  await assert.rejects(()=>invalid.list('owned',null,new AbortController().signal));
 }
});
