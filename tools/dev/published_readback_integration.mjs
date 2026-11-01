/** Read-only regression against an existing, genuinely published local meeting.
 * Run from the project root: node tools/dev/published_readback_integration.mjs OUTPUT MEETING_UUID
 * OUTPUT must be a new directory under .tools/local-app/d08-page-matrix/.
 */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000});
const [output,meetingId]=process.argv.slice(2);
assert(output?.startsWith('.tools/local-app/d08-page-matrix/')&&!output.split('/').includes('..'));
assert.match(meetingId??'',/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
await mkdir(output,{recursive:false,mode:0o700});
await writeFile(output+'/executed-script.mjs',await readFile(new URL(import.meta.url)));
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1';
const state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
assert.equal(state.integrationOrigins.cas,'http://127.0.0.1:8792');
const actor=process.env.AMA_READBACK_ACTOR??'dev-owner';assert(['dev-owner','dev-member'].includes(actor));
const base=`http://127.0.0.1:${state.ports.web}`,root=`/api/v1/meetings/${meetingId}`;
const source=()=>execFileSync('python3',['-c','import sys;sys.path.insert(0,"tools/dev");import local_app;print(local_app.source_signature())'],{encoding:'utf8'}).trim();
const signature=source();assert.equal(state.sourceSignature,signature,'Deploy current source before qualifying');
const browser=await chromium.launch(),context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai'}),page=await context.newPage();
const errors=[],writes=[],checks=[],captures=[];
page.on('pageerror',e=>errors.push(e.message));
page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/v1/')&&!['GET','HEAD','OPTIONS'].includes(r.method()))writes.push({method:r.method(),path:new URL(r.url()).pathname});});
async function current(){return page.evaluate(async root=>{const response=await fetch(root+'/minutes');if(!response.ok)throw Error('Current minutes '+response.status);return response.json();},root);}
async function capture(label,width){
 await page.evaluate(()=>document.fonts.ready);
 const geometry=await page.evaluate(()=>({viewport:innerWidth,documentWidth:document.documentElement.scrollWidth}));
 assert(geometry.documentWidth<=geometry.viewport,'Published page must fit the viewport');
 captures.push({label,width,...geometry});await page.screenshot({path:`${output}/${label}-${width}.png`,animations:'disabled'});
}
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/meetings');
 const before=await current();assert.equal(before.observation.readOnlyReason,'PUBLISHED');assert(before.body&&before.setId);
 await page.goto(base+`/meeting/${meetingId}/published`);
 await expect(page.locator('.minutes .core-content')).toBeVisible();
 for(const [width,height] of [[1440,900],[1024,768],[390,844]]){
  await page.setViewportSize({width,height});
  const tabs=page.getByRole('navigation',{name:'纪要内容',exact:true});await expect(tabs).toBeVisible();
  await tabs.getByRole('button',{name:'核心要素',exact:true}).click();await expect(page.locator('.minutes .core-content')).toBeVisible();
  await expect(page.locator('.published-transcript .m19-audio-heading')).toBeVisible();
  await expect(page.locator('.minutes').getByRole('button',{name:'局部重新生成',exact:true})).toHaveCount(0);
  await expect(page.locator('.minutes').getByRole('button',{name:'编辑要素',exact:true})).toHaveCount(0);
  const background=page.getByRole('button',{name:'背景材料',exact:true});await expect(background).toBeVisible();await background.click();const materials=page.getByRole('dialog',{name:'背景材料',exact:true});await expect(materials).toBeVisible();await expect(materials).toContainText('暂无背景材料');await capture('published-materials',width);await materials.getByRole('button',{name:'关闭背景材料',exact:true}).click();await expect(materials).not.toBeVisible();
  if(actor==='dev-owner'){const paragraph=page.locator('.minutes .core-content p[role=button]').first();await expect(paragraph).toBeEnabled();const original=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname.startsWith(root+'/minutes/sources/'));await paragraph.click();assert.equal((await original).status(),200,'Published source inspection must read the authorized original');const sourceDialog=page.getByRole('dialog',{name:'来源与审阅',exact:true});await expect(sourceDialog).toBeVisible();await expect(sourceDialog.locator('article pre')).not.toBeEmpty();await expect(sourceDialog.getByRole('link',{name:'读取原始文件',exact:true})).toBeVisible();await expect(sourceDialog.getByRole('button',{name:'提交全文审阅',exact:true})).toHaveCount(0);await expect(sourceDialog.getByRole('combobox')).toHaveCount(0);const history=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname===root+'/minutes/reviews');await sourceDialog.getByRole('button',{name:'读取审阅记录',exact:true}).click();const historyResponse=await history;assert.equal(historyResponse.status(),200);const acceptedReviews=await page.evaluate(async url=>{const r=await fetch(url);if(!r.ok)throw Error('Review read '+r.status);return r.json();},historyResponse.url());if(acceptedReviews.items.length){for(const review of acceptedReviews.items)await expect(sourceDialog).toContainText(review.review_kind);}else await expect(sourceDialog).toContainText('暂无审阅记录');await sourceDialog.getByRole('button',{name:'关闭',exact:true}).click();}
  await page.evaluate(()=>window.scrollTo(0,0));await capture('published-core',width);
  await tabs.getByRole('button',{name:/^(会议纪要|AI纪要)$/,exact:true}).click();await expect(page.locator('.minutes-body>pre')).toHaveText(before.body.body);
  await expect(page.locator('.minutes').getByRole('button',{name:'编辑正文',exact:true})).toHaveCount(0);
  const openExport=page.locator('.minutes-body').getByRole('button',{name:/^(⇩ 导出|下载纪要)$/,exact:true});await expect(openExport).toBeVisible();await openExport.click();
  const dialog=page.getByRole('dialog',{name:'导出会议纪要',exact:true});await expect(dialog).toBeVisible();
  assert.deepEqual(await dialog.getByRole('combobox').locator('option').evaluateAll(options=>options.map(o=>o.value)),['md','pdf','docx']);
  await capture('published-export',width);await dialog.getByRole('button',{name:'关闭',exact:true}).click();await expect(dialog).not.toBeVisible();
  checks.push({width,coreAndBody:true,sourceMediaVisible:true,readOnlyMaterials:true,currentSourceInspection:actor==='dev-owner',threeExportFormatsVisible:true,writeControlsAbsent:true});
 }
 await delay(6000);await expect(page.locator('.minutes-body>pre')).toHaveText(before.body.body);
 assert.deepEqual((await current()).body,before.body);assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);assert.equal(source(),signature);
 await writeFile(output+'/report.json',JSON.stringify({passed:true,signature,actor,meetingId,checks,captures,writes,errors,scope:'READ_ONLY_PUBLISHED_UI_NO_EXPORT_GENERATION_OR_MODEL_ACCURACY_CLAIM'},null,2));
 console.log(JSON.stringify({passed:true,meetingId,viewports:checks.length,businessWrites:writes.length}));
}catch(error){await page.screenshot({path:output+'/failure.png'});await writeFile(output+'/failure-page.txt',await page.locator('body').innerText());await writeFile(output+'/report.json',JSON.stringify({passed:false,signature,meetingId,checks,captures,writes,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}
