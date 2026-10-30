import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {resolve} from 'node:path';import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');const base='http://127.0.0.1:5173',output='.tools/local-app/minutes-smoke';await mkdir(output,{recursive:true});
const {meetingId}=JSON.parse(await readFile(output+'/report.json','utf8'));const browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage(),errors=[];page.setDefaultTimeout(12000);page.on('pageerror',e=>errors.push(e.message));
const current=()=>page.evaluate(async id=>(await (await fetch(`/api/v1/meetings/${id}/minutes`)).json()),meetingId);
async function fresh(){await page.locator('.minutes').getByRole('button',{name:'刷新纪要',exact:true}).click();await page.locator('.minutes').getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.minutes').getByRole('button',{name:'编辑正文',exact:true})).toBeEnabled({timeout:15000});}
function element(text){return page.locator('.minutes article').filter({has:page.getByText(text,{exact:true})});}

try{
 await page.goto(base+'/api/dev/login');await page.getByRole('button',{name:'登录本地环境'}).click();await page.waitForURL(base+'/meetings');await page.goto(base+'/meeting/'+meetingId);await page.getByRole('button',{name:'纪要与审阅',exact:true}).click();await fresh();
 await page.locator('.minutes').getByRole('button',{name:'编辑正文',exact:true}).click();let editor=page.locator('.minutes .editor');await editor.getByRole('button',{name:'开始编辑',exact:true}).click();await expect(editor.locator('textarea')).toBeEditable();
 await editor.locator('input').fill('演示纪要 · 人工核对');await editor.locator('textarea').fill('😀 Controlled rich body.\n人工核对：保留来源关联，并检查行动项。\n核对批次：'+new Date().toISOString());
 const savedBody=page.waitForResponse(r=>r.url().includes(`/meetings/${meetingId}/minutes/`)&&r.request().method()==='PUT');await editor.getByRole('button',{name:'保存',exact:true}).click();assert.equal((await savedBody).status(),200);await fresh();assert.equal((await current()).body.title,'演示纪要 · 人工核对');console.log('saved-body');
 for(const number of [1,2]){
  await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();const text='😀 Controlled ACTION_'+number;const existing=(await current()).elements.ACTION.find(x=>x.body===text);if(existing.action.owner_display==='开发参会人'&&existing.action.due_on==='2026-11-01')continue;await element(text).getByRole('button',{name:'来源与审阅',exact:true}).click();await page.locator('.inspection').getByRole('button',{name:'编辑负责人和截止日期',exact:true}).click();editor=page.locator('.minutes .editor');await editor.getByRole('button',{name:'开始编辑',exact:true}).click();await expect(editor.locator('input').first()).toBeEditable();await editor.locator('input').first().fill('开发参会人');await editor.locator('input[type=date]').fill('2026-11-01');
  const savedAction=page.waitForResponse(r=>r.url().includes(`/meetings/${meetingId}/minutes/`)&&r.request().method()==='PUT');await editor.getByRole('button',{name:'保存',exact:true}).click();assert.equal((await savedAction).status(),200);await fresh();
 }
 console.log('saved-action-fields');
 // Each decision is submitted through the real UI after examining current source text.
 const targets=['BODY','😀 Controlled CONCLUSION_1','😀 Controlled ACTION_1','😀 Controlled ACTION_2','😀 Controlled OPEN_QUESTION_1'];
 for(const target of targets){
  if(target==='BODY')await page.locator('.minutes').getByRole('button',{name:'查看正文来源与审阅',exact:true}).click();else{await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();await element(target).getByRole('button',{name:'来源与审阅',exact:true}).click();}
  const inspection=page.locator('.inspection');await inspection.getByRole('button',{name:'查看原始段落',exact:true}).first().click();await expect(inspection).toContainText('第一份开场');
  await inspection.locator('select').selectOption('PARTIAL');const reviewed=page.waitForResponse(r=>r.url().includes(`/meetings/${meetingId}/minutes/reviews`)&&r.request().method()==='POST');await inspection.getByRole('button',{name:'提交全文审阅',exact:true}).click();assert.equal((await reviewed).status(),200);await fresh();
  if(target.includes('ACTION_')){await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();await element(target).getByRole('button',{name:'来源与审阅',exact:true}).click();const fieldsReview=page.waitForResponse(r=>r.url().includes(`/meetings/${meetingId}/minutes/reviews`)&&r.request().method()==='POST');await page.locator('.inspection').getByRole('button',{name:'确认已保存的负责人和日期',exact:true}).click();assert.equal((await fieldsReview).status(),200);await fresh();}
  console.log('reviewed',target);
 }
 await page.locator('.minutes').getByRole('button',{name:'最终确认与发布',exact:true}).click();await page.locator('.publication').getByRole('button',{name:'刷新最终状态',exact:true}).click();const readiness=await page.evaluate(async id=>(await (await fetch(`/api/v1/meetings/${id}/final-confirmations/readiness`)).json()),meetingId);console.log('readiness',JSON.stringify(readiness));
 assert.equal(readiness.ready,false);assert.deepEqual(readiness.blockers,['REVIEW_REQUIRED']);assert(readiness.required.some(r=>r.outcome==='PARTIAL'&&!r.satisfied));
 // Scripted positive decisions validate this synthetic fixture's protocol and gating only.
 // They are not evidence of model accuracy or a human's semantic source assessment.
 for(const target of ['😀 Controlled CONCLUSION_1','😀 Controlled ACTION_1','😀 Controlled ACTION_2']){
  await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();await element(target).getByRole('button',{name:'来源与审阅',exact:true}).click();const inspection=page.locator('.inspection');await inspection.locator('select').selectOption('SUPPORTED');
  const reviewed=page.waitForResponse(r=>r.url().includes(`/meetings/${meetingId}/minutes/reviews`)&&r.request().method()==='POST');await inspection.getByRole('button',{name:'提交全文审阅',exact:true}).click();assert.equal((await reviewed).status(),200);await fresh();
 }
 const positiveReadiness=await page.evaluate(async id=>(await (await fetch(`/api/v1/meetings/${id}/final-confirmations/readiness`)).json()),meetingId);assert.equal(positiveReadiness.ready,true);assert.deepEqual(positiveReadiness.blockers,[]);console.log('positive-readiness-true');
 const saved=await current();assert.equal(saved.body.title,'演示纪要 · 人工核对');assert.equal(saved.elements.ACTION.length,2);for(const a of saved.elements.ACTION){assert.equal(a.action.owner_display,'开发参会人');assert.equal(a.action.due_on,'2026-11-01');}
 assert.deepEqual(errors,[]);await page.reload();await page.getByRole('button',{name:'纪要与审阅',exact:true}).click();await expect(page.locator('.minutes')).toContainText('演示纪要 · 人工核对');
 await writeFile(output+'/review-report.json',JSON.stringify({pass:true,meetingId,partialReadiness:readiness,readiness:positiveReadiness,syntheticReviewDecisions:true,saved,checks:['saved-body-title-and-text','two-action-fields','source-drill-down','whole-source-review','action-fields-review','partial-review-blocks-final','supported-controlled-review-opens-final','reload-persistence'],errors},null,2));await page.screenshot({path:output+'/reviewed-1440.png',fullPage:true});console.log('PASS saved/edit/review');
}catch(e){await writeFile(output+'/review-failure.txt',await page.locator('body').innerText());await page.screenshot({path:output+'/review-failure.png',fullPage:true});console.error('PAGE ERRORS',errors);throw e;}finally{await browser.close();}
