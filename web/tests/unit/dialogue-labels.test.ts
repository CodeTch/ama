import {test} from 'node:test';import assert from 'node:assert/strict';
import {dialogueLabels} from '../../src/features/transcript/dialogue-labels';
test('file speaker labels retain body and distinguish identities and sources',()=>{
 const rows=['林晨：第一段','陈宇: 第二段','林晨：第三段','https://example.com','时间：下午三点','12:30 开始'].map((body,i)=>({segmentId:String(i),body,sourceFileId:'f',startMs:null,speakerId:null}));
 const result=dialogueLabels(rows);assert.deepEqual(result.items.map(x=>[x.label,x.segments]),[['林晨',2],['陈宇',1]]);assert.equal(result.labels['0'],'林晨');assert.equal(result.labels['3'],undefined);
 assert.equal(dialogueLabels([{...rows[0]!,startMs:0}]).items.length,0);
 assert.equal(dialogueLabels([{...rows[0]!,speakerId:'human'}]).items.length,0);
});
