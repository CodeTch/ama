/** Read-only diagnostic; never submits reviews, information, confirmation, or publication. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect:originalExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=originalExpect.configure({timeout:30000}),out=resolve(process.argv[2]);await mkdir(out,{recursive:true});
const meeting='035f788a-51d7-4e5b-944f-41711cff3877',root='/api/v1/meetings/'+meeting,base='http://127.0.0.1:5174';
const browser=await chromium.launch(),context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage();
await context.routeWebSocket(/ws:\/\/127\.0\.0\.1:5174/,()=>{});
const requests=[],writes=[],errors=[],starts=new WeakMap();let report;
page.on('pageerror',error=>errors.push(error.message));
page.on('request',r=>starts.set(r,performance.now()));
page.on('response',r=>{if(r.url().includes(root))requests.push({path:new URL(r.url()).pathname,method:r.request().method(),status:r.status(),ms:Math.round(performance.now()-starts.get(r.request()))});});
page.on('requestfailed',r=>{if(r.url().includes(root))requests.push({path:new URL(r.url()).pathname,error:r.failure(),ms:Math.round(performance.now()-starts.get(r))});});
await page.route('**'+root+'/**',async route=>{if(!['GET','HEAD','OPTIONS'].includes(route.request().method())){writes.push(route.request().url());await route.abort('blockedbyclient');}else await route.continue();});
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill('confirmation-owner-20261103');await page.locator('#login-password').fill('dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 await page.goto(base+'/meeting/'+meeting+'/confirm');await expect(page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
 const checks=page.locator('.ac-confirm-check');await expect(checks.locator('[data-check]')).toHaveCount(12);if(!await checks.evaluate(e=>e.open))await checks.locator('summary').click();
 await expect(checks.getByRole('button',{name:'重新检查',exact:true})).toBeEnabled();await checks.locator('[data-check="conclusions"] .final-check-action').first().getByRole('button',{name:/去处理/}).click();
 await page.waitForURL('**/minutes');const dialog=page.getByRole('dialog',{name:'来源与审阅',exact:true});await expect(dialog).toBeVisible();await expect(dialog.locator('.source-original-quote')).not.toBeEmpty();
 const source=await dialog.locator('.source-original-quote').innerText();await page.waitForTimeout(16000);await expect(dialog).toBeVisible();await expect(dialog.locator('.source-original-quote')).toHaveText(source);assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);report={pass:true,meeting,requests,errors,writes};
}catch(error){report={pass:false,meeting,error:String(error.stack??error),requests,errors,writes};process.exitCode=1;await writeFile(resolve(out,'failure.txt'),await page.locator('body').innerText());await page.screenshot({path:resolve(out,'failure.png'),fullPage:true});}
finally{await writeFile(resolve(out,'report.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify({pass:report.pass,error:report.error,requests:requests.length}));}
