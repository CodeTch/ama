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
const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-repair-2026-11-05/visual');await mkdir(output,{recursive:true});
const report={meetingId:meeting,startedAt:new Date().toISOString(),checks:[],requests:[],pageErrors:[],expectedDenials:[]};
const browser=await chromium.launch({headless:true}),context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'}),page=await context.newPage();
await page.addInitScript(()=>performance.setResourceTimingBufferSize(5000));
page.on('requestfinished',request=>{if(new URL(request.url()).pathname.startsWith('/api/'))report.requests.push({path:new URL(request.url()).pathname,method:request.method(),timing:request.timing()});});
page.on('pageerror',e=>report.pageErrors.push(e.message));page.on('response',async r=>{if(r.status()>=400&&new URL(r.url()).pathname.startsWith('/api/'))report.requests.push({method:r.request().method(),path:new URL(r.url()).pathname,status:r.status(),observed:true,error:await r.text().catch(()=>null)});});page.setDefaultTimeout(45000);
const root='/api/v1/meetings/'+meeting;
async function call(method,path,body,expected=200,key=randomUUID(),p=page){const r=await p.evaluate(async({method,path,body,key})=>{const session=method==='GET'?null:await(await fetch('/api/v1/session',{signal:AbortSignal.timeout(15000)})).json();const response=await fetch(path,{method,signal:AbortSignal.timeout(60000),...(method==='GET'?{}:{headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':key},body:JSON.stringify(body)})});const text=await response.text();let value;try{value=JSON.parse(text);}catch{value=text;}return{status:response.status,value};},{method,path,body,key});report.requests.push({method,path,status:r.status});assert.equal(r.status,expected,`${method} ${path}: ${JSON.stringify(r.value)}`);if(expected>=400)report.expectedDenials.push({method,path,status:expected});return r.value;}
const get=(suffix,p=page)=>call('GET',root+(suffix?'/'+suffix:''),undefined,200,undefined,p),saved=()=>get('minutes');
async function login(p,actor){await p.goto(base+'/login');await p.locator('#login-username').fill(actor);await p.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/meetings');}
async function persist(){await writeFile(output+'/report.json',JSON.stringify(report,null,2));}
async function check(name,details={}){report.checks.push({name,...details});console.log('PASS '+name);await persist();}
async function intent(current){await call('POST',root+'/minutes/editor-intents',{expectedRevisionToken:current.observation.revisionToken});}
async function mutate(method,suffix,fields,options={}){const current=await saved();await intent(current);return call(method,root+'/minutes/'+suffix,{expectedRevisionToken:current.observation.revisionToken,...fields},options.expected??200,options.key);}
async function generate(scope={scope:'FULL'}){const current=await get('minutes/generation-inputs/current');assert.equal(current.eligible,true,JSON.stringify(current));const body={expectedRevisionToken:current.observation.revisionToken,...scope},key=randomUUID();const ack=await call('POST',root+'/minutes/generations',body,202,key);const replay=await call('POST',root+'/minutes/generations',body,202,key);assert.equal(replay.replayed,true);assert.deepEqual(replay.result,ack.result);await expect.poll(async()=>{const state=(await get('minutes/generation-tasks/'+ack.result.taskId)).task.state;if(['FAILED','STALE','CANCELLED','MANUAL_REQUIRED'].includes(state))throw Error('Generation '+state);return state;},{timeout:150000,intervals:[1000,2000]}).toBe('SUCCEEDED');const all=await get('minutes/candidates?state=PENDING&limit=20');const candidate=all.items.find(x=>x.inputId===ack.result.inputId);assert(candidate,JSON.stringify(all));return get('minutes/candidates/'+candidate.candidateId);}
async function adopt(candidate){return mutate('POST','candidates/'+candidate.candidate.candidateId+'/adoptions',{inputId:candidate.candidate.inputId,inputFingerprint:candidate.candidate.inputFingerprint});}
function shape(current){return Object.fromEntries(Object.entries(current.elements).map(([category,items])=>[category,items.map(x=>({body:x.body,action:x.action?{owner:x.action.owner_enterprise_id,display:x.action.owner_display,due:x.action.due_on}:null}))]));}
async function showBody(){await page.goto(base+'/meeting/'+meeting+'/minutes');await expect(page.locator('.am-layout')).toBeVisible();await page.locator('.minutes').getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.am-saved-body')).toBeVisible();}
async function geometry(p,selectors){return p.evaluate(selectors=>Object.fromEntries(Object.entries(selectors).map(([key,selector])=>{const e=document.querySelector(selector);if(!e)return[key,null];const s=getComputedStyle(e),r=e.getBoundingClientRect();return[key,{x:r.x,y:r.y,width:r.width,height:r.height,fontSize:s.fontSize,fontWeight:s.fontWeight,color:s.color,background:s.backgroundColor,borderRadius:s.borderRadius}];})),selectors);}
try{
 await login(page,ownerId);const reference=await context.newPage();await reference.goto('file://'+resolve('designDocs/智能会议助手1021版.html')+'#/meeting/MOCK-20261020-OWNED-4-1/minutes');await expect(reference.locator('.am-layout')).toBeVisible();
 report.geometry=[];
 for(const [width,height] of [[1440,900],[1024,768]]){
  for(const p of [page,reference])await p.setViewportSize({width,height});await page.goto(base+'/meeting/'+meeting+'/minutes');await expect(page.locator('.am-layout')).toBeVisible();await reference.getByRole('button',{name:'核心要素',exact:true}).click();await page.evaluate(()=>document.fonts.ready);await reference.evaluate(()=>document.fonts.ready);
  const expected=await geometry(reference,{workspace:'.am-workbench',navigation:'.am-toc',content:'.am-section-list',heading:'.ms-t',paragraph:'.mp-text'}),actual=await geometry(page,{workspace:'.am-workbench',navigation:'.core-navigation',content:'.core-content',heading:'.ms-t',paragraph:'.mp-text'});
  report.geometry.push({width,state:'core',expected,actual});
  // Data changes paragraph heights, but typography and the prototype workbench frame remain fixed.
  for(const key of ['workspace','heading','paragraph']){assert(actual[key]&&expected[key],key);assert.equal(actual[key].fontSize,expected[key].fontSize,key+' font');assert.equal(actual[key].color,expected[key].color,key+' color');}
  assert(Math.abs(actual.workspace.x-expected.workspace.x)<=1);assert(Math.abs(actual.workspace.width-expected.workspace.width)<=1);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.screenshot({path:output+`/actual-core-${width}.png`});await reference.screenshot({path:output+`/prototype-core-${width}.png`});
  for(const p of [page,reference])await p.getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.getByLabel('纪要模板',{exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:'查看正文来源与审阅',exact:true})).toBeEnabled();
  const bodySelectors={toolbar:'.am-document-toolbar',heading:'.am-document-heading',body:'.am-saved-body'};report.geometry.push({width,state:'body',expected:await geometry(reference,bodySelectors),actual:await geometry(page,bodySelectors)});
  const actions=await page.locator('.am-document-actions').innerText();assert(actions.indexOf('复制全文')<actions.indexOf('下载'));assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:output+`/actual-body-${width}.png`});await reference.screenshot({path:output+`/prototype-body-${width}.png`});
  await check('prototype-frame-typography-toolbar-order-and-no-overflow-'+width);
 }
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(r=>r.status>=500),[],'Unexpected application server error');report.pass=true;await persist();
}catch(error){report.pass=false;report.failure=String(error);await persist();await writeFile(output+'/failure-page.txt',await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});throw error;}
finally{await browser.close();}
