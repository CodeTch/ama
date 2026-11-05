import {test} from 'node:test';
import assert from 'node:assert/strict';
import {playbackSegments} from '../../src/features/transcript/playback-segments.js';
const row=(segmentId:string,startMs:number|null,endMs:number|null,sourceFileId='a')=>({segmentId,startMs,endMs,sourceFileId});
test('playback uses half-open intervals, transcript order on overlaps and separate file timelines',()=>{
 const select=playbackSegments([row('first',100,220),row('overlap',180,300),row('later',400,500),row('other',0,120,'b'),row('missing',null,null)]);
 const at=(ms:number,fileId='a',playing=true)=>select({ms,fileId,playing});
 assert.equal(at(99),undefined);assert.equal(at(100),'first');assert.equal(at(185),'first');assert.equal(at(220),'overlap');assert.equal(at(300),undefined);assert.equal(at(450),'later');assert.equal(at(100,'b'),'other');assert.equal(at(100,'b',false),undefined);
 const irregular=playbackSegments([row('late',500,600),row('long',0,1000),row('short',20,40)]);
 assert.equal(irregular({fileId:'a',ms:530,playing:true}),'late');assert.equal(irregular({fileId:'a',ms:30,playing:true}),'long');
});
