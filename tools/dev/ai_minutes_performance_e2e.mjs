/** No business mutations. Browser contexts are cold; all samples include real session and content gates. */
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const origin='http://127.0.0.1:5174',reading=process.env.AMA_MINUTES_READ_ORIGIN??origin;
assert(['http://127.0.0.1:5174','http://127.0.0.1:4286'].includes(reading));
const out=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-perf');
const meeting=process.env.AMA_MINUTES_E2E_MEETING??'22113bd2-0719-4e51-9ab3-ed321f46070d';
const runs=Number(process.env.AMA_MINUTES_PERF_RUNS??30);assert(Number.isInteger(runs)&&runs>=1&&runs<=100);
const browser=await chromium.launch(),report={samples:[],reads:[],errors:[],browser:browser.version()};
await mkdir(out,{recursive:true});
const persist=()=>writeFile(out+'/report.json',JSON.stringify(report,null,2));
const percentile=(values,p)=>[...values].sort((a,b)=>a-b)[Math.ceil(p*values.length)-1];
try{
 const session=await browser.newContext(),login=await session.newPage();await login.goto(origin+'/login');await login.locator('#login-username').fill('1');await login.locator('#login-password').fill('1');await login.getByRole('button',{name:'登录',exact:true}).click();await login.waitForURL('**/meetings');const state=await session.storageState();await session.close();
 for(const slow of [false,true])for(let i=0;i<runs;i++){
  const context=await browser.newContext({storageState:state,locale:'zh-CN',viewport:{width:1440,height:900}});
  try{
   const p=await context.newPage(),background=[];p.on('request',r=>{const path=new URL(r.url()).pathname;if(/\/minutes\/(settings|templates|sources\/[^/]+)$/.test(path))background.push(path);});p.on('pageerror',e=>report.errors.push(e.message));
   const cdp=await context.newCDPSession(p);if(slow){await cdp.send('Network.enable');await cdp.send('Network.emulateNetworkConditions',{offline:false,latency:120,downloadThroughput:10e6/8,uploadThroughput:3e6/8});await cdp.send('Emulation.setCPUThrottlingRate',{rate:4});}
   await p.addInitScript(()=>{
    window.sample={cls:0,lcp:null};let firstShift=0,lastShift=0,windowScore=0;
    new PerformanceObserver(list=>{for(const e of list.getEntries())if(!e.hadRecentInput){
     if(!firstShift||e.startTime-lastShift>1000||e.startTime-firstShift>5000){firstShift=e.startTime;windowScore=0;}
     lastShift=e.startTime;windowScore+=e.value;window.sample.cls=Math.max(window.sample.cls,windowScore);
    }}).observe({type:'layout-shift',buffered:true});
    new PerformanceObserver(list=>{for(const e of list.getEntries())window.sample.lcp=Math.round(e.startTime);}).observe({type:'largest-contentful-paint',buffered:true});
    let pending=false;const obs=new MutationObserver(()=>{if(pending)return;pending=true;requestAnimationFrame(()=>{pending=false;
     const core=document.querySelector('.am-section-list [data-element-id] .mp-text');if(!core?.getClientRects().length)return;
     window.sample.core??=Math.round(performance.now());
     const chips=document.querySelectorAll('[data-element-id] .am-source-tag span');
     if(chips.length&&[...chips].every(n=>n.textContent!=='来源与审阅')){window.sample.labels=Math.round(performance.now());obs.disconnect();}
    });});obs.observe(document,{childList:true,subtree:true,characterData:true});
   });
   await p.goto(reading+'/meeting/'+meeting+'/minutes');await p.waitForFunction(()=>window.sample.labels);
   const row=await p.evaluate(()=>({...window.sample,api:performance.getEntriesByType('resource').filter(r=>/\/(session|workspace|minutes)$/.test(new URL(r.name).pathname)).map(r=>({name:new URL(r.name).pathname.split('/').pop(),start:Math.round(r.startTime),end:Math.round(r.responseEnd)})),details:performance.getEntriesByType('resource').filter(r=>new URL(r.name).pathname.includes('/minutes/sources/')).length,scripts:performance.getEntriesByType('resource').filter(r=>new URL(r.name).pathname.endsWith('.js')).reduce((n,r)=>n+r.encodedBodySize,0)}));
   assert.deepEqual(background,[]);assert.equal(row.details,0);report.samples.push({slow,...row,background:background.length});
  }finally{await context.close();}
  await persist();
 }
 report.timings=[false,true].map(slow=>{const rows=report.samples.filter(s=>s.slow===slow);return {slow,n:rows.length,p50:percentile(rows.map(r=>r.core),.5),p95:percentile(rows.map(r=>r.core),.95),maxLabelLag:Math.max(...rows.map(r=>r.labels-r.core))};});
 // Independent HTTP requests can exceed Chromium's per-origin connection limit.
 const cookie=state.cookies.map(c=>c.name+'='+c.value).join('; ');
 for(const parallel of [1,10,50]){
  const values=await Promise.all(Array.from({length:parallel},async()=>{
   const start=performance.now();const response=await fetch(origin+'/api/v1/meetings/'+meeting+'/minutes',{headers:{Cookie:cookie},signal:AbortSignal.timeout(65000)});
   const bytes=Buffer.from(await response.arrayBuffer());return {status:response.status,digest:createHash('sha256').update(bytes).digest('hex'),ms:Math.round(performance.now()-start)};
  }));report.reads.push({parallel,statuses:values.map(v=>v.status),variants:new Set(values.map(v=>v.digest)).size,p95:percentile(values.map(r=>r.ms),.95)});await persist();
  assert(values.every(v=>v.status===200));assert.equal(new Set(values.map(v=>v.digest)).size,1);
 }
 assert.deepEqual(report.errors,[]);report.meetsColdBudget=report.timings.every(r=>r.p95<=(r.slow?2500:1000));report.pass=report.meetsColdBudget;
 console.log(JSON.stringify({timings:report.timings,reads:report.reads,meetsColdBudget:report.meetsColdBudget}));if(!report.pass)process.exitCode=1;
}catch(error){report.pass=false;report.failure=String(error);throw error;}finally{await persist();await browser.close();}
