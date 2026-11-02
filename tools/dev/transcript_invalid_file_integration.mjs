import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {resolve} from 'node:path';import {mkdir,writeFile,readFile} from 'node:fs/promises';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000});
const out=process.argv[2];assert(out?.startsWith('.tools/local-app/dialogue-'));await mkdir(out,{recursive:false});
const browser=await chromium.launch(),context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage();page.setDefaultTimeout(30000);
const checks=[],errors=[];let meeting;page.on('pageerror',e=>errors.push(e.message));
const check=name=>{checks.push(name);console.log('PASS',name);};
async function login(p,user,password){await p.goto('http://127.0.0.1:5174/login');await p.locator('#login-username').fill(user);await p.locator('#login-password').fill(password);await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL('**/meetings');}
async function get(path,p=page){return p.evaluate(async path=>{const r=await fetch(path);return {status:r.status,body:await r.json()};},path);}
try{
 await login(page,'1','1');await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
 await page.getByRole('button',{name:'导入会议',exact:true}).click();await page.waitForURL(/import=[0-9a-f-]+/);meeting=new URL(page.url()).searchParams.get('import');
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles(resolve('test-data/meeting-main-files-2026-11-02/files/05_negative/invalid_utf8.txt'));
 await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();await dialog.getByRole('button',{name:'开始处理',exact:true}).click();
 await expect.poll(async()=>{const r=await get(`/api/v1/meetings/${meeting}/tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20`);return r.body.items?.[0]?.state;},{timeout:90000}).toBe('FAILED');check('invalid-utf8-task-failed-explicitly');
 const root=`/api/v1/meetings/${meeting}/transcript`;const current=await get(root),history=await get(root+'/history?limit=20');assert.equal(current.body.saved,null);assert.equal(history.body.items.length,0);check('no-fabricated-transcript-after-invalid-file');await expect(page.getByText('处理失败',{exact:true}).first()).toBeVisible();check('visible-processing-failure');
 const foreign=await browser.newContext(),other=await foreign.newPage();await login(other,'dev-viewer','dev-password');assert.equal((await get(root,other)).status,403);await foreign.close();check('foreign-user-denied');assert.deepEqual(errors,[]);
 await writeFile(out+'/report.json',JSON.stringify({pass:true,meeting,checks,errors},null,2));
}catch(e){await writeFile(out+'/failure.json',JSON.stringify({pass:false,meeting,checks,errors,error:String(e)},null,2));await page.screenshot({path:out+'/failure.png',fullPage:true});throw e;}finally{await browser.close();}
