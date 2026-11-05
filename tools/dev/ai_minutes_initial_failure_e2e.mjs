/** Real fixed-harness CAS, real imports and persisted minutes; no business SQL. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:45000});
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const ownerId=process.env.AMA_MINUTES_E2E_OWNER??'dev-owner',memberId=process.env.AMA_MINUTES_E2E_MEMBER??'dev-member',memberName=process.env.AMA_MINUTES_E2E_MEMBER_NAME??'开发参会人';
const base='http://127.0.0.1:5174',output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-repair-2026-11-03');
await mkdir(output,{recursive:true,mode:0o700});
const runtime=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
for(const kind of ['cas','directory','knowledge','model'])assert.equal(runtime.integrationOrigins[kind],'http://127.0.0.1:8792');
const report={startedAt:new Date().toISOString(),sourceSignature:runtime.sourceSignature,checks:[],requests:[],pageErrors:[],meetingId:process.env.AMA_MINUTES_E2E_MEETING??null};
const browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'}),page=await context.newPage();
page.setDefaultTimeout(45000);page.on('pageerror',e=>report.pageErrors.push(e.message));page.on('response',async r=>{if(r.status()>=400&&new URL(r.url()).pathname.startsWith('/api/'))report.requests.push({method:r.request().method(),path:new URL(r.url()).pathname,status:r.status(),observed:true,error:await r.text().catch(()=>null)});});
function check(name,details={}){report.checks.push({name,...details});console.log('PASS '+name);}
const root=()=>'/api/v1/meetings/'+report.meetingId;
async function call(method,path,body,expected=200,key=randomUUID(),p=page){const r=await p.evaluate(async({method,path,body,key})=>{const session=method==='GET'?null:await(await fetch('/api/v1/session',{signal:AbortSignal.timeout(15000)})).json();const response=await fetch(path,{method,signal:AbortSignal.timeout(60000),...(method==='GET'?{}:{headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':key},body:JSON.stringify(body)})});const text=await response.text();let value;try{value=JSON.parse(text);}catch{value=text;}return {status:response.status,value};},{method,path,body,key});report.requests.push({method,path,status:r.status});assert.equal(r.status,expected,`${method} ${path}: ${JSON.stringify(r.value)}`);return r.value;}
const get=suffix=>call('GET',root()+'/'+suffix);
async function login(p,actor){const entry=await p.request.get(base+'/auth/cas/login',{maxRedirects:0});assert.equal(entry.status(),302);assert.equal(new URL(entry.headers().location).origin,'http://127.0.0.1:8792');await p.goto(base+'/login');await p.locator('#login-username').fill(actor);await p.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/meetings');}
async function saved(){return get('minutes');}
async function persist(){await writeFile(output+'/report.json',JSON.stringify(report,null,2));}
let scenarioId=null;
async function scenario(template,status){const response=await fetch('http://127.0.0.1:8792/admin/api/scenarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({module:'llm',name:'minutes-recovery-'+randomUUID(),method:'POST',path:'/v1/minutes/compositions/',priority:1000,match:{field_equals:{template:{revisionId:template.revisionId,name:template.name,themes:template.themes}}},status_code:status,response_type:'json',response_body:{error:'minutes_dedicated_scenario'}})});assert.equal(response.status,201);scenarioId=(await response.json()).id;}
async function clearScenario(){if(scenarioId!==null){const id=scenarioId;scenarioId=null;const response=await fetch('http://127.0.0.1:8792/admin/api/scenarios/'+id,{method:'DELETE'});assert.equal(response.status,200);}}
try{
 await login(page,ownerId);
  const created=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await created).status(),201);await page.waitForURL(/\/meetings\?import=[0-9a-f-]{36}&fresh=1$/);report.meetingId=new URL(page.url()).searchParams.get('import');await persist();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  const text='会议讨论检索服务升级方案。\n决定：先完成灰度验证后再全面上线。\n行动：开发参会人负责完成性能报告。\n风险：高峰期容量不足，需要补充压测。\n待确认：上线日期尚未确定。\n分歧：各方对扩容顺序未达成一致。\n内部经营数据：本季度营收123万元。\n政治敏感内容：此段仅用于联调排除验证。\n';
  await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'纪要完整场景验证.txt',mimeType:'text/plain',buffer:Buffer.from(text.replaceAll('开发参会人',memberName))});await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();const intake=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===root()+'/transcript-imports');await dialog.getByRole('button',{name:'开始处理',exact:true}).click();assert.equal((await intake).status(),202);
  await expect.poll(async()=> (await get('tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20')).items[0]?.state,{timeout:120000,intervals:[1500,2500]}).toBe('SUCCEEDED');
  const history=await get('transcript/history?limit=20'),raw=history.items.find(x=>x.kind==='RAW');assert(raw);const detail=await get('transcript/revisions/'+raw.id);await call('POST',root()+'/transcript/restorations',{expectedRevisionToken:detail.observation.revisionToken,sourceRevisionId:raw.id});

 check('native-import-before-first-generation-failure');
 await page.goto(base+'/meetings');await expect(page.getByRole('button',{name:'导入会议',exact:true})).toBeVisible();
 const transcript=await get('transcript');await call('POST',root()+'/transcript/confirmations',{expectedRevisionToken:transcript.observation.revisionToken,savedRevisionId:transcript.saved.id});
 const templateName='首版失败验证 '+randomUUID().slice(0,8);await call('POST','/api/v1/templates',{name:templateName,themes:['会议概述','关键内容','会议结论','行动项']},201);const personal=(await call('GET','/api/v1/templates?limit=20')).items.find(x=>x.name===templateName);assert(personal);
 const input=await get('minutes/generation-inputs/current');
 await call('POST',root()+'/minutes/initial-settings',{expectedRevisionToken:input.observation.revisionToken,templateRevisionId:personal.revisionId});
 assert.equal((await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.length,0);
 let firstSubmissions=0;page.on('request',r=>{if(r.method()==='POST'&&new URL(r.url()).pathname===root()+'/minutes/generations')firstSubmissions++;});
 await scenario(personal,422);const firstAccepted=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===root()+'/minutes/generations');await page.goto(base+'/meeting/'+report.meetingId+'/minutes');await expect(page.locator('.generation-panel')).toBeVisible();assert.equal(await page.locator('.am-layout').count(),0);const firstResponse=await firstAccepted;assert.equal(firstResponse.status(),202);const acceptedTasks=(await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items;assert.equal(acceptedTasks.length,1);const firstTaskId=acceptedTasks[0].taskId;assert(firstTaskId);
 await expect.poll(async()=>(await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.find(t=>t.taskId===firstTaskId)?.state,{timeout:150000,intervals:[2000]}).toBe('FAILED');const firstTasks=(await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items;assert.equal(firstTasks.length,1,'The automatic first generation must submit exactly once');const failedTask=firstTasks.find(t=>t.taskId===firstTaskId);assert(failedTask);assert.equal((await get('minutes/generation-tasks/'+failedTask.taskId)).task.errorCode,'HARNESS_INPUT_REJECTED');await expect(page.getByRole('button',{name:'生成完整纪要',exact:true})).toBeEnabled();await page.reload();await expect(page.locator('.generation-panel')).toContainText('生成失败');assert.equal(await page.locator('.am-layout').count(),0);assert.equal((await saved()).body,null);
 for(const [width,height] of [[1440,900],[1024,768]]){await page.setViewportSize({width,height});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:output+`/first-failure-${width}.png`});}assert.equal(firstSubmissions,1);check('first-permanent-provider-failure-has-exclusive-failed-state-and-no-synthetic-body',{firstTaskId,firstSubmissions});
 await clearScenario();await page.getByRole('button',{name:'生成完整纪要',exact:true}).click();await expect.poll(async()=>{const tasks=(await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items;return tasks.length===2&&tasks.some(t=>t.state==='SUCCEEDED')},{timeout:180000,intervals:[2000]}).toBe(true);await page.reload();await expect(page.locator('.am-layout')).toBeVisible();const current=await saved();assert(current.body&&current.setId);assert(current.body.body.includes('灰度验证'));assert.equal((await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.filter(t=>t.state==='FAILED').length,1);check('explicit-first-generation-retry-creates-one-new-task-and-real-saved-body');
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(r=>r.status>=500),[]);report.pass=true;await persist();
}catch(error){report.pass=false;report.failure=String(error);await persist();await writeFile(output+'/failure-page.txt',await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});throw error;}finally{await clearScenario();await browser.close();}
