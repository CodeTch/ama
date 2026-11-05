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
try{
 await login(page,ownerId);const memberSetup=await browser.newContext();await login(await memberSetup.newPage(),memberId);await memberSetup.close();check('fixed-harness-cas-and-external-origins');
 if(!report.meetingId){
  const created=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await created).status(),201);await page.waitForURL(/\/meetings\?import=[0-9a-f-]{36}&fresh=1$/);report.meetingId=new URL(page.url()).searchParams.get('import');await persist();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  const text='会议讨论检索服务升级方案。\n决定：先完成灰度验证后再全面上线。\n行动：开发参会人负责完成性能报告。\n风险：高峰期容量不足，需要补充压测。\n待确认：上线日期尚未确定。\n分歧：各方对扩容顺序未达成一致。\n内部经营数据：本季度营收123万元。\n政治敏感内容：此段仅用于联调排除验证。\n';
  await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'纪要完整场景验证.txt',mimeType:'text/plain',buffer:Buffer.from(text.replaceAll('开发参会人',memberName))});await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();const intake=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===root()+'/transcript-imports');await dialog.getByRole('button',{name:'开始处理',exact:true}).click();assert.equal((await intake).status(),202);
  await expect.poll(async()=> (await get('tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20')).items[0]?.state,{timeout:120000,intervals:[1500,2500]}).toBe('SUCCEEDED');
  const history=await get('transcript/history?limit=20'),raw=history.items.find(x=>x.kind==='RAW');assert(raw);const detail=await get('transcript/revisions/'+raw.id);await call('POST',root()+'/transcript/restorations',{expectedRevisionToken:detail.observation.revisionToken,sourceRevisionId:raw.id});
  check('native-import-and-real-transcript-worker');const unconfirmed=await call('GET',root());await call('POST',root()+'/minutes/generations',{expectedRevisionToken:unconfirmed.revisionToken,scope:'FULL'},422);assert.equal((await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.length,0);check('unconfirmed-transcript-rejects-generation-without-enqueuing');await page.goto(base+'/meeting/'+report.meetingId+'/transcript');await page.getByRole('button',{name:'确认转写并生成纪要',exact:true}).click();const confirm=page.getByRole('dialog',{name:'确认转写并生成纪要',exact:true});await confirm.getByRole('button',{name:'确认并生成',exact:true}).click();
  await expect.poll(async()=> (await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.length,{timeout:120000,intervals:[1500,2500]}).toBe(1);
  if(process.env.AMA_MINUTES_E2E_FIRST_GUARD==='1'){const header=await call('GET',root());await call('POST',root()+'/minutes/editor-intents',{expectedRevisionToken:header.revisionToken});check('editor-intent-recorded-while-first-generation-is-running');}
  await expect(page.locator('.generation-panel')).toBeVisible();assert.equal(await page.locator('.am-layout').count(),0,'No empty workbench beside first generation');
  for(const [width,height] of [[1440,900],[1024,768]]){await page.setViewportSize({width,height});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:output+`/first-generation-${width}.png`});}await page.setViewportSize({width:1440,height:900});check('first-generation-is-exclusive-and-captured-at-both-desktop-widths');
  // A real page reload must discover the same durable generation, without another POST.
  await page.goto(base+'/meeting/'+report.meetingId+'/minutes');await page.reload();
  await expect.poll(async()=> (await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items[0]?.state,{timeout:150000,intervals:[2000,3000]}).toBe('SUCCEEDED');
  if(process.env.AMA_MINUTES_E2E_FIRST_GUARD==='1'){const guarded=await saved();assert.equal(guarded.body,null);assert.equal(guarded.setId,null);const candidates=await get('minutes/candidates?state=PENDING&limit=20');assert.equal(candidates.items.length,1);const candidate=candidates.items[0];await call('POST',root()+'/minutes/editor-intents',{expectedRevisionToken:guarded.observation.revisionToken});await call('POST',root()+'/minutes/candidates/'+candidate.candidateId+'/adoptions',{expectedRevisionToken:guarded.observation.revisionToken,inputId:candidate.inputId,inputFingerprint:candidate.inputFingerprint});check('first-generation-editor-intent-prevents-auto-save-and-explicit-adoption-recovers');}
  await page.reload();await expect(page.locator('.am-layout')).toBeVisible();const initial=await saved();assert(initial.body&&initial.setId);assert(initial.body.body.includes('灰度验证'));assert(!initial.body.body.includes('Controlled'));assert.equal((await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items.length,1);check(process.env.AMA_MINUTES_E2E_FIRST_GUARD==='1'?'guarded-first-candidate-persists-only-after-explicit-adoption':'confirm-auto-generates-first-saved-input-dependent-minutes-without-duplicate-on-return',{bodyId:initial.body.id,setId:initial.setId});
 }
 const current=await saved();assert(current.body);report.currentBodyId=current.body.id;
 await page.goto(base+'/meeting/'+report.meetingId+'/minutes');await expect(page.locator('.am-layout')).toBeVisible();
 const catalog=await get('minutes/templates');for(const name of ['AI生成','正式会议纪要','简明总结'])assert(catalog.items.some(x=>x.name===name&&x.selectable),name);assert(catalog.items.some(x=>x.name==='AI生成'&&x.default));
 check('three-persisted-builtin-templates');
 await expect(page.locator('#minutes-category-ACTION')).toContainText('待核对会议依据');await expect(page.locator('#minutes-category-ACTION')).not.toContainText('截止日期：');
 await expect(page.locator('#minutes-category-OPEN_QUESTION')).toContainText('分歧');await expect(page.locator('#minutes-category-OPEN_QUESTION')).toContainText('待确认事项');check('explicit-review-state-null-date-and-distinct-question-subtypes');
 for(const [width,height] of [[1440,900],[1024,768]]){await page.setViewportSize({width,height});await page.screenshot({path:output+`/core-${width}.png`});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No page horizontal overflow');await page.locator('.minutes').getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.am-saved-body')).toContainText('灰度验证');await expect(page.getByLabel('纪要模板',{exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:'查看正文来源与审阅',exact:true})).toBeEnabled();await page.screenshot({path:output+`/body-${width}.png`});await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();}
 check('two-desktop-viewports-real-core-and-body');
 await page.getByRole('button',{name:'进入会议确认',exact:true}).click();await page.waitForURL(base+'/meeting/'+report.meetingId+'/confirm');await expect(page.locator('.ac-confirm-info')).toBeVisible();const readiness=await get('final-confirmations/readiness');assert.equal(readiness.ready,false);assert.equal((await get('final-confirmations/current')).confirmation,null);check('missing-information-can-enter-confirmation-but-cannot-create-a-valid-final-result');
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(r=>r.status>=500),[],'Unexpected application server error');report.pass=true;await persist();
}catch(error){report.pass=false;report.failure=String(error);await persist();await writeFile(output+'/failure-page.txt',await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});throw error;}
finally{await browser.close();}
