import {test} from 'node:test';
import assert from 'node:assert/strict';
import {workspaceStages,resolveMeetingStage} from '../../src/features/meetings/workspace-stages';
const meetingStages=(value:any,published=false)=>workspaceStages(value,published).available;
const navigation:any={stage:'MINUTES_PENDING',saved:{transcriptRevisionId:'t',transcriptConfirmationId:'tc',minutesRevisionId:'m',elementSetId:'e'},privateSections:{finalConfirmationId:null,tasks:{items:[]},candidates:{items:[],hasMore:false}}};
test('recorded RAW does not unlock correction until initial enhancement creates the working transcript',()=>{
 const recorded:any={stage:'ENTRY',saved:{transcriptRevisionId:null},privateSections:{tasks:{items:[{kind:'RECORDING_ARCHIVE_V1',state:'SUCCEEDED'},{kind:'TRANSCRIPT_IMPORT_V1',state:'SUCCEEDED'}]}}};
 assert.equal(meetingStages(recorded).transcript,false);assert.equal(resolveMeetingStage('transcript',meetingStages(recorded)),'processing');
 assert.equal(meetingStages({...recorded,saved:{transcriptRevisionId:'working'}}).transcript,true);
 assert.equal(meetingStages({...recorded,privateSections:{tasks:{items:[{kind:'TRANSCRIPT_IMPORT_V1',state:'SUCCEEDED'}]}}}).transcript,true);
});
test('formal route and progress use the same saved-content and candidate gates; info is completed inside confirmation',()=>{
 assert.equal(meetingStages(navigation).confirm,true);
 assert.equal(meetingStages({...navigation,privateSections:{...navigation.privateSections,candidates:{items:[{id:'pending'}],hasMore:false}}}).confirm,false);
 assert.equal(meetingStages({...navigation,privateSections:{...navigation.privateSections,candidates:{items:[],hasMore:true}}}).confirm,false);
 assert.equal(resolveMeetingStage('confirm',meetingStages({...navigation,saved:{...navigation.saved,minutesRevisionId:null}})),'minutes');
 assert.equal(resolveMeetingStage('confirm',meetingStages(navigation)),'confirm');
 assert.equal(resolveMeetingStage('confirm',meetingStages(navigation,true)),'published');
});

test('a previous successful RAW task cannot mark a later failed import complete',()=>{
 const value:any={stage:'TRANSCRIPT_FAILED',saved:{},privateSections:{tasks:{items:[
  {kind:'TRANSCRIPT_IMPORT_V1',state:'FAILED'},{kind:'TRANSCRIPT_IMPORT_V1',state:'SUCCEEDED'}]}}};
 assert.equal(workspaceStages(value).complete[0],false);
 assert.equal(workspaceStages(value).available.transcript,false);
 value.saved.transcriptRevisionId='previous-working-version';
 assert.equal(workspaceStages(value).complete[0],false);
 assert.equal(workspaceStages(value).available.transcript,true);
});
