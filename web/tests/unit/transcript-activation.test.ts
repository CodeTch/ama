import {test} from 'node:test';
import assert from 'node:assert/strict';
import {ref,nextTick} from 'vue';
import {useTranscriptActivation} from '../../src/features/transcript/useTranscriptActivation';
test('processing completion and step activation refresh without navigation feedback loops',async()=>{
 const active=ref(false),result=ref(''),blocked=ref(false);let reads=0;
 const stop=useTranscriptActivation(()=>active.value,()=>result.value,()=>blocked.value,()=>{reads++;});
 active.value=true;await nextTick();assert.equal(reads,1);
 result.value='task-1';await nextTick();assert.equal(reads,2);
 result.value='task-1';await nextTick();assert.equal(reads,2);
 blocked.value=true;result.value='task-2';await nextTick();assert.equal(reads,2);
 blocked.value=false;await nextTick();assert.equal(reads,3);
 active.value=false;result.value='task-3';await nextTick();assert.equal(reads,3);
 active.value=true;await nextTick();assert.equal(reads,4);stop();
});
