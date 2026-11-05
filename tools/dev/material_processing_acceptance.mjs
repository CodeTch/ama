import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000});
const [fixtureFile,output,scenario]=process.argv.slice(2);
const fixture=JSON.parse(await readFile(fixtureFile,'utf8')),meeting=fixture.meeting;
assert.match(meeting,/^[0-9a-f-]{36}$/);await mkdir(output,{recursive:true});
const browser=await chromium.launch(),page=await browser.newPage(),base='http://127.0.0.1:5174',checks=[],errors=[],traffic=[];
page.on('pageerror',e=>errors.push(e.message));
page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/v1/'))traffic.push({path,method:r.request().method(),status:r.status()});});
let faultId;
const harness='http://127.0.0.1:8792/admin/api/scenarios';
const pass=s=>{checks.push(s);console.log('PASS',s);};
const api=async suffix=>page.evaluate(async url=>{const r=await fetch(url);if(!r.ok)throw Error(String(r.status));return r.json();},'/api/v1/meetings/'+meeting+suffix);
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill('demo-owner');await page.locator('#login-password').fill('demo-password');await page.locator('button[type=submit]').click();await page.waitForURL('**/meetings');
 let workspace=await api('/workspace');
 if(!workspace.processingSource){
  if(scenario==='fault'){
   const fault=await fetch(harness,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({module:'llm',name:'offline-material-retry-'+meeting,method:'POST',path:'/v1/transcript-enhancements/',priority:10000,match:{field_equals:{meetingId:meeting}},status_code:503,response_type:'json',response_body:{error:'test_outage'}})});assert.equal(fault.status,201);faultId=(await fault.json()).id;
  }
  await page.goto(base+'/meeting/'+meeting+'?import=1');const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  const response=page.waitForResponse(r=>r.url().endsWith('/transcript-imports')&&r.request().method()==='POST');await dialog.getByRole('button',{name:'开始处理',exact:true}).click();const r=await response;assert.equal(r.status(),202);assert.equal(r.request().postDataJSON().enhance,true);pass('browser submits durable offline enhancement');
 }
 await page.goto(base+'/meeting/'+meeting+'/processing');
 if(faultId){
  await expect.poll(async()=>(await api('/workspace')).processingMaterialStage,{timeout:120000}).toBe('FAILED');const task=(await api('/workspace')).processingSource.taskId;
  await page.reload();await expect(page.getByRole('button',{name:'重试增强',exact:true})).toBeVisible();assert.equal((await api('/processing-tasks/'+task)).state,'SUCCEEDED');
  assert.equal((await fetch(harness+'/'+faultId,{method:'DELETE'})).status,200);faultId=null;
  await page.getByRole('button',{name:'重试增强',exact:true}).click();pass('scoped harness outage retained RAW, reloaded and retried enhancement only');
 }
 await expect.poll(async()=>{workspace=await api('/workspace');return workspace.processingMaterialStage;},{timeout:180000}).toBe('SUCCEEDED');
 assert.ok(workspace.saved.transcriptRevisionId);assert.ok(workspace.temporaryTitle);pass('offline RAW and locked-material enhancement installed a working transcript');
 const raw=await api('/processing-tasks/'+workspace.processingSource.taskId);assert.equal(raw.materialStage,'SUCCEEDED');assert.deepEqual(raw.materialFiles.map(x=>x.materialRevisionId).sort(),fixture.materials.map(x=>x.materialRevisionId).sort());
 await expect(page.getByRole('button',{name:'进入转写校正',exact:true})).toBeEnabled();pass('results unlocked after success and sources match the submitted versions');
 const before=await api('/materials'),adjust=page.getByRole('button',{name:'调整背景材料'}),dialog=page.getByRole('dialog',{name:'会议背景材料',exact:false});
 await adjust.click();await dialog.getByRole('button',{name:'移除',exact:true}).first().click();await dialog.getByRole('button',{name:'取消',exact:true}).click();assert.deepEqual(await api('/materials'),before);pass('cancel discards removal without changing the snapshot');
 await adjust.click();for(let i=0;i<before.items.length;i++)await dialog.getByRole('button',{name:'移除',exact:true}).first().click();
 const applied=page.waitForResponse(r=>r.url().endsWith('/materials/batches')&&r.request().method()==='POST');await dialog.getByRole('button',{name:'应用材料并重新增强'}).click();const r=await applied;assert.equal(r.status(),201);assert.equal((await api('/materials')).items.length,0);
 await expect.poll(async()=>(await api('/material-adjustments/latest')).stage,{timeout:90000}).toBe('READY');
 assert.equal((await api('/workspace')).saved.transcriptRevisionId,workspace.saved.transcriptRevisionId);pass('empty set applied atomically and candidate leaves the previous working transcript intact');
 await page.reload();await adjust.click();await expect(dialog.getByText('增强候选核对')).toBeVisible();pass('reopen recovers persisted candidate');
 await page.screenshot({path:resolve(output,'candidate.png')});assert.deepEqual(errors,[]);
}catch(e){console.error(e);await page.screenshot({path:resolve(output,'failure.png')}).catch(()=>{});process.exitCode=1;}
finally{if(faultId)await fetch(harness+'/'+faultId,{method:'DELETE'});await writeFile(resolve(output,'result.json'),JSON.stringify({meeting,checks,errors,traffic,pass:!process.exitCode},null,2));await browser.close();}
