/** Fixed-harness CAS -> private CRUD -> genuine TXT/RAW -> v2 generation; no business SQL. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000}),base='http://127.0.0.1:5174',output=process.argv[2];
assert(output?.startsWith('.tools/local-app/personal-templates/'));await mkdir(output,{recursive:false,mode:0o700});
const state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
for(const kind of ['cas','knowledge','model'])assert.equal(state.integrationOrigins[kind],'http://127.0.0.1:8792');
const source=()=>execFileSync('python3',['-c','import sys;sys.path.insert(0,"tools/dev");import local_app;print(local_app.source_signature())'],{encoding:'utf8'}).trim();
const signature=source(),checks=[],requests=[],errors=[],run=randomUUID();let meetingId,primary,taskId,inputId;
const browser=await chromium.launch(),context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',viewport:{width:1440,height:900}}),page=await context.newPage();
page.setDefaultTimeout(30000);page.on('pageerror',error=>errors.push(error.message));
const route=suffix=>`/api/v1/meetings/${meetingId}/${suffix}`;
function check(name,facts={}){checks.push({name,...facts});console.log('PASS '+name);}
async function login(p,actor){
 const entry=await p.request.get(base+'/auth/cas/login',{maxRedirects:0});assert.equal(entry.status(),302);assert.equal(new URL(entry.headers().location).origin,'http://127.0.0.1:8792');
 await p.goto(base+'/login');await p.locator('#login-username').fill(actor);await p.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL(base+'/meetings');await expect(p.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
}
async function call(method,path,body,expected=200,key=randomUUID(),p=page){
 const result=await p.evaluate(async({method,path,body,key})=>{
  const session=await(await fetch('/api/v1/session')).json(),headers=method==='GET'?{}:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':key};
  const r=await fetch(path,{method,headers,...(body===undefined?{}:{body:JSON.stringify(body)})});const text=await r.text();let value;try{value=JSON.parse(text);}catch{value=text;}
  return{status:r.status,value,cacheControl:r.headers.get('cache-control')};
 },{method,path,body,key});
 requests.push({actor:p===page?'owner':'foreign',method,path,status:result.status});
 if(result.status!==expected)throw Object.assign(new Error(`${method} ${path}: ${result.status} ${JSON.stringify(result.value)}`),{httpStatus:result.status});return result.value;
}
// Metadata can change between discovery and locked capture while the worker commits.
// Retain every 503 in the report and retry only the same read, never a mutation.
async function get(path){
 for(let attempt=0;;attempt++){
  try{return await call('GET',path);}catch(error){if(error.httpStatus!==503||attempt>=4)throw error;await delay(250*(attempt+1));}
 }
}
try{
 await login(page,'dev-owner');check('real-cas-session-through-fixed-8792');
 const createKey=randomUUID(),createBody={name:`个人模板 ${run}`,themes:['决定😀','后续行动']};
 const original=await call('POST','/api/v1/templates',createBody,201,createKey);primary=original;
 assert.deepEqual(await call('POST','/api/v1/templates',createBody,201,createKey),original);check('private-create-and-exact-original-key-replay',{templateId:primary.id});
 for(const themes of [[],Array(9).fill('超限')])await call('POST','/api/v1/templates',{name:`非法 ${randomUUID()}`,themes},400);
 await call('POST','/api/v1/templates',{name:' '+createBody.name+' ',themes:['别的']},409);check('zero-nine-themes-and-normalized-duplicate-rejected');
 const themes=['会议概览','主要讨论😀','已定事项','待办负责人','时间安排','风险','待确认','其他备注'];
 const updateBody={expectedRevisionToken:primary.revisionToken,name:`八主题 ${run}`,themes},updateKey=randomUUID();
 primary=await call('PUT',`/api/v1/templates/${primary.id}`,updateBody,200,updateKey);assert.equal(primary.themes.length,8);assert.notEqual(primary.revisionId,original.revisionId);
 assert.deepEqual(await call('PUT',`/api/v1/templates/${primary.id}`,updateBody,200,updateKey),primary);await call('PUT',`/api/v1/templates/${primary.id}`,updateBody,409);
 assert.deepEqual(await call('POST','/api/v1/templates',createBody,201,createKey),original);check('immutable-eight-theme-revision-and-stale-edit-conflict');
 const foreign=await browser.newContext(),other=await foreign.newPage();try{
  await login(other,'dev-member');await call('GET',`/api/v1/templates/${primary.id}`,undefined,403,undefined,other);
  await call('PUT',`/api/v1/templates/${primary.id}`,{expectedRevisionToken:primary.revisionToken,name:'外部编辑',themes:['无权限']},403,undefined,other);
  assert(!(await call('GET','/api/v1/templates',undefined,200,undefined,other)).items.some(item=>item.id===primary.id));
 }finally{await foreign.close();}check('independent-foreign-cas-identity-cannot-read-or-edit-personal-template');
 const create=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');await page.getByRole('button',{name:'导入会议',exact:true}).click();assert.equal((await create).status(),201);await page.waitForURL(/\/meetings\?import=[0-9a-f-]{36}&fresh=1$/);meetingId=new URL(page.url()).searchParams.get('import');
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await dialog.getByLabel('选择会议文件',{exact:true}).setInputFiles({name:'个人模板真实来源.txt',mimeType:'text/plain',buffer:Buffer.from('第一份开场，讨论项目计划。\n已决定继续实施，由开发参会人负责，截止日期为2026年11月1日。\n核对风险和仍待确认的问题。\n')});await expect(dialog.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();
 const intake=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===route('transcript-imports'));await dialog.getByRole('button',{name:'开始处理',exact:true}).click();assert.equal((await intake).status(),202);await page.waitForURL(base+`/meeting/${meetingId}/processing`);
 await expect.poll(async()=> (await get(route('tasks?kind=TRANSCRIPT_IMPORT_V1&limit=20'))).items[0]?.state,{timeout:90000,intervals:[1000,2000]}).toBe('SUCCEEDED');
 const history=await get(route('transcript/history?limit=20')),rawId=history.items.find(item=>item.kind==='RAW').id,raw=await get(route(`transcript/revisions/${rawId}`));
 await call('POST',route('transcript/restorations'),{expectedRevisionToken:raw.observation.revisionToken,sourceRevisionId:rawId});const working=await get(route('transcript'));
 await call('POST',route('transcript/confirmations'),{expectedRevisionToken:working.observation.revisionToken,savedRevisionId:working.saved.id});check('genuine-txt-worker-raw-working-and-current-confirmation',{meetingId,rawId});
 const catalog=await get(route('minutes/templates')),choice=catalog.items.find(item=>item.revisionId===primary.revisionId);assert(choice?.selectable,'Installed archive must explicitly permit personal generation');assert.deepEqual(choice.themes,themes);
 let current=await get(route('minutes/generation-inputs/current'));assert.equal(current.saved.templateRevisionId,null);
 await call('POST',route('minutes/initial-settings'),{expectedRevisionToken:current.observation.revisionToken,templateRevisionId:primary.revisionId});current=await get(route('minutes/generation-inputs/current'));assert.equal(current.saved.templateRevisionId,primary.revisionId);assert.equal(current.eligible,true,JSON.stringify(current));check('personal-template-initial-selection-uses-installed-v2-capability');
 const generationBody={expectedRevisionToken:current.observation.revisionToken,scope:'FULL'},generationKey=randomUUID(),accepted=await call('POST',route('minutes/generations'),generationBody,202,generationKey);
 ({taskId,inputId}=accepted.result);const replay=await call('POST',route('minutes/generations'),generationBody,202,generationKey);assert.deepEqual(replay.result,accepted.result);
 let task;await expect.poll(async()=>{task=await get(route(`minutes/generation-tasks/${taskId}`));return task.task.state;},{timeout:90000,intervals:[1000,2000]}).toBe('SUCCEEDED');
 const input=await get(route(`minutes/generation-inputs/${inputId}`));assert.equal(input.provenanceVerified,true);assert.equal(input.input.template_revision_id,primary.revisionId);
 let minutes=await get(route('minutes'));const candidates=await get(route('minutes/candidates'));
 if(!minutes.body){const candidate=candidates.items[0];assert(candidate);await call('POST',route(`minutes/candidates/${candidate.candidateId}/adoptions`),{expectedRevisionToken:minutes.observation.revisionToken,inputId,inputFingerprint:candidate.inputFingerprint});minutes=await get(route('minutes'));}
 assert(minutes.body);for(const theme of themes)assert(minutes.body.body.includes(theme));check('fixed-harness-v2-generation-and-exact-saved-chapter-layout',{taskId,inputId,bodyId:minutes.body.id});
 const savedBody=minutes.body;
 primary=await call('DELETE',`/api/v1/templates/${primary.id}`,{expectedRevisionToken:primary.revisionToken});assert.equal(primary.deleted,true);
 assert(!(await get('/api/v1/templates')).items.some(item=>item.id===primary.id));assert.deepEqual((await get(route('minutes'))).body,savedBody);
 assert.deepEqual(await call('POST','/api/v1/templates',createBody,201,createKey),original);assert(!(await get('/api/v1/templates')).items.some(item=>item.id===primary.id));check('template-delete-retains-saved-minutes-and-original-create-replay-does-not-reactivate');
 await page.goto(base+`/meeting/${meetingId}/minutes`);await page.getByRole('button',{name:'会议纪要',exact:true}).click();await expect(page.locator('.minutes-body h3')).toHaveText(savedBody.title);
 await page.getByRole('button',{name:'新增模板',exact:true}).click();const personal=page.getByRole('dialog',{name:'新增总结模板',exact:true});await expect(personal).toBeVisible();
 for(const [width,height] of [[1440,900],[1024,768]]){await page.setViewportSize({width,height});await page.screenshot({path:`${output}/personal-dialog-${width}.png`});}
 await personal.getByRole('button',{name:'取消',exact:true}).click();check('native-template-dialog-and-saved-output-two-desktop-viewports');
 let info=await get(`/api/v1/meetings/${meetingId}`);
 info=await call('PUT',route('info'),{expectedRevisionToken:info.revisionToken,name:'个人模板全链路联调',category:'project',summary:'八个个人主题的保存、删除后历史保留及最终发布。',occurredOn:'2026-10-31',rosterDraftIds:['dev-member'],externalRosterNames:[]});
 await call('POST',route('roster-confirmations'),{expectedRevisionToken:info.revisionToken});
 minutes=await get(route('minutes'));
 // Every edit copies the complete immutable set, so all element IDs change.
 const actionCount=minutes.elements.ACTION.length;
 for(let index=0;index<actionCount;index++){
  minutes=await get(route('minutes'));const expectedRevisionToken=minutes.observation.revisionToken;
  assert.equal(minutes.elements.ACTION.length,actionCount);const action=minutes.elements.ACTION[index];assert(action);
  await call('POST',route('minutes/editor-intents'),{expectedRevisionToken});
  await call('PUT',route(`minutes/elements/${action.id}/action-fields`),{expectedRevisionToken,sourceSetId:minutes.setId,ownerEnterpriseId:null,ownerDisplay:'开发参会人',dueOn:'2026-11-01'});
 }
 // These explicit decisions exercise synthetic fixture gates; they do not assess model accuracy.
 let readiness=await get(route('final-confirmations/readiness'));
 for(const required of readiness.required){
  if(required.satisfied)continue;
  minutes=await get(route('minutes'));const expectedRevisionToken=minutes.observation.revisionToken;
  await call('POST',route('minutes/editor-intents'),{expectedRevisionToken});
  await call('POST',route('minutes/reviews'),{expectedRevisionToken,target:{kind:required.targetKind,id:required.targetId},reviewKind:required.reviewKind,scope:required.reviewKind==='ACTION_FIELDS'?null:'WHOLE',outcome:required.reviewKind==='ACTION_FIELDS'?'CONFIRMED':'SUPPORTED'});
 }
 readiness=await get(route('final-confirmations/readiness'));assert.equal(readiness.ready,true,JSON.stringify(readiness));
 await call('POST',route('final-confirmations'),{expectedRevisionToken:readiness.observation.revisionToken,optionalReviewIds:[]},201);
 const frozen=await get(route('final-confirmations/current'));assert.equal(frozen.body.body,savedBody.body);check('deleted-template-history-remains-valid-for-immutable-final-confirmation');
 const libraries=await get(route('publication-destinations/libraries')),library=libraries.items[0];assert(library);
 const folders=await get(route(`publication-destinations/libraries/${encodeURIComponent(library.id)}/folders`)),folder=folders.items.find(item=>item.id===library.rootFolderId);assert(folder);
 const publishing=await call('POST',route('publications'),{expectedRevisionToken:frozen.observation.revisionToken,confirmationId:frozen.confirmation.id,providerId:libraries.providerId,libraryId:library.id,folderId:folder.id},202);
 let publication;await expect.poll(async()=>{publication=(await get(route('publications/current'))).publication;return publication?.rawState;},{timeout:90000,intervals:[1000,2000]}).toBe('SUCCEEDED');
 assert.equal(publication.documentViewAvailability,'READY');
 const document=await page.evaluate(async path=>{const r=await fetch(path);return{status:r.status,html:await r.text()};},publication.officialDocumentUrl);
 assert.equal(document.status,200);for(const theme of themes)assert(document.html.includes(theme));check('fixed-harness-v2-publication-preserves-every-saved-personal-chapter',{publicationId:publication.publicationId});
 assert.deepEqual(errors,[]);assert.equal(source(),signature,'No source change during this acceptance case');assert.equal(state.sourceSignature,signature,'Acceptance must use the deployed current source');
 await writeFile(`${output}/report.json`,JSON.stringify({passed:true,scope:'SIMULATED_PROTOCOL_NOT_REAL_MODEL_QUALITY',syntheticReviewDecisions:true,signature,deployedSignature:state.sourceSignature,meetingId,templateId:primary.id,taskId,inputId,checks,requests,errors},null,2));
}catch(error){await page.screenshot({path:`${output}/failure.png`,fullPage:true});await writeFile(`${output}/failure-page.txt`,await page.locator('body').innerText());await writeFile(`${output}/report.json`,JSON.stringify({passed:false,signature,deployedSignature:state.sourceSignature,meetingId,templateId:primary?.id,taskId,inputId,checks,requests,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}
