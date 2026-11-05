import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const out=process.argv[2],base='http://127.0.0.1:5174',browser=await chromium.launch(),page=await browser.newPage(),checks=[],errors=[];
await mkdir(out,{recursive:true});page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(45000);
let meeting;
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill('1');await page.locator('#login-password').fill('1');await page.locator('button[type=submit]').click();await page.waitForURL('**/meetings');
 await page.getByRole('button',{name:'导入会议',exact:true}).click();await page.waitForURL(/\?import=/);meeting=new URL(page.url()).searchParams.get('import');
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true}),input=dialog.getByLabel('选择会议文件',{exact:true});
 const names=['第一段-'+meeting.slice(0,8)+'.txt','第二段-'+meeting.slice(0,8)+'.txt'];
 await expect(input).toBeEnabled();
 for(const name of names){await input.setInputFiles({name,mimeType:'text/plain',buffer:Buffer.from('会议正文 '+name)});await expect(dialog.getByRole('button',{name:'保存并返回列表',exact:true})).toBeEnabled({timeout:30000});}
 await dialog.getByRole('button',{name:'上移'+names[1],exact:true}).click();await dialog.getByRole('button',{name:'保存并返回列表',exact:true}).click();await expect(dialog).not.toBeVisible();
 await page.reload();const row=page.getByRole('row').filter({hasText:meeting});await expect(row).toContainText(names[1],{timeout:45000});checks.push('reordered saved draft is titled by its first selected file after list reload');
 await page.goto(base+'/meeting/'+meeting+'/processing');await expect(page.locator('h1')).toHaveText(names[1],{timeout:30000});
 const saved=await page.evaluate(async id=>{const r=await fetch('/api/v1/meetings/'+id);if(!r.ok)throw Error(String(r.status));return r.json();},meeting);assert(!saved.name);checks.push('detail shares the title without persisting a confirmed meeting name');assert.deepEqual(errors,[]);
}catch(e){console.error(e);await writeFile(resolve(out,'failure.txt'),await page.locator('body').innerText().catch(()=>''));process.exitCode=1;}
finally{await writeFile(resolve(out,'report.json'),JSON.stringify({pass:!process.exitCode,meeting,checks,errors},null,2));await browser.close();}
