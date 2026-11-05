/** Read-only completion of the original withdrawal acceptance; never recreates its publication. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const prior=JSON.parse(await readFile(process.env.AMA_MINUTES_E2E_PRIOR_REPORT,'utf8'));
assert(prior.checks.some(x=>x.name==='native-member-markdown-pdf-word-downloads-contain-the-exact-frozen-title-and-body'));
assert.equal(prior.downloads.length,3);
const meeting=prior.meetingId,root='/api/v1/meetings/'+meeting,publication=prior.publicationId;
const governance=prior.requests.findLast(x=>x.method==='GET'&&x.path.startsWith(root+'/publications/'+publication+'/governance/')&&x.status===200)?.path;
assert(governance,'The original withdrawal command must have a saved successful poll');
const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT);await mkdir(output,{recursive:true});
const report={meetingId:meeting,publicationId:publication,priorReport:resolve(process.env.AMA_MINUTES_E2E_PRIOR_REPORT),checks:[],requests:[],pageErrors:[],startedAt:new Date().toISOString()};
const browser=await chromium.launch({headless:true});
try{
 for(const [role,actor] of [['owner',process.env.AMA_MINUTES_E2E_OWNER],['member',process.env.AMA_MINUTES_E2E_MEMBER]]){
  const context=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN'}),page=await context.newPage();
  page.on('pageerror',e=>report.pageErrors.push(e.message));page.on('response',r=>{if(r.status()>=500&&new URL(r.url()).pathname.startsWith('/api/'))report.requests.push({role,path:new URL(r.url()).pathname,status:r.status()});});
  await page.goto('http://127.0.0.1:5174/login');await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('http://127.0.0.1:5174/meetings');
  async function get(path,expected){const value=await page.evaluate(async path=>{const r=await fetch(path,{signal:AbortSignal.timeout(30000)});return {status:r.status,cache:r.headers.get('cache-control'),value:await r.json()};},path);report.requests.push({role,path,status:value.status});assert.equal(value.status,expected,JSON.stringify(value));return value;}
  if(role==='owner')assert.equal((await get(governance,200)).value.state,'SUCCEEDED');
  const paths=[root+'/publications/'+publication+'/document',root+'/minutes',root+'/minutes/workspace-versions',root+'/minutes/revisions/'+prior.downloads[0].sourceRevision,...prior.downloads.map(x=>'/api/v1/exports/'+x.exportJobId+'/download')];
  for(const path of paths){const r=await get(path,403);assert(r.value.error);assert(!JSON.stringify(r.value).includes('灰度验证'));}
  await page.goto('http://127.0.0.1:5174/meeting/'+meeting+'/published');await expect(page.getByText('暂无访问权限。',{exact:true})).toBeVisible({timeout:45000});await expect(page.locator('.minutes-body pre')).toHaveCount(0);await expect(page.locator('.m19-published-minutes-title')).toHaveCount(0);await page.screenshot({path:output+'/'+role+'-withdrawn.png'});
  report.checks.push({name:role+'-withdrawal-clears-view-and-denies-formal-current-history-and-all-old-downloads',paths:paths.length});console.log('PASS '+report.checks.at(-1).name);
 }
 assert.deepEqual(report.pageErrors,[]);assert.deepEqual(report.requests.filter(x=>x.status>=500),[]);report.pass=true;
}catch(error){report.pass=false;report.failure=String(error);throw error;}
finally{report.finishedAt=new Date().toISOString();await writeFile(output+'/report.json',JSON.stringify(report,null,2));await browser.close();}
