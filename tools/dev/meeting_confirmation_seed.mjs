import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:45000});
const out=resolve(process.argv[2]??'.tools/local-app/meeting-confirmation-e2e');
await mkdir(out,{recursive:true});
const owner=process.env.AMA_CONFIRM_OWNER??'dev-owner',memberName=process.env.AMA_CONFIRM_MEMBER_NAME??'开发参会人',recovery=process.env.AMA_CONFIRM_SEED_KIND==='recovery';
const base='http://127.0.0.1:5174',browser=await chromium.launch(),page=await browser.newPage({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'});
const errors=[],requests=[],checks=[];let meetingId=process.env.AMA_CONFIRM_MEETING_ID,report;
await page.context().routeWebSocket(/ws:\/\/127\.0\.0\.1:5174/,()=>{});
page.setDefaultTimeout(30000);page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))requests.push({path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
const api=async path=>page.evaluate(async path=>{const r=await fetch(path);return{status:r.status,value:await r.json()};},path);
async function checkpoint(name){checks.push(name);console.log('PASS',name);await writeFile(resolve(out,'seed-state.json'),JSON.stringify({meetingId,checks},null,2));}
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill(owner);await page.locator('#login-password').fill('dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 if(!meetingId){
  await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
  const created=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');
  await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await created).status(),201);
  await page.waitForURL(/\?import=[0-9a-f-]{36}&fresh=1$/);meetingId=new URL(page.url()).searchParams.get('import');await checkpoint('UI-created-independent-confirmation-meeting');
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();
  await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'会议确认实操-'+Date.now()+'.txt',mimeType:'text/plain',buffer:Buffer.from(recovery?'会议确认修复实操专用会议。讨论系统接入方案和界面核对步骤，尚未形成结论与行动。':'会议确认修复实操专用会议。\n会议决定：先验证来源，再完成最终确认和发布。\n行动：开发参会人负责完成验收报告。\n行动：会议负责人负责归档，截止日期可在核对后填写。\n背景材料仅供参考，不能代替会议结论。\n待确认：外部机构提供后续资料的时间。\n分歧：双方对排期仍未达成一致。\n包含中文、emoji 😀、长文本和空值场景。'.replaceAll('开发参会人',memberName))});
  await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();
  const imported=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith(`/meetings/${meetingId}/transcript-imports`));
  await dialog.getByRole('button',{name:'开始处理',exact:true}).click();assert.equal((await imported).status(),202);await page.waitForURL('**/processing');
  await expect.poll(async()=>(await api(`/api/v1/meetings/${meetingId}/tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20`)).value.items?.[0]?.state,{timeout:120000,intervals:[1000,2000]}).toBe('SUCCEEDED');await checkpoint('UI-imported-TXT-through-real-worker');
 }
 const root=`/api/v1/meetings/${meetingId}`;
 let current=(await api(root+'/transcript')).value;
 if(!current.confirmation?.isCurrent&&!current.saved?.confirmation?.isCurrent){
  await page.goto(`${base}/meeting/${meetingId}/confirm`);
  await expect(page.getByRole('button',{name:'会议确认',exact:true})).toBeDisabled();await checkpoint('direct-confirm-route-cannot-bypass-saved-minutes-gate');
  await page.goto(`${base}/meeting/${meetingId}/transcript`);
  await expect(page.locator('.transcript-section')).toContainText('会议确认修复实操专用会议');
  const create=page.getByRole('button',{name:'建立人工工作版本',exact:true});if(await create.isVisible())await create.click();
  await expect(page.getByRole('button',{name:'确认转写并生成纪要',exact:true})).toBeEnabled();await page.getByRole('button',{name:'确认转写并生成纪要',exact:true}).click();
  await page.getByRole('dialog',{name:'确认转写并生成纪要',exact:true}).getByRole('button',{name:'确认并生成',exact:true}).click();await page.waitForURL('**/minutes');
 }
 await page.goto(`${base}/meeting/${meetingId}/minutes`);
 await expect.poll(async()=>{const r=await api(root+'/minutes');return r.status===200&&!!r.value.body;},{timeout:120000,intervals:[1500,2500]}).toBe(true);await checkpoint('real-confirmed-transcript-default-template-generation');
 await page.goto(`${base}/meeting/${meetingId}/confirm`);await expect(page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
 await expect(page.locator('[data-check]')).toHaveCount(12);await checkpoint('actual-confirmation-form-and-twelve-checks-mounted');
 const saved=(await api(root)).value,suggestions=(await api(root+'/info-suggestions')).value,readiness=(await api(root+'/final-confirmations/readiness')).value;
 assert.equal(saved.confirmedRosterId,null);assert.equal(saved.occurredOn,null);assert.equal(saved.category,null);assert.equal(suggestions.items.length,3);assert(suggestions.items.some(s=>s.field==='CATEGORY'));
 assert(recovery?readiness.required.length===0:readiness.required.length>0);assert.deepEqual(errors,[]);await checkpoint('incomplete-information-enters-confirmation-without-auto-writing-fields-or-roster');
 report={pass:true,meetingId,checks,errors,requests,saved,suggestions,readiness,syntheticModel:true};
}catch(error){report={pass:false,meetingId,checks,errors,requests,error:String(error.stack??error)};process.exitCode=1;await writeFile(resolve(out,'seed-failure.txt'),await page.locator('body').innerText());}
finally{await page.screenshot({path:resolve(out,'seed.png'),fullPage:true});await writeFile(resolve(out,'seed-report.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify({pass:report.pass,meetingId,error:report.error,checks}));}
