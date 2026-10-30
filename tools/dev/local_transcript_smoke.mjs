import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
const require=createRequire(resolve('web/package.json'));
const {chromium,expect}=require('@playwright/test');
const queueOnly=process.argv.includes('--queue-only');
const base='http://127.0.0.1:5173',output=queueOnly?'.tools/local-app/qualification-intake':'.tools/local-app/transcript-smoke';
await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true});
try {
 const context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.goto(base+'/api/dev/login');await page.getByLabel('开发账号').selectOption('dev-owner');
 await page.getByRole('button',{name:'登录本地环境'}).click();await page.waitForURL(base+'/meetings');
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 try{await page.waitForURL(/\/meeting\/[0-9a-f-]{36}(?:\?import=1)?$/);}catch(failure){console.log(JSON.stringify({phase:'CREATE_NAVIGATION_FAILED',url:page.url(),visibleText:(await page.locator('body').innerText()).slice(-2000),pageErrors:errors}));throw failure;}
 const meetingId=new URL(page.url()).pathname.split('/').at(-1);
 const capability=await page.evaluate(async id=>await (await fetch(`/api/v1/meetings/${id}/processing-capabilities`)).json(),meetingId);
 assert.equal(capability.canSubmit,true,'Local launcher must install the genuine local file route and processing archive');
 if(!queueOnly)assert.equal(capability.execution,'INSTALLED','Independent TXT Worker must report healthy');
 const receiptPaths=new Set();if(queueOnly)page.on('response',response=>{if(response.request().method()==='GET'&&response.status()===200&&new RegExp(`/meetings/${meetingId}/uploads/[0-9a-f-]{36}$`).test(response.url()))receiptPaths.add(new URL(response.url()).pathname);});
 const upload=page.locator('.upload-panel');
 const input=upload.locator('input[type=file][multiple]');
 await expect(input).toBeEnabled();
 await expect(page).toHaveURL(/\/meeting\/[0-9a-f-]{36}$/);
 await input.setInputFiles([
  {name:'01-first.txt',mimeType:'text/plain',buffer:Buffer.from('第一份开场 😀\n确认项目范围')},
  {name:'02-second.txt',mimeType:'text/plain',buffer:Buffer.from('第二份决策\n负责人下周跟进')}
 ]);
 await expect(upload.locator('.upload-list li')).toHaveCount(2);
 await expect(upload.getByRole('button',{name:'按列表顺序提交导入'})).toBeEnabled({timeout:15000});
 let taskId;
 if(queueOnly){
  // The normal UI deliberately refuses to submit while execution is unavailable.
  // For crash qualification use the authenticated real producer API after real UI uploads.
  await expect.poll(()=>receiptPaths.size).toBe(2);const receipts=await page.evaluate(async paths=>Promise.all(paths.map(async path=>{const r=await fetch(path);if(r.status!==200)throw new Error('Genuine authorized receipt required');return r.json();})),[...receiptPaths]);const completedFiles=new Map(receipts.map(r=>{assert.equal(r.state,'COMPLETED');assert.equal(r.completed.intakeEligibility.state,'ELIGIBLE');return [r.displayName,r.completed.fileId];}));const orderedFileIds=['01-first.txt','02-second.txt'].map(name=>completedFiles.get(name));assert(orderedFileIds.every(Boolean));
  const result=await page.evaluate(async({meetingId,orderedFileIds})=>{const session=await(await fetch('/api/v1/session')).json(),capability=await(await fetch(`/api/v1/meetings/${meetingId}/processing-capabilities`)).json();if(!capability.canSubmit||capability.intake!=='AVAILABLE'||!capability.formats.includes('TXT'))throw new Error('Genuine intake required');const response=await fetch(`/api/v1/meetings/${meetingId}/transcript-imports`,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({expectedRevisionToken:capability.revisionToken,orderedFileIds})});return {status:response.status,value:await response.json()};},{meetingId,orderedFileIds});assert.equal(result.status,202,JSON.stringify(result.value));taskId=result.value.taskId;
 }else{
  const acceptance=page.waitForResponse(r=>r.url().endsWith(`/meetings/${meetingId}/transcript-imports`)&&r.request().method()==='POST');
  await upload.getByRole('button',{name:'按列表顺序提交导入'}).click();const accepted=await acceptance;assert.equal(accepted.status(),202);await expect(upload).toContainText('导入任务已受理');const taskText=await upload.innerText();taskId=taskText.match(/导入任务已受理（([0-9a-f-]{36})）/)[1];
 }
 const body={taskId};
 if(queueOnly){assert.deepEqual(errors,[]);const queued={meetingId,taskId,queueOnlyAccepted:true,origin:"real-ui-upload-and-authenticated-business-api",workerExecutionObserved:capability.execution,expectedSegments:['第一份开场 😀','确认项目范围','第二份决策','负责人下周跟进']};await writeFile(output+'/queued.json',JSON.stringify(queued,null,2));await page.screenshot({path:output+'/queued-1440.png'});console.log(JSON.stringify(queued));}else{
 await expect.poll(async()=>await page.evaluate(async({meetingId,taskId})=>{
  const response=await fetch(`/api/v1/meetings/${meetingId}/processing-tasks/${taskId}`);const value=await response.json();return value.state;
 },{meetingId,taskId:body.taskId}),{timeout:30000,intervals:[500,1000]}).toBe('SUCCEEDED');
 await page.reload();
 await expect(page.getByRole('button',{name:'会议转写',exact:true})).toHaveAttribute('aria-pressed','true');
 await page.getByRole('button',{name:'处理任务',exact:true}).click();
 await expect(page.getByRole('region',{name:'处理任务工作区',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'会议转写',exact:true}).click();
 await expect(page.getByRole('region',{name:'会议转写工作区',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'读取历史版本',exact:true}).click();
 await page.getByRole('button',{name:/原始转写 RAW/}).click();
 await page.getByRole('button',{name:'恢复为新的工作版本',exact:true}).click();
 const transcript=page.locator('.transcript-section');
 await expect(transcript.locator('textarea')).toHaveCount(4);
 const original=await transcript.locator('textarea').evaluateAll(nodes=>nodes.map(n=>n.value));
 assert.deepEqual(original,['第一份开场 😀','确认项目范围','第二份决策','负责人下周跟进']);
 await transcript.locator('textarea').first().fill('第一份开场 😀 · 人工核对');
 await transcript.getByLabel('重点',{exact:true}).first().check();
 const saveResponse=page.waitForResponse(r=>r.url().endsWith(`/meetings/${meetingId}/transcript`)&&r.request().method()==='PUT');
 await transcript.getByRole('button',{name:'保存正文与标记',exact:true}).click();
 // The facade validates its complete ACK before the confirmation button is enabled.
 const saved=await saveResponse;assert.equal(saved.status(),200);
 await expect(transcript.getByRole('button',{name:'确认当前保存版本',exact:true})).toBeEnabled();
 await transcript.getByRole('button',{name:'确认当前保存版本',exact:true}).click();
 await expect(transcript).toContainText('当前版本已确认');
 await page.reload();
 await expect(page.locator('.transcript-section textarea').first()).toHaveValue('第一份开场 😀 · 人工核对');
 await expect(page.locator('.transcript-section')).toContainText('当前版本已确认');
 assert.deepEqual(errors,[]);
 await page.screenshot({path:output+'/transcript-1440.png',fullPage:true});
 // A valid first file followed by malformed UTF-8 must not publish a partial RAW.
 await page.goto(base+'/meetings');await page.getByRole('button',{name:'新建会议',exact:true}).click();await page.getByRole('button',{name:'创建并打开',exact:true}).click();await page.waitForURL(/\/meeting\/[0-9a-f-]{36}$/);
 const failedMeetingId=new URL(page.url()).pathname.split('/').at(-1),badUpload=page.locator('.upload-panel');
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 await expect(badUpload.locator('input[multiple]')).toBeEnabled();
 await badUpload.locator('input[multiple]').setInputFiles([{name:'valid-first.txt',mimeType:'text/plain',buffer:Buffer.from('不得部分发布')},{name:'malformed-second.txt',mimeType:'text/plain',buffer:Buffer.from([0xff,0xfe,0xff])}]);
 await expect(badUpload.getByRole('button',{name:'按列表顺序提交导入'})).toBeEnabled({timeout:15000});await badUpload.getByRole('button',{name:'按列表顺序提交导入'}).click();await expect(badUpload).toContainText('导入任务已受理');
 const failedTask=(await badUpload.innerText()).match(/导入任务已受理（([0-9a-f-]{36})）/)[1];
 await expect.poll(()=>page.evaluate(async({meeting,task})=>(await (await fetch(`/api/v1/meetings/${meeting}/processing-tasks/${task}`)).json()).state,{meeting:failedMeetingId,task:failedTask}),{timeout:30000,intervals:[500,1000]}).toBe('FAILED');
 const history=await page.evaluate(async id=>await (await fetch(`/api/v1/meetings/${id}/transcript/history`)).json(),failedMeetingId);assert.deepEqual(history.items,[],'Bad second file cannot publish first-file RAW');
 const report={pass:true,meetingId,taskId:body.taskId,failedMeetingId,failedTask,checks:['real-local-upload','two-files-ordered','worker-completion','raw-to-working','edit-mark-save','confirm','refresh-persistence','no-page-errors','malformed-second-file-no-partial-raw']};
 await writeFile(output+'/report.json',JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
 }
 await context.close();
}finally{await browser.close();}
