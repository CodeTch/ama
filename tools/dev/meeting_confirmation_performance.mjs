/** Cold, read-only confirmation-page measurements against an existing local meeting. */
import {createRequire} from 'node:module';
import {writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const origin='http://127.0.0.1:5174';
const meeting=process.env.AMA_CONFIRM_PERF_MEETING??'2c814da3-a958-4912-a109-cc7c8a25eb6a';
const actor=process.env.AMA_CONFIRM_PERF_ACTOR??'1';
const password=process.env.AMA_CONFIRM_PERF_PASSWORD??'1';
const diagnostic=process.env.AMA_CONFIRM_PERF_DIAGNOSTIC==='1';
const output=resolve(process.argv[2]??'.tools/meeting-confirmation-performance/report.json');
const browser=await chromium.launch();
const login=await browser.newContext();
const entry=await login.newPage();
await entry.goto(origin+'/login');
await entry.locator('#login-username').fill(actor);
await entry.locator('#login-password').fill(password);
await entry.getByRole('button',{name:'登录',exact:true}).click();
await entry.waitForURL('**/meetings');
const storageState=await login.storageState();
await login.close();
const samples=[];
try{
 for(const [width,height] of diagnostic?[[1440,900]]:[[1440,900],[1024,768]])for(let run=0;run<(diagnostic?1:3);run++){
  const context=await browser.newContext({storageState,viewport:{width,height},locale:'zh-CN'});
  await context.routeWebSocket(/ws:\/\/127\.0\.0\.1:5174/,()=>{});
  const page=await context.newPage(),errors=[],requests=[];
  page.on('pageerror',error=>errors.push(error.message));
  page.on('request',request=>{if(request.method()==='GET'&&new URL(request.url()).pathname.startsWith('/api/'))requests.push(new URL(request.url()).pathname);});
  await page.addInitScript(()=>{
   window.confirmMetrics={cls:0,shifts:[],timeline:[]};
   const ticker=setInterval(()=>{
    const rect=selector=>{const node=document.querySelector(selector);if(!node)return null;const box=node.getBoundingClientRect();return [Math.round(box.y),Math.round(box.height)];};
    window.confirmMetrics.timeline.push({at:Math.round(performance.now()),scrollY:Math.round(window.scrollY),head:rect('.page-head'),progress:rect('.meeting-progress'),skeleton:!!document.querySelector('.confirm-initial'),publication:rect('.publication'),grid:rect('.ac-confirm-grid'),checks:rect('.ac-confirm-check'),fields:[...document.querySelectorAll('.ac-confirm-info .field')].map(node=>[Math.round(node.getBoundingClientRect().y),Math.round(node.getBoundingClientRect().height)]),suggestions:document.querySelectorAll('.ac-inline-suggestion').length,status:[...document.querySelectorAll('.publication [role=status]')].map(node=>node.textContent?.slice(0,40))});
   },100);setTimeout(()=>clearInterval(ticker),3800);
   let first=0,last=0,score=0;
   new PerformanceObserver(list=>{for(const event of list.getEntries())if(!event.hadRecentInput){if(!first||event.startTime-last>1000||event.startTime-first>5000){first=event.startTime;score=0;}last=event.startTime;score+=event.value;window.confirmMetrics.cls=Math.max(window.confirmMetrics.cls,score);window.confirmMetrics.shifts.push({at:Math.round(event.startTime),value:event.value,sources:event.sources.map(source=>({node:source.node?.tagName+'.'+String(source.node?.className).slice(0,100),previous:source.previousRect,current:source.currentRect}))});}}).observe({type:'layout-shift',buffered:true});
  });
  await page.goto(origin+'/meeting/'+meeting+'/confirm');
  await expect(page.getByRole('textbox',{name:'会议名称',exact:true})).toBeEditable();
  const editableMs=await page.evaluate(()=>Math.round(performance.now()));
  await expect(page.locator('[data-check]')).toHaveCount(12);
  await expect.poll(async()=>page.locator('[data-check] .tag').filter({hasText:'待核实'}).count(),{timeout:15000}).toBe(0);
  const checksMs=await page.evaluate(()=>Math.round(performance.now()));
  const before=await page.locator('.ac-confirm-check .card-h').innerText();
  const readiness=page.waitForResponse(response=>response.request().method()==='GET'&&new URL(response.url()).pathname.endsWith('/final-confirmations/readiness'));
  await page.locator('.ac-confirm-check').getByRole('button',{name:'重新检查',exact:true}).click();
  assert.equal((await readiness).status(),200);
  await expect(page.locator('.ac-confirm-check').getByRole('button',{name:'重新检查',exact:true})).toBeEnabled();
  const after=await page.locator('.ac-confirm-check .card-h').innerText();
  const metrics=await page.evaluate(()=>window.confirmMetrics);
  const resources=await page.evaluate(()=>performance.getEntriesByType('resource').filter(entry=>new URL(entry.name).pathname.startsWith('/api/')).map(entry=>({path:new URL(entry.name).pathname.split('/').slice(-2).join('/'),start:Math.round(entry.startTime),end:Math.round(entry.responseEnd),duration:Math.round(entry.responseEnd-entry.startTime)})));
  const sample={viewport:width+'x'+height,editableMs,checksMs,cls:metrics.cls,shifts:metrics.shifts,...(diagnostic?{timeline:metrics.timeline}:{}),requests:requests.length,recheckReadinessRequests:requests.filter(path=>path.endsWith('/final-confirmations/readiness')).length,summaryStable:before===after,slowResources:resources.sort((a,b)=>b.duration-a.duration).slice(0,5),errors};
  samples.push(sample);
  await context.close();
 }
 assert(samples.every(row=>row.errors.length===0&&row.summaryStable&&row.recheckReadinessRequests===2));
 await mkdir(resolve(output,'..'),{recursive:true});
 await writeFile(output,JSON.stringify({meeting,samples},null,2));
 console.log(JSON.stringify({samples}));
}finally{await browser.close();}
