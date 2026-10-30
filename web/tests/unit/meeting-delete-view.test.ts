import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

test('delete panel exposes a busy state to assistive technology',async()=>{
 const source=await readFile(new URL('../../src/features/meetings/FuturePanels.vue',import.meta.url),'utf8');
 assert.match(source,/aria-busy=\"busy\"/);
});
