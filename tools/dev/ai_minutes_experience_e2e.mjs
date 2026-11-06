/** Exercise an already-saved acceptance meeting without modifying it. Other suites own the write paths. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const origin='http://127.0.0.1:5174',reading=process.env.AMA_MINUTES_READ_ORIGIN??origin,meeting=process.env.AMA_MINUTES_E2E_MEETING??'22113bd2-0719-4e51-9ab3-ed321f46070d';
assert([origin,'http://127.0.0.1:4286'].includes(reading));
const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-experience');
const runtime=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
for(const service of ['cas','directory','knowledge','model'])assert.equal(runtime.integrationOrigins[service],'http://127.0.0.1:8792');
await mkdir(output,{recursive:true});
const report={checks:[],errors:[],writes:[],screens:[],widths:{},meeting};
const browser=await chromium.launch();
const root='/api/v1/meetings/'+meeting;
async function evidence(name,page){await page.screenshot({path:resolve(output,name+'.png')});report.screens.push(name);}
function pass(name){report.checks.push(name);console.log('PASS '+name);}
async function login(actor,password){
 const c=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN',permissions:['clipboard-read','clipboard-write']});
 const p=await c.newPage();p.setDefaultTimeout(30000);
 await p.goto(origin+'/login');await p.locator('#login-username').fill(actor);await p.locator('#login-password').fill(password);await p.getByRole('button',{name:'登录',exact:true}).click();await p.waitForURL('**/meetings');
 p.on('pageerror',err=>report.errors.push(err.message));
 await p.route('**/api/**',r=>{if(r.request().method()==='GET'||r.request().method()==='HEAD')return r.continue();report.writes.push(r.request().method()+' '+new URL(r.request().url()).pathname);return r.abort();});
 return p;
}
const get=(p,path)=>p.evaluate(async path=>{const r=await fetch(path);if(!r.ok)throw Error('GET '+r.status);return r.json();},path);
const size=loc=>loc.evaluate(n=>{const r=n.getBoundingClientRect();return {y:r.y,h:r.height};});
try{
 const p=await login('1','1'),requests=[];p.on('request',r=>requests.push(new URL(r.url()).pathname));
 await p.goto(reading+'/meeting/'+meeting+'/minutes');const core=p.locator('.am-section-list'),first=core.locator('[data-element-id] .mp-text').first();
 await expect(first).toBeVisible();await expect(core.locator('.sensitive-excerpt').first()).toBeAttached();await expect(core.locator('[data-element-id] .am-source-tag').first()).toContainText('转写文件');
 assert.equal(requests.filter(s=>s.includes('/minutes/sources/')).length,0);pass('core-labels-and-sensitive-excerpts-without-detail-requests');
 const body=p.locator('.minutes-views').getByRole('button',{name:'会议纪要'}),elements=p.locator('.minutes-views').getByRole('button',{name:'核心要素'}),state=p.getByRole('button',{name:'查看会议状态与处理记录'});
 const reference=await browser.newPage({locale:'zh-CN'});await reference.goto('file://'+resolve('designDocs/智能会议助手1021版.html')+'#/meeting/MOCK-20261020-OWNED-4-1/minutes');
 await reference.getByRole('button',{name:'会议纪要',exact:true}).click();
 for(const [width,height] of [[1440,900],[1024,768]]){
  await reference.setViewportSize({width,height});
  await p.setViewportSize({width,height});const initial=await size(core);await state.click();const panel=p.locator('.advanced-tools');await expect(panel).toBeVisible();const expanded=await size(core);assert(Math.abs(initial.h-expanded.h)<=1);await panel.getByRole('button',{name:'关闭',exact:true}).click();await expect(state).toBeFocused();await evidence('core-'+width,p);
  await body.click();await expect(p.getByLabel('纪要模板',{exact:true})).toBeEnabled();
  const exactBody=await p.locator('.am-saved-body').textContent();
  await reference.locator('.am-saved-body').evaluate((node,text)=>{node.textContent=text;},exactBody);
  const geometry={};
  for(const selector of ['.am-document-toolbar','.am-document-heading','.am-saved-body']){
   const expected=await reference.locator(selector).boundingBox(),actual=await p.locator(selector).boundingBox();
   assert(actual&&expected);for(const key of ['x','y','width','height'])assert(Math.abs(actual[key]-expected[key])<=1,width+' '+selector+' '+key+' '+actual[key]+' vs '+expected[key]);
   geometry[selector]={expected,actual};
  }
  await p.getByRole('button',{name:'版本对比',exact:true}).click();const dialog=p.getByRole('dialog',{name:'候选与版本'});const atOpen=await size(dialog),closeButton=dialog.getByRole('button',{name:'关闭',exact:true}).first(),closeAtOpen=await closeButton.boundingBox();await expect(dialog.locator('.version-restore')).toBeVisible({timeout:45000});const after=await size(dialog),closeAfter=await closeButton.boundingBox();assert(closeAtOpen&&closeAfter&&Math.abs(closeAtOpen.x-closeAfter.x)<=1&&Math.abs(closeAtOpen.y-closeAfter.y)<=1);assert(Math.abs(after.y-atOpen.y)<=1&&Math.abs(after.h-atOpen.h)<=1);assert.equal(await dialog.getByRole('button',{name:/保存排除|开始修改设置/}).count(),0);
  report.widths[width]={initial,expanded,atOpen,after,closeAtOpen,closeAfter,geometry};await evidence('versions-'+width,p);await p.keyboard.press('Escape');await elements.click();
 }
 await reference.close();pass('two-desktop-document-geometry-with-identical-prototype-text');
 pass('two-desktop-record-pane-and-version-dialog-stability');
 await core.evaluate(e=>e.scrollTop=400);const savedScroll=await core.evaluate(e=>e.scrollTop);
 const reads=requests.length;await p.evaluate(()=>{window.switchPaint=[];window.blankFrames=0;window.layoutShift=0;
  window.hotObserver=new PerformanceObserver(list=>{for(const entry of list.getEntries())if(!entry.hadRecentInput)window.layoutShift+=entry.value;});
  window.hotObserver.observe({type:'layout-shift'});
  function sample(){if(!document.querySelector('.am-section-list')?.getClientRects().length&&!document.querySelector('.am-saved-body')?.getClientRects().length)window.blankFrames++;window.hotFrame=requestAnimationFrame(sample);}
  window.hotFrame=requestAnimationFrame(sample);document.addEventListener('click',e=>{if(!e.target.closest('.minutes-views button.tab'))return;const start=performance.now();requestAnimationFrame(()=>requestAnimationFrame(()=>window.switchPaint.push(performance.now()-start)));},true);});
 for(let n=0;n<50;n++){await body.click();await expect(p.locator('.am-saved-body')).toBeVisible();await elements.click();}
 assert.equal(await core.evaluate(e=>e.scrollTop),savedScroll);assert.equal(requests.slice(reads).filter(s=>s.includes('/minutes/sources/')||s.endsWith('/minutes/templates')||s.endsWith('/minutes/settings')).length,0);
 await p.waitForFunction(()=>window.switchPaint.length===100);
 const frames=await p.evaluate(()=>{cancelAnimationFrame(window.hotFrame);window.hotObserver.disconnect();return {blank:window.blankFrames,cls:window.layoutShift};});assert.equal(frames.blank,0);assert.equal(frames.cls,0);report.hotFrames=frames;
 const samples=await p.evaluate(()=>window.switchPaint);report.switchSamples=samples.length;report.switchP95=samples.sort((a,b)=>a-b)[Math.ceil(.95*samples.length)-1];assert(report.switchP95<=50);pass('one-hundred-hot-tab-switches-keep-scroll-and-read-state');
 const curr=await get(p,root+'/minutes'),action=curr.elements.ACTION.find(e=>e.action&&!e.action.due_on);
 assert(action);const row=core.locator('[data-element-id="'+action.id+'"]');await row.getByRole('button',{name:/^负责人/}).click();await p.getByRole('button',{name:'编辑负责人和截止日期'}).click();await expect(p.locator('dialog[open] .editor')).toBeVisible();assert.equal(await p.locator('dialog[open] input[type=date]:visible').count(),0);await p.keyboard.press('Escape');pass('null-deadline-hidden-in-editor');
 await state.click();await p.route('**'+root+'/workspace',r=>r.fulfill({status:503,json:{error:{code:'MEETING_UNAVAILABLE'}}}),{times:1});await p.locator('.advanced-tools').getByRole('button',{name:'刷新',exact:true}).click();await expect(p.getByRole('button',{name:'重新读取导航'})).toBeVisible();await expect(first).toBeAttached();await p.locator('.advanced-tools').getByRole('button',{name:'关闭'}).click();await p.getByRole('button',{name:'重新读取导航'}).click();await expect(p.getByRole('button',{name:'重新读取导航'})).toHaveCount(0);
 await p.route('**'+root+'/minutes/sources/**',r=>r.fulfill({status:403,json:{error:{code:'MEETING_FORBIDDEN'}}}),{times:1});await core.locator('[data-element-id] .am-source-tag').first().click();await expect(p.getByText('该来源当前不可访问。已重新核对其余纪要内容的权限。')).toBeVisible({timeout:45000});await expect(first).toBeAttached();pass('transient-navigation-and-isolated-source-denial-recover');
 await p.route('**/tasks?**',async r=>{if(new URL(r.request().url()).searchParams.get('kind')!=='MINUTES_GENERATE_V1')return r.fallback();const resp=await r.fetch(),data=await resp.json();if(data.items?.length)data.items[0].state='RUNNING';await r.fulfill({response:resp,json:data});});
 await p.reload();const banner=p.locator('.generation-progress');await expect(banner).toContainText('正在生成');await banner.getByRole('button',{name:'查看生成状态'}).click();await p.getByRole('dialog',{name:'重新生成纪要'}).getByRole('button',{name:'关闭',exact:true}).click();await expect(banner).toContainText('正在生成');pass('closing-status-dialog-retains-running-observation');
 // A browser-only volume fixture keeps the real authorized observation and exercises the
 // actual schema parser, pagination and DOM. Synthetic IDs are never submitted to AMA.
 await p.route('**'+root+'/minutes',async route=>{
  const response=await route.fetch(),data=await response.json(),example=data.elements.OVERVIEW[0],link=data.evidence.find(item=>item.element_id);
  assert(example&&link);
  for(let i=0;i<1100;i++){
   const element=crypto.randomUUID(),locator=crypto.randomUUID();
   data.elements.OVERVIEW.push({...example,id:element,ordinalToken:String(10000+i),body:'分页验证 '+i});
   data.evidence.push({...link,id:crypto.randomUUID(),element_id:element,minutes_revision_id:null,locator_id:locator});
   data.presentation.sourceLabels.push({locatorId:locator,kind:'transcript',label:'分页来源 '+i});
  }
  await route.fulfill({response,json:data});
 });
 await p.reload();
 const section=p.locator('#minutes-category-OVERVIEW'),next=section.getByRole('button',{name:'下一页',exact:true});
 await expect(next).toBeVisible();await expect(section.locator('article')).toHaveCount(20);
 const detailsBefore=requests.filter(url=>url.includes('/minutes/sources/')).length;
 for(let i=0;i<8;i++)await next.click();
 await expect(section.locator('article')).toHaveCount(20);
 await expect(section.locator('.am-source-tag').first()).toContainText('分页来源');
 assert.equal(requests.filter(url=>url.includes('/minutes/sources/')).length,detailsBefore);
 assert(await core.locator('article[data-element-id]').count()<=120);pass('synthetic-1100-elements-real-pagination-and-labels-beyond-128-locators');
 await p.context().close();
 const member=await login('dev-member',process.env.AMA_CAS_PASSWORD??'dev-password');const published=await get(member,'/api/v1/meetings?scope=joined&stages=PUBLISHED&limit=10');
 let verified=false;
 for(const item of published.items){await member.goto(origin+'/meeting/'+item.id+'/published');const tab=member.locator('.minutes-views').getByRole('button',{name:'AI纪要'});if(!await tab.waitFor({timeout:10000}).then(()=>true,()=>false))continue;await tab.click();const title=member.locator('.m19-published-minutes-title'),bodyText=member.locator('.minutes-body>pre');await expect(title).toBeVisible();const wanted=(await title.innerText())+'\n\n'+(await bodyText.textContent());await member.getByRole('button',{name:'复制全文',exact:true}).click();assert.equal(await member.evaluate(()=>navigator.clipboard.readText()),wanted);await member.evaluate(()=>Object.defineProperty(navigator.clipboard,'writeText',{value:()=>Promise.reject(Error('denied'))}));await member.getByRole('button',{name:'复制全文',exact:true}).click();await expect(member.getByLabel('可复制的全文')).toHaveValue(wanted);await member.keyboard.press('Escape');assert.equal(await member.getByRole('button',{name:'编辑正文'}).count(),0);verified=true;break;}
 assert(verified);pass('published-member-copies-bound-title-body-and-has-manual-fallback');assert.deepEqual(report.errors,[]);assert.deepEqual(report.writes,[]);report.pass=true;
}catch(error){report.pass=false;report.failure=String(error);throw error;}finally{await writeFile(resolve(output,'report.json'),JSON.stringify(report,null,2));await browser.close();}
