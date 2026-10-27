import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useDraft} from '../../src/shared/composables/useDraft.js';
test('save ACK clears only sent generation and late observations cannot replace dirty text',()=>{
 const d=useDraft({title:'saved',body:'base'},'9007199254740993',()=>true);d.edit({title:'HUMAN',body:'first'});const first=d.capture();d.edit({title:'HUMAN',body:'later'});d.accept(first,{title:'HUMAN',body:'first'},'9007199254740994');assert.equal(d.dirty.value,true);assert.equal(d.value.value.body,'later');assert.equal(d.baseToken.value,'9007199254740994');d.observe({title:'AI',body:'candidate'},'9007199254740995');assert.equal(d.value.value.title,'HUMAN');assert.equal(d.conflict.value?.value.body,'candidate');assert.equal(d.baseToken.value,'9007199254740994');
 const second=d.capture();d.accept(second,{title:'HUMAN',body:'later'},'9007199254740996');assert.equal(d.dirty.value,false);
});
test('409 retains exact draft/base and explicit discard/reapply is required',()=>{
 const d=useDraft({body:'saved'},'1',()=>true);d.edit({body:'mine'});d.observe({body:'theirs'},'2');assert.equal(d.value.value.body,'mine');assert.equal(d.baseToken.value,'1');d.reapply();assert.equal(d.value.value.body,'mine');assert.equal(d.baseToken.value,'2');assert.equal(d.dirty.value,true);d.observe({body:'latest'},'3');d.discard();assert.equal(d.value.value.body,'latest');assert.equal(d.baseToken.value,'3');assert.equal(d.dirty.value,false);
});
test('permission gate blocks edits and stale/foreign ACK tickets cannot clear current draft',()=>{
 let allowed=false;const d=useDraft({body:'base'},'0',()=>allowed);assert.throws(()=>d.edit({body:'typed'}));allowed=true;d.edit({body:'ok'});const ticket=d.capture();d.reset({body:'new scope'},'9');assert.throws(()=>d.accept(ticket,{body:'old'},'1'));assert.equal(d.value.value.body,'new scope');
 const other=useDraft({body:'base'},'9',()=>true);assert.throws(()=>other.accept(d.capture(),{body:'fake'},'10'));
});
