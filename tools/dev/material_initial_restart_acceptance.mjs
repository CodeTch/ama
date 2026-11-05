import assert from 'node:assert/strict';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';

const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const [kind,fixturePath,output]=process.argv.slice(2);
let {meeting,sid}=kind==='recording'?{}:JSON.parse(await readFile(fixturePath,'utf8'));
assert(['recording','offline'].includes(kind));await mkdir(output,{recursive:true});
const browser=await chromium.launch({args:['--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--disable-audio-output']}),page=await browser.newPage();
const base='http://127.0.0.1:5174',harness='http://127.0.0.1:8792/admin/api/scenarios';
let meetingPath='/api/v1/meetings/'+meeting;const recording=kind==='recording',errors=[],checks=[];
const read=path=>page.evaluate(async url=>{const r=await fetch(url);if(!r.ok)throw Error(String(r.status));return r.json();},path);
const current=()=>read(recording?'/api/v1/recordings/'+sid+'/processing':meetingPath+'/workspace');
const phase=async()=>{const r=await current();return recording?r.state:r.processingMaterialStage;};
const pass=s=>{checks.push(s);console.log('PASS',s);};
page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(40000);
let scenario;
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill(recording?'dev-owner':'demo-owner');await page.locator('#login-password').fill(recording?'dev-password':'demo-password');await page.locator('button[type=submit]').click();await page.waitForURL('**/meetings');
 if(recording){
  await page.goto(base+'/recording?start=1');await expect(page.locator('#ac-record-status')).toContainText('录音中',{timeout:60000});
  meeting=new URL(page.url()).pathname.split('/').at(-1);meetingPath='/api/v1/meetings/'+meeting;sid=(await read(meetingPath+'/recordings')).session.sessionId;
  await expect.poll(async()=>(await read('/api/v1/recordings/'+sid+'/manifest')).parts[0].sampleCount,{timeout:60000}).toBeGreaterThan(32000);
  await page.getByRole('button',{name:'暂停录音',exact:true}).click();
  await page.getByRole('button',{name:/添加背景资料/}).click();
  const picker=page.waitForEvent('filechooser');await page.getByRole('button',{name:'上传本地文件',exact:true}).click();await (await picker).setFiles(fixturePath);
  await expect.poll(async()=>(await read(meetingPath+'/materials')).items.length,{timeout:60000}).toBe(1);
  await page.getByRole('dialog',{name:'会议背景材料及相关文档'}).getByRole('button',{name:'完成',exact:true}).click();
 }
 const before=await read(meetingPath+'/materials');assert(before.items.length);
 const fault=await fetch(harness,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({module:'llm',name:'initial-restart-'+meeting,method:'POST',path:'/v1/transcript-enhancements/',priority:10000,match:{field_equals:{meetingId:meeting}},status_code:503,response_type:'json',response_body:{error:'controlled_outage'}})});
 assert.equal(fault.status,201);scenario=(await fault.json()).id;
 if(recording){await page.getByRole('button',{name:'结束录音',exact:true}).click();await page.getByRole('button',{name:'保存并开始转写',exact:true}).click();await page.waitForURL('**/meeting/'+meeting+'/processing*');}
 else {await page.goto(base+'/meeting/'+meeting+'?import=1');const submit=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/transcript-imports'));await page.getByRole('dialog',{name:'导入会议'}).getByRole('button',{name:'开始处理',exact:true}).click();assert.equal((await submit).status(),202);await page.goto(base+'/meeting/'+meeting+'/processing');}
 await expect.poll(phase,{timeout:240000}).toBe('FAILED');await page.reload();
 const old=await current(),oldTask=recording?old.taskId:old.processingSource.taskId;
 assert.equal((await read(meetingPath+'/processing-tasks/'+oldTask)).state,'SUCCEEDED');pass('controlled outage retains successful RAW before first working draft');
 if(recording){await page.getByRole('button',{name:'取消处理',exact:true}).click();await expect.poll(phase).toBe('CANCELLED');await expect(page.getByRole('button',{name:'按当前版本重新处理'})).toBeEnabled();pass('observed run cancels and offers an explicit restart');}
 await page.getByRole('button',{name:'调整背景材料'}).click();const dialog=page.getByRole('dialog',{name:'会议背景材料'});for(let i=0;i<before.items.length;i++)await dialog.getByRole('button',{name:'移除',exact:true}).first().click();
 assert.equal((await read(meetingPath+'/materials')).items.length,before.items.length);
 assert.equal((await fetch(harness+'/'+scenario,{method:'DELETE'})).status,200);scenario=null;
 const submitted=page.waitForResponse(r=>r.url().endsWith('/materials/batches'));await dialog.getByRole('button',{name:'应用材料并重新增强'}).click();assert.equal((await submitted).status(),201);
 await expect.poll(phase,{timeout:240000}).toBe('SUCCEEDED');const finished=await current(),task=recording?finished.taskId:finished.processingSource.taskId;
 assert.notEqual(task,oldTask);assert.equal((await read(meetingPath+'/materials')).items.length,0);assert.equal((await read(meetingPath+'/processing-tasks/'+oldTask)).state,'SUCCEEDED');
 const workspace=await read(meetingPath+'/workspace');assert(workspace.saved.transcriptRevisionId);
 await page.reload();await expect(page.getByRole('link',{name:'进入转写校正'}).or(page.getByRole('button',{name:'进入转写校正'}))).toBeEnabled({timeout:30000});pass('applied empty snapshot starts a fresh task, preserves former RAW and opens working transcript');
 await page.screenshot({path:resolve(output,'completed.png')});assert.deepEqual(errors,[]);
}catch(e){console.error(e);await writeFile(resolve(output,'failure.txt'),await page.locator('body').innerText().catch(()=>''));process.exitCode=1;}
finally{if(scenario)await fetch(harness+'/'+scenario,{method:'DELETE'});await writeFile(resolve(output,'report.json'),JSON.stringify({pass:!process.exitCode,kind,meeting,checks,errors},null,2));await browser.close();}
