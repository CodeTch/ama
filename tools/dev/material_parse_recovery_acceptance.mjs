import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const out=process.argv[2];await mkdir(out,{recursive:true});
const browser=await chromium.launch(),page=await browser.newPage(),checks=[],errors=[];
const base='http://127.0.0.1:5174';let meeting;
page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(60000);
async function read(suffix){return page.evaluate(async url=>{const r=await fetch(url);if(!r.ok)throw Error(String(r.status));return r.json();},'/api/v1/meetings/'+meeting+suffix);}
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill('1');await page.locator('#login-password').fill('1');await page.locator('button[type=submit]').click();await page.waitForURL('**/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();await page.waitForURL(/\?import=/);meeting=new URL(page.url()).searchParams.get('import');
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true}),start=dialog.getByRole('button',{name:'开始处理',exact:true});
 await expect(start).toBeDisabled();await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled({timeout:30000});await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'只保留主文件.txt',mimeType:'text/plain',buffer:Buffer.from('会议记录。请核对交付时间。')});await expect(start).toBeEnabled({timeout:30000});
 await dialog.getByLabel('选择背景材料',{exact:true}).setInputFiles({name:'损坏文档.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:Buffer.from('invalid-office-archive')});
 const retry=dialog.getByRole('button',{name:'重试解析',exact:true});await expect(retry).toBeEnabled({timeout:40000});await expect(start).toBeDisabled();const original=(await read('/materials')).items[0];assert.equal(original.state,'FAILED');checks.push('invalid background blocks start and explains its parse error');
 await retry.click();await expect.poll(async()=>(await read('/materials')).items[0].materialRevisionId,{timeout:40000}).not.toBe(original.materialRevisionId);await expect(start).toBeDisabled();
 await dialog.getByRole('button',{name:'明确不使用'}).click();await expect(start).toBeEnabled({timeout:30000});assert.equal((await read('/materials')).items.length,0);checks.push('retry created a new failed revision, explicit exclusion kept the main file usable');
 const accepted=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/transcript-imports'));await start.click();assert.equal((await accepted).status(),202);
 await expect.poll(async()=>(await read('/workspace')).processingMaterialStage,{timeout:180000}).toBe('SUCCEEDED');checks.push('first processing succeeded with no background or completed meeting information');
 await page.goto(base+'/meeting/'+meeting+'/processing');await expect(page.getByRole('button',{name:'进入转写校正',exact:true})).toBeEnabled({timeout:30000});await page.screenshot({path:resolve(out,'empty.png')});assert.deepEqual(errors,[]);
}catch(e){console.error(e);await writeFile(resolve(out,'failure.txt'),await page.locator('body').innerText().catch(()=>''));process.exitCode=1;}
finally{await writeFile(resolve(out,'report.json'),JSON.stringify({pass:!process.exitCode,meeting,checks,errors},null,2));await browser.close();}
