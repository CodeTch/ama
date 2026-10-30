import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('closed knowledge picker cannot intercept the import dialog',async()=>{
 const source=await readFile(new URL('../../src/features/import/KnowledgeMaterialPicker.vue',import.meta.url),'utf8');
 assert.match(source,/\.material-picker-mask:not\(\[open\]\)\{display:none\}/);
});
