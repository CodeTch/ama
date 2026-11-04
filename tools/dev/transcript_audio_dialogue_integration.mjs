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
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles(resolve('test-data/meeting-main-files-2026-11-02/files/01_audio/meeting.zh-CN.aac'));
 await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();await dialog.getByRole('button',{name:'开始处理',exact:true}).click();
 await expect(page.locator('.transcript-section')).toBeVisible({timeout:90000});await expect(page.locator('.transcript-section .s-text')).toHaveCount(2);check('audio-processing-without-reload');
 const text=await page.locator('.transcript-section').innerText();assert(text.includes('林晨：'));assert(text.includes('陈宇：'));assert(!text.includes('大家好，今天我们开始开会。'));check('exact-source-harness-dialogue-response');
 const root=`/api/v1/meetings/${meeting}/transcript`;
 await expect(page.locator('audio')).toHaveAttribute('src',/^blob:/);await page.getByRole('button',{name:'播放或暂停录音',exact:true}).click();await expect.poll(()=>page.locator('audio').evaluate(a=>a.currentTime)).toBeGreaterThan(0);await page.getByRole('button',{name:'播放或暂停录音',exact:true}).click();check('original-audio-playback');
 await page.getByRole('button',{name:'定位原录音第 2 段',exact:true}).click();await expect.poll(()=>page.locator('audio').evaluate(a=>a.currentTime)).toBeGreaterThan(119);check('audio-seek');
 await page.getByRole('button',{name:'快进 10 秒',exact:true}).click();
 await expect.poll(()=>page.locator('audio').evaluate(a=>a.currentTime)).toBeGreaterThan(129);
 await page.getByRole('button',{name:'后退 10 秒',exact:true}).click();
 await expect.poll(()=>page.locator('audio').evaluate(a=>a.currentTime)).toBeLessThan(129);
 await expect(page.getByRole('slider',{name:'播放进度'})).toBeEnabled();
 check('playback-skip-and-seek-slider');
 await page.getByRole('slider',{name:'播放进度'}).fill('5');
 await expect.poll(()=>page.locator('audio').evaluate(a=>a.currentTime)).toBeLessThan(6);
 await expect(page.locator('.p-wave i')).toHaveCount(64);
 await page.getByRole('button',{name:'播放速度'}).click();
 await expect.poll(()=>page.locator('audio').evaluate(a=>a.playbackRate)).toBe(1.25);
 check('real-waveform-slider-and-speed');

 await page.reload();await expect(page.locator('.s-text')).toHaveCount(2);check('raw-readback-after-refresh');
 for(const width of [1440,1024]){await page.setViewportSize({width,height:width===1440?900:768});await page.screenshot({path:`${out}/${width}.png`,fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}check('two-desktop-viewports');
 const foreign=await browser.newContext(),other=await foreign.newPage();await login(other,'dev-viewer','dev-password');assert.equal((await get(root,other)).status,403);await foreign.close();check('foreign-user-denied');assert.deepEqual(errors,[]);
 await writeFile(out+'/report.json',JSON.stringify({pass:true,simulatedAsr:true,meeting,checks,errors},null,2));
}catch(e){await writeFile(out+'/failure.json',JSON.stringify({pass:false,meeting,checks,errors,error:String(e)},null,2));await page.screenshot({path:out+'/failure.png',fullPage:true});throw e;}finally{await browser.close();}
