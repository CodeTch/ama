import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir, writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';

process.env.NO_PROXY = process.env.no_proxy = 'localhost,127.0.0.1,127.0.0.2';
const {chromium, expect} = createRequire(resolve('web/package.json'))('@playwright/test');
const base = process.env.AMA_LOCAL_URL ?? 'http://127.0.0.1:5174';
const meeting = process.env.AMA_MEETING_ID;
assert.match(meeting ?? '', /^[0-9a-f-]{36}$/);
const output = process.env.AMA_MINUTES_OUTPUT ?? '.tools/local-app/material-minutes/current';
const sourceSha256 = execFileSync('python3', ['-c', 'import sys;sys.path.insert(0,"tools/dev");import local_app;print(local_app.source_signature())'], {encoding: 'utf8'}).trim();
const harnessRevision = execFileSync('git', ['-C', '/home/tanren/ama-integration-harness', 'rev-parse', 'HEAD'], {encoding: 'utf8'}).trim();
const identity = {sourceSha256, harnessRevision, casOrigin: 'http://127.0.0.1:8792', fixture: meeting};
await mkdir(output, {recursive: true});
const checks = [], requests = [], errors = [], network = [];
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({locale: 'zh-CN', timezoneId: 'Asia/Shanghai', viewport: {width: 1440, height: 900}});
const page = await context.newPage();
page.on('pageerror', error => errors.push(error.message));
page.on('response', response => {
  const path = new URL(response.url()).pathname;
  if (path.startsWith('/api/')) network.push({path, method: response.request().method(), status: response.status()});
});

async function call(suffix, body, key = crypto.randomUUID()) {
  const result = await page.evaluate(async ({meeting, suffix, body, key}) => {
    const session = await (await fetch('/api/v1/session')).json();
    const response = await fetch(`/api/v1/meetings/${meeting}/${suffix}`, body === undefined ? {} : {
      method: 'POST', headers: {'Content-Type': 'application/json', 'X-CSRF-Token': session.csrfToken, 'Idempotency-Key': key},
      body: JSON.stringify(body),
    });
    const text = await response.text();
    return {status: response.status, value: text ? JSON.parse(text) : null};
  }, {meeting, suffix, body, key});
  requests.push({method: body === undefined ? 'GET' : 'POST', suffix, ...result});
  return result;
}

async function get(suffix) {
  const result = await call(suffix);
  assert.equal(result.status, 200, `${suffix}: ${JSON.stringify(result.value)}`);
  return result.value;
}

function pass(name, details = {}) {
  checks.push({name, ...details});
  console.log(`PASS ${name}`);
}

