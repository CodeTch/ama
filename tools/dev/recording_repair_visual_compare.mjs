/** Read-only prototype comparison using an owned device run's paused observations. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const {chromium,expect}=createRequire(resolve('web/package.json'))('@playwright/test');
const source=process.argv[2],out=process.argv[3];assert(source?.startsWith('.tools/local-app/')&&out?.startsWith('.tools/local-app/'));await mkdir(out,{recursive:true});
const html=await readFile('designDocs/智能会议助手1021版.html','utf8'),hash=createHash('sha256').update(html).digest('hex');assert.equal(hash,'ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867');
const browser=await chromium.launch(),context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',deviceScaleFactor:1}),page=await context.newPage(),cases=[];
await page.route('http://127.0.0.1:5174/__recording_reference__',route=>route.fulfill({status:200,contentType:'text/html; charset=utf-8',body:html}));
try{
 for(const[width,height]of[[1440,900],[1024,768]]){
  const data=JSON.parse(await readFile(source+`/paused-${width}-projection.json`,'utf8'));assert(data.manifest.paused&&!data.manifest.finished);assert.equal(data.manifest.parts.length,1);
  await page.setViewportSize({width,height});await page.goto('http://127.0.0.1:5174/__recording_reference__#/meetings',{waitUntil:'domcontentloaded'});
  await page.evaluate(data=>{
   const bySegment=new Map();for(const event of data.subtitle.events){const prior=bySegment.get(event.segmentId);if(!prior||event.revision>prior.revision)bySegment.set(event.segmentId,event);}
   const events=[...bySegment.values()].sort((a,b)=>a.startMs-b.startMs||a.segmentId.localeCompare(b.segmentId)),part=data.manifest.parts[0],chunkIds=Array.from({length:part.highestContiguousSeq},(_,n)=>data.sessionId+':'+(n+1));
   Object.assign(curUser(),{id:'dev-owner',name:data.owner.displayName,dept:data.owner.department,roleName:'会议负责人'});
   const record={sessionId:data.sessionId,elapsed:data.elapsed,started:null,phase:'paused',parts:[{id:part.partId,index:1,startMs:0,endMs:data.elapsed,state:'paused',chunkIds}],activePartId:part.partId,segments:events.map(e=>({id:e.segmentId,text:e.text,t:e.startMs/1000,speaker:'说话人 1',recordPartId:part.partId})),chunkIds,savedChunks:chunkIds.length,savedElapsed:part.sampleCount/16,finished:false,error:''};
   S.meetings=[{id:data.meetingId,name:data.info.name,ownerId:'dev-owner',status:'已暂停',attendees:[],content:{transcript:[],speakers:[],entities:[],minutes:null},ac:{source:'record',files:[],info:{name:data.info.name,people:[]},record}}];AC.ctx=null;AC.media={id:data.meetingId,rec:{state:'paused'},lost:false,unsaved:[]};location.hash='/meeting/'+data.meetingId+'/record';render();
  },data);
  await expect(page.locator('#ac-record-status')).toContainText('已暂停');await page.getByRole('button',{name:'继续录音',exact:true}).hover();await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo(0,0);});await page.screenshot({path:out+`/paused-${width}-reference.png`,fullPage:true,animations:'disabled'});
  const geometry=await page.evaluate(selectors=>Object.fromEntries(selectors.map(selector=>{const r=document.querySelector(selector)?.getBoundingClientRect();return[selector,r?{x:r.x,y:r.y,width:r.width,height:r.height}:null];})),Object.keys(data.geometry??{}));const layoutDifferences=Object.entries(geometry).flatMap(([selector,rect])=>!rect||!data.geometry[selector]?[{selector,missing:true}]:Object.keys(rect).filter(key=>Math.abs(rect[key]-data.geometry[selector][key])>0.5).map(key=>({selector,property:key,prototype:rect[key],actual:data.geometry[selector][key]})));
  const diff=spawnSync('python3',['tools/dev/prototype_pixel_diff.py',out+`/paused-${width}-reference.png`,source+`/paused-${width}.png`,out+`/paused-${width}-diff.png`],{encoding:'utf8'});assert([0,1].includes(diff.status),diff.stderr);cases.push({width,height,state:'PAUSED',geometry,layoutDifferences,layoutPass:!!data.geometry&&!layoutDifferences.length,projection:source+`/paused-${width}-projection.json`,...JSON.parse(diff.stdout)});
 }
 await writeFile(out+'/report.json',JSON.stringify({comparisonCompleted:true,pixelPass:cases.every(c=>c.pass),layoutPass:cases.every(c=>c.layoutPass),prototypeSha256:hash,cases,qualification:'Same paused sample clock, hovered resume control and observed real recording facts projected into the untouched prototype. Exact full-image differences retained without masks, crop, resizing or tolerance. Current shared header, durable server facts and playable part sources intentionally differ from the local simulation prototype.'},null,2));console.log(JSON.stringify({comparisonCompleted:true,cases:cases.map(c=>({width:c.width,differentPixels:c.differentPixels,pixelPass:c.pass}))}));
}finally{await browser.close();}
