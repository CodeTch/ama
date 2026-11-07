/** Repeatable read-only measurements, with real CAS/API and no request interception. */
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const origin=process.env.AMA_MEETINGS_ORIGIN??'http://127.0.0.1:5174';
const output=resolve(process.argv[2]??'.tools/meetings-performance/current');
const runs=Number(process.env.AMA_MEETINGS_RUNS??5);
assert(Number.isInteger(runs)&&runs>0&&runs<=100);
await mkdir(output,{recursive:true});
const browser=await chromium.launch();
const report={origin,browser:browser.version(),samples:[],interactions:[],errors:[],failedResponses:[]};
const persist=()=>writeFile(resolve(output,'report.json'),JSON.stringify(report,null,2));
const percentile=(values,p)=>[...values].sort((a,b)=>a-b)[Math.ceil(values.length*p)-1];
try{
 const login=await browser.newContext();
 const entry=await login.newPage();
 await entry.goto(origin+'/login');
 await entry.locator('#login-username').fill(process.env.AMA_CAS_USERNAME??'1');
 await entry.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'1');
 await entry.getByRole('button',{name:'登录',exact:true}).click();
 await entry.waitForURL('**/meetings');
 const state=await login.storageState();await login.close();
 for(const slow of [false,true])for(let run=0;run<runs;run++){
  const context=await browser.newContext({storageState:state,viewport:{width:1440,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai'});
  try{
   const page=await context.newPage();
   page.on('pageerror',error=>report.errors.push(error.message));
   page.on('response',response=>{if(response.status()>=400&&new URL(response.url()).pathname.startsWith('/api/'))report.failedResponses.push({status:response.status(),path:new URL(response.url()).pathname});});
   const cdp=await context.newCDPSession(page);
   if(slow){await cdp.send('Network.enable');await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:120,downloadThroughput:10e6/8,uploadThroughput:3e6/8});await cdp.send('Emulation.setCPUThrottlingRate',{rate:4});}
   await page.addInitScript(()=>{
    window.meetingMetrics={cls:0,shifts:[],longTasks:[],firstRows:null};
    let first=0,last=0,score=0;
    new PerformanceObserver(list=>{for(const event of list.getEntries()){
     window.meetingMetrics.shifts.push({at:event.startTime,value:event.value,input:event.hadRecentInput});
     if(!event.hadRecentInput){if(!first||event.startTime-last>1000||event.startTime-first>5000){first=event.startTime;score=0;}last=event.startTime;score+=event.value;window.meetingMetrics.cls=Math.max(window.meetingMetrics.cls,score);}
    }}).observe({type:'layout-shift',buffered:true});
    new PerformanceObserver(list=>{for(const event of list.getEntries())window.meetingMetrics.longTasks.push(event.duration);}).observe({type:'longtask',buffered:true});
    const observer=new MutationObserver(()=>{if(window.meetingMetrics.firstRows!==null)return;requestAnimationFrame(()=>{if(document.querySelector('#meeting-panel tbody .m19-name')?.getClientRects().length)window.meetingMetrics.firstRows??=performance.now();});});
    observer.observe(document,{childList:true,subtree:true});
   });
   await page.goto(origin+'/meetings');
   await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
   await expect(page.locator('#meeting-panel tbody .m19-name').first()).toBeVisible();
   const sample=await page.evaluate(()=>({...window.meetingMetrics,api:performance.getEntriesByType('resource').filter(r=>new URL(r.name).pathname.startsWith('/api/')).map(r=>({path:new URL(r.name).pathname,start:Math.round(r.startTime),end:Math.round(r.responseEnd),ms:Math.round(r.duration)})),scripts:performance.getEntriesByType('resource').filter(r=>new URL(r.name).pathname.endsWith('.js')).reduce((n,r)=>n+r.encodedBodySize,0)}));
   report.samples.push({slow,run,...sample});
   if(run===0&&!slow){
    const initial=await page.locator('#meeting-panel tbody .m19-name').first().innerText();
    for(const name of ['我参与的','我负责的']){
     await page.evaluate(()=>{window.switchSample={start:performance.now(),empty:0,skeleton:0,blank:0,done:false};function frame(){const panel=document.querySelector('#meeting-panel');if(!panel?.querySelector('table'))window.switchSample.empty++;if(panel?.querySelector('.meeting-loading-row'))window.switchSample.skeleton++;if(!panel?.querySelector('tbody .m19-name,tbody .empty'))window.switchSample.blank++;if(!window.switchSample.done)requestAnimationFrame(frame);}requestAnimationFrame(frame);});
     const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/meetings');
     await page.getByRole('tab',{name,exact:true}).click();await response;
     await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
     report.interactions.push({name,...await page.evaluate(()=>{window.switchSample.done=true;return {...window.switchSample,ms:performance.now()-window.switchSample.start};})});
    }
    await expect(page.locator('#meeting-panel tbody .m19-name').first()).toHaveText(initial);
    await page.screenshot({path:resolve(output,'home.png'),fullPage:true});
   }
  }finally{await context.close();}
  await persist();
 }
 report.timings=[false,true].map(slow=>{const samples=report.samples.filter(row=>row.slow===slow);return{slow,n:samples.length,p50:Math.round(percentile(samples.map(row=>row.firstRows),.5)),p95:Math.round(percentile(samples.map(row=>row.firstRows),.95)),maxCls:Math.max(...samples.map(row=>row.cls)),maxApiRequests:Math.max(...samples.map(row=>row.api.length)),maxScriptBytes:Math.max(...samples.map(row=>row.scripts))};});
 report.pass=report.errors.length===0&&report.failedResponses.length===0&&report.interactions.every(row=>row.empty===0&&row.skeleton===0&&row.blank===0);
 if(process.env.AMA_MEETINGS_ENFORCE==='1')report.pass&&=report.timings.every(row=>row.p95<=(row.slow?2500:1000)&&row.maxCls<=.01);
 console.log(JSON.stringify({pass:report.pass,timings:report.timings,interactions:report.interactions,errors:report.errors,failedResponses:report.failedResponses},null,2));
 if(!report.pass)process.exitCode=1;
}catch(error){report.pass=false;report.failure=String(error);throw error;}finally{await persist();await browser.close();}
