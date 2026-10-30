import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';

process.env.NO_PROXY = process.env.no_proxy = 'localhost,127.0.0.1,127.0.0.2';
const require = createRequire(resolve('web/package.json'));
const {chromium, expect} = require('@playwright/test');

const base = process.env.AMA_LOCAL_URL ?? 'http://127.0.0.1:5173';
const casOrigin = process.env.AMA_CAS_ORIGIN ?? 'http://127.0.0.1:8792';
const username = process.env.AMA_CAS_USERNAME ?? 'dev-owner';
const password = process.env.AMA_CAS_PASSWORD ?? 'dev-password';
const output = process.env.AMA_IMPORT_CAS_OUTPUT ?? '.tools/local-app/integration-import-cas';
const callback = `${base}/auth/cas/callback`;
if (process.env.FIXED_PORTS === '1' && casOrigin !== 'http://127.0.0.1:8792') {
  throw new Error(`FIXED_PORTS requires the CAS harness at http://127.0.0.1:8792, got ${casOrigin}`);
}
const checks = [];
const requests = [];
const requestEntries = new Map();
const errors = [];
let meetingId = null;

await mkdir(output, {recursive: true});
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({locale: 'zh-CN', timezoneId: 'Asia/Shanghai'});
const page = await context.newPage();
page.setDefaultTimeout(30000);
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => {
  const url = new URL(request.url());
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/')) {
    const entry = {method: request.method(), path: url.pathname, status: null};
    requests.push(entry);
    requestEntries.set(request, entry);
  }
});
page.on('response', response => {
  const entry = requestEntries.get(response.request());
  if (entry) entry.status = response.status();
});
page.on('requestfailed', request => {
  const entry = requestEntries.get(request);
  if (entry) {
    entry.status = 'FAILED';
    entry.error = request.failure()?.errorText ?? 'request_failed';
  }
});
page.on('requestfinished', request => {
  const entry = requestEntries.get(request);
  if (entry && entry.status === null) entry.status = 'FINISHED_WITHOUT_RESPONSE';
});

function record(name, details = {}) {
  checks.push({name, pass: true, ...details});
  console.log(`PASS ${name}`);
}

async function session() {
  return page.evaluate(async () => {
    const response = await fetch('/api/v1/session', {credentials: 'include'});
    return {status: response.status, value: await response.json()};
  });
}

async function saveReport(value) {
  await writeFile(`${output}/report.json`, JSON.stringify(value, null, 2));
}

async function settleTrackedRequests() {
  // Navigation can cancel a fetch after the response observer has run. Give
  // Playwright a short drain window, then classify anything still unresolved
  // as an aborted request instead of emitting a misleading null status.
  const deadline = Date.now() + 1000;
  while ([...requestEntries.values()].some(entry => entry.status === null) && Date.now() < deadline) {
    await page.waitForTimeout(25);
  }
  for (const entry of requestEntries.values()) {
    if (entry.status === null) {
      entry.status = 'ABORTED';
      entry.error = 'not_settled_before_report';
    }
  }
}

function unexpectedServerErrors() {
  return requests.filter(entry => typeof entry.status === 'number' && entry.status >= 500);
}

function unexpectedRequestFailures() {
  return requests.filter(entry => entry.status === 'FAILED' && entry.error !== 'net::ERR_ABORTED');
}

