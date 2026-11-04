import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
const require=createRequire(resolve('web/package.json'));
const {chromium}=require('@playwright/test');
const expect=require('@playwright/test').expect.configure({timeout:45000});
const output=resolve(process.argv[2]??'.tools/local-app/meeting-confirmation-guard-probe');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN'});
const page=await context.newPage();page.setDefaultTimeout(15000);
const writes=[],errors=[],requests=[];let report;
const pending=new WeakMap();page.on('request',r=>pending.set(r,Date.now()));page.on('response',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push({path:new URL(r.url()).pathname,status:r.status(),ms:Date.now()-(pending.get(r.request())??Date.now())});});page.on('requestfailed',r=>requests.push({path:new URL(r.url()).pathname,failure:r.failure(),ms:Date.now()-(pending.get(r)??Date.now())}));
try{
 await page.goto('http://127.0.0.1:5174/login');
 await page.locator('#login-username').fill('dev-owner');await page.locator('#login-password').fill('dev-password');
 await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 await page.route('**/api/v1/**',async route=>{if(!['GET','HEAD','OPTIONS'].includes(route.request().method())){writes.push(new URL(route.request().url()).pathname);await route.abort();}else await route.continue();});
 page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:5174/meeting/f5824b8f-c499-43ff-8f4b-8526b7f6edea/confirm');
 const name=page.getByRole('textbox',{name:'会议名称',exact:true});await expect(name).toBeEditable();
 const original=await name.inputValue();await name.fill(original+' · 离开保护验证');
 await page.getByRole('button',{name:'返回纪要编辑',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'会议信息有未保存修改',exact:true});await expect(dialog).toBeVisible({timeout:5000});
 await dialog.getByRole('button',{name:'继续编辑',exact:true}).click();await expect(name).toHaveValue(original+' · 离开保护验证');
 await page.getByRole('button',{name:'返回纪要编辑',exact:true}).click();await dialog.getByRole('button',{name:'放弃修改',exact:true}).click();
 await page.waitForURL('**/minutes');await page.getByRole('button',{name:'会议确认',exact:true}).click();await expect(name).toHaveValue(original);
 await expect(page.getByLabel('会议日期',{exact:true})).not.toHaveAttribute('aria-required','true');
 await expect(page.getByText('暂未添加参会人',{exact:true})).toBeVisible();
 expect(writes).toEqual([]);expect(errors).toEqual([]);report={passed:true,writes,errors,requests};
}catch(error){report={passed:false,writes,errors,requests,error:String(error.stack??error)};process.exitCode=1;}
finally{await page.screenshot({path:resolve(output,'page.png'),fullPage:true});await writeFile(resolve(output,'report.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report,null,2));}
