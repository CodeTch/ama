/** Native UI plus authenticated business APIs; only a dedicated test meeting is mutated. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:45000});
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const ownerId=process.env.AMA_MINUTES_E2E_OWNER??'dev-owner',memberId=process.env.AMA_MINUTES_E2E_MEMBER??'dev-member',memberName=process.env.AMA_MINUTES_E2E_MEMBER_NAME??'开发参会人';
const base='http://127.0.0.1:5174',meeting=process.env.AMA_MINUTES_E2E_MEETING;
assert.match(meeting??'',/^[0-9a-f-]{36}$/);
const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-repair-2026-11-05/editing');await mkdir(output,{recursive:true});
const report={meetingId:meeting,startedAt:new Date().toISOString(),checks:[],requests:[],pageErrors:[],expectedDenials:[]};
const browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'}),page=await context.newPage();
await page.addInitScript(()=>performance.setResourceTimingBufferSize(5000));
page.on('requestfailed',request=>{
 const url=new URL(request.url());
 (report.networkFailures??=[]).push({type:request.resourceType(),path:url.pathname,error:request.failure()?.errorText});
});
page.on('console',message=>{
 if(message.type()==='error')(report.consoleErrors??=[]).push(message.text().slice(0,500));
});
page.on('requestfinished',request=>{if(new URL(request.url()).pathname.startsWith('/api/'))report.requests.push({path:new URL(request.url()).pathname,method:request.method(),timing:request.timing()});});
page.on('pageerror',e=>report.pageErrors.push(e.message));page.on('response',async r=>{if(r.status()>=400&&new URL(r.url()).pathname.startsWith('/api/'))report.requests.push({method:r.request().method(),path:new URL(r.url()).pathname,status:r.status(),observed:true,error:await r.text().catch(()=>null)});});page.setDefaultTimeout(45000);
const root='/api/v1/meetings/'+meeting;
async function call(method,path,body,expected=200,key=randomUUID(),p=page){const r=await p.evaluate(async({method,path,body,key})=>{const session=method==='GET'?null:await(await fetch('/api/v1/session',{signal:AbortSignal.timeout(15000)})).json();const response=await fetch(path,{method,signal:AbortSignal.timeout(60000),...(method==='GET'?{}:{headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':key},body:JSON.stringify(body)})});const text=await response.text();let value;try{value=JSON.parse(text);}catch{value=text;}return{status:response.status,value};},{method,path,body,key});report.requests.push({method,path,status:r.status});assert.equal(r.status,expected,`${method} ${path}: ${JSON.stringify(r.value)}`);if(expected>=400)report.expectedDenials.push({method,path,status:expected});return r.value;}
const get=(suffix,p=page)=>call('GET',root+(suffix?'/'+suffix:''),undefined,200,undefined,p),saved=()=>get('minutes');
async function login(p,actor){await p.goto(base+'/login');await p.locator('#login-username').fill(actor);await p.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/meetings');}
async function screenshots(name){for(const [width,height] of [[1440,900],[1024,768]]){await page.setViewportSize({width,height});await page.evaluate(()=>document.fonts.ready);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Horizontal overflow: '+name+' '+width);await page.screenshot({path:output+'/'+name+'-'+width+'.png'});}await page.setViewportSize({width:1440,height:900});}
async function persist(){await writeFile(output+'/report.json',JSON.stringify(report,null,2));}
async function check(name,details={}){report.checks.push({name,...details});console.log('PASS '+name);await persist();}
async function intent(current){await call('POST',root+'/minutes/editor-intents',{expectedRevisionToken:current.observation.revisionToken});}
async function mutate(method,suffix,fields,options={}){const current=await saved();await intent(current);return call(method,root+'/minutes/'+suffix,{expectedRevisionToken:current.observation.revisionToken,...fields},options.expected??200,options.key);}
async function generate(scope={scope:'FULL'}){const current=await get('minutes/generation-inputs/current');assert.equal(current.eligible,true,JSON.stringify(current));const body={expectedRevisionToken:current.observation.revisionToken,...scope},key=randomUUID();const ack=await call('POST',root+'/minutes/generations',body,202,key);const replay=await call('POST',root+'/minutes/generations',body,202,key);assert.equal(replay.replayed,true);assert.deepEqual(replay.result,ack.result);await expect.poll(async()=>{const state=(await get('minutes/generation-tasks/'+ack.result.taskId)).task.state;if(['FAILED','STALE','CANCELLED','MANUAL_REQUIRED'].includes(state))throw Error('Generation '+state);return state;},{timeout:150000,intervals:[1000,2000]}).toBe('SUCCEEDED');const all=await get('minutes/candidates?state=PENDING&limit=20');const candidate=all.items.find(x=>x.inputId===ack.result.inputId);assert(candidate,JSON.stringify(all));return get('minutes/candidates/'+candidate.candidateId);}
async function adopt(candidate){return mutate('POST','candidates/'+candidate.candidate.candidateId+'/adoptions',{inputId:candidate.candidate.inputId,inputFingerprint:candidate.candidate.inputFingerprint});}
function shape(current){return Object.fromEntries(Object.entries(current.elements).map(([category,items])=>[category,items.map(x=>({body:x.body,action:x.action?{owner:x.action.owner_enterprise_id,display:x.action.owner_display,due:x.action.due_on}:null}))]));}
async function showBody(){await page.goto(base+'/meeting/'+meeting+'/minutes');await expect(page.locator('.am-layout')).toBeVisible();await page.locator('.minutes').getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.am-saved-body')).toBeVisible();}
// Drop only the first native PUT response after the server has processed it.
// Page-wide Playwright routing also intercepts unrelated module loads and disables HTTP caching.
const bodyUrl=base+root+'/minutes/body';
const network=await context.newCDPSession(page);
let committedStatus=null,injectionError=null;
const saveRequests=[];
async function loseFirstSaveResponse(event){
 const {request,requestId}=event;
 if(request.url!==bodyUrl||request.method!=='PUT')return network.send('Fetch.continueRequest',{requestId});
 const key=Object.entries(request.headers).find(([name])=>name.toLowerCase()==='idempotency-key')?.[1];
 saveRequests.push({key,body:request.postData});
 if(saveRequests.length===1){
  committedStatus=event.responseStatusCode;
  return network.send('Fetch.failRequest',{requestId,errorReason:'Failed'});
 }
 return network.send('Fetch.continueRequest',{requestId});
}
network.on('Fetch.requestPaused',event=>loseFirstSaveResponse(event).catch(error=>{injectionError=error;}));
page.on('dialog',dialog=>dialog.accept());
try{
 await login(page,ownerId);await showBody();const initial=await saved();await context.grantPermissions(['clipboard-read','clipboard-write']);
 await page.getByRole('button',{name:'编辑正文',exact:true}).click();const area=page.getByLabel('会议纪要正文',{exact:true});await expect(area).toBeEditable();
 const changed=initial.body.body+'\n人工补充记录：保存回执丢失后仍应保留中文😀。';await network.send('Fetch.enable',{patterns:[{urlPattern:bodyUrl,resourceType:'Fetch',requestStage:'Response'}]});await area.fill(changed);await page.getByRole('button',{name:'复制全文',exact:true}).click();assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),initial.body.title+'\n\n'+changed);await check('native-copy-uses-unsaved-edited-text');
 await expect.poll(()=>{if(injectionError)throw injectionError;return committedStatus;},{timeout:60000}).toBe(200);await expect(page.getByRole('button',{name:'查回原保存结果',exact:true})).toBeEnabled();await screenshots('editing-recovery');const committed=await saved();assert.equal(committed.body.body,changed);assert.notEqual(committed.body.id,initial.body.id);
 const refreshed=await page.reload();assert.equal(refreshed.status(),200);
 report.documentReload={url:new URL(refreshed.url()).pathname,type:refreshed.headers()['content-type']};await expect(page.getByRole('button',{name:'恢复编辑',exact:true})).toBeEnabled();await page.getByRole('button',{name:'恢复编辑',exact:true}).click();await expect(area).toHaveValue(changed);await page.getByRole('button',{name:'查回原保存结果',exact:true}).click();await expect(page.getByRole('region',{name:'编辑状态与恢复',exact:true})).toContainText('已与读取版本同步');const recovered=await saved();assert.equal(recovered.body.id,committed.body.id);assert.equal(recovered.body.body,changed);
 assert.equal(injectionError,null);assert.equal(saveRequests.length,2);assert.match(saveRequests[0].key,/^[0-9a-f-]{36}$/);assert(saveRequests[0].body);assert.deepEqual(saveRequests[1],saveRequests[0]);
 report.saveRecovery={nativeRequests:saveRequests.length,firstStatus:committedStatus,sameKeyAndBody:true,revision:recovered.body.id};
 await check('lost-save-acknowledgment-refresh-restores-exact-draft-and-replays-without-second-revision');
 await showBody();await page.getByRole('button',{name:'编辑正文',exact:true}).click();await expect(area).toBeEditable();await area.dispatchEvent('compositionstart');await area.fill(changed+'\n尚未保存的临时备注');await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();const leave=page.getByRole('dialog',{name:'有尚未保存的修改',exact:true});await expect(leave).toBeVisible();await leave.getByRole('button',{name:'继续编辑',exact:true}).click();await expect(area).toHaveValue(changed+'\n尚未保存的临时备注');await page.locator('.minutes').getByRole('button',{name:'核心要素',exact:true}).click();await leave.getByRole('button',{name:'放弃修改',exact:true}).click();await expect(page.locator('.am-layout')).toBeVisible();assert.equal((await saved()).body.id,committed.body.id);await check('native-dirty-tab-navigation-stay-and-discard-never-replace-saved-body');
 const current=await saved(),conclusion=current.elements.CONCLUSION[0];assert(conclusion);const row=page.locator('[data-element-id="'+conclusion.id+'"]');await row.getByRole('button',{name:'编辑要素',exact:true}).click();const edit=page.getByRole('dialog',{name:'编辑纪要内容',exact:true});await edit.getByRole('button',{name:'开始编辑',exact:true}).click();await expect(edit.getByLabel('正文',{exact:true})).toBeEditable();const elementSave=page.waitForResponse(r=>r.request().method()==='PUT'&&new URL(r.url()).pathname===root+'/minutes/elements/'+conclusion.id);await edit.getByLabel('正文',{exact:true}).fill(conclusion.body+'（人工核对原文）');assert.equal((await elementSave).status(),200);assert.equal((await saved()).elements.CONCLUSION[0].body,conclusion.body+'（人工核对原文）');await edit.getByRole('button',{name:'关闭',exact:true}).click();const edited=await saved();assert.equal(edited.body.id,committed.body.id);assert(edited.observation.needsGeneration);assert(edited.presentation.reviewStates.some(x=>x.elementId===edited.elements.CONCLUSION[0].id&&!x.sourceSupported));await check('native-element-autosave-preserves-body-and-invalidates-evidence-review');
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(r=>r.status>=500),[],'Unexpected application server error');report.pass=true;await persist();
}catch(error){report.pass=false;report.failure=String(error);report.document=await page.evaluate(()=>({
  path:location.pathname,readyState:document.readyState,title:document.title,app:!!document.querySelector('#app'),
  mounted:document.querySelector('#app')?.hasAttribute('data-v-app')??false,scripts:[...document.scripts].map(s=>s.getAttribute('src')),
  bodyLength:document.body?.innerHTML.length,recentResources:performance.getEntriesByType('resource').slice(-12).map(item=>new URL(item.name).pathname)
 })).catch(()=>null);await persist();await writeFile(output+'/failure-page.txt',await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});throw error;}
finally{await network.detach().catch(()=>{});await browser.close();}
