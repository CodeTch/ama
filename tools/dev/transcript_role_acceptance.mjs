import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const output=resolve(process.argv[2]),base='http://127.0.0.1:5174';
await mkdir(output,{recursive:true});
let state={checks:[]};
try{state=JSON.parse(await readFile(output+'/role-state.json','utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
const browser=await chromium.launch(),errors=[];
const save=()=>writeFile(output+'/role-state.json',JSON.stringify(state,null,2));
const pass=async label=>{if(!state.checks.includes(label))state.checks.push(label);await save();console.log('PASS',label);};
async function login(user){
 const page=await browser.newPage({viewport:{width:1440,height:920},locale:'zh-CN'});page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base+'/login');await page.locator('#login-username').fill(user);await page.locator('#login-password').fill('dev-password');
 await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');await page.goto(base+'/api/v1/session');return page;
}
async function api(page,suffix,method='GET',body,key=crypto.randomUUID()){
 return page.evaluate(async({url,method,body,key})=>{
  const r=await fetch(url,{method,...(body?{body:JSON.stringify(body),headers:{'Content-Type':'application/json','Idempotency-Key':key,'X-CSRF-Token':(await(await fetch('/api/v1/session')).json()).csrfToken}}:{})});
  return {status:r.status,data:await r.json().catch(()=>null)};
 },{url:'/api/v1/meetings/'+state.meeting+suffix,method,body,key});
}
async function read(page,suffix){const r=await api(page,suffix);assert.equal(r.status,200,suffix+' '+JSON.stringify(r.data));return r.data;}
async function roster(page,ids){
 const info=await read(page,'');
 const put=await api(page,'/info','PUT',{expectedRevisionToken:info.revisionToken,name:'转写双账号回归',category:'project',summary:'用于验证版本选择和确认续办。',occurredOn:null,rosterDraftIds:ids,externalRosterNames:[]});assert.equal(put.status,200,JSON.stringify(put.data));
 const grant=await api(page,'/roster-confirmations','POST',{expectedRevisionToken:put.data.revisionToken});assert.equal(grant.status,200,JSON.stringify(grant.data));
}
const transcriptUrl=()=>base+'/meeting/'+state.meeting+'/transcript';
try{
 let owner=await login('dev-owner');
 if(process.argv[3]==='--resume-generation'){
  assert(state.humanMinutes&&state.v2);
  await expect.poll(async()=>(await read(owner,'/tasks?kind=MINUTES_GENERATE_V1&limit=50')).items.filter(t=>t.state==='SUCCEEDED').length,{timeout:150000,intervals:[2000]}).toBe(2);
  const saved=await read(owner,'/minutes');assert.equal(saved.body.id,state.humanMinutes);assert.equal(saved.body.title,'人工核对后的纪要标题');
  const list=await read(owner,'/minutes/candidates?state=PENDING&limit=20');assert.equal(list.items.length,1);
  await pass('重启后原确认意图自动续办，第二次生成保留既有人工纪要，只有一条待采用候选');
  assert.deepEqual(errors,[]);state.pass=true;await save();
 }else{
 const member=await login('dev-member');
 if(!state.meeting){
  await owner.goto(base+'/meetings');
  const created=owner.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/v1/meetings');
  await owner.getByRole('button',{name:'导入会议',exact:true}).click();const response=await created;assert.equal(response.status(),201);
  const dialog=owner.getByRole('dialog',{name:'导入会议',exact:true});await expect(dialog).toBeVisible();
  state.meeting=new URL(owner.url()).searchParams.get('import')??(owner.url().match(/meeting\/([0-9a-f-]{36})/)??[])[1];
  if(!state.meeting)state.meeting=(await response.json()).id;assert.match(state.meeting,/^[0-9a-f-]{36}$/);await save();
 }
 if(!state.uploaded){
  let dialog=owner.getByRole('dialog',{name:'导入会议',exact:true});if(!await dialog.isVisible())await owner.goto(transcriptUrl().replace('/transcript','?import=1'));
  const input=dialog.getByLabel('选择会议文件',{exact:true});await expect(input).toBeEnabled({timeout:30000});await input.setInputFiles({name:'双账号版本验证.txt',mimeType:'text/plain',buffer:Buffer.from('风险核对会议开始。\n决定复核业务数据。\n行动项由负责人落实。')});
  const start=dialog.getByRole('button',{name:'开始处理',exact:true});try{await expect(start).toBeEnabled({timeout:30000});}catch(e){console.error(await dialog.innerText());throw e;}const submitted=owner.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/transcript-imports'));await start.click();const response=await submitted;assert.equal(response.status(),202);state.uploaded=true;await pass('真实浏览器上传并启动离线处理');
 }
 await expect.poll(async()=>(await read(owner,'/transcript')).saved?.id,{timeout:150000,intervals:[1500,2000]}).toBeTruthy();
 if(!state.v1){
  await roster(owner,['dev-member']);await member.goto(transcriptUrl());
  await expect(member.locator('.transcript-section')).toContainText('风险',{timeout:30000});
  const pre=await read(member,'/transcript');assert.equal(pre.confirmation,null);assert.equal(pre.capabilities.canSave,false);assert(pre.saved?.id);await pass('参会人首次确认前读取增强稿和未确认状态');
  await owner.goto(transcriptUrl());await expect(owner.locator('.transcript-section')).toContainText('风险');
  const search=owner.getByPlaceholder('搜索转写');await search.fill('业务数据');await expect(owner.locator('.transcript-paragraphs mark')).toBeVisible();await search.fill('');
  const btn=owner.getByRole('button',{name:'确认转写并生成纪要',exact:true});await expect(btn).toBeEnabled({timeout:30000});await btn.click();
  const confirmed=owner.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/transcript/confirmations'));
  await owner.getByRole('dialog',{name:'确认转写并生成纪要'}).getByRole('button',{name:'确认并生成',exact:true}).click();
  const response=await confirmed;assert.equal(response.status(),200);state.v1=pre.saved.id;state.confirmBody=response.request().postDataJSON();state.confirmKey=response.request().headers()['idempotency-key'];await owner.context().close();await pass('负责人搜索高亮、确认后立即关闭会话');
  owner=await login('dev-owner');
 }
 const current=()=>read(owner,'/tasks?kind=MINUTES_GENERATE_V1&limit=50');
 await expect.poll(async()=>(await current()).items[0]?.state,{timeout:160000,intervals:[1500,2500]}).toBe('SUCCEEDED');
 assert.equal((await current()).items.length,1);const replay=await api(owner,'/transcript/confirmations','POST',state.confirmBody,state.confirmKey);assert.equal(replay.status,200);assert.equal(replay.data.replayed,true);await pass('关闭浏览器后仍完成纪要生成，重放确认只有一个任务');
 if(!state.humanMinutes&&!state.v2){
  const minutes=await read(owner,'/minutes');
  const intent=await api(owner,'/minutes/editor-intents','POST',{expectedRevisionToken:minutes.observation.revisionToken});assert.equal(intent.status,200);
  const fresh=await read(owner,'/minutes');
  const body=await api(owner,'/minutes/body','PUT',{expectedRevisionToken:fresh.observation.revisionToken,sourceRevisionId:fresh.body.id,title:'人工核对后的纪要标题',body:fresh.body.body,evidenceAnchors:fresh.evidence.filter(item=>item.minutes_revision_id===fresh.body.id).map(item=>{
   const anchor=JSON.parse(item.target_anchor);return {evidenceId:item.id,startToken:String(anchor.start),endToken:String(anchor.end)};
  })});assert.equal(body.status,200,JSON.stringify(body.data));
  state.humanMinutes=(await read(owner,'/minutes')).body.id;await pass('确认版本仍有效时完成纪要人工编辑');
 }
 if(!state.v2){
  await owner.goto(transcriptUrl());await owner.getByRole('button',{name:'编辑第 1 段',exact:true}).click();const edit=owner.getByRole('dialog',{name:'编辑转写片段',exact:true});
  await edit.getByRole('textbox').fill('负责人尚未确认的第二版草稿。');await edit.getByRole('button',{name:'保存',exact:true}).click();await expect(edit).toBeHidden({timeout:30000});
  const draft=await read(owner,'/transcript');assert.notEqual(draft.saved.id,state.v1);state.v2=draft.saved.id;await pass('负责人改正文形成第二版工作稿');
 }
 await member.goto(transcriptUrl());await expect(member.locator('.transcript-section')).toContainText('风险',{timeout:30000});await expect(member.locator('.transcript-section')).not.toContainText('第二版草稿');
 const confirmed=await read(member,'/transcript');assert.equal(confirmed.saved.id,state.v1);assert.equal(confirmed.confirmation.revisionId,state.v1);
 assert.equal((await read(member,'/workspace')).saved.transcriptRevisionId,state.v1);
 await expect(member.getByRole('button',{name:'确认转写并生成纪要',exact:true})).toHaveCount(0);
 assert([403,404].includes((await api(member,'/transcript/history?limit=20')).status));
 await pass('参会人导航、正文、确认指向同一 V1，历史与编辑受限');
 await member.screenshot({path:output+'/participant.png',fullPage:true});
 await roster(owner,[]);assert([403,404].includes((await api(member,'/transcript')).status));await pass('撤销参会权后拒绝读取转写');
 if(state.humanMinutes){
  await owner.goto(transcriptUrl());const confirm=owner.getByRole('button',{name:'确认转写并生成纪要',exact:true});
  await expect(confirm).toBeEnabled({timeout:30000});await confirm.click();await owner.getByRole('dialog',{name:'确认转写并生成纪要'}).getByRole('button',{name:'确认并生成',exact:true}).click();await owner.waitForURL('**/minutes');
  await expect.poll(async()=>(await current()).items.filter(task=>task.state==='SUCCEEDED').length,{timeout:150000,intervals:[2000]}).toBe(2);
  assert((await read(owner,'/minutes/candidates?state=PENDING&limit=20')).items.length>0);
  const saved=await read(owner,'/minutes');assert.equal(saved.body.id,state.humanMinutes);assert.equal(saved.body.title,'人工核对后的纪要标题');await pass('二次确认只创建一个新候选并保留既有人工纪要');
 }
 assert.deepEqual(errors,[]);state.pass=true;await save();
 }
} catch(e){console.error(e);process.exitCode=1;}finally{await browser.close();}
