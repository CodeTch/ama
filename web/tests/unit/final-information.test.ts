import {test} from 'node:test';import assert from 'node:assert/strict';import {canPrepareFinalInformation,finalInformationError} from '../../src/features/minutes/final-information';
const base:any={ready:false,blockers:['MEETING_INFO_REQUIRED','ROSTER_CONFIRMATION_REQUIRED'],required:[{satisfied:true}]};
test('final confirmation may save its information and roster, but unresolved reviews or contradictory observations stay blocked',()=>{
 assert.equal(canPrepareFinalInformation(base),true);assert.equal(canPrepareFinalInformation({...base,blockers:[...base.blockers,'REVIEW_REQUIRED']}),false);assert.equal(canPrepareFinalInformation({...base,blockers:[]}),false);assert.equal(canPrepareFinalInformation({...base,ready:true,blockers:[],required:[{satisfied:false}]}),false);assert.equal(canPrepareFinalInformation(null),false);assert.equal(canPrepareFinalInformation({...base,ready:true,blockers:[]}),true);
});
test('required final information uses the exact prototype validation and does not reject an empty attendee list',()=>{
 const value={name:null,occurredOn:null,category:null,summary:null,rosterDraftIds:[],externalRosterNames:[]};assert.equal(finalInformationError(value),'会议名称必填，请补充后确认');assert.equal(finalInformationError({...value,name:'会议'}),'会议日期必填，请补充后确认');assert.equal(finalInformationError({...value,name:'会议',occurredOn:'2026-02-30'}),'会议日期无效，请重新选择');assert.equal(finalInformationError({...value,name:'会议',occurredOn:'2026-10-29'}),'会议分类必填，请选择后确认');assert.equal(finalInformationError({...value,name:'会议',occurredOn:'2026-10-29',category:'project'}),null);
});
