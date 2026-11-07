/** Final restarted-build readback, including independent source authorization. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const out=resolve(process.argv[2]??'.tools/knowledge-publish-verification-20261107/readback');await mkdir(out,{recursive:true});
const state=JSON.parse(await readFile(resolve(process.env.AMA_KP_E2E_STATE??'.tools/knowledge-publish-verification-20261107/final/state.json'),'utf8'));
const base='http://127.0.0.1:5174',browser=await chromium.launch(),report={startedAt:new Date().toISOString(),checks:[],pageErrors:[]};let page;
async function login(user,password){const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN'});page=await context.newPage();page.on('pageerror',error=>report.pageErrors.push(error.message));await page.goto(base+'/login');await page.locator('#login-username').fill(user);await page.locator('#login-password').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');return page;}
try{
 const publication=state.facts.recoveredPublication,meeting=state.facts.recoveryMeeting;assert(publication?.officialDocumentUrl&&meeting,'The complete real publication suite must pass first');
 const owner=await login('dev-owner','dev-password');const current=await owner.evaluate(async path=>{const response=await fetch(path);return {status:response.status,value:await response.json()};},'/api/v1/meetings/'+meeting+'/final-confirmations/current');assert.equal(current.status,200);const frozen=current.value;
 await owner.goto(base+publication.officialDocumentUrl);await expect(owner.getByRole('heading',{name:frozen.body.title,exact:true})).toBeVisible();await expect(owner.locator('[aria-label="正式纪要正文"]>pre')).toHaveText(frozen.body.body);await expect(owner.locator('aside')).toContainText(frozen.information.summary);
 const category=await owner.locator('aside dt').filter({hasText:'会议分类'}).evaluate(element=>element.nextElementSibling.textContent);assert(category.trim()&&category!==frozen.information.category,'Formal category must use the dictionary label');
 await owner.screenshot({path:out+'/formal-final-1440.png',fullPage:true});report.checks.push('restarted-build-frozen-body-summary-and-category-label');
 const reader=await login('kp-e2e-reader','kp-e2e-pass');await reader.goto(base+publication.officialDocumentUrl);await expect(reader.getByRole('heading',{name:frozen.body.title,exact:true})).toBeVisible();
 const forbidden=reader.waitForResponse(response=>response.url().startsWith(base+'/api/v1/meetings/'+meeting)&&response.status()===403);await reader.getByRole('link',{name:'访问原始证据',exact:true}).click();await forbidden;await expect(reader.locator('main [role="alert"]')).toBeVisible();await expect(reader.getByRole('dialog',{name:'来源与审阅',exact:true})).toHaveCount(0);await expect(reader.locator('.minutes-body')).toHaveCount(0);
 await reader.screenshot({path:out+'/library-reader-source-denied.png',fullPage:true});report.checks.push('library-reading-does-not-grant-meeting-source-access');
 await owner.goto(base+'/meetings');for(const governed of [state.facts.mainPublication,state.facts.unknownPublication]){const status=await owner.evaluate(async path=>(await fetch(path)).status,governed.officialDocumentUrl);assert([403,404].includes(status));}report.checks.push('withdrawn-and-obsolete-direct-document-links-remain-denied');
 assert.deepEqual(report.pageErrors,[]);report.pass=true;
}catch(error){report.pass=false;report.failure=String(error.stack??error);await page?.screenshot({path:out+'/failure.png',fullPage:true}).catch(()=>{});process.exitCode=1;}
finally{await writeFile(out+'/report.json',JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report));}
