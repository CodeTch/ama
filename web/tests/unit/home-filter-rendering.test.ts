import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';

test('home filter updates keep the page shell mounted',()=>{
 const source=readFileSync(resolve(process.cwd(),'src/App.vue'),'utf8');
 const template=source.slice(source.indexOf('<template>'));
 assert.match(template,/<div[^>]*class="layout"/);
 assert.doesNotMatch(template,/<div[^>]*:key="homeRenderVersion"[^>]*class="layout"/);
 assert.doesNotMatch(source,/homeRenderVersion/);
 assert.doesNotMatch(template,/正在加载会议/);
});
