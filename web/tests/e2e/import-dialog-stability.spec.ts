import {test,expect,type Page} from '@playwright/test';

test.use({contextOptions:{reducedMotion:'no-preference'}});
const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
function gate(){let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});return {wait,release};}

async function setup(page:Page,workspaceStatus=200){
 const workspace=gate(),discard=gate();
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({json:{items:[{stableId:'owner',displayName:'测试账号1',department:null,active:true}],nextCursor:null}}));
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/topics',route=>route.fulfill({json:[]}));
 await page.route('**/api/v1/meetings?*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null,total:0,stageCounts:{ENTRY:0,TRANSCRIPT_PENDING:0,TRANSCRIPT_FAILED:0,MINUTES_PENDING:0,MINUTES_FAILED:0,READY_TO_PUBLISH:0,PUBLISH_FAILED:0,PUBLISHED:0},topicGroups:{}}}));
 await page.route('**/api/v1/meetings',route=>route.fulfill({status:201,json:{id:meeting,revision:0,revisionToken:'0',stage:'ENTRY'}}));
 await page.route(`**/api/v1/meetings/${meeting}/workspace`,async route=>{
  await workspace.wait;
  if(workspaceStatus!==200){await route.fulfill({status:workspaceStatus,json:{}});return;}
  await route.fulfill({json:{meetingId:meeting,revisionToken:'0',accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',stage:'ENTRY',relation:'OWNER',navigationState:'CHECK_PRODUCER',saved:{infoRevisionId:meeting,elementSetId:null,minutesRevisionId:null,rosterRevisionId:null,transcriptConfirmationId:null,transcriptRevisionId:null}}});
 });
 await page.route(`**/api/v1/meetings/${meeting}/import-draft`,route=>route.fulfill({json:{meetingId:meeting,revisionToken:'0',meetingRevisionToken:'0',orderedFileIds:[],files:[]}}));
 await page.route(`**/api/v1/meetings/${meeting}/materials`,route=>route.fulfill({json:{items:[],materialSnapshotId:null,materialsPending:false,meetingRevision:'0'}}));
 await page.route(`**/api/v1/meetings/${meeting}/import-discard`,async route=>{await discard.wait;await route.fulfill({json:{meetingId:meeting,state:'DISCARDED',revisionToken:'1'}});});
 await page.goto('/meetings');
 await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 return {workspace,discard,release(){workspace.release();discard.release();}};
}

async function frame(page:Page){
 await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
 return page.getByRole('dialog',{name:'导入会议',exact:true}).boundingBox();
}

test('opening import keeps its position and size while the upload authorization loads',async({page})=>{
 const data=await setup(page);
 try{
  await page.getByRole('button',{name:'导入会议',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  await expect(dialog).toBeVisible();
  const loading=await frame(page);
  data.workspace.release();
  await expect(page.getByRole('button',{name:'添加会议主文件',exact:true})).toHaveAttribute('aria-disabled','false');
  expect(await frame(page)).toEqual(loading);
 }finally{data.release();}
});

test('a failed upload authorization shows its error without removed recovery modules',async({page})=>{
 const data=await setup(page,503);
 try{
  await page.getByRole('button',{name:'导入会议',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('上传恢复记录暂不可用，请重新检查后操作。',{exact:true})).toBeHidden();
  data.workspace.release();
  await expect(dialog.getByText('上传恢复记录暂不可用，请重新检查后操作。',{exact:true})).toBeVisible();
  await expect(dialog.getByText('上传记录与恢复',{exact:true})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:'重新读取上传记录',exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'添加会议主文件',exact:true})).toHaveAttribute('aria-disabled','true');
 }finally{data.release();}
});

test('closing a new import keeps its contents and size until discard finishes',async({page})=>{
 const data=await setup(page);
 try{
  data.workspace.release();
  await page.getByRole('button',{name:'导入会议',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
  const drop=page.getByRole('button',{name:'添加会议主文件',exact:true});
  await expect(drop).toHaveAttribute('aria-disabled','false');
  const opened=await frame(page);
  const requested=page.waitForRequest(`**/api/v1/meetings/${meeting}/import-discard`);
  await dialog.getByRole('button',{name:'关闭导入会议',exact:true}).click();
  await requested;
  expect(await frame(page)).toEqual(opened);
  await expect(drop).toBeVisible();
  await expect(drop).toHaveAttribute('aria-disabled','true');
  data.discard.release();
  await expect(dialog).toBeHidden();
  await expect(page).toHaveURL(/\/meetings$/);
 }finally{data.release();}
});

test('an uncertain discard can close and resume the exact request after reload', async ({page}) => {
 const data=await setup(page);
 data.release();
 const requests: string[][]=[];
 await page.route(`**/api/v1/meetings/${meeting}/import-discard`, async route => {
  const request=route.request();
  requests.push([request.headers()['idempotency-key']!,request.postData()!]);
  if(requests.length===1) await route.fulfill({status:503,json:{}});
  else await route.fulfill({json:{meetingId:meeting,state:'DISCARDED',revisionToken:'1'}});
 });
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'导入会议',exact:true});
 await expect(dialog.getByRole('button',{name:'添加会议主文件'})).toHaveAttribute('aria-disabled','false');
 await dialog.getByRole('button',{name:'关闭导入会议'}).click();
 await expect(dialog.getByRole('button',{name:'重试放弃导入'})).toBeEnabled();
 await page.keyboard.press('Escape');
 await expect(dialog).toBeHidden();
 await expect(page).toHaveURL(/\/meetings$/);
 await page.reload();
 await page.getByRole('button',{name:'继续处理',exact:true}).click();
 await expect(dialog.getByText('上传恢复记录暂不可用，请重新检查后操作。')).toHaveCount(0);
 await dialog.getByRole('button',{name:'重试放弃导入'}).click();
 await expect(dialog).toBeHidden();
 await expect(page.getByRole('button',{name:'继续处理',exact:true})).toHaveCount(0);
 expect(requests).toEqual([requests[0],requests[0]]);
});
