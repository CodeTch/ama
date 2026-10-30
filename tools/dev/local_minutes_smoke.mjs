import assert from 'node:assert/strict';import {createRequire} from 'node:module';import {resolve} from 'node:path';import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const base='http://127.0.0.1:5173',output='.tools/local-app/minutes-smoke';await mkdir(output,{recursive:true});
const source=JSON.parse(await readFile('.tools/local-app/transcript-smoke/report.json','utf8')),meetingId=source.meetingId;
const browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage(),errors=[];
page.setDefaultTimeout(12000);
page.on('pageerror',e=>errors.push(e.message));
try{
 await page.goto(base+'/api/dev/login');await page.getByRole('button',{name:'登录本地环境'}).click();await page.waitForURL(base+'/meetings');await page.goto(base+'/meeting/'+meetingId);
 const savedInfo=await page.evaluate(async id=>(await (await fetch(`/api/v1/meetings/${id}`)).json()),meetingId);
 if(!savedInfo.confirmedRoster?.some(x=>x.enterpriseId==='dev-member')){
 await page.getByRole('button',{name:'编辑会议信息',exact:true}).click();const drawer=page.getByRole('dialog',{name:'编辑会议信息'});
 await drawer.getByLabel('会议名称',{exact:true}).fill('本地完整纪要演示');await drawer.locator('input[type=date]').fill(new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()));await drawer.getByLabel('会议简介',{exact:true}).fill('通过实际工作流验证 TXT、纪要、审阅和发布。');
 await drawer.getByRole('button',{name:'加载会议类别',exact:true}).click();await drawer.locator('select').selectOption('project');
 await drawer.getByLabel('搜索企业人员',{exact:true}).fill('dev-member');await drawer.getByRole('button',{name:'搜索人员',exact:true}).click();await drawer.getByRole('button',{name:'加入名单',exact:true}).click();
 await drawer.getByRole('button',{name:'保存信息与名单草稿',exact:true}).click();await expect(drawer.getByRole('button',{name:'确认已保存名单',exact:true})).toBeEnabled();const rosterResponse=page.waitForResponse(r=>r.url().includes('/roster-confirmations')&&r.request().method()==='POST');await drawer.getByRole('button',{name:'确认已保存名单',exact:true}).click();assert.equal((await rosterResponse).status(),200);await page.keyboard.press('Escape');
 }
 console.log('meeting-info-saved');
 await page.getByRole('button',{name:'纪要与审阅',exact:true}).click();const generation=page.locator('.generation-panel'),minutes=page.locator('.minutes');
 const generationBefore=await page.evaluate(async id=>(await (await fetch(`/api/v1/meetings/${id}/minutes/generation-inputs/current`)).json()),meetingId);
 if(!generationBefore.saved.templateRevisionId){
 await expect(generation.getByRole('button',{name:'读取可选模板',exact:true})).toBeVisible({timeout:15000});
 await generation.getByRole('button',{name:'读取可选模板',exact:true}).click();const templates=generation.locator('select');await expect(templates.locator('option')).toHaveCount(2);
 await templates.selectOption({label:'Controlled minutes（默认）'});await generation.getByRole('button',{name:'保存初始模板与设置',exact:true}).click();
 }
 await expect(generation).toContainText('已选择模板版本',{timeout:15000});
 const taskIds=[];
 for(let round=0;round<(generationBefore.observation.currentMinutesRevisionId?1:2);round++){
  await generation.getByRole('button',{name:'刷新生成条件',exact:true}).click();await expect(generation.getByRole('button',{name:'生成完整纪要',exact:true})).toBeEnabled();
  await generation.getByRole('button',{name:'生成完整纪要',exact:true}).click();await expect.poll(async()=>{const match=(await generation.innerText()).match(/生成任务已受理：([0-9a-f-]{36})/);return match&&!taskIds.includes(match[1])?match[1]:null;},{timeout:15000}).not.toBeNull();
  const taskId=(await generation.innerText()).match(/生成任务已受理：([0-9a-f-]{36})/)[1];taskIds.push(taskId);
  await expect.poll(()=>page.evaluate(async({meetingId,taskId})=>{const r=await fetch(`/api/v1/meetings/${meetingId}/minutes/generation-tasks/${taskId}`);const v=await r.json();return {status:r.status,value:v};},{meetingId,taskId}),{timeout:45000,intervals:[500,1000]}).toMatchObject({status:200,value:{task:{state:'SUCCEEDED'}}});
 }
 const taskId=taskIds.at(-1);
 await minutes.getByRole('button',{name:'刷新纪要',exact:true}).click();await minutes.getByRole('button',{name:'会议纪要',exact:true}).click();await expect(minutes.getByRole('button',{name:'编辑正文',exact:true})).toBeEnabled({timeout:15000});
 await minutes.getByRole('button',{name:'候选与版本',exact:true}).click();await minutes.getByRole('button',{name:'读取候选',exact:true}).click();await minutes.getByRole('button',{name:'检查候选',exact:true}).click();await expect(minutes).toContainText('Controlled rich minutes');
 const comparison=page.getByRole('dialog',{name:'核对纪要候选',exact:true});await expect(comparison).toBeVisible();await expect(comparison).toContainText('当前已保存纪要');await expect(comparison).toContainText('新生成候选');
 for(const width of [1440,1024]){const height=width===1440?900:768;await page.setViewportSize({width,height});const box=await comparison.boundingBox();assert(box&&box.x>=0&&box.y>=0&&box.x+box.width<=width+1&&box.y+box.height<=height+1);const actionBox=await comparison.getByRole('button',{name:'采用此候选',exact:true}).boundingBox();assert(actionBox&&actionBox.y>=0&&actionBox.y+actionBox.height<=height,'Candidate decision must stay in viewport');await expect(comparison.locator('.candidate-columns article>div')).toHaveCount(9);await page.screenshot({path:output+`/candidate-comparison-${width}.png`});}await page.setViewportSize({width:1440,height:900});
 await minutes.getByRole('button',{name:'采用此候选',exact:true}).click();await expect(minutes.getByRole('button',{name:'编辑正文',exact:true})).toBeEnabled({timeout:15000});
 const report={pass:true,meetingId,taskId,taskIds,checks:[...(generationBefore.saved.templateRevisionId?[]:['real-template-initialization']),'real-generation-task','candidate-inspection','native-comparison-two-viewports','candidate-adoption'],errors};await writeFile(output+'/report.json',JSON.stringify(report,null,2));await page.screenshot({path:output+'/adopted-1440.png',fullPage:true});console.log(JSON.stringify(report));
}catch(e){await writeFile(output+'/failure.txt',await page.locator('body').innerText());await page.screenshot({path:output+'/failure.png',fullPage:true});console.error('PAGE ERRORS',errors);throw e;}finally{await browser.close();}
