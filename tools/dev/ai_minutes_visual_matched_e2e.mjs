/** Real fixed-harness CAS, real imports and persisted minutes; no business SQL. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {randomUUID,createHash} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:45000});
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const ownerId=process.env.AMA_MINUTES_E2E_OWNER??'dev-owner',memberId=process.env.AMA_MINUTES_E2E_MEMBER??'dev-member',memberName=process.env.AMA_MINUTES_E2E_MEMBER_NAME??'开发参会人';
const base='http://127.0.0.1:5174',output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-repair-2026-11-05/visual-matched');
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

async function mutate(suffix,fields){const current=await saved();await call('POST',root()+'/minutes/editor-intents',{expectedRevisionToken:current.observation.revisionToken});return call('PUT',root()+'/minutes/'+suffix,{expectedRevisionToken:current.observation.revisionToken,...fields});}
async function generateAndAdopt(){
 const current=await get('minutes/generation-inputs/current');assert.equal(current.eligible,true);
 const ack=await call('POST',root()+'/minutes/generations',{expectedRevisionToken:current.observation.revisionToken,scope:'FULL'},202);
 await expect.poll(async()=>(await get('tasks?kind=MINUTES_GENERATE_V1&limit=50')).items.find(x=>x.taskId===ack.result.taskId)?.state,{timeout:180000,intervals:[2000]}).toBe('SUCCEEDED');
 const candidate=(await get('minutes/candidates?state=PENDING&limit=50')).items.find(x=>x.inputId===ack.result.inputId);assert(candidate);
 const latest=await saved();await call('POST',root()+'/minutes/editor-intents',{expectedRevisionToken:latest.observation.revisionToken});
 await call('POST',root()+'/minutes/candidates/'+candidate.candidateId+'/adoptions',{expectedRevisionToken:latest.observation.revisionToken,inputId:candidate.inputId,inputFingerprint:candidate.inputFingerprint});
}
async function geometry(p,selector){return p.locator(selector).first().evaluate(e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return{x:r.x,y:r.y,width:r.width,height:r.height,font:s.fontFamily,fontSize:s.fontSize,fontWeight:s.fontWeight,lineHeight:s.lineHeight,color:s.color,background:s.backgroundColor,padding:s.padding,borderRadius:s.borderRadius};});}
try{
 report.prototypeSha256=createHash('sha256').update(await readFile('designDocs/智能会议助手1021版.html')).digest('hex');assert.equal(report.prototypeSha256,'ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867');
 await login(page,ownerId);
 const reference=await context.newPage();await reference.goto('file://'+resolve('designDocs/智能会议助手1021版.html')+'#/meeting/MOCK-20261020-OWNED-4-1/minutes');await expect(reference.locator('.am-layout')).toBeVisible();
 const groups=await reference.locator('.am-section-list .min-sec').evaluateAll(nodes=>nodes.map(e=>({title:e.querySelector('.ms-t')?.textContent,paragraphs:[...e.querySelectorAll('.mp-text')].map(x=>x.textContent),actions:[...e.querySelectorAll('.am-action-fields')].map(x=>x.textContent)})));
 const categories=['OVERVIEW','DISCUSSION','CONCLUSION','ACTION','OPEN_QUESTION','RISK'];
 const desired=Object.fromEntries(categories.map((category,index)=>[category,groups[index].paragraphs]));
 await reference.getByRole('button',{name:'会议纪要',exact:true}).click();const title=await reference.locator('.am-document-heading h2').innerText(),body=await reference.locator('.am-saved-body').textContent();assert(body);
 report.reference={title,body,groups};await persist();
 if(!report.meetingId){
  // Each physical source remains a real imported paragraph. Manual edits below set the
  // reference wording; evidence remains present and unreviewed, never forged as verified.
  const one=t=>t.replaceAll('。','；');
  const text=[one(desired.OVERVIEW[0]+' '+desired.DISCUSSION[0])+'。',one(desired.DISCUSSION[1])+'。',desired.DISCUSSION[2],desired.CONCLUSION[0],...desired.ACTION.map(t=>'行动：'+one(t)+'。'),'待确认：'+one(desired.OPEN_QUESTION[0])+'。','风险：'+one(desired.RISK[0])+'。'].join('\n');
  const created=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await created).status(),201);await page.waitForURL(/\/meetings\?import=[0-9a-f-]{36}&fresh=1$/);report.meetingId=new URL(page.url()).searchParams.get('import');await persist();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'原型同内容视觉验证.txt',mimeType:'text/plain',buffer:Buffer.from(text)});await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();await dialog.getByRole('button',{name:'开始处理',exact:true}).click();
  await expect.poll(async()=>(await get('tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20')).items[0]?.state,{timeout:150000,intervals:[1500]}).toBe('SUCCEEDED');
  const raw=(await get('transcript/history?limit=20')).items.find(x=>x.kind==='RAW');assert(raw);const detail=await get('transcript/revisions/'+raw.id);await call('POST',root()+'/transcript/restorations',{expectedRevisionToken:detail.observation.revisionToken,sourceRevisionId:raw.id});
  await page.goto(base+'/meeting/'+report.meetingId+'/transcript');await page.getByRole('button',{name:'确认转写并生成纪要',exact:true}).click();await page.getByRole('dialog',{name:'确认转写并生成纪要',exact:true}).getByRole('button',{name:'确认并生成',exact:true}).click();
  await expect.poll(async()=>(await get('tasks?kind=MINUTES_GENERATE_V1&limit=20')).items[0]?.state,{timeout:180000,intervals:[2000]}).toBe('SUCCEEDED');
 }
 await page.goto(base+'/meetings');
 let current=await saved(),elementsChanged=false;
 for(const category of categories){assert.equal(current.elements[category].length,desired[category].length,category+' fixture count');for(let index=0;index<desired[category].length;index++){
  current=await saved();const item=current.elements[category][index],text=desired[category][index];if(item.body===text)continue;elementsChanged=true;
  await mutate('elements/'+item.id,{sourceSetId:current.setId,body:text,evidenceAnchors:current.evidence.filter(e=>e.element_id===item.id).map(e=>({evidenceId:e.id,startToken:'0',endToken:String(Array.from(text).length)}))});
 }}
 if(elementsChanged||current.observation.needsGeneration)await generateAndAdopt();current=await saved();
 if(current.body.title!==title||current.body.body!==body)await mutate('body',{sourceRevisionId:current.body.id,title,body,evidenceAnchors:current.evidence.filter(e=>e.minutes_revision_id===current.body.id).map(e=>({evidenceId:e.id,startToken:'0',endToken:String(Array.from(body).length)}))});
 current=await saved();assert.equal(current.body.body,body);assert.equal(current.body.title,title);for(const category of categories)assert.deepEqual(current.elements[category].map(x=>x.body),desired[category]);check('real-import-and-persisted-reference-title-body-and-core-paragraphs');
 report.geometry=[];
 for(const [width,height] of [[1440,900],[1024,768]]){
  for(const p of [page,reference])await p.setViewportSize({width,height});await page.goto(base+'/meeting/'+report.meetingId+'/minutes');await expect(page.locator('.am-layout')).toBeVisible();await expect(page.locator('#minutes-category-OVERVIEW .am-source-tag').first()).toHaveText('转写文件');await expect.poll(async()=>page.locator('.am-source-tag').evaluateAll(nodes=>nodes.filter(e=>e.textContent.includes('来源与审阅')).length),{timeout:90000}).toBe(0);await reference.getByRole('button',{name:'核心要素',exact:true}).click();
  for(const p of [page,reference]){await p.evaluate(()=>document.fonts.ready);await p.locator('.am-section-list').evaluate(e=>e.scrollTo({top:0,behavior:'instant'}));}
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.screenshot({path:output+`/actual-core-${width}.png`});await reference.screenshot({path:output+`/prototype-core-${width}.png`});
  for(const [name,actualSelector,referenceSelector] of [['navigation','.am-toc','.am-toc'],['content','.am-section-list','.am-section-list'],['sensitive-heading','.sensitive-section .ms-t','#sec-am-sensitive .ms-t'],['workspace','.am-workbench','.am-workbench'],['overview','#minutes-category-OVERVIEW','#sec-am-overview'],['overview-text','#minutes-category-OVERVIEW .mp-text','#sec-am-overview .mp-text']]){
   const actual=await geometry(page,actualSelector),expected=await geometry(reference,referenceSelector);report.geometry.push({width,name,actual,expected});
  }
  assert(current.sensitive.length>0,'The real imported fixture must contain a sensitive excerpt');
  await expect(page.locator('.sensitive-section .sensitive-excerpt').first()).toBeAttached();
  for(const p of [page,reference])await p.locator('.am-toc').getByRole('button',{name:'敏感信息',exact:true}).click();
  await page.locator('.sensitive-section').evaluate(e=>e.scrollIntoView({block:'start',behavior:'instant'}));await reference.locator('#sec-am-sensitive').evaluate(e=>e.scrollIntoView({block:'start',behavior:'instant'}));
  await page.screenshot({path:output+`/actual-sensitive-${width}.png`});await reference.screenshot({path:output+`/prototype-sensitive-${width}.png`});
  for(const [name,actualSelector,referenceSelector] of [['sensitive-row','.sensitive-section .am-sensitive-para','#sec-am-sensitive .am-sensitive-para'],['sensitive-copy','.sensitive-section .am-sensitive-copy','#sec-am-sensitive .am-sensitive-copy'],['sensitive-category','.sensitive-section .tag.outline','#sec-am-sensitive .tag.outline']]){
   report.geometry.push({width,name,actual:await geometry(page,actualSelector+':first-of-type'),expected:await geometry(reference,referenceSelector+':first-of-type')});
  }
  for(const p of [page,reference])await p.getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.am-saved-body')).toHaveText(body);await expect(page.getByLabel('纪要模板',{exact:true})).toBeEnabled();
  for(const selector of ['.am-document-toolbar','.am-document-heading','.am-saved-body','.am-body-footer'])report.geometry.push({width,name:selector,actual:await geometry(page,selector),expected:await geometry(reference,selector)});
  await page.screenshot({path:output+`/actual-body-${width}.png`});await reference.screenshot({path:output+`/prototype-body-${width}.png`});
  await page.locator('.am-document-content').screenshot({path:output+`/actual-document-content-${width}.png`});await reference.locator('.am-document-content').screenshot({path:output+`/prototype-document-content-${width}.png`});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));check('matched-content-two-view-evidence-'+width);await persist();
 }
 for(const row of report.geometry){
  const {actual,expected,name,width}=row;
  for(const property of ['font','fontSize','fontWeight','lineHeight','color','background','padding','borderRadius'])assert.equal(actual[property],expected[property],`${width} ${name} ${property}`);
  assert(Math.abs(actual.x-expected.x)<=1,`${width} ${name} horizontal position`);
  // The requirement adds a persistent 34px editing affordance beyond the prototype's paragraph padding.
  const reserved=name==='overview-text'?34:0;
  assert(Math.abs(actual.width-(expected.width-reserved))<=1,`${width} ${name} width`);
  if(['navigation','overview-text','.am-document-toolbar','.am-document-heading','.am-saved-body'].includes(name))assert(Math.abs(actual.height-expected.height)<=1,`${width} ${name} height`);
 }
 for(const width of [1440,1024]){
  const byName=Object.fromEntries(report.geometry.filter(x=>x.width===width).map(x=>[x.name,x]));
  for(const [name,parent] of [['navigation','workspace'],['content','workspace'],['overview','content'],['overview-text','overview'],['.am-document-heading','.am-document-toolbar'],['.am-saved-body','.am-document-heading']]){
   const a=byName[name],p=byName[parent];assert(Math.abs((a.actual.y-p.actual.y)-(a.expected.y-p.expected.y))<=1,`${width} ${name} relative vertical spacing`);
  }
 }
 check('prototype-computed-typography-colors-horizontal-geometry-and-relative-spacing-match-at-both-widths');
 report.intentionalDifferences=['真实公共导航和会议元数据占据的纵向空间','需求要求的条目编辑入口保留34px操作宽度','正文版本、修改人时间、模板应用状态及版本比较入口','结论行动的待核对状态、真实来源标签','每条真实证据均提供来源入口，原型演示的部分段落无引用','行动负责人尚未由真实企业身份确认时显示未明确','待确认与分歧类型标签以及无内容提示','两类敏感业务文案，不添加原型个人信息类别','最终确认条件真实未满足时入口状态不伪造'];
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(r=>r.status>=500),[]);report.pass=true;await persist();
}catch(error){report.pass=false;report.failure=String(error);await persist();await writeFile(output+'/failure-page.txt',await page.locator('body').innerText().catch(()=>''));await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});throw error;}
finally{await browser.close();}
