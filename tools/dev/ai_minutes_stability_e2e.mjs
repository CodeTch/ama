/** Only mutate the dedicated meeting created by the import-review acceptance case. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000});
const input=JSON.parse(await readFile(process.env.AMA_MINUTES_QUALITY_REPORT,'utf8'));
assert(input.pass&&input.checks.some(check=>check.name==='confirmed-import-review-agenda-decision-and-three-sourced-actions'));
const meeting=input.meetingId;
const origin='http://127.0.0.1:5174',root='/api/v1/meetings/'+meeting;
const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT);
const report={meeting,checks:[],errors:[],pass:false};
await mkdir(output,{recursive:true});
const browser=await chromium.launch();
async function login(user){
 const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',reducedMotion:'reduce',permissions:['clipboard-read','clipboard-write']});
 const page=await context.newPage();page.setDefaultTimeout(30000);
 page.on('pageerror',error=>report.errors.push(error.message));
 await page.goto(origin+'/login');await page.locator('#login-username').fill(user);
 await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');
 await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 return page;
}
function pass(name){report.checks.push(name);console.log('PASS '+name);}
try{
 const page=await login('dev-owner'),url=origin+'/meeting/'+meeting+'/minutes';
 const tab=name=>page.locator('.minutes-views').getByRole('button',{name,exact:true});
 const get=path=>page.evaluate(async path=>{const r=await fetch(path);if(!r.ok)throw Error(path+' returned '+r.status);return r.json();},path);
 await page.goto(url);const core=page.locator('.am-section-list'),first=core.locator('[data-element-id]').first();
 await expect(first).toBeVisible();
 await page.route('**/minutes/templates*',r=>r.fulfill({status:503,json:{error:{code:'MINUTES_UNAVAILABLE'}}}),{times:1});
 await tab('会议纪要').click();await page.getByRole('button',{name:'重试模板'}).click();
 await expect(page.locator('#minutes-template')).toBeEnabled();assert(await page.locator('#minutes-template option').allTextContents().then(x=>x.includes('简明总结')));
 pass('transient-template-retry');
 const heading=page.locator('.am-document-heading'),top=(await heading.boundingBox()).y;
 await page.getByRole('button',{name:'复制全文',exact:true}).click();await expect(page.getByText('已复制全文',{exact:true})).toBeVisible();
 assert(Math.abs((await heading.boundingBox()).y-top)<=1);pass('copy-feedback-stable');
 await page.getByRole('button',{name:'编辑正文',exact:true}).click();
 const area=page.getByLabel('会议纪要正文',{exact:true});await expect(area).toBeEditable();
 const prior=await area.inputValue(),suffix='\n稳定性核验 '+Date.now();
 let release,intercepted;
 const reached=new Promise(done=>intercepted=done),held=new Promise(done=>release=done);
 await page.route('**/minutes/body',async route=>{
  intercepted();await held;await route.continue();
 },{times:1});
 await area.fill(prior+suffix);await Promise.race([reached,page.waitForTimeout(15000).then(()=>{throw Error('Save never started');})]);
 try{
  await expect(area).toBeEditable();await area.press('Control+End');
  await page.keyboard.insertText('，继续中文输入');await page.keyboard.type(' XYZ');
  await expect(area).toHaveValue(prior+suffix+'，继续中文输入 XYZ');
 }finally{release();}
 await expect.poll(async()=>(await get(root+'/minutes')).body.body,{timeout:60000}).toBe(prior+suffix+'，继续中文输入 XYZ');
 await expect(page.locator('.document-editor-state')).toContainText('标题与正文编辑中');
 await page.locator('.am-document-actions').getByRole('button',{name:'保存',exact:true}).click();
 await expect(area).toHaveCount(0);await page.reload();await expect(first).toBeVisible();await tab('会议纪要').click();await expect(page.locator('.am-saved-body')).toContainText(suffix.trim()+'，继续中文输入 XYZ');
 pass('native-typing-during-save-persists-followup-generation');
 await page.getByRole('button',{name:'版本对比',exact:true}).click();
 const versions=page.getByRole('region',{name:'完整版本比较与恢复'});
 await versions.getByRole('button',{name:/预览恢复版本/}).first().click();
 await versions.getByRole('button',{name:'查看依据 1',exact:true}).click();
 await expect(versions.locator('.version-restore-preview>p').last()).not.toBeEmpty();
 await expect(versions.getByRole('button',{name:'确认恢复为新版本'})).toBeEnabled();
 await page.keyboard.press('Escape');pass('historical-source-before-restoration');
 await tab('核心要素').click();await page.locator('.am-toc [data-category=ACTION]').click();
 const pos=await core.evaluate(n=>n.scrollTop);
 await page.route('**/minutes/sources/**',r=>r.fulfill({status:403,json:{error:{code:'MEETING_FORBIDDEN'}}}),{times:1});
 await core.locator('#minutes-category-ACTION .am-source-tag').first().click();
 await expect(page.getByText('该来源当前不可访问。已重新核对其余纪要内容的权限。',{exact:false}).first()).toBeVisible();
 assert.equal(await core.evaluate(n=>n.scrollTop),pos);
 const renewed=page.waitForResponse(r=>r.url().endsWith('/workspace/access')&&r.status()===503);
 await page.route('**/workspace/access',r=>r.fulfill({status:503,json:{error:{code:'MEETING_UNAVAILABLE'}}}),{times:1});
 await renewed;await page.waitForResponse(r=>r.url().endsWith('/workspace/access')&&r.status()===200);
 await expect(first).toBeVisible();assert.equal(await core.evaluate(n=>n.scrollTop),pos);
 pass('source-rejection-and-transient-lease-renewal-preserve-reading-position');
 await core.locator('#minutes-category-ACTION .am-source-tag').first().click();const sourceDialog=page.getByRole('dialog',{name:'来源与审阅'});
 await expect(sourceDialog).toBeVisible();
 const accessPath='**/workspace/access';
 await page.route(accessPath,r=>r.fulfill({status:503,json:{error:{code:'MEETING_UNAVAILABLE'}}}));
 await expect(page.locator('.am-layout')).toHaveCount(0,{timeout:9000});
 await expect(sourceDialog).toHaveCount(0);
 await page.unroute(accessPath);await expect(sourceDialog).toBeVisible({timeout:20000});
 await expect(sourceDialog.locator('.source-original-card')).toBeVisible();
 await page.keyboard.press('Escape');
 pass('expired-lease-masks-content-and-reopens-authorized-source');
 await tab('会议纪要').click();await page.getByRole('button',{name:'下载',exact:true}).click();
 const exportDialog=page.getByRole('dialog',{name:'导出会议纪要',exact:true});
 await expect(exportDialog).toBeVisible();
 await page.route(accessPath,r=>r.fulfill({status:503,json:{error:{code:'MEETING_UNAVAILABLE'}}}));
 await expect(page.locator('.am-layout')).toHaveCount(0,{timeout:9000});
 await expect(exportDialog).toHaveCount(0);
 await page.unroute(accessPath);await expect(exportDialog).toBeVisible({timeout:20000});
 await page.keyboard.press('Escape');await tab('核心要素').click();
 pass('expired-lease-restores-export-without-resubmitting');

 const section=core.locator('#minutes-category-RISK'),note='仅保留已陈述的风险依据';
 const failedBefore=(await get(root+'/tasks?kind=MINUTES_GENERATE_V1&view=current')).items[0]?.taskId;
 const scenario=await fetch('http://127.0.0.1:8792/admin/api/scenarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
  module:'llm',name:'region-retry-'+crypto.randomUUID(),method:'POST',path:'/v1/minutes/compositions/',priority:1000,
  match:{minutes_meeting_id:meeting},status_code:422,response_type:'json',response_body:{error:'controlled-rejection'}
 })});
 assert.equal(scenario.status,201);
 const scenarioId=(await scenario.json()).id;
 try{
  await section.getByRole('button',{name:'局部重新生成'}).click();const dialog=page.getByRole('dialog',{name:'重新生成纪要'});
  await dialog.getByLabel('局部生成要求').fill(note);
  await dialog.getByRole('button',{name:'开始生成区域候选'}).click();
  await dialog.getByRole('button',{name:'关闭',exact:true}).click();
  await expect.poll(async()=>{const job=(await get(root+'/tasks?kind=MINUTES_GENERATE_V1&view=current')).items[0];return job?.taskId!==failedBefore&&job?.state;},{timeout:150000}).toBe('FAILED');
 }finally{assert.equal((await fetch('http://127.0.0.1:8792/admin/api/scenarios/'+scenarioId,{method:'DELETE'})).status,200);}
 await page.reload();await page.getByRole('button',{name:'查看原因并重试'}).click();
 const retryDialog=page.getByRole('dialog',{name:'重新生成纪要'});
 await expect(retryDialog.getByLabel('局部生成要求')).toHaveValue(note);
 await expect(retryDialog.getByLabel('目标区域')).toHaveValue('RISK');
 await retryDialog.getByRole('button',{name:'开始生成区域候选'}).click();
 await retryDialog.getByRole('button',{name:'关闭',exact:true}).click();
 await expect.poll(async()=> (await get(root+'/minutes/candidates?state=PENDING&limit=20')).items.length,{timeout:150000}).toBe(1);
 await page.reload();await page.getByRole('button',{name:'核对纪要候选',exact:true}).click();
 await page.getByRole('button',{name:'保留当前稿',exact:true}).click();
 await expect(page.getByRole('dialog',{name:'核对纪要候选'})).toHaveCount(0);
 pass('regional-provider-failure-reopens-original-scope-and-instructions-after-reload');
 const saved=await get(root+'/minutes'),session=await get('/api/v1/session');
 let nav=await get(root+'/workspace');assert.equal(nav.activeMinutesGeneration,false);
 for(let count=0;!nav.privateSections.tasks.hasMore&&count<22;count++){
  const status=await page.evaluate(async({root,token,revision})=>(await fetch(root+'/exports',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':token,'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({sourceRevision:revision,format:'md'})})).status,{root,token:session.csrfToken,revision:saved.body.id});
  assert.equal(status,202);
  nav=await get(root+'/workspace');
 }
 assert(nav.privateSections.tasks.hasMore);assert.equal(nav.activeMinutesGeneration,false);
 await page.reload();await page.getByRole('button',{name:'进入会议确认',exact:true}).click();await expect(page.locator('.ac-confirm-info')).toBeVisible();
 pass('real-paginated-tasks-allow-confirmation');
 const member=await login('dev-member');
 // Existing read-only member fixture, accessed through normal authenticated routes.
 const memberMeeting=process.env.AMA_MINUTES_MEMBER_SAMPLE??'97af141a-3b9e-436c-a136-b2dfad3d1188';
 for(const [width,height] of [[1440,900],[1024,768],[1536,791]]){
  await member.setViewportSize({width,height});await member.goto(origin+'/meeting/'+memberMeeting+'/minutes');
  await member.locator('.minutes-views .tab').nth(1).click();await expect(member.locator('.am-saved-body')).toBeVisible();
  await expect(member.locator('.acf-dock')).toHaveCount(0);await member.getByRole('button',{name:'复制全文',exact:true}).click();
  await member.locator('.minutes-body [title="导出当前已保存版本"]').click();
  assert.equal(await member.locator('dialog[open] select option').count(),3);await member.keyboard.press('Escape');
 }
 pass('unpublished-member-pointer-copy-and-export-three-sizes');
 assert.deepEqual(report.errors,[]);report.pass=true;
}catch(cause){report.failure=String(cause);const page=browser.contexts()[0]?.pages()[0];if(page){await page.screenshot({path:output+'/failure.png'});await writeFile(output+'/failure.txt',await page.locator('body').innerText());}throw cause;}
finally{await writeFile(output+'/report.json',JSON.stringify(report,null,2));await browser.close();}
