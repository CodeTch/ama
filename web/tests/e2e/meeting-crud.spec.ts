import {test,expect} from '@playwright/test';

const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const item={accessRevisionToken:'0',category:null,id:meeting,lifecycle:'ACTIVE',name:'可删除会议',occurredOn:null,participants:[],relation:'OWNER',revisionToken:'1',stage:'ENTRY',updatedAt:'2026-10-30T12:00:00Z',workspaceStage:'ENTRY',writeState:'CHECK_WORKSPACE'};

test('owner delete confirmation sends the command and reloads the list',async({page})=>{
 let deleted=false,deleteCalls=0;
 await page.route('**/api/v1/session',route=>route.fulfill({status:200,json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({status:200,json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({status:200,json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/meetings?*',route=>route.fulfill({status:200,json:{hasMore:false,items:deleted?[]:[item],nextCursor:null}}));
 await page.route(`**/api/v1/meetings/${meeting}`,async route=>{
  if(route.request().method()==='DELETE'){deleteCalls++;deleted=true;await route.fulfill({status:200,json:{id:meeting,revision:2,revisionToken:'2'}});return;}
  await route.fulfill({status:200,json:{id:meeting,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'可删除会议',category:null,summary:null,occurredOn:null,confirmedRosterId:null,confirmedRoster:[],rosterDraftId:null,rosterDraft:[]}});
 });
 await page.goto('/meetings');
 await expect(page.getByRole('button',{name:'可删除会议'})).toBeVisible();
 await page.getByRole('button',{name:'删除',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'删除会议'});
 await expect(dialog).toContainText('确认删除“可删除会议”？');
 await dialog.getByRole('button',{name:'确认删除'}).click();
 await expect(dialog).toBeHidden();
 await expect(page.getByRole('button',{name:'可删除会议'})).toHaveCount(0);
 expect(deleteCalls).toBe(1);
});
