import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {randomUUID,createHash} from 'node:crypto';
const require=createRequire(resolve('web/package.json'));
const {chromium}=require('@playwright/test');
const expect=require('@playwright/test').expect.configure({timeout:45000});
const base='http://127.0.0.1:5174',harness='http://127.0.0.1:8792';
const output=resolve(process.argv[2]??`.tools/local-app/material-repair-e2e/${randomUUID()}`);
const mode=process.argv[3]??'prepare';
await mkdir(output,{recursive:true});
const checks=[],requests=[],errors=[];
const browser=await chromium.launch({headless:true});let fixture,report;
const save=async(name,value)=>writeFile(resolve(output,name),JSON.stringify(value,null,2),{mode:0o600});
try{
 const context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',viewport:{width:1440,height:1000}});
 const page=await context.newPage();page.setDefaultTimeout(45000);
 page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{const p=new URL(r.url()).pathname;if(p.startsWith('/api/'))requests.push({method:r.request().method(),path:p,status:r.status()});});
 const check=(name)=>{checks.push(name);console.log('PASS '+name);};
 const api=async(path,method='GET',body,key)=>page.evaluate(async({path,method,body,key})=>{
  const session=await(await fetch('/api/v1/session')).json();
  const r=await fetch(path,{method,headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,...(key?{'Idempotency-Key':key}:{})},...(body===undefined?{}:{body:typeof body==='string'?body:JSON.stringify(body)})});
  return {status:r.status,body:await r.json()};
 },{path,method,body,key});
 await page.goto(base+'/login');await page.locator('#login-username').fill('demo-owner');await page.locator('#login-password').fill('demo-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/meetings');check('real-CAS-login');
 if(mode==='recover'){
  fixture=JSON.parse(await readFile(resolve(output,'fixture.json'),'utf8'));
  const listing=await api(`/api/v1/meetings/${fixture.meeting}/materials`);assert.equal(listing.status,200);assert.equal(listing.body.items.length,3);
  assert.deepEqual(listing.body.items.map(x=>x.id),fixture.materials.map(x=>x.id));check('all-three-materials-survive-full-service-restart');
  const replay=await api(`/api/v1/meetings/${fixture.meeting}/materials/batches`,'POST',fixture.command.body,fixture.command.key);assert.equal(replay.status,201);assert.deepEqual(replay.body,fixture.result);check('original-key-and-body-replay-after-restart');
  for(const item of fixture.materials){const r=await page.evaluate(async id=>{const response=await fetch('/api/v1/files/'+id+'/content');const bytes=await response.arrayBuffer();return {status:response.status,sha:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('')};},item.fileId);assert.equal(r.status,200);assert.equal(r.sha,item.sha256);}check('cached-document-bytes-match-all-three-hashes-after-restart');
  await page.goto(`${base}/meeting/${fixture.meeting}?import=1`);await expect(page.getByRole('dialog',{name:'导入会议',exact:true})).toBeVisible();await expect(page.locator('.material-import-section .material-job')).toHaveCount(0);await expect(page.locator('.material-import-section [data-aci-files="material"] > .file-item')).toHaveCount(3);await page.screenshot({path:resolve(output,'after-restart.png'),fullPage:true});check('real-import-dialog-restores-three-materials');
 }else{
  assert.equal(await readFile(resolve(output,'fixture.json'),'utf8').then(()=>true,()=>false),false,'Never replace an existing fixture');
  const created=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await created).status(),201);await page.waitForURL(/\?import=/);const meeting=new URL(page.url()).searchParams.get('import');assert.ok(meeting);fixture={meeting};await save('fixture.json',fixture);
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog).toBeVisible();await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'知识库材料实操.txt',mimeType:'text/plain',buffer:Buffer.from('会议记录：核对存储价格走势、估值报告与合规制度。\n确认背景材料可以读取并生成会议内容。\n')});await expect(dialog.getByRole('button',{name:'保存并返回列表',exact:true})).toBeEnabled();check('real-primary-TXT-upload');
  // Verify the old wrong hash is durably rejected before permitting a new selection.
  let current=await api(`/api/v1/meetings/${meeting}/materials`);assert.equal(current.status,200);
  const old={expectedRevisionToken:current.body.meetingRevision,items:[{libraryId:'kb-compliance',documentId:'doc-5eb9619557310d8249965f2cffb70ca8',version:'2b71bf6d2a52ca5ac7d0ca0d0087c77761609e0d9edcef4383595329d192bf2f'}]};
  const oldKey=randomUUID();const refused=await api(`/api/v1/meetings/${meeting}/materials/batches`,'POST',old,oldKey);assert.equal(refused.status,409);assert.equal(refused.body.error.code,'MATERIAL_BATCH_VERSION_REJECTED');const refusedAgain=await api(`/api/v1/meetings/${meeting}/materials/batches`,'POST',old,oldKey);assert.equal(refusedAgain.body.error.code,'MATERIAL_BATCH_VERSION_REJECTED');current=await api(`/api/v1/meetings/${meeting}/materials`);assert.equal(current.body.items.length,0);check('old-hash-original-request-durably-rejected-with-zero-materials');
  await dialog.getByRole('button',{name:'从知识库选择',exact:true}).click();const picker=page.getByRole('dialog',{name:'从知识库选择材料',exact:true});await expect(picker).toBeVisible();await picker.locator('.ac-library').filter({hasText:'合规制度示例库'}).click();
  for(const title of ['存储每月价格走势','场外期权估值报告','合规制度材料 01']){const row=picker.locator('.ac-kb-file').filter({hasText:title});await expect(row).toHaveCount(1);await row.locator('input').check();}check('select-the-three-screenshot-documents-in-real-picker');
  let command,result;await page.route('**/materials/batches',async route=>{try{const request=route.request();command={key:request.headers()['idempotency-key'],body:request.postData()};await save('original-command.json',command);const response=await route.fetch({url:'http://127.0.0.1:8182'+new URL(request.url()).pathname,timeout:30000});result=await response.json();assert.equal(response.status(),201,JSON.stringify(result));await save('accepted-before-lost-ack.json',{command,result});await route.abort('failed');}catch(error){errors.push('Fault driver: '+error.name);await route.abort('failed').catch(()=>{});}});
  await picker.getByRole('button',{name:'添加所选文件',exact:true}).click();await expect(picker.getByRole('button',{name:'取消',exact:true})).toBeEnabled();await picker.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog.locator('.material-job').getByRole('button',{name:'查回原请求',exact:true})).toBeVisible();assert.ok(command?.key);await page.unroute('**/materials/batches');
  await dialog.locator('.material-job').getByRole('button',{name:'查回原请求',exact:true}).click();await expect(dialog.locator('.material-job')).toHaveCount(0);await expect(dialog.locator('.material-import-section [data-aci-files="material"] > .file-item')).toHaveCount(3);check('accepted-batch-with-lost-ack-recovers-original-request-without-duplicates');
  const listing=await api(`/api/v1/meetings/${meeting}/materials`);assert.equal(listing.status,200);assert.equal(listing.body.items.length,3);assert.ok(listing.body.items.every(x=>x.state==='READY'));fixture={meeting,command,result,materials:listing.body.items};await save('fixture.json',fixture);
  for(const item of fixture.materials){const r=await page.evaluate(async id=>{const response=await fetch('/api/v1/files/'+id+'/content');const bytes=await response.arrayBuffer();return {status:response.status,sha:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('')};},item.fileId);assert.equal(r.status,200);assert.equal(r.sha,item.sha256);}check('all-three-cached-document-hashes-and-authorized-downloads');
  await dialog.getByRole('button',{name:'保存并返回列表',exact:true}).click();await expect(dialog).toBeHidden();await page.goto(`${base}/meeting/${meeting}?import=1`);await expect(dialog).toBeVisible();await expect(dialog.locator('.material-import-section [data-aci-files="material"] > .file-item')).toHaveCount(3);await page.screenshot({path:resolve(output,'import-success.png'),fullPage:true});check('save-and-reopen-import-dialog-with-all-three-materials');
 }
 assert.deepEqual(errors,[]);report={pass:true,mode,checks,meeting:fixture.meeting,requests,errors};
}catch(e){report={pass:false,mode,checks,meeting:fixture?.meeting,requests,errors,failure:String(e.stack??e)};process.exitCode=1;}
finally{await save(`report-${mode}.json`,report);await browser.close();console.log(JSON.stringify({...report,output},null,2));}
