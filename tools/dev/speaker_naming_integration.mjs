import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

const { chromium, expect } = createRequire(resolve('web/package.json'))('@playwright/test');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const base = process.env.AMA_SPEAKER_TEST_ORIGIN ?? 'http://127.0.0.1:5174';
const errors = [];
page.on('pageerror', error => errors.push(error.message));
async function read(browserPage, path) {
  return browserPage.evaluate(async path => {
    const response = await fetch(path, { credentials: 'include' });
    return { status: response.status, body: await response.json() };
  }, path);
}
try {
  await page.goto(base + '/login');
  await page.locator('#login-username').fill('1');
  await page.locator('#login-password').fill('1');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL('**/meetings');
  await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy', 'false', { timeout: 30000 });
  await page.getByRole('button', { name: '导入会议', exact: true }).click();
  await page.waitForURL(/import=[0-9a-f-]+/);
  const meeting = new URL(page.url()).searchParams.get('import');
  const importer = page.getByRole('dialog', { name: '导入会议', exact: true });
  const format = process.argv[2] ?? 'txt';
  assert(['txt', 'srt', 'vtt'].includes(format));
  const fileInput = importer.getByLabel('选择会议文件', { exact: true });
  await expect(fileInput).toBeEnabled({ timeout: 30000 });
  await fileInput.setInputFiles(
    resolve('test-data/meeting-main-files-2026-11-02/files/02_text/meeting.zh-CN.' + format),
  );
  const processButton = importer.getByRole('button', { name: '开始处理', exact: true });
  await expect(processButton).toBeEnabled({ timeout: 30000 });
  await processButton.click();
  const speaker = page.getByRole('region', { name: '当前说话人' });
  const edit = speaker.locator('div.flex.mb12').filter({ hasText: '林晨' }).getByRole('button', { name: '编辑', exact: true });
  await expect(edit).toBeEnabled({ timeout: 90000 });
  console.log('PASS', format, 'import and speaker sidebar');
  const writes = [];
  page.on('request', request => {
    if (request.method() !== 'GET' && request.url().includes('/transcript')) writes.push(request.url());
  });
  await edit.click();
  const naming = page.getByRole('dialog', { name: '命名说话人 · 林晨', exact: true });
  await expect(naming).toBeVisible();
  await expect(naming.getByRole('combobox')).toBeVisible();
  await expect(naming.getByRole('radio', { name: /仅用于本次会议/ })).toBeChecked();
  await expect.poll(async () => Math.round((await naming.boundingBox())?.width ?? 0)).toBe(520);
  await page.keyboard.press('Escape');
  await expect(naming).toBeVisible();
  await naming.getByRole('button', { name: '保留匿名', exact: true }).click();
  assert.equal(writes.length, 0);
  await edit.click();
  await naming.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(naming).not.toBeVisible();
  await edit.click();
  await page.mouse.click(5, 70);
  await expect(naming).not.toBeVisible();
  assert.equal(writes.length, 0);
  await edit.click();
  const external = naming.getByRole('button', { name: '输入外部人员名称' });
  await external.click();
  await naming.getByRole('button', { name: '确认命名' }).click();
  await expect(page.getByText('请输入外部人员名称', { exact: true })).toBeVisible();
  await naming.getByPlaceholder('例如：陈分析师（某券商）').fill('核对林晨');
  await naming.getByRole('radio', { name: /提交声纹库审核/ }).check();
  await naming.getByRole('button', { name: '确认命名' }).click();
  const review = page.getByRole('dialog', { name: '提交声纹库审核', exact: true });
  await expect(review.getByRole('textbox')).toBeDisabled();
  await review.getByRole('button', { name: '取消', exact: true }).click();
  await expect(naming).not.toBeVisible();
  assert.equal(writes.length, 0);
  await edit.click();
  await external.click();
  await naming.getByPlaceholder('例如：陈分析师（某券商）').fill('核对林晨');
  console.log('PASS', format, 'prototype opening, zero-write exits and validation');
  const attempts = [];
  const dropAck = format === 'txt' && process.env.AMA_SPEAKER_DROP_ACK === '1';
  if (dropAck) {
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/transcript/speakers'))
        attempts.push([request.headers()['idempotency-key'], request.postData()]);
    });
    await page.evaluate(() => {
      const fetchOriginal = window.fetch.bind(window);
      window.fetch = async (input, options) => {
        const response = await fetchOriginal(input, options);
        if (options?.method === 'POST' && String(input).endsWith('/transcript/speakers') && response.ok) {
          window.fetch = fetchOriginal;
          throw new TypeError('Controlled test: lost response after the server accepted the operation');
        }
        return response;
      };
    });
  }
  await naming.getByRole('button', { name: '确认命名' }).click();
  if (dropAck) {
    await expect(naming.getByRole('alert')).toBeVisible({ timeout: 30000 });
    assert.equal(attempts.length, 1, await naming.getByRole('alert').textContent());
    await expect(naming.getByPlaceholder('例如：陈分析师（某券商）')).toBeDisabled();
    await naming.getByRole('button', { name: '确认命名' }).click();
  }
  await expect(naming).not.toBeVisible({ timeout: 30000 });
  if (dropAck) {
    assert.equal(attempts.length, 2);
    assert.deepEqual(attempts[0], attempts[1]);
  }
  await page.reload();
  await expect(speaker.locator('div.flex.mb12').filter({ hasText: '核对林晨' })).toBeVisible({ timeout: 30000 });
  const root = '/api/v1/meetings/' + meeting + '/transcript';
  const response = await read(page, root);
  assert.equal(response.status, 200);
  const { saved } = response.body;
  assert.equal(saved.segments.length, 10);
  assert.equal(saved.segments.filter(segment => segment.speakerId).length, 4);
  console.log('PASS', format, 'rename, refresh and command recovery');
  const namedRow = speaker.locator('div.flex.mb12').filter({ hasText: '核对林晨' });
  await namedRow.getByRole('button', { name: '编辑', exact: true }).click();
  await expect(naming.getByRole('combobox')).toBeVisible();
  await external.click();
  await expect(naming.getByPlaceholder('例如：陈分析师（某券商）')).toHaveValue('核对林晨');
  await naming.getByPlaceholder('例如：陈分析师（某券商）').fill('本次声纹核对');
  await naming.getByRole('radio', { name: /提交声纹库审核/ }).check();
  await naming.getByRole('button', { name: '确认命名' }).click();
  await review.getByRole('button', { name: '确认提交审核' }).click();
  await expect(page.getByText('说话人已命名，本次会议无可用原录音，未提交声纹审核。', { exact: true }))
    .toBeVisible({ timeout: 30000 });
  await expect(speaker.locator('div.flex.mb12').filter({ hasText: '本次声纹核对' })).toBeVisible({ timeout: 30000 });
  await speaker.locator('div.flex.mb12').filter({ hasText: '本次声纹核对' }).getByRole('button', { name: '编辑', exact: true }).click();
  await expect(naming.locator('option').first()).toBeAttached({ timeout: 30000 });
  const directory = naming.getByRole('combobox');
  await directory.selectOption('dev-owner');
  await naming.getByRole('button', { name: '确认命名' }).click();
  await expect(naming).not.toBeVisible({ timeout: 30000 });
  const readGroups = async () => {
    const current = await read(page, root);
    assert.equal(current.status, 200);
    const groups = await read(page, root + '/speakers?sourceRevisionId=' + current.body.saved.id);
    assert.equal(groups.status, 200);
    return groups.body.items;
  };
  const linked = (await readGroups()).find(group => group.enterpriseId === 'dev-owner');
  assert.equal(linked.segmentIds.length, 4);
  console.log('PASS', format, 'voice failure preserves name and enterprise directory association');
  await page.setViewportSize({ width: 1024, height: 768 });
  await speaker.locator('div.flex.mb12').filter({ hasText: '开发负责人' }).getByRole('button', { name: '编辑', exact: true }).click();
  await expect(naming.getByRole('combobox')).toHaveValue('dev-owner', { timeout: 30000 });
  await expect.poll(async () => Math.round((await naming.boundingBox())?.width ?? 0)).toBe(520);
  await naming.getByRole('button', { name: '保留匿名', exact: true }).click();
  await speaker.getByRole('button', { name: '拆分说话人', exact: true }).click();
  const split = page.getByRole('dialog', { name: '拆分说话人', exact: true });
  await split.getByLabel('源说话人').selectOption(linked.id);
  await split.getByRole('button', { name: '拆分为新说话人' }).click();
  await expect(split.getByRole('alert')).toHaveText('请选择至少一个片段');
  await split.getByRole('checkbox').first().check();
  await split.getByRole('button', { name: '拆分为新说话人' }).click();
  await expect(split).not.toBeVisible({ timeout: 30000 });
  const newGroup = (await readGroups()).find(group => group.label === '说话人 1');
  assert.equal(newGroup.segmentIds.length, 1);
  await speaker.getByRole('button', { name: '合并说话人', exact: true }).click();
  const merge = page.getByRole('dialog', { name: '合并说话人', exact: true });
  await merge.getByRole('button', { name: '合并', exact: true }).click();
  await expect(merge.getByRole('alert')).toHaveText('来源与目标不能相同');
  await merge.getByLabel('来源说话人').selectOption(newGroup.id);
  await merge.getByLabel('目标说话人').selectOption(linked.id);
  await merge.getByRole('button', { name: '合并', exact: true }).click();
  await expect(merge).not.toBeVisible({ timeout: 30000 });
  assert.equal((await readGroups()).find(group => group.enterpriseId === 'dev-owner').segmentIds.length, 4);
  await speaker.getByRole('button', { name: '撤销', exact: true }).click();
  await expect.poll(async () => (await readGroups()).length, { timeout: 30000 }).toBe(2);
  await expect(page.getByRole('dialog', { name: '转写版本与语音审阅' })).not.toBeVisible();
  const reader = await browser.newPage();
  await reader.goto(base + '/login');
  await reader.locator('#login-username').fill('dev-viewer');
  await reader.locator('#login-password').fill('dev-password');
  await reader.getByRole('button', { name: '登录', exact: true }).click();
  await reader.waitForURL('**/meetings');
  assert.equal((await read(reader, root)).status, 403);
  await reader.close();
  assert.deepEqual(errors, []);
  console.log('PASS', format, 'RAW cancel, blank name, voice cancel and no-audio failure, external rename, directory identity, reload, split, merge and undo');
} catch (error) {
  console.error('Visible alerts:', await page.getByRole('alert').allTextContents());
  throw error;
} finally {
  await browser.close();
}
