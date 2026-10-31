import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
process.env.NO_PROXY = process.env.no_proxy = 'localhost,127.0.0.1,127.0.0.2';
const {chromium, expect} = createRequire(resolve('web/package.json'))('@playwright/test');
const base = process.env.AMA_LOCAL_URL ?? 'http://127.0.0.1:5174';
const meeting = process.env.AMA_MEETING_ID;
assert.match(meeting ?? '', /^[0-9a-f-]{36}$/);
const output = process.env.AMA_PUBLICATION_OUTPUT ?? '.tools/local-app/material-publication/current';
await mkdir(output, {recursive: true});
const browser = await chromium.launch({headless: true});
const checks = [], requests = [], errors = [];
let owner, member;
async function login(actor) {
  const context = await browser.newContext({viewport: {width: 1440, height: 900}, locale: 'zh-CN', timezoneId: 'Asia/Shanghai'});
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}/login`);
  await page.locator('#login-username').fill(actor);
  await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD ?? 'dev-password');
  await page.getByRole('button', {name: '登录', exact: true}).click();
  await page.waitForURL(`${base}/meetings`);
  return page;
}
async function call(page, suffix, body, key = crypto.randomUUID()) {
  const result = await page.evaluate(async ({meeting, suffix, body, key}) => {
    const session = await (await fetch('/api/v1/session')).json();
    const response = await fetch(`/api/v1/meetings/${meeting}/${suffix}`, body === undefined ? {} : {
      method: 'POST', headers: {'Content-Type': 'application/json', 'X-CSRF-Token': session.csrfToken, 'Idempotency-Key': key}, body: JSON.stringify(body),
    });
    return {status: response.status, value: await response.json()};
  }, {meeting, suffix, body, key});
  requests.push({suffix, method: body === undefined ? 'GET' : 'POST', ...result});
  return result;
}
async function get(page, suffix) {
  const result = await call(page, suffix);
  assert.equal(result.status, 200, `${suffix}: ${JSON.stringify(result.value)}`);
  return result.value;
}
function pass(name, facts = {}) { checks.push({name, ...facts}); console.log(`PASS ${name}`); }
try {
  owner = await login('dev-owner');
  const libraries = await get(owner, 'publication-destinations/libraries');
  assert.ok(libraries.items.length);
  const library = libraries.items[0];
  const folders = await get(owner, `publication-destinations/libraries/${encodeURIComponent(library.id)}/folders`);
  const folder = folders.items.find(f => f.id === library.rootFolderId);
  assert.ok(folder);
  pass('fixed-harness-exact-provider-library-and-root-folder');
  const frozen = await get(owner, 'final-confirmations/current');
  assert.ok(frozen.confirmation.id);
  const key = crypto.randomUUID();
  const body = {expectedRevisionToken: frozen.observation.revisionToken, confirmationId: frozen.confirmation.id, providerId: libraries.providerId, libraryId: library.id, folderId: folder.id};
  let publication = (await get(owner, 'publications/current')).publication;
  let accepted;
  if (!publication) {
    accepted = await call(owner, 'publications', body, key);
    assert.equal(accepted.status, 202, JSON.stringify(accepted.value));
    pass('real-publication-accepted');
  } else pass('recover-existing-publication-with-get-only', {publicationId: publication.publicationId});
  const deadline = Date.now() + 90000;
  do {
    publication = (await get(owner, 'publications/current')).publication;
    if (publication && ['SUCCEEDED', 'FAILED'].includes(publication.rawState)) break;
    await owner.waitForTimeout(1000);
  } while (Date.now() < deadline);
  assert.equal(publication.rawState, 'SUCCEEDED', JSON.stringify(publication));
  assert.equal(publication.documentViewAvailability, 'READY');
  if (accepted) {
    const replay = await call(owner, 'publications', body, key);
    assert.equal(replay.status, 202, JSON.stringify(replay.value));
    pass('accepted-command-replay-keeps-original-publication');
  }
  pass('worker-publish-and-formal-document-ready', {publicationId: publication.publicationId});
  member = await login('dev-member');
  for (const [actor, page] of [['owner', owner], ['member', member]]) {
    const document = await page.evaluate(async path => { const r = await fetch(path); return {status: r.status, html: await r.text()}; }, publication.officialDocumentUrl);
    assert.equal(document.status, 200);
    assert.ok(document.html.includes(frozen.body.title));
    await writeFile(`${output}/document-${actor}.html`, document.html);
    pass(`formal-document-${actor}-acl-and-bytes`, {sha256: createHash('sha256').update(document.html).digest('hex')});
  }
  const logs = await (await owner.request.get('http://127.0.0.1:8792/admin/api/logs?module=knowledge&limit=500')).json();
  const sends = logs.items.filter(r => r.method === 'POST' && r.path === '/v1/knowledge/publications' && r.requestSummary?.json?.publicationId === publication.publicationId);
  assert.equal(sends.length, 1, 'Refresh and command replay must not create another provider publication');
  pass('single-provider-post-through-fixed-harness', {posts: sends.length});
  await owner.goto(`${base}/meeting/${meeting}/published`);
  await expect(owner.locator('.m19-published-page')).toBeVisible();
  for (const [width, height] of [[1440, 900], [1024, 768]]) {
    await owner.setViewportSize({width, height});
    await owner.screenshot({path: `${output}/published-${width}.png`, fullPage: true});
  }
  assert.deepEqual(errors, []);
  await writeFile(`${output}/report.json`, JSON.stringify({pass: true, base, meeting, checks, requests, errors}, null, 2));
} catch (error) {
  if (owner) await owner.screenshot({path: `${output}/failure.png`, fullPage: true});
  await writeFile(`${output}/report.json`, JSON.stringify({pass: false, base, meeting, checks, requests, errors, error: String(error)}, null, 2));
  throw error;
} finally { await browser.close(); }
