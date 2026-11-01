/** Read an actual published fixture through CAS, current provider READ and the original page. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {execFileSync,spawnSync} from 'node:child_process';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000}),base='http://127.0.0.1:5174';
const output=process.argv[2],meeting=process.env.AMA_MEETING_ID;
assert(output?.startsWith('.tools/local-app/b-followup/'));assert.match(meeting??'',/^[0-9a-f-]{36}$/);
await mkdir(output,{recursive:false});
const signature=()=>execFileSync('python3',['-c',"import sys;sys.path.insert(0,'tools/dev');import local_app;print(local_app.source_signature())"],{encoding:'utf8'}).trim();
const working=signature(),state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));assert.equal(state.sourceSignature,working);
const harnessOrigin='http://127.0.0.1:8792';assert.equal(state.integrationOrigins?.knowledge,harnessOrigin,'Library qualification requires the fixed harness');const harnessRevision=execFileSync('git',['-C','/home/tanren/ama-integration-harness','rev-parse','HEAD'],{encoding:'utf8'}).trim();
const harnessSourceSha256=execFileSync('python3',['-c',"import pathlib,hashlib;r=pathlib.Path('/home/tanren/ama-integration-harness/backend');d=hashlib.sha256();[(d.update(str(p.relative_to(r)).encode()),d.update(p.read_bytes()))for p in sorted((r/'app').rglob('*.py'))];d.update((r/'run.py').read_bytes());print(d.hexdigest())"],{encoding:'utf8'}).trim();
const prototype=await readFile('designDocs/智能会议助手1021版.html','utf8'),prototypeSha256=createHash('sha256').update(prototype).digest('hex');
assert.equal(prototypeSha256,'ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867');
const browser=await chromium.launch({args:['--force-color-profile=srgb','--disable-gpu','--disable-partial-raster']}),pages=[],checks=[],requests=[],pixels=[],errors=[];
let publication,location,grantBefore=null,grantChanged=false;
async function login(actor){
 const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai',deviceScaleFactor:1,colorScheme:'light',reducedMotion:'reduce'}),page=await context.newPage();pages.push(page);page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base+'/login');await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');return page;
}
async function get(page,path,status=200){const r=await page.evaluate(async path=>{const r=await fetch(path);const text=await r.text();return{status:r.status,value:text?JSON.parse(text):null};},path);requests.push({path,status:r.status});assert.equal(r.status,status,JSON.stringify(r));return r.value;}
function check(name){checks.push(name);console.log('PASS '+name);}
const entry=page=>page.locator(`.file-item[data-meeting-id="${meeting}"]`).getByRole('button');
// Only the launcher's owned local simulator can be altered, with the original grant restored.
function grant(read){const code=`import sys,json,pathlib,importlib.util
sys.path.insert(0,'tools/dev');import local_app
s=json.loads(pathlib.Path('.tools/local-app/state.json').read_text());c=json.loads(pathlib.Path('.tools/local-app/knowledge/provider.json').read_text())
assert c['port']==s['knowledgePort'] and c['store']==str(local_app.RUNTIME/'knowledge/store.sqlite')
spec=importlib.util.spec_from_file_location('provider','tools/providers/controlled_knowledge.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);p=m.Provider(c)
try:
 row=p.db.execute('SELECT can_publish,can_read FROM grant_record WHERE actor=? AND library=? AND folder=?',('dev-member',sys.argv[1],sys.argv[2])).fetchone();assert row is not None
 if sys.argv[3]!='observe':p.administer('dev-member',sys.argv[1],sys.argv[2],bool(int(sys.argv[4])),sys.argv[3]=='1')
 print(json.dumps(list(row)))
finally:p.db.close()
`;return JSON.parse(execFileSync('python3',['-c',code,location.libraryId,location.folderId,read===null?'observe':read?'1':'0',String(grantBefore?.[0]??0)],{encoding:'utf8'}));}
async function complete(page){const more=page.getByRole('button',{name:'继续读取会议成果',exact:true});for(let n=0;n<50&&await more.count();n++){await expect(more).toBeEnabled();await more.click();await expect(page.locator('main.content')).toHaveAttribute('aria-busy','false');}await expect(more).toHaveCount(0);}
async function settle(page){await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo(0,0);return document.fonts.ready;});await page.mouse.move(0,0);await page.waitForTimeout(400);await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));}
async function capture(page,reference,name){await settle(page);await settle(reference);for(const fullPage of [false,true]){const id=name+(fullPage?'-full':''),expected=output+'/'+id+'-reference.png',actual=output+'/'+id+'-actual.png',diff=output+'/'+id+'-diff.png';await reference.screenshot({path:expected,fullPage,caret:'hide'});await page.screenshot({path:actual,fullPage,caret:'hide'});const r=spawnSync('python3',['tools/dev/prototype_pixel_diff.py',expected,actual,diff],{encoding:'utf8'});assert([0,1].includes(r.status),r.stderr);pixels.push({id,...JSON.parse(r.stdout)});}}
try{
 const owner=await login('dev-owner'),member=await login('dev-member'),outsider=await login('dev-viewer'),root=`/api/v1/meetings/${meeting}`;
 publication=(await get(owner,root+'/publications/current')).publication;assert.equal(publication.rawState,'SUCCEEDED');assert.equal(publication.documentViewAvailability,'READY');
 const path=root+'/publications/'+publication.publicationId+'/library-context';location=await get(owner,path);assert.equal(location.documentUrl,publication.officialDocumentUrl);assert.match(location.versionToken,/^[0-9a-f]{64}$/);assert.deepEqual(await get(owner,path),location);check('current-owner-location-is-bound-to-the-real-frozen-document');
 const memberLocation=await get(member,path);assert.equal(memberLocation.documentId,location.documentId);await get(outsider,path,403);check('current-member-read-and-nonmember-denial');
 if(process.env.AMA_LIBRARY_TEST_PROVIDER_REVOCATION==='1'){
  grantBefore=grant(null);assert.equal(grantBefore[1],1);grantChanged=true;grant(false);await get(member,path,403);await get(owner,path);await member.goto(base+'/meeting-library/'+encodeURIComponent(location.libraryId)+'?meeting='+meeting);await complete(member);await expect(entry(member)).toHaveCount(0);check('actual-provider-read-revocation-hides-formal-result-after-refresh');
  grant(Boolean(grantBefore[1]));grantChanged=false;const restored=await get(member,path);assert.equal(restored.documentId,memberLocation.documentId);assert.notEqual(restored.versionToken,memberLocation.versionToken);check('restored-read-uses-a-fresh-provider-grant-version');
 }
 const info=await get(owner,root),profile=(await get(owner,'/api/v1/directory/people?query=dev-owner&limit=20')).items.find(p=>p.stableId==='dev-owner');
 const publishedRows=[];for(const scope of ['owned','joined']){let cursor=null;for(let n=0;n<100;n++){const page=await get(owner,'/api/v1/meetings?'+new URLSearchParams({scope,stages:'PUBLISHED',limit:'10',...(cursor?{cursor}:{})}));for(const meeting of page.items){const current=(await get(owner,`/api/v1/meetings/${meeting.id}/publications/current`)).publication;if(!current||current.documentViewAvailability!=='READY')continue;const context=await get(owner,`/api/v1/meetings/${meeting.id}/publications/${current.publicationId}/library-context`);if(context.libraryId===location.libraryId&&!publishedRows.some(row=>row.meeting.id===meeting.id))publishedRows.push({meeting,context});}if(!page.hasMore)break;assert(page.nextCursor&&page.nextCursor!==cursor);cursor=page.nextCursor;if(n===99)throw Error('Fixture publication pages exceed the qualification bound');}}
 const reference=await owner.context().newPage();await reference.route(base+'/__visual_reference__/library.html',route=>route.fulfill({status:200,contentType:'text/html; charset=utf-8',body:prototype}));
 const url='/meeting-library/'+encodeURIComponent(location.libraryId);
 for(const[width,height]of [[1440,900],[1536,791],[1024,768]]){
  await owner.setViewportSize({width,height});await reference.setViewportSize({width,height});
  for(const source of [true,false]){
   const route=url+(source?'?meeting='+meeting:'');await owner.goto(base+route);await expect(entry(owner)).toBeVisible();await complete(owner);
   await reference.goto(base+'/__visual_reference__/library.html#'+route);await reference.evaluate(({location,info,profile,publishedRows})=>{Object.assign(curUser(),{id:'dev-owner',name:profile.displayName,dept:profile.department??'--',roleName:'会议负责人'});DB.kbs=[{id:location.libraryId,name:location.libraryName,desc:location.libraryDescription,perm:true}];S.meetings=publishedRows.map(({meeting,context})=>({id:meeting.id,name:meeting.name??'未命名会议',ownerId:meeting.relation==='OWNER'?'dev-owner':'other-owner',owner:meeting.relation==='OWNER'?profile.displayName:'其他负责人',status:'已发布',attendees:meeting.relation==='MEMBER'?[profile.displayName]:[],publish:{kbId:context.libraryId,folderPath:context.folderPath,version:context.versionToken}}));render();},{location,info,profile,publishedRows});await capture(owner,reference,`library-${source?'source':'listing'}-${width}`);
  }
 }
 await owner.reload();await expect(entry(owner)).toBeVisible();await entry(owner).click();await owner.waitForURL(base+`/meeting/${meeting}/read`);check('real-page-pagination-refresh-and-meeting-navigation');
 const harnessLogs=await(await owner.request.get(harnessOrigin+'/admin/api/logs?module=knowledge&limit=500')).json();const observed=harnessLogs.items.filter(row=>row.path==='/v1/knowledge/documents/'+location.documentId&&row.requestSummary?.query?.view==='location');assert(observed.some(row=>row.status_code===200));assert(observed.some(row=>row.status_code===403),'Current provider denial must traverse the harness too');check('fixed-harness-observed-current-location-and-provider-denial');
 assert.equal(signature(),working);assert.deepEqual(errors,[]);const pixelPassed=pixels.every(p=>p.pass);await writeFile(output+'/report.json',JSON.stringify({pass:pixelPassed,businessPassed:true,pixelPassed,scope:'ACTUAL_CAS_LIBRARY_READ_AND_PIXELS',meetingId:meeting,publicationId:publication.publicationId,deployedSourceSignature:state.sourceSignature,workingSourceSignature:working,prototypeSha256,location,checks,requests,pixels,maskedRegions:[],errors,harnessOrigin,harnessRevision,harnessSourceSha256,providerQualification:'FIXED_HARNESS_CONTROLLED_BRIDGE_NOT_COMPANY_KNOWLEDGE'},null,2));console.log(JSON.stringify({businessPassed:true,pixelPassed,checks:checks.length,pixels:pixels.length}));if(!pixelPassed)process.exitCode=1;
}catch(error){await writeFile(output+'/failure.json',JSON.stringify({error:String(error),meeting,checks,requests,pixels,errors},null,2));for(let n=0;n<pages.length;n++)await pages[n].screenshot({path:output+`/failure-${n}.png`}).catch(()=>{});throw error;}finally{if(grantChanged)grant(Boolean(grantBefore[1]));await browser.close();}
