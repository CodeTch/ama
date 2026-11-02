import {test,expect,type Page} from '@playwright/test';

function gate(){let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});return {wait,release};}
const meetingId='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const secondId='e69e55b0-a3a9-4ad4-9adf-4ef5610eaeab';
const item={accessRevisionToken:'0',category:null,id:meetingId,lifecycle:'ACTIVE',name:'刷新前后的会议',occurredOn:null,participants:[],relation:'OWNER',revisionToken:'1',stage:'ENTRY',updatedAt:'2026-11-02T02:38:36Z',workspaceStage:'ENTRY',writeState:'CHECK_WORKSPACE'};
const stageCounts={ENTRY:2,TRANSCRIPT_PENDING:0,TRANSCRIPT_FAILED:0,MINUTES_PENDING:0,MINUTES_FAILED:0,READY_TO_PUBLISH:0,PUBLISH_FAILED:0,PUBLISHED:0};

async function home(page:Page){
 const session=gate(),profile=gate(),meetings=gate();let paused=false,sessionStatus=200,meetingStatus=200;
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/api/v1/session',async route=>{if(paused)await session.wait;await route.fulfill({status:sessionStatus,json:sessionStatus===200?{actor:{enterpriseId:'refresh-owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}:{}});});
 await page.route('**/api/v1/directory/people*',async route=>{if(paused)await profile.wait;await route.fulfill({json:{items:[{stableId:'refresh-owner',displayName:'测试账号1',department:'AMA受控联调',active:true}],nextCursor:null}});});
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/topics',route=>route.fulfill({json:[]}));
 await page.route('**/api/v1/topics/memberships?*',route=>route.fulfill({json:[{meetingId,topicId:null},{meetingId:secondId,topicId:null}]}));
 await page.route('**/api/v1/meetings?*',async route=>{if(paused)await meetings.wait;await route.fulfill({status:meetingStatus,json:meetingStatus===200?{hasMore:false,items:[item,{...item,id:secondId,name:'第二场会议'}],nextCursor:null,total:2,stageCounts,topicGroups:{__none:2}}:{}});});
 await page.goto('/meetings');
 await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 await expect(page.getByRole('button',{name:item.name,exact:true})).toBeVisible();
 return {
  session,profile,meetings,
  denySession(){sessionStatus=401;},failMeetings(){meetingStatus=503;},restoreMeetings(){meetingStatus=200;},
  async reload(){paused=true;const requested=page.waitForRequest('**/api/v1/session');await page.reload();await requested;await page.getByLabel('当前用户').waitFor();},
  release(){session.release();profile.release();meetings.release();}
 };
}

async function positions(page:Page){
 return page.evaluate(()=>{
  const box=(selector:string)=>{const r=document.querySelector(selector)!.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};};
  return {feedback:box('.topbar-right>.btn'),profile:box('.home-profile'),filters:box('.m19-filters'),panel:box('#meeting-panel')};
 });
}

test('browser refresh keeps the profile and feedback stable while the display name loads',async({page})=>{
 const data=await home(page);
 try{
  const before=await positions(page);
  await data.reload();
  const pending=await positions(page);
  expect(Math.abs(pending.feedback.x-before.feedback.x)).toBeLessThanOrEqual(1);
  expect(Math.abs(pending.profile.width-before.profile.width)).toBeLessThanOrEqual(1);
  expect(await page.locator('.home-profile .rc-name').textContent()).not.toMatch(/正在检查登录|尚未登录|refresh-owner/);
  const requested=page.waitForRequest('**/api/v1/directory/people*');data.session.release();data.meetings.release();await requested;
  expect(await page.locator('.home-profile .rc-name').textContent()).not.toContain('refresh-owner');
  expect(Math.abs((await positions(page)).feedback.x-before.feedback.x)).toBeLessThanOrEqual(1);
  data.profile.release();
  await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
  expect(Math.abs((await positions(page)).feedback.x-before.feedback.x)).toBeLessThanOrEqual(1);
 }finally{data.release();}
});

test('browser refresh preserves the meeting table frame through session and list reads',async({page})=>{
 const data=await home(page);
 try{
  const before=await positions(page);
  const table=page.getByRole('table',{name:'当前范围的会议'});
  const columns=await table.getByRole('columnheader').evaluateAll(cells=>cells.map(cell=>cell.getBoundingClientRect().x));
  await data.reload();
  expect(await table.count()).toBe(1);
  expect(await page.getByRole('heading',{name:'正在检查登录',exact:true}).count()).toBe(0);
  expect((await positions(page)).panel.height).toBeGreaterThanOrEqual(200);
  const requested=page.waitForRequest('**/api/v1/meetings?*');data.profile.release();data.session.release();await requested;
  expect(await table.count()).toBe(1);
  const pending=await positions(page);
  expect(pending.panel.height).toBeGreaterThanOrEqual(200);
  expect(pending.filters).toEqual(before.filters);
  expect(pending.panel.y).toBe(before.panel.y);
  const pendingColumns=await table.getByRole('columnheader').evaluateAll(cells=>cells.map(cell=>cell.getBoundingClientRect().x));
  expect(pendingColumns.length).toBe(columns.length);
  for(let i=0;i<columns.length;i++)expect(Math.abs(pendingColumns[i]!-columns[i]!)).toBeLessThanOrEqual(1);
  data.meetings.release();
  await expect(page.getByRole('button',{name:item.name,exact:true})).toBeVisible();
  expect((await positions(page)).filters).toEqual(before.filters);
  await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-busy','false');
 }finally{data.release();}
});

test('an expired session after refresh clears the old identity and meeting rows',async({page})=>{
 const data=await home(page);
 try{
  data.denySession();await data.reload();
  expect(await page.getByRole('button',{name:item.name,exact:true}).count()).toBe(0);
  expect(await page.locator('.home-profile .rc-name').textContent()).not.toContain('测试账号1');
  data.release();
  await expect(page.getByRole('heading',{name:'请登录企业账号',exact:true})).toBeVisible();
  await expect(page.locator('.home-profile .rc-name')).toHaveText('尚未登录');
  await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-busy','false');
  await expect(page.getByRole('table')).toHaveCount(0);
 }finally{data.release();}
});

test('a failed first meeting read leaves a working retry instead of a permanent placeholder',async({page})=>{
 const data=await home(page);
 try{
  data.failMeetings();await data.reload();data.release();
  const retry=page.getByRole('button',{name:'重试当前页',exact:true});
  await expect(retry).toBeVisible();
  await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-busy','false');
  data.restoreMeetings();await retry.click();
  await expect(page.getByRole('button',{name:item.name,exact:true})).toBeVisible();
 }finally{data.release();}
});
