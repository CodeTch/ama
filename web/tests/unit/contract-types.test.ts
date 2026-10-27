import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('generated command types preserve required version alternatives and reject invalid fields', () => {
  const root = fileURLToPath(new URL('../../', import.meta.url));
  const result = spawnSync(process.execPath, ['node_modules/typescript/bin/tsc', '--strict', '--skipLibCheck', '--noEmit', '--module', 'NodeNext', '--moduleResolution', 'NodeNext', 'tests/types/commands.mts'], { cwd: root, encoding: 'utf8', timeout: 30000 });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stdout + result.stderr);
});
