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
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles(resolve('test-data/meeting-main-files-2026-11-02/files/02_text/meeting.zh-CN.txt'));
 await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();await dialog.getByRole('button',{name:'开始处理',exact:true}).click();
 await expect(page.locator('.transcript-section')).toBeVisible({timeout:90000});await expect(page.locator('.transcript-section .s-text')).toHaveCount(10);check('upload-processing-to-dialogue-without-reload');
 await expect(page.getByText('说话人（4）',{exact:true})).toBeVisible();await expect(page.getByText('文件标签',{exact:true})).toHaveCount(4);check('four-file-labels-without-enterprise-identity');
 await expect(page.locator('.s-time').first()).toHaveText('--:--');await expect(page.getByText('本次为文本转写文件，无原录音，不虚构播放时长。',{exact:true})).toBeVisible();check('text-without-invented-media-or-timestamp');
 const root=`/api/v1/meetings/${meeting}/transcript`,history=await get(root+'/history?limit=20'),rawId=history.body.items.find(x=>x.kind==='RAW').id,raw=await get(root+'/revisions/'+rawId);
 await page.getByLabel('搜索转写',{exact:true}).fill('格式兼容性报告');await expect(page.locator('.s-text')).toHaveCount(1);await page.getByLabel('搜索转写',{exact:true}).fill('');check('search-and-clear');
 await page.getByRole('button',{name:'建立人工工作版本',exact:true}).click();await expect(page.getByRole('button',{name:'编辑第 1 段',exact:true})).toBeVisible();check('explicit-working-version');
 await page.getByRole('button',{name:'编辑第 1 段',exact:true}).click();const edit=page.getByRole('dialog',{name:'编辑转写片段'});await expect(edit).toBeVisible();await edit.locator('textarea').fill(raw.body.selected.segments[0].body+' 已核对。');await edit.getByRole('button',{name:'保存',exact:true}).click();await expect(edit).not.toBeVisible();await expect(page.locator('.s-text').first()).toContainText('已核对。');
 await page.reload();await expect(page.locator('.s-text').first()).toContainText('已核对。');assert.deepEqual((await get(root+'/revisions/'+rawId)).body.selected.segments,raw.body.selected.segments);check('edit-save-reload-retains-original-raw');
 for(const width of [1440,1024]){await page.setViewportSize({width,height:width===1440?900:768});await page.screenshot({path:`${out}/${width}.png`,fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}check('two-desktop-viewports');
 const foreign=await browser.newContext(),other=await foreign.newPage();await login(other,'dev-viewer','dev-password');assert.equal((await get(root,other)).status,403);await foreign.close();check('foreign-user-denied');assert.deepEqual(errors,[]);
 await writeFile(out+'/report.json',JSON.stringify({pass:true,meeting,checks,errors},null,2));
}catch(e){await writeFile(out+'/failure.json',JSON.stringify({pass:false,meeting,checks,errors,error:String(e)},null,2));await page.screenshot({path:out+'/failure.png',fullPage:true});throw e;}finally{await browser.close();}
