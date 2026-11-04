import {test,expect,type Page} from '@playwright/test';

async function prepare(page:Page){
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'header-owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({json:{items:[{stableId:'header-owner',displayName:'测试账号1',department:'测试',active:true}],nextCursor:null}}));
}

// Use the running application's router so every real route can be checked
// without coupling the test to page-specific data loading or menu layout.
async function navigate(page:Page,path:string){
 await page.evaluate(async path=>{await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push(path);},path);
}

test('one header preserves the identity across workspace routes and browser history',async({page})=>{
 await prepare(page);let profileReads=0;page.on('request',request=>{if(request.url().includes('/directory/people'))profileReads++;});
 await page.goto('/meetings');
 await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 const original=await page.locator('.topbar').elementHandle();
 const reads=profileReads;
 await page.evaluate(()=>{
  const profile=document.querySelector('.home-profile')!;
  (window as any).__profileFrames=[];
  new MutationObserver(()=>{(window as any).__profileFrames.push({name:profile.querySelector('.rc-name')?.textContent,busy:profile.querySelector('summary')?.getAttribute('aria-busy')});}).observe(profile,{subtree:true,childList:true,characterData:true,attributes:true});
 });
 for(const [path,label] of [['/recording','在线录音'],['/feedback','我的语料反馈'],['/operations','运营治理'],['/meeting-library','会议库'],['/meeting/00000000-0000-4000-8000-000000000001','会议详情'],['/meeting/00000000-0000-4000-8000-000000000001/minutes','会议详情'],['/transcript-correction-demo','转写校正'],['/knowledge-publish-demo','知识库发布']]){
  await navigate(page,path!);
  await expect(page.locator('.topbar')).toHaveCount(1);
  expect(await original!.evaluate(element=>element===document.querySelector('.topbar'))).toBe(true);
  await expect(page.locator('.topbar-home')).toHaveText('智会助手');
  await expect(page.locator('.topbar-crumb')).toHaveText(`/ ${label}`);
  await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 }
 expect(profileReads).toBe(reads);
 await page.locator('.topbar-home').click();
 await expect(page).toHaveURL(/\/meetings$/);
 await expect(page.locator('.topbar-crumb')).toHaveCount(0);
 await page.goBack();
 await expect(page.locator('.topbar-crumb')).toHaveText('/ 知识库发布');
 expect(await original!.evaluate(element=>element===document.querySelector('.topbar'))).toBe(true);
 await page.goForward();
 await expect(page.locator('.topbar-crumb')).toHaveCount(0);
 const frames=await page.evaluate(()=>(window as any).__profileFrames as {name:string;busy:string}[]);
 expect(frames.every(frame=>frame.name==='测试账号1'&&frame.busy==='false')).toBe(true);
 expect(profileReads).toBe(reads);
});

test('recreating the header within the same session keeps the loaded profile without a second lookup',async({page})=>{
 await prepare(page);let profileReads=0;
 page.on('request',request=>{if(request.url().includes('/directory/people'))profileReads++;});
 await page.goto('/meetings');await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 const reads=profileReads;
 // Leaving the workspace destroys the header, while the application and its
 // authenticated session remain alive. Re-entering must reuse that identity.
 await navigate(page,'/login?signedOut=1');await expect(page.locator('.topbar')).toHaveCount(0);
 await navigate(page,'/meetings');await expect(page.locator('.home-profile .rc-name')).toHaveText('测试账号1');
 expect(profileReads).toBe(reads);
});

test('the shared header scrolls with the page and login keeps its own layout',async({page},info)=>{
 await prepare(page);await page.goto('/transcript-correction-demo');
 await expect(page.locator('.topbar-crumb')).toHaveText('/ 转写校正');
 await page.screenshot({path:info.outputPath('workspace-header.png')});
 // This short demo fits on desktop; a narrow viewport stacks its columns
 // and supplies real overflowing content for the normal document-flow check.
 await page.setViewportSize({width:390,height:600});
 await page.screenshot({path:info.outputPath('workspace-header-mobile.png')});
 await page.evaluate(()=>window.scrollTo(0,600));
 await expect.poll(()=>page.evaluate(()=>window.scrollY)).toBeGreaterThan(0);
 expect((await page.locator('.topbar').boundingBox())!.y).toBe(-await page.evaluate(()=>window.scrollY));
 expect((await page.locator('.topbar').boundingBox())!.height).toBe(56);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(390);
 await expect(page.locator('.topbar-home')).toBeVisible();
 await expect(page.locator('.topbar-crumb')).toBeVisible();
 await navigate(page,'/login');
 await expect(page.locator('.topbar')).toHaveCount(0);
});
