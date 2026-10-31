import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const scripts = [
  '../../../tools/dev/prototype_home_behaviour.mjs',
  '../../../tools/dev/prototype_home_pixel.mjs',
  '../../../tools/dev/prototype_parity_info_drawer.mjs',
  '../../../tools/dev/prototype_import_pixel.mjs',
];

test('prototype parity scripts use the production CAS login page', async () => {
  for (const relative of scripts) {
    const source = await readFile(new URL(relative, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /\/api\/dev\/login/);
    assert.match(source, /#login-username/);
    assert.match(source, /#login-password/);
    assert.match(source, /name:'登录'/);
  }
});
