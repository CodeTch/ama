import {test} from 'node:test';
import assert from 'node:assert/strict';
import {workspaceStages} from '../../src/features/meetings/workspace-stages';
const navigation=()=>({stage:'MINUTES_PENDING',relation:'OWNER',saved:{transcriptRevisionId:'t',transcriptConfirmationId:'tc',minutesRevisionId:'m',elementSetId:'s',infoRevisionId:null,rosterRevisionId:null},privateSections:{candidates:{items:[],hasMore:false},tasks:{items:[],hasMore:false},finalConfirmationId:null}} as any);
test('saved minutes may enter confirmation before required information and roster are filled',()=>{
 const n=navigation();assert.equal(workspaceStages(n).available.confirm,true);
 n.saved.minutesRevisionId=null;assert.equal(workspaceStages(n).available.confirm,false);
});
test('pending candidates and unfinished generation prevent every confirmation entry',()=>{
 const n=navigation();n.privateSections.candidates.items=[{id:'candidate'}];assert.equal(workspaceStages(n).available.confirm,false);
 n.privateSections.candidates.items=[];n.privateSections.candidates.hasMore=true;assert.equal(workspaceStages(n).available.confirm,false);
 n.privateSections.candidates.hasMore=false;n.privateSections.tasks.items=[{kind:'MINUTES_GENERATE_V1',state:'RUNNING'}];assert.equal(workspaceStages(n).available.confirm,false);
 n.privateSections.tasks.items[0].state='SUCCEEDED';assert.equal(workspaceStages(n).available.confirm,true);
});
test('read failures do not invent a ready confirmation and publication requires a current final',()=>{
 const n=navigation();assert.equal(workspaceStages(n).available.publish,false);
 n.privateSections.finalConfirmationId='final';assert.equal(workspaceStages(n).available.publish,true);
 n.privateSections=undefined;assert.equal(workspaceStages(n).available.confirm,false);
 assert.equal(workspaceStages(null).available.confirm,false);
});

test('authoritative active generation is independent of historical pages and active exports',()=>{
 const n=navigation();
 n.privateSections.tasks={items:[{kind:'MINUTES_EXPORT_V1',state:'RUNNING'}],hasMore:true};
 n.activeMinutesGeneration=false;
 assert.equal(workspaceStages(n).available.confirm,true);
 n.activeMinutesGeneration=true;
 assert.equal(workspaceStages(n).available.confirm,false);
});
