/** Browser-only edge checks. The reference meeting is read-only; standalone roster uses an isolated fixture. */
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const {chromium,expect}=createRequire(process.cwd()+'/web/package.json')('@playwright/test');
const base='http://127.0.0.1:5174';
const reference=process.env.AMA_CONFIRM_REFERENCE_MEETING??'2c814da3-a958-4912-a109-cc7c8a25eb6a';
const isolated=process.env.AMA_CONFIRM_EDGE_MEETING;
const browser=await chromium.launch(),errors=[];
async function session(actor,password){const context=await browser.newContext({viewport:{width:1024,height:768}});const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));await page.goto(base+'/login');await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');return{context,page};}
try{
 const {context,page}=await session('1','1');
 await page.route('**/api/v1/meetings/categories**',route=>route.fulfill({status:503,contentType:'application/json',body:'{}'}));
 await page.goto(base+'/meeting/'+reference+'/confirm');
 await expect(page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
 await expect(page.getByRole('button',{name:'重试分类',exact:true})).toBeVisible();
 await page.unroute('**/api/v1/meetings/categories**');
 await page.getByRole('button',{name:'重试分类',exact:true}).click();
 await expect(page.getByLabel('会议分类',{exact:true}).locator('option')).not.toHaveCount(1);
 await page.getByRole('button',{name:'从发言人获取',exact:true}).click();
 await expect(page.getByText(/待核实的发言人标签|未发现可新增的发言人标签/)).toBeVisible();
 const candidates=await page.locator('.speaker-candidates li').allTextContents();
 const original=await page.evaluate(async id=>(await(await fetch('/api/v1/meetings/'+id)).json()).rosterDraftId,reference);
 assert(Array.isArray(candidates));
 assert.equal(await page.evaluate(async id=>(await(await fetch('/api/v1/meetings/'+id)).json()).rosterDraftId,reference),original);
 await context.close();
 if(isolated){
  const owned=await session('confirmation-owner-20261103','dev-password');
  await owned.page.goto(base+'/meeting/'+isolated+'/confirm');
  await expect(owned.page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
  const action=owned.page.getByRole('button',{name:'确认已保存名单',exact:true});
  await expect(action).toBeVisible();
  const response=owned.page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/roster-confirmations'));
  await action.click();
  assert.equal((await response).status(),200);
  await expect(owned.page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
  const saved=await owned.page.evaluate(async id=>(await(await fetch('/api/v1/meetings/'+id)).json()),isolated);
  const final=await owned.page.evaluate(async id=>(await(await fetch('/api/v1/meetings/'+id+'/final-confirmations/current')).json()),isolated);
  assert(saved.confirmedRosterId);
  assert.equal(final.confirmation,null);
  await owned.context.close();
 }
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({pass:true,unverifiedSpeakerCandidates:candidates.length,standaloneRosterConfirmed:!!isolated,pageErrors:errors}));
}finally{await browser.close();}