try {
  await page.goto(`${base}/login`);
  await page.locator('#login-username').fill(process.env.AMA_CAS_USERNAME ?? 'dev-owner');
  await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD ?? 'dev-password');
  await page.getByRole('button', {name: '登录', exact: true}).click();
  await page.waitForURL(`${base}/meetings`);
  const sessionStatus = await page.evaluate(async () => (await fetch('/api/v1/session')).status);
  assert.equal(sessionStatus, 200, 'CAS must issue an active AMA session before domain requests');
  pass('fixed-harness-cas-session');
  const materials = await get('materials');
  assert.ok(materials.materialSnapshotId);
  assert.equal(materials.materialsPending, false);
  pass('exact-ready-material-snapshot', {snapshotId: materials.materialSnapshotId});
  let current = await get('minutes/generation-inputs/current');
  if (!current.saved.templateRevisionId) {
    const templates = await get('minutes/templates');
    const body = {expectedRevisionToken: current.observation.revisionToken, templateRevisionId: templates.items[0].revisionId};
    const key = crypto.randomUUID();
    const initial = await call('minutes/initial-settings', body, key);
    assert.equal(initial.status, 200, JSON.stringify(initial.value));
    const replay = await call('minutes/initial-settings', body, key);
    assert.equal(replay.status, 200);
    assert.equal(replay.value.replayed, true);
    assert.deepEqual(initial.value.result, replay.value.result);
    pass('initial-settings-with-material-and-exact-replay');
  }
  current = await get('minutes/generation-inputs/current');
  let taskId = process.env.AMA_MINUTES_TASK_ID, inputId;
  if (taskId) {
    inputId = (await get(`minutes/generation-tasks/${taskId}`)).task.inputId;
    pass('reuse-recorded-task-without-another-generation', {taskId, inputId});
  } else {
    assert.equal(current.eligible, true, JSON.stringify(current.reasons));
    const body = {expectedRevisionToken: current.observation.revisionToken, scope: 'FULL'};
    const key = crypto.randomUUID();
    const accepted = await call('minutes/generations', body, key);
    assert.equal(accepted.status, 202, JSON.stringify(accepted.value));
    const replay = await call('minutes/generations', body, key);
    assert.equal(replay.status, 202, JSON.stringify(replay.value));
    assert.equal(replay.value.replayed, true);
    assert.deepEqual(accepted.value.result, replay.value.result);
    ({taskId, inputId} = accepted.value.result);
    pass('generation-acceptance-and-exact-replay', {taskId, inputId});
  }
  let task;
  const deadline = Date.now() + 90000;
  do {
    task = await get(`minutes/generation-tasks/${taskId}`);
    if (['SUCCEEDED', 'FAILED', 'STALE', 'CANCELLED', 'MANUAL_REQUIRED'].includes(task.task.state)) break;
    await page.waitForTimeout(1000);
  } while (Date.now() < deadline);
  assert.equal(task.task.state, 'SUCCEEDED', JSON.stringify(task));
  const input = await get(`minutes/generation-inputs/${inputId}`);
  assert.equal(input.input.material_snapshot_id, materials.materialSnapshotId);
  assert.equal(input.provenanceVerified, true);
  const minutes = await get('minutes');
  const candidates = await get('minutes/candidates');
  assert.ok(minutes.body || candidates.items.length);
  pass('worker-output-and-material-provenance-readback');
  const readiness = await get('final-confirmations/readiness');
  assert.ok(!readiness.blockers.includes('MATERIALS_UNAVAILABLE'), 'A verified ready material snapshot must not block final confirmation');
  pass('final-readiness-reports-current-blockers', {blockers: readiness.blockers});
  await page.goto(`${base}/meeting/${meeting}/minutes`);
  await page.getByRole('button', {name: '会议纪要', exact: true}).click();
  await expect(page.locator('.minutes-body h3')).toHaveText(minutes.body.title);
  pass('native-minutes-route-displays-saved-output');
  if (process.env.AMA_MINUTES_EDIT === '1') {
    await page.getByRole('button', {name: '编辑正文', exact: true}).click();
    const editor = page.getByRole('region', {name: '纪要文字编辑'});
    await expect(editor.locator('input')).toBeEditable();
    const title = `${minutes.body.title} · 材料联调核对`;
    await editor.locator('input').fill(title);
    const saved = page.waitForResponse(r => new URL(r.url()).pathname === `/api/v1/meetings/${meeting}/minutes/body` && r.request().method() === 'PUT');
    await editor.getByRole('button', {name: '保存', exact: true}).click();
    assert.equal((await saved).status(), 200);
    assert.equal((await get('minutes')).body.title, title);
    await page.reload();
    await page.getByRole('button', {name: '会议纪要', exact: true}).click();
    await expect(page.locator('.minutes-body h3')).toHaveText(title);
    pass('native-title-edit-persistent-api-and-reload');
  }
  for (const [width, height] of [[1440, 900], [1024, 768]]) {
    await page.setViewportSize({width, height});
    await page.screenshot({path: `${output}/minutes-${width}.png`, fullPage: true});
  }
  assert.deepEqual(errors, []);
  assert.deepEqual(network.filter(r => r.status >= 500), [], 'The native workspace must not hide failed producer reads');
  await writeFile(`${output}/report.json`, JSON.stringify({pass: true, ...identity, base, meeting, checks, requests, network, errors}, null, 2));
} catch (error) {
  await writeFile(`${output}/failure-page.txt`, `${page.url()}\n${await page.locator('body').innerText()}`);
  await page.screenshot({path: `${output}/failure-page.png`, fullPage: true});
  await writeFile(`${output}/report.json`, JSON.stringify({pass: false, ...identity, base, meeting, checks, requests, network, errors, error: String(error)}, null, 2));
  throw error;
} finally {
  await browser.close();
}
