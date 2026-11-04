import {test} from 'node:test';
import assert from 'node:assert/strict';
import {finalConfirmationChecks} from '../../src/features/minutes/final-confirmation-checks';
const ready:any={ready:true,blockers:[],required:[],pendingCandidateIds:[],outstandingTaskIds:[]};
test('all twelve design checks are represented without inventing audio, date or roster requirements',()=>{
 const checks=finalConfirmationChecks(ready,true,{});
 assert.equal(checks.length,12);assert.ok(checks.every(x=>x.status==='passed'));
});
test('failed source and action checks point to their exact saved target and review type',()=>{
 const checks=finalConfirmationChecks({...ready,ready:false,blockers:['REVIEW_REQUIRED'],required:[{targetId:'conclusion',reviewKind:'SOURCE_SUPPORT',satisfied:false},{targetId:'action',reviewKind:'ACTION_FIELDS',satisfied:false}]},true,{conclusion:'结论一',action:'行动一'});
 assert.deepEqual(checks.find(c=>c.id==='conclusions')?.actions[0]?.target,{kind:'review',targetId:'conclusion',reviewKind:'SOURCE_SUPPORT'});
 assert.deepEqual(checks.find(c=>c.id==='actions')?.actions[0]?.target,{kind:'review',targetId:'action',reviewKind:'ACTION_FIELDS'});
 assert.equal(checks.find(c=>c.id==='actions')?.status,'failed');
});
test('information, pending work and unknown failures remain actionable, without exposing internal codes',()=>{
 const checks=finalConfirmationChecks({...ready,ready:false,blockers:['MEETING_INFO_REQUIRED','ROSTER_CONFIRMATION_REQUIRED','CORRECTIONS_PENDING','PENDING_CANDIDATE','OUTSTANDING_GENERATION','PUBLICATION_HISTORY_UNAVAILABLE','FUTURE_PRIVATE_CODE'],pendingCandidateIds:['candidate'],outstandingTaskIds:['task']},true,{});
 assert.equal(checks.find(c=>c.id==='information')?.status,'failed');assert.ok(checks.some(c=>c.actions.some(a=>a.target.kind==='candidate'&&a.target.candidateId==='candidate')));
 assert.ok(checks.some(c=>c.actions.some(a=>a.target.kind==='transcript')));assert.ok(checks.some(c=>c.actions.some(a=>a.target.kind==='tasks')));
 assert.doesNotMatch(JSON.stringify(checks),/FUTURE_PRIVATE_CODE/);
});
test('failed or missing observations never display successful validation',()=>{
 assert.ok(finalConfirmationChecks(null,true,{}).every(x=>x.status==='unknown'));
 assert.equal(finalConfirmationChecks(ready,false,{}).find(x=>x.id==='permission')?.status,'failed');
});