try {
  const anonymous = await page.request.get(`${base}/api/v1/session`, {maxRedirects: 0});
  assert.equal(anonymous.status(), 401);
  record('anonymous-session-rejected', {status: anonymous.status()});

  const entry = await page.request.get(`${base}/auth/cas/login`, {maxRedirects: 0});
  assert.equal(entry.status(), 302);
  const location = entry.headers().location;
  assert.ok(location?.startsWith(`${casOrigin}/cas/login?service=`));
  assert.equal(new URL(location).searchParams.get('service'), callback);
  record('cas-entry-uses-fixed-harness-and-exact-callback', {location});

  await page.goto(`${base}/login`);
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', {name: '登录', exact: true}).click();
  await page.waitForURL(`${base}/meetings`);
  const authenticated = await session();
  assert.equal(authenticated.status, 200);
  assert.equal(authenticated.value.actor.enterpriseId, username);
  record('cas-ticket-validates-and-issues-ama-session', {actor: authenticated.value.actor.enterpriseId});

  const createResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/meetings');
  await page.getByRole('button', {name: '导入会议', exact: true}).click();
  const created = await createResponse;
  assert.equal(created.status(), 201);
  await page.waitForURL(/\/meetings\?import=[0-9a-f-]{36}&fresh=1$/);
  meetingId = new URL(page.url()).searchParams.get('import');
  assert.match(meetingId, /^[0-9a-f-]{36}$/);
  const importDialog = page.getByRole('dialog', {name: '导入会议', exact: true});
  await expect(importDialog).toBeVisible();
  assert.equal(new URL(page.url()).pathname, '/meetings');
  record('real-meeting-create-opens-import-dialog', {meetingId});

  const uploadCountBefore = requests.filter(request => request.method === 'POST' && request.path.endsWith('/uploads')).length;
  await importDialog.getByLabel('选择会议文件', {exact: true}).setInputFiles({
    name: '联调导入.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('CAS 与导入页真实联调验证。\n保留 UTF-8 内容。\n'),
  });
  await expect(importDialog.getByRole('button', {name: '开始处理', exact: true})).toBeEnabled({timeout: 30000});
  await expect(importDialog.getByRole('button', {name: '保存并返回列表', exact: true})).toBeEnabled({timeout: 30000});
  const uploadCountAfter = requests.filter(request => request.method === 'POST' && request.path.endsWith('/uploads')).length;
  assert.ok(uploadCountAfter > uploadCountBefore);
  await importDialog.getByRole('button', {name: '保存并返回列表', exact: true}).click();
  await expect(importDialog).toBeHidden();
  await page.waitForURL(`${base}/meetings`);
  record('txt-upload-and-draft-save-use-real-requests', {uploadRequests: uploadCountAfter - uploadCountBefore});

  await page.goto(`${base}/meeting/${meetingId}?import=1`);
  const restoredDialog = page.getByRole('dialog', {name: '导入会议', exact: true});
  await expect(restoredDialog).toBeVisible();
  await expect(restoredDialog.locator('.f-name')).toContainText('联调导入.txt');
  assert.equal(requests.filter(request => request.method === 'POST' && request.path.endsWith('/uploads')).length, uploadCountAfter);
  record('fresh-meeting-route-restores-saved-file-without-reupload');

  await restoredDialog.getByRole('button', {name: '从知识库选择', exact: true}).click();
  const picker = page.getByRole('dialog', {name: '从知识库选择背景材料', exact: true});
  await expect(picker).toBeVisible();
  await expect(picker.locator('.material-library-option').first()).toBeVisible();
  await picker.locator('.material-library-option').first().click();
  await expect(picker.locator('.material-document-row').first()).toBeVisible();
  const documentRow = picker.locator('.material-document-row').first();
  const documentText = await documentRow.innerText();
  assert.match(documentText, /版本 [0-9a-f]{12}/i);
  await documentRow.locator('input[type="checkbox"]').check();
  await picker.getByRole('button', {name: '添加到会议', exact: true}).click();
  await expect(picker).toBeHidden();
  record('knowledge-picker-reads-harness-document-version', {document: documentText});

  const denialScenario = await page.request.post(`${casOrigin}/admin/api/scenarios`, {
    data: {
      module: 'knowledge',
      name: `ama-import-provider-denial-${Date.now()}`,
      method: 'GET',
      path: '/v1/knowledge/material-libraries',
      priority: 1000,
      status_code: 403,
      response_type: 'json',
      response_body: {error: 'material_access_denied'},
    },
  });
  assert.equal(denialScenario.status(), 201);
  const denialScenarioId = (await denialScenario.json()).id;
  try {
    await restoredDialog.getByRole('button', {name: '从知识库选择', exact: true}).click();
    const deniedPicker = page.getByRole('dialog', {name: '从知识库选择背景材料', exact: true});
    await expect(deniedPicker).toBeVisible();
    await expect(deniedPicker.getByRole('alert')).toContainText('暂无访问权限');
    record('knowledge-picker-surfaces-provider-denial', {providerStatus: 403, amaStatus: 403});
    await deniedPicker.getByRole('button', {name: '关闭知识库选择', exact: true}).click();
    await expect(deniedPicker).toBeHidden();
  } finally {
    const deleted = await page.request.delete(`${casOrigin}/admin/api/scenarios/${denialScenarioId}`);
    assert.equal(deleted.status(), 200);
  }

  await expect(restoredDialog.getByRole('button', {name: '开始处理', exact: true})).toBeEnabled({timeout: 30000});
  const intakeResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === `/api/v1/meetings/${meetingId}/transcript-imports`);
  await restoredDialog.getByRole('button', {name: '开始处理', exact: true}).click();
  const intake = await intakeResponse;
  assert.equal(intake.status(), 202);
  await page.waitForURL(`${base}/meeting/${meetingId}/processing`);
  const taskPage = await page.evaluate(async path => {
    const response = await fetch(path, {credentials: 'include'});
    return {status: response.status, value: await response.json()};
  }, `/api/v1/meetings/${meetingId}/tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20`);
  assert.equal(taskPage.status, 200);
  const acceptedTaskId = taskPage.value.items?.[0]?.taskId;
  assert.match(acceptedTaskId ?? '', /^[0-9a-f-]{36}$/);
  record('import-acknowledgement-routes-to-processing', {taskId: acceptedTaskId, status: intake.status()});

  await page.goto(`${base}/auth/cas/logout`);
  await page.waitForURL(`${base}/meetings`);
  assert.equal((await session()).status, 401);
  assert.deepEqual(errors, []);
  const harnessLogResponse = await page.request.get(`${casOrigin}/admin/api/logs?limit=500`);
  assert.equal(harnessLogResponse.status(), 200);
  const harnessLogs = (await harnessLogResponse.json()).items ?? [];
  const harnessPaths = new Set(harnessLogs.filter(item => item.module === 'cas').map(item => item.path));
  assert.ok(harnessPaths.has('/cas/login'));
  assert.ok(harnessPaths.has('/cas/serviceValidate') || harnessPaths.has('/cas/p3/serviceValidate'));
  assert.ok(harnessPaths.has('/cas/logout'));
  await settleTrackedRequests();
  const serverErrors = unexpectedServerErrors();
  const requestFailures = unexpectedRequestFailures();
  assert.deepEqual(serverErrors, []);
  assert.deepEqual(requestFailures, []);
  record('logout-revokes-session-without-page-errors');
  await saveReport({pass: true, base, casOrigin, checks, requests, harnessLogs, errors, meetingId, serverErrors, requestFailures});
  console.log(JSON.stringify({pass: true, checks: checks.length, meetingId}));
} catch (error) {
  await settleTrackedRequests();
  await saveReport({pass: false, base, casOrigin, checks, requests, errors, meetingId, error: String(error), serverErrors: unexpectedServerErrors(), requestFailures: unexpectedRequestFailures()});
  throw error;
} finally {
  await browser.close();
}
