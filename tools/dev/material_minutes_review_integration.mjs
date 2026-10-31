import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir, writeFile} from 'node:fs/promises';
process.env.NO_PROXY = process.env.no_proxy = 'localhost,127.0.0.1,127.0.0.2';
const {chromium} = createRequire(resolve('web/package.json'))('@playwright/test');
const base = process.env.AMA_LOCAL_URL ?? 'http://127.0.0.1:5174';
const meeting = process.env.AMA_MEETING_ID;
assert.match(meeting ?? '', /^[0-9a-f-]{36}$/);
const output = process.env.AMA_REVIEW_OUTPUT ?? '.tools/local-app/material-minutes/review';
await mkdir(output, {recursive: true});
const checks = [], requests = [], errors = [];
const browser = await chromium.launch({headless: true});
async function login(actor) {
  const context = await browser.newContext({viewport: {width: 1440, height: 900}, locale: 'zh-CN', timezoneId: 'Asia/Shanghai'});
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(`${base}/login`);
  await page.locator('#login-username').fill(actor);
  await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD ?? 'dev-password');
  await page.getByRole('button', {name: '登录', exact: true}).click();
  await page.waitForURL(`${base}/meetings`);
  assert.equal(await page.evaluate(async () => (await fetch('/api/v1/session')).status), 200);
  return page;
}
async function call(page, suffix, body, method = 'POST', key = crypto.randomUUID()) {
  const result = await page.evaluate(async ({meeting, suffix, body, method, key}) => {
    const session = await (await fetch('/api/v1/session')).json();
    const response = await fetch(`/api/v1/meetings/${meeting}${suffix ? `/${suffix}` : ''}`, body === undefined ? {} : {
      method, headers: {'Content-Type': 'application/json', 'X-CSRF-Token': session.csrfToken, 'Idempotency-Key': key}, body: JSON.stringify(body),
    });
    const text = await response.text();
    return {status: response.status, value: text ? JSON.parse(text) : null};
  }, {meeting, suffix, body, method, key});
  requests.push({actor: page === owner ? 'owner' : 'member', suffix, method: body === undefined ? 'GET' : method, ...result});
  return result;
}
async function get(page, suffix) {
  const result = await call(page, suffix);
  assert.equal(result.status, 200, `${suffix}: ${JSON.stringify(result.value)}`);
  return result.value;
}
function pass(name, facts = {}) { checks.push({name, ...facts}); console.log(`PASS ${name}`); }
let owner, member;
try {
  owner = await login('dev-owner');
  member = await login('dev-member');
  pass('two-independent-fixed-harness-cas-identities');
  let saved = await get(owner, '');
  const fields = {name: '材料与纪要完整联调', category: 'project', summary: '受控模型生成、人工状态核对、权限撤回与最终确认。', occurredOn: '2026-10-31', externalRosterNames: ['联调外部参会人']};
  async function roster(ids) {
    saved = await get(owner, '');
    const update = await call(owner, 'info', {expectedRevisionToken: saved.revisionToken, ...fields, rosterDraftIds: ids}, 'PUT');
    assert.equal(update.status, 200, JSON.stringify(update.value));
    const confirmed = await call(owner, 'roster-confirmations', {expectedRevisionToken: update.value.revisionToken});
    assert.equal(confirmed.status, 200, JSON.stringify(confirmed.value));
    saved = await get(owner, '');
    assert.deepEqual(saved.confirmedRoster.filter(p => p.enterpriseId).map(p => p.enterpriseId).sort(), ids);
  }
  await roster(['dev-member']);
  assert.equal((await call(member, '')).status, 200);
  assert.equal((await call(member, 'minutes/editor-intents', {expectedRevisionToken: saved.revisionToken})).status, 403);
  pass('confirmed-roster-grants-member-read-and-denies-edit');
  await roster([]);
  assert.equal((await call(member, '')).status, 403);
  pass('roster-reconfirmation-revokes-existing-member-session');
  await roster(['dev-member']);
  assert.equal((await call(member, '')).status, 200);
  pass('roster-regrant-restores-member-read');
  async function review(target, reviewKind, outcome) {
    const current = await get(owner, 'minutes');
    const expectedRevisionToken = current.observation.revisionToken;
    const intent = await call(owner, 'minutes/editor-intents', {expectedRevisionToken});
    assert.equal(intent.status, 200, JSON.stringify(intent.value));
    const body = {expectedRevisionToken, target, reviewKind, scope: reviewKind === 'ACTION_FIELDS' ? null : 'WHOLE', outcome};
    const response = await call(owner, 'minutes/reviews', body);
    assert.equal(response.status, 200, JSON.stringify(response.value));
    const readback = await get(owner, `minutes/reviews/${response.value.resultId}`);
    assert.equal(readback.review.outcome, outcome);
    return response.value.resultId;
  }
  const current = await get(owner, 'minutes');
  await review({kind: 'BODY', id: current.body.id}, 'SOURCE_SUPPORT', 'PARTIAL');
  let readiness = await get(owner, 'final-confirmations/readiness');
  assert.equal(readiness.ready, false);
  assert.ok(readiness.blockers.includes('REVIEW_REQUIRED'));
  assert.ok(!readiness.blockers.includes('MATERIALS_UNAVAILABLE'));
  pass('saved-partial-review-and-final-blocking-with-ready-material');
  // Synthetic fixture decisions exercise protocol gates, not model quality or
  // a human semantic assessment of this generated content.
  for (const required of readiness.required) {
    if (required.satisfied) continue;
    await review({kind: required.targetKind, id: required.targetId}, required.reviewKind, required.reviewKind === 'ACTION_FIELDS' ? 'CONFIRMED' : 'SUPPORTED');
  }
  readiness = await get(owner, 'final-confirmations/readiness');
  assert.equal(readiness.ready, true, JSON.stringify(readiness));
  pass('all-required-controlled-reviews-read-back-and-open-final');
  const key = crypto.randomUUID();
  const body = {expectedRevisionToken: readiness.observation.revisionToken, optionalReviewIds: []};
  const confirmed = await call(owner, 'final-confirmations', body, 'POST', key);
  assert.equal(confirmed.status, 201, JSON.stringify(confirmed.value));
  const replay = await call(owner, 'final-confirmations', body, 'POST', key);
  assert.equal(replay.status, 200);
  assert.equal(replay.value.replayed, true);
  const finalOwner = await get(owner, 'final-confirmations/current');
  const finalMember = await get(member, 'final-confirmations/current');
  assert.deepEqual(finalMember, finalOwner);
  pass('final-confirmation-immutable-replay-and-member-read');
  await owner.goto(`${base}/meeting/${meeting}/confirm`);
  for (const [width, height] of [[1440, 900], [1024, 768]]) {
    await owner.setViewportSize({width, height});
    await owner.screenshot({path: `${output}/confirmation-${width}.png`, fullPage: true});
  }
  assert.deepEqual(errors, []);
  await writeFile(`${output}/report.json`, JSON.stringify({pass: true, base, meeting, syntheticReviewDecisions: true, checks, requests, errors}, null, 2));
} catch (error) {
  if (owner) {
    await writeFile(`${output}/failure-page.txt`, `${owner.url()}\n${await owner.locator('body').innerText()}`);
    await owner.screenshot({path: `${output}/failure-page.png`, fullPage: true});
  }
  await writeFile(`${output}/report.json`, JSON.stringify({pass: false, base, meeting, checks, requests, errors, error: String(error)}, null, 2));
  throw error;
} finally { await browser.close(); }
