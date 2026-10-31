import {test} from 'node:test';import assert from 'node:assert/strict';import {createTopicsApi} from '../../src/shared/api/topics.js';
const id='00000000-0000-4000-8000-000000000001',other='00000000-0000-4000-8000-000000000002';
test('topic summary is a current bounded private read of the selected topic only',async()=>{
 const calls:any[]=[];let value:any={id,meetingCount:2,revisionToken:'7'};const api=createTopicsApi({json:async(path:string,options:any)=>{calls.push({path,...options});return value;}} as any,()=> 'owner');
 assert.equal((await api.summary(id,new AbortController().signal)).meetingCount,2);assert.equal(calls[0].path,`/api/v1/topics/${id}/summary`);assert.equal(calls[0].expectedStatus,200);assert.equal(calls[0].body,undefined);
 for(const invalid of [{...value,id:other},{...value,meetingCount:-1},{...value,meetingCount:1000000001},{...value,revisionToken:7}]){value=invalid;await assert.rejects(api.summary(id,new AbortController().signal));}
});
test('a late topic count cannot cross an identity or abort boundary',async()=>{
 let actor='owner';const api=createTopicsApi({json:async()=>{actor='other';return{id,meetingCount:0,revisionToken:'0'};}} as any,()=>actor);await assert.rejects(api.summary(id,new AbortController().signal),{name:'AbortError'});
});
