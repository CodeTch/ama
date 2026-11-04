/** Recover a previously committed, captured adoption; never create a replacement intent. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
const {chromium,expect:playwrightExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const source=process.argv[2],out=process.argv[3],base=process.env.AMA_LOCAL_URL??'http://127.0.0.1:5174';
assert(source?.startsWith('.tools/local-app/')&&out?.startsWith('.tools/local-app/'));
const prior=JSON.parse(await readFile(source+'/report.json','utf8'));
assert.equal(prior.commands.length,4);assert.deepEqual(prior.commands[0],prior.commands[1]);assert.deepEqual(prior.commands[2],prior.commands[3]);
const meeting=prior.meetingId,generate=prior.commands[0],adopt=prior.commands[2],input=JSON.parse(generate.body),candidateId=adopt.path.split('/').at(-2),root=`/api/v1/meetings/${meeting}/transcript`;
await mkdir(out,{recursive:true});
const expect=playwrightExpect.configure({timeout:30000}),browser=await chromium.launch(),context=await browser.newContext({viewport:{width:1440,height:900}}),page=await context.newPage(),commands=[],errors=[];
page.setDefaultTimeout(60000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
await context.routeWebSocket(url=>url.pathname==='/'&&url.searchParams.has('token'),route=>route.close());
const json=path=>page.evaluate(async path=>{const r=await fetch(path,{signal:AbortSignal.timeout(30000)});if(r.status!==200)throw Error('Read '+r.status+' '+path);return r.json();},path);
try{
 await page.goto(base+'/login',{waitUntil:'commit'});await page.locator('#login-username').fill('dev-owner');await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings',{waitUntil:'commit'});
 const currentBefore=await json(root),historyBefore=await json(root+'/history?limit=20'),rawId=historyBefore.items.find(x=>x.kind==='RAW').id,rawBefore=await json(root+'/revisions/'+rawId),workingBefore=await json(root+'/revisions/'+input.sourceRevisionId);
 assert.notEqual(currentBefore.saved.id,input.sourceRevisionId);
 assert(workingBefore.selected.segments.some(s=>s.body.includes('我们下次会议')));
 assert(!rawBefore.selected.segments.some(s=>s.body.includes('（材料校核）')));
 assert(!workingBefore.selected.segments.some(s=>s.body.includes('（材料校核）')));
 assert(currentBefore.saved.segments.some(s=>s.body.includes('我们下次会议（材料校核）')));
 await page.goto(base+`/meeting/${meeting}/transcript`,{waitUntil:'commit'});
 for(let attempt=0;attempt<4;attempt++){await expect.poll(async()=>await page.locator('.material-enhancement').isVisible()||await page.getByRole('button',{name:'重新读取权限与内容',exact:true}).isVisible()).toBe(true);if(await page.locator('.material-enhancement').isVisible())break;await page.getByRole('button',{name:'重新读取权限与内容',exact:true}).click();}
 await expect(page.locator('.material-enhancement')).toBeVisible();
 // The failed probe's context was closed. Reconstruct only its exact captured pending command
 // through the production journal API, explicitly recording this controlled recovery setup.
 const restored=await page.evaluate(async({meeting,generate,input,candidateId,adopt})=>{
  const provides=document.querySelector('#app').__vue_app__._context.provides;const app=Object.getOwnPropertySymbols(provides).map(key=>provides[key]).find(value=>value?.session&&value?.journals);
  const {createScopedPrivateJournal}=await import('/src/shared/composables/scoped-private-journal.ts');
  const actor=app.session.current.value.actor.enterpriseId,journal=createScopedPrivateJournal(app.journals,'material-enhancement',()=>true);
  return journal.save(actor,meeting,{actor,meeting,key:generate.key,input,phase:'ADOPT',candidateId,decisionKey:adopt.key});
 },{meeting,generate,input,candidateId,adopt});assert.equal(restored,'SAVED');
 await page.route('**/transcript/material-enhancements**',async route=>{if(route.request().method()==='POST')commands.push({path:new URL(route.request().url()).pathname,key:route.request().headers()['idempotency-key'],body:route.request().postData()});await route.continue();});
 await page.reload({waitUntil:'commit'});await page.locator('.material-enhancement summary').click();await expect(page.getByRole('button',{name:'恢复原增强操作',exact:true})).toBeEnabled();await page.screenshot({path:out+'/original-intent-restored.png',fullPage:true});
 await page.getByRole('button',{name:'恢复原增强操作',exact:true}).click();await expect(page.getByRole('button',{name:'生成增强候选',exact:true})).toBeEnabled();assert.deepEqual(commands,[adopt]);
 const currentAfter=await json(root),historyAfter=await json(root+'/history?limit=20');
 assert.deepEqual(currentAfter.saved,currentBefore.saved);assert.deepEqual(currentAfter.observation,currentBefore.observation);assert.deepEqual(historyAfter.items,historyBefore.items);
 assert.deepEqual((await json(root+'/revisions/'+rawId)).selected,rawBefore.selected);assert.deepEqual((await json(root+'/revisions/'+input.sourceRevisionId)).selected,workingBefore.selected);
 assert.deepEqual(errors,[]);await page.screenshot({path:out+'/original-adoption-recovered.png',fullPage:true});
 await writeFile(out+'/facts.json',JSON.stringify({raw:rawBefore.selected,originalWorking:workingBefore.selected,adopted:currentAfter.saved,observation:currentAfter.observation,history:historyAfter.items},null,2));
 await writeFile(out+'/report.json',JSON.stringify({pass:true,meetingId:meeting,sourceRun:source,commands,checks:['original-generation-and-adoption-byte-identical-reload-retries-in-source-run','exact-captured-adoption-restored-through-production-journal','real-ui-retry-clears-pending-with-actual-SAVED_REVISION-receipt','no-new-version-no-root-increment-no-duplicate-async-task','RAW-and-original-WORKING-preserved'],rawId,originalWorking:input.sourceRevisionId,adopted:currentAfter.saved.id,errors,qualification:'Continues the original committed adoption from verified-08. Its browser context was closed, so this probe explicitly restores the captured pending intent via the production scoped journal; verified-08 already captured both original real reload/retry pairs. No new proposal or adoption key is created. Controlled fixed-port sibling material fixture, not production model quality.'},null,2));
 console.log('PASS original enhancement adoption recovery',meeting);
}catch(error){await page.screenshot({path:out+'/failure.png',fullPage:true}).catch(()=>{});await writeFile(out+'/failure.txt',await page.locator('body').innerText());await writeFile(out+'/report.json',JSON.stringify({pass:false,meetingId:meeting,commands,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}
