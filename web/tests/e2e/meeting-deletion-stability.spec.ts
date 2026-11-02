import {test,expect,type Page} from '@playwright/test';
const meeting='00000000-0000-4000-8000-000000000001',other='00000000-0000-4000-8000-000000000002',task='00000000-0000-4000-8000-000000000003';
async function setup(page:Page,options:{manual?:boolean;send?:Promise<void>;refresh?:Promise<void>}={}){
 let deleted=false,polls=0,reads=0,complete=false;
 const job=()=>({deletionTaskId:task,meetingId:meeting,state:complete?'COMPLETE':'PENDING',phase:complete?'COMPLETE':'STORAGE',revisionToken:'1',attempts:polls,errorCode:null});
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({json:{items:[],nextCursor:null}}));
 await page.route('**/api/v1/topics',route=>route.fulfill({json:[]}));
 await page.route('**/api/v1/topics/memberships?*',route=>route.fulfill({json:[]}));
 await page.route('**/api/v1/meetings?*',async route=>{
  reads++;const ids=deleted?[other]:[meeting,other];
  if(deleted)await options.refresh;
  await route.fulfill({json:{hasMore:false,items:ids.map(id=>({accessRevisionToken:'0',category:null,id,lifecycle:'ACTIVE',name:id===meeting?'待删除会议':'保留会议',occurredOn:null,participants:[],relation:'OWNER',revisionToken:'0',stage:'ENTRY',updatedAt:'2026-11-02T12:00:00Z',workspaceStage:'ENTRY',writeState:'CHECK_WORKSPACE'})),nextCursor:null,total:ids.length,stageCounts:{ENTRY:ids.length,TRANSCRIPT_PENDING:0,TRANSCRIPT_FAILED:0,MINUTES_PENDING:0,MINUTES_FAILED:0,READY_TO_PUBLISH:0,PUBLISH_FAILED:0,PUBLISHED:0},topicGroups:{__none:ids.length}}});
 });
 await page.route(`**/api/v1/meetings/${meeting}`,async route=>{await options.send;deleted=true;return route.fulfill({status:202,json:job()});});
 await page.route(`**/api/v1/deletions/${task}`,route=>{polls++;return route.fulfill({json:job()});});
 await page.goto('/meetings');
 await expect(page.getByRole('button',{name:'保留会议',exact:true})).toBeVisible();
 if(!options.manual){
  await page.getByRole('row').filter({hasText:'待删除会议'}).getByRole('button',{name:'删除',exact:true}).click();
  await page.getByRole('button',{name:'确认删除',exact:true}).click();
  await expect(page.getByRole('button',{name:'待删除会议',exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'保留会议',exact:true})).toBeVisible();
 }
 return {reads:()=>reads,polls:()=>polls,finish:()=>{complete=true;}};
}

function gate(){let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});return {wait,release};}

test('sending deletion and refreshing its receipt never replaces the list with an empty shell or skeleton',async({page})=>{
 const send=gate(),refresh=gate();
 const state=await setup(page,{manual:true,send:send.wait,refresh:refresh.wait});
 const table=await page.getByRole('table',{name:'当前范围的会议'}).elementHandle();
 const row=await page.getByRole('row').filter({hasText:'保留会议'}).elementHandle();
 const panel=page.locator('#meeting-panel'),before=await panel.boundingBox();
 await page.evaluate(()=>{
  const panel=document.querySelector('#meeting-panel')!;
  const state={missingTable:false,skeleton:false};(window as any).deletionPaint=state;
  new MutationObserver(()=>{state.missingTable ||= !panel.querySelector('table');state.skeleton ||= !!panel.querySelector('.meeting-loading-row');}).observe(panel,{childList:true,subtree:true});
 });
 try{
  await page.getByRole('row').filter({hasText:'待删除会议'}).getByRole('button',{name:'删除',exact:true}).click();
  const sent=page.waitForRequest(request=>request.method()==='DELETE');
  await page.getByRole('button',{name:'确认删除',exact:true}).click();await sent;
  await expect(page.getByRole('button',{name:'保留会议',exact:true})).toBeVisible();
  expect(await table!.evaluate(el=>el.isConnected)).toBe(true);
  expect(await panel.boundingBox()).toEqual(before);
  send.release();await expect.poll(state.reads).toBe(2);
  await expect(panel).toHaveAttribute('aria-busy','true');
  expect(await row!.evaluate(el=>el.isConnected)).toBe(true);
  expect(await panel.boundingBox()).toEqual(before);
  refresh.release();
  await expect(page.getByRole('button',{name:'待删除会议',exact:true})).toHaveCount(0);
  expect(await row!.evaluate(el=>el.isConnected)).toBe(true);
  state.finish();await expect(page.getByRole('dialog')).toBeHidden();
  expect(await page.evaluate(()=>(window as any).deletionPaint)).toEqual({missingTable:false,skeleton:false});
 }finally{send.release();refresh.release();}
});

test('cleanup polls preserve the list DOM and do not refetch meeting pages',async({page})=>{
 const state=await setup(page),reads=state.reads();
 const row=await page.getByRole('row').filter({hasText:'保留会议'}).elementHandle();
 const box=await row!.boundingBox();
 await expect.poll(state.polls,{timeout:10000}).toBeGreaterThanOrEqual(2);
 expect(state.reads()).toBe(reads);
 expect(await row!.evaluate(el=>el.isConnected)).toBe(true);
 expect(await row!.boundingBox()).toEqual(box);
});

test('cleanup completes automatically, closes the dialog and removes progress controls',async({page})=>{
 const state=await setup(page),dialog=page.getByRole('dialog');
 await expect(dialog).toBeVisible();
 await expect(dialog.getByRole('button',{name:'刷新清理状态',exact:true})).toHaveCount(0);
 state.finish();
 await expect(dialog).toBeHidden({timeout:6000});
 await expect(page.getByRole('button',{name:'查看删除进度',exact:true})).toHaveCount(0);
 await page.getByRole('row').filter({hasText:'保留会议'}).getByRole('button',{name:'删除',exact:true}).click();
 await page.getByRole('button',{name:'取消',exact:true}).click();
 await expect(page.getByRole('button',{name:'保留会议',exact:true})).toBeVisible();
 await page.reload();
 await expect(page.getByRole('button',{name:'保留会议',exact:true})).toBeVisible();
 await expect(dialog).toBeHidden();
});

test('a completed cleanup closes within one second of the preceding pending poll',async({page})=>{
 const state=await setup(page);
 await expect.poll(state.polls,{intervals:[25],timeout:5000}).toBeGreaterThanOrEqual(1);
 state.finish();
 await expect(page.getByRole('dialog')).toBeHidden({timeout:1000});
});
