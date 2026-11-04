import {test} from 'node:test';
import assert from 'node:assert/strict';
import {correctionRange} from '../../src/features/transcript/correction-range';

test('the correction display keeps unchanged repeated text and Unicode intact',()=>{
 const change=correctionRange('😀 甲方同意，甲方补签','😀 甲方同意，乙方补签');
 assert.deepEqual(change,{prefix:'😀 甲方同意，',original:'甲',suggestion:'乙',suffix:'方补签'});
});

test('the correction display handles insertions and deletions without losing context',()=>{
 assert.deepEqual(correctionRange('补签','待补签'),{prefix:'',original:'',suggestion:'待',suffix:'补签'});
 assert.deepEqual(correctionRange('待补签','补签'),{prefix:'',original:'待',suggestion:'',suffix:'补签'});
});
