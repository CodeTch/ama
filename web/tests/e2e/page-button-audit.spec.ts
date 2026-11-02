import {test,expect,type Page} from '@playwright/test';
import {writeFile} from 'node:fs/promises';
import {installButtonFixtures,auditId} from '../support/button-fixtures';

test.use({contextOptions:{reducedMotion:'no-preference'}});
const demo='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';
type Scene={name:string;path:string;stage?:string;feedbackState?:'ACCEPTED';ready:string;prepare?:(page:Page)=>Promise<unknown>};
const scenes:Scene[]=[
 {name:'login',path:'/login?signedOut=1',ready:'.login-submit'},
 {name:'meetings-owned',path:'/meetings',ready:'.m19-name'},
 {name:'meetings-joined',path:'/meetings',ready:'.m19-name',prepare:async page=>{await page.getByRole('tab',{name:'我参与的'}).click();await expect(page.getByRole('tabpanel')).toHaveAttribute('aria-busy','false');}},
 {name:'meetings-grouped',path:'/meetings',ready:'.m19-name',prepare:page=>page.getByRole('button',{name:'专题分组',exact:true}).click()},
 {name:'meetings-calendar',path:'/meetings',ready:'.m19-name',prepare:page=>page.getByLabel('会议日期',{exact:true}).click()},
 {name:'feedback',path:'/feedback',ready:'.feedback-value'},
 {name:'feedback-menu',path:'/feedback',ready:'.feedback-value',prepare:page=>page.getByLabel('当前用户',{exact:true}).click()},
 {name:'feedback-detail',path:'/feedback',ready:'.feedback-value',prepare:async page=>{await page.locator('.feedback-value').click();await expect(page.getByRole('dialog',{name:'反馈详情'})).toContainText('按钮反馈检查');}},
 {name:'feedback-submit',path:'/feedback?meetingId='+auditId,ready:'.feedback-value',prepare:async page=>{await expect(page.getByRole('dialog',{name:'提交语料反馈',exact:true})).toBeVisible();await page.getByLabel('反馈内容',{exact:true}).fill('逐页按钮检查');}},
 {name:'feedback-review-detail',path:'/feedback?reviewing=1',ready:'.feedback-value',prepare:async page=>{await page.locator('.feedback-value').click();await page.getByLabel('审核意见',{exact:true}).fill('已核对按钮交互');}},
 {name:'feedback-review-undo',path:'/feedback?reviewing=1',feedbackState:'ACCEPTED',ready:'.feedback-value',prepare:async page=>{await page.locator('.feedback-value').click();await expect(page.getByRole('button',{name:'撤销审核',exact:true})).toBeVisible();}},
 {name:'library',path:'/meeting-library',stage:'published',ready:'.m19-name'},
 {name:'library-detail',path:'/meeting-library/research',stage:'published',ready:'.m19-name'},
 {name:'recording',path:'/recording',ready:'.acr-icon-button'},
 {name:'recording-explicit',path:'/recording/'+auditId,ready:'.acr-icon-button'},
 ...['processing','transcript','minutes','confirm','publish','published'].map(stage=>({name:'meeting-'+stage,path:`/meeting/${auditId}/${stage}`,stage,ready:'.meeting-progress,.m19-published-status'})),
 ...['transcript','minutes','confirm'].map(stage=>({name:'prototype-'+stage,path:`/meeting/${demo}/${stage}`,ready:'.prototype-detail'})),
 {name:'transcript-demo',path:'/transcript-correction-demo',ready:'.transcript-demo-detail'},
 {name:'publish-demo',path:'/knowledge-publish-demo',ready:'.knowledge-publish-demo-page'},
 {name:'unknown',path:'/missing-button-audit',ready:'.content h1'},
];

for(const scene of scenes)test(`page controls: ${scene.name}`,async({page},info)=>{
 test.setTimeout(180000);
 page.setDefaultTimeout(8000);
 const data=await installButtonFixtures(page);data.stage=scene.stage??'minutes';
 if(scene.feedbackState)data.feedbackState=scene.feedbackState;
 await page.addInitScript(()=>{if(navigator.mediaDevices)navigator.mediaDevices.getUserMedia=async()=>{throw new DOMException('Controlled audit: no device','NotAllowedError');};});
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/auth/cas/**',route=>route.fulfill({contentType:'text/html',body:'<main>受控登录导航</main>'}));
 async function open(){data.feedbackState=scene.feedbackState??'PENDING';data.feedbackRevision='1';await page.goto(scene.path,{waitUntil:'domcontentloaded',timeout:20000});await page.locator(scene.ready).first().waitFor();await page.waitForLoadState('networkidle',{timeout:15000});await page.evaluate(()=>document.fonts.ready);if(scene.prepare)await scene.prepare(page);}
 const selector='button:visible,summary:visible,[role="button"]:visible,a:visible';
 async function controls(){const dialogs=page.locator('dialog[open]');if(await dialogs.count())return dialogs.last().locator(selector);const calendar=page.locator('.m19-range-panel:visible');if(await calendar.count())return calendar.locator(selector);if(scene.name==='feedback-menu')return page.locator('.feedback-profile[open]').locator(selector);return page.locator(selector);}
 await open();
 const entries=await (await controls()).evaluateAll(elements=>elements.map((element,index)=>({index,label:element.getAttribute('aria-label')??element.textContent?.trim()??'',disabled:element.matches(':disabled,[aria-disabled="true"]'),tag:element.tagName})));
 const results:Array<Record<string,unknown>>=[];
 for(const entry of entries){
  if(entry.index>0)await open();
  const control=(await controls()).nth(entry.index);
  if(await control.count()===0)throw new Error(`Missing control ${JSON.stringify(entry)}`);
  if(await control.isDisabled()||await control.getAttribute('aria-disabled')==='true'){results.push({...entry,result:'disabled'});continue;}
  await control.scrollIntoViewIfNeeded();
  const original=await control.elementHandle();
  const read=()=>original!.evaluate(element=>{const r=element.getBoundingClientRect();return {x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height};});
  const before=await read(),url=page.url();
  await control.click({timeout:5000});
  // Observe the immediate paint and the end of the previous 180–220ms animation window.
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  if(page.url()===url&&await original!.evaluate(element=>element.isConnected)){
   const after=await read();
   results.push({...entry,result:after.width&&after.height?'clicked':'changed view',before,after});
   // A control may legitimately move vertically when expanding its own content. Its hit area must stay the same size.
   if(after.width&&after.height){expect.soft(Math.abs(before.width-after.width),`${scene.name}: ${entry.label} width`).toBeLessThanOrEqual(1);expect.soft(Math.abs(before.height-after.height),`${scene.name}: ${entry.label} height`).toBeLessThanOrEqual(1);}
  }else results.push({...entry,result:page.url()===url?'changed view':'navigated'});
 }
 const report=info.outputPath('button-clicks.json');await writeFile(report,JSON.stringify({scene:scene.name,results,unmodelledRequests:[...new Set(data.unknown)],errors},null,2));
 await info.attach('button-clicks.json',{path:report,contentType:'application/json'});
 expect(errors).toEqual([]);
});
