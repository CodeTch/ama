import {test,expect} from '@playwright/test';
test('application recorder and receipt cleanup keep capturing while the UI and journal replies are blocked',async({page})=>{
 await page.goto('/login');await page.evaluate(async()=>{
  const {usePcmRecorder}=await import('/src/features/recording/usePcmRecorder.ts' as string),{createPcmJournalClient}=await import('/src/features/recording/pcm-journal-client.ts' as string);
  const actor='receipt-stall-'+crypto.randomUUID(),collectorId=crypto.randomUUID(),session={sessionId:crypto.randomUUID(),partId:crypto.randomUUID(),writerEpoch:1,backendId:'a'.repeat(64)},meeting=crypto.randomUUID();
  let issued={...session,collectorId};const sdk={create:async()=>issued,current:async()=>issued,acquireWriter:async(value:any,id:string)=>issued={...value,writerEpoch:value.writerEpoch+1,collectorId:id},renewWriter:async(value:any)=>value,manifest:async()=>({sessionId:session.sessionId,paused:false,finished:false,parts:[{partId:session.partId,highestContiguousSeq:0,highestReceivedSeq:0,sampleCount:0}]}),resume:async()=>{},pause:async()=>{},finish:async()=>({archiveTaskId:crypto.randomUUID()})};
  const recorder=usePcmRecorder(sdk as any,()=>({actor:{enterpriseId:actor},csrfToken:'X'.repeat(43)}) as any,collectorId,{connect:async()=>({put:async(chunk:any)=>({highestContiguousSeq:chunk.seq,missingSeqs:[]}),close(){}})}),journal=createPcmJournalClient(actor);await recorder.attach(meeting);await recorder.start();await journal.load(session.sessionId);(window as any).receiptStall={recorder,journal,session};
 });await page.waitForFunction(()=>(window as any).receiptStall.recorder.elapsed.value>2200);
 const result=await page.evaluate(async()=>{const f=(window as any).receiptStall,before=f.recorder.elapsed.value,transactions=Promise.all(Array.from({length:16},()=>f.journal.markPart(f.session.sessionId,'ACTIVE','UNKNOWN')));await Promise.resolve();const start=performance.now();while(performance.now()-start<2400){}await transactions;await new Promise(resolve=>setTimeout(resolve,200));await f.recorder.pause();await f.recorder.finish();const result={before,elapsed:f.recorder.elapsed.value,phase:f.recorder.phase.value,pending:f.recorder.pendingCount.value,recorded:f.recorder.record.value.parts[0].sampleCount/16};f.journal.close();await f.recorder.dispose();return result;});
 expect(result.elapsed-result.before).toBeGreaterThan(2000);expect(result.phase).toBe('FINISHED');expect(result.pending).toBe(0);expect(result.recorded).toBe(result.elapsed);
});
test('pending-cache ceiling pauses before overflow and journal iteration reads at most eight chunks',async({page})=>{
 test.setTimeout(45000);await page.goto('/login');await page.evaluate(async()=>{
  const {openPcmCapture}=await import('/src/features/recording/browser-pcm-capture.ts' as string),{createPcmJournal}=await import('/src/features/recording/pcm-journal.ts' as string),actor='budget-'+crypto.randomUUID(),session={sessionId:crypto.randomUUID(),partId:crypto.randomUUID(),writerEpoch:1,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()},journal=createPcmJournal(actor),lost:string[]=[];
  await journal.begin(crypto.randomUUID(),session);const pcm=new ArrayBuffer(64000),sha256=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',pcm)),b=>b.toString(16).padStart(2,'0')).join('');
  for(let i=1;i<=298;i++)await journal.put({sessionId:session.sessionId,partId:session.partId,seq:i,sampleStart:(i-1)*32000,sampleCount:32000,sha256,pcm});
  const capture=await openPcmCapture(async()=>{},(cause:Error)=>lost.push(cause.message),undefined,true),state={capture,journal,session,lost,limit:''};(window as any).budgetTest=state;await capture.configure!({actor,session,snapshot:()=>{},limit:(message:string)=>{state.limit=message;}});await capture.resume();
 });await page.waitForFunction(()=>(window as any).budgetTest.limit);
 const result=await page.evaluate(async()=>{const f=(window as any).budgetTest,stats=await f.journal.stats(f.session.sessionId),getAll=IDBObjectStore.prototype.getAll,pages:number[]=[];IDBObjectStore.prototype.getAll=function(...args:any[]){if(this.name==='chunks')pages.push(args[1]);return getAll.apply(this,args as any);};let count=0,bytes=0;try{for await(const chunk of f.journal.pending(f.session.sessionId)){count++;bytes+=chunk.pcm.byteLength;}}finally{IDBObjectStore.prototype.getAll=getAll;}await f.capture.release();f.capture.pipeline.dispose();f.journal.close();return{stats,count,bytes,pages,lost:f.lost,limit:f.limit};});
 expect(result.lost).toEqual([]);expect(result.limit).toContain('缓存上限');expect(result.stats.bytes).toBeLessThanOrEqual(19200000);expect(result.count).toBe(result.stats.count);expect(result.bytes).toBe(result.stats.bytes);expect(result.pages.length).toBeGreaterThan(1);expect(result.pages.every(size=>size===8)).toBe(true);
});
test.use({launchOptions:{args:['--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream','--disable-audio-output']},permissions:['microphone']});
test('worker persists continuous audio through a blocked UI thread and flushes an exact tail',async({page})=>{
 await page.goto('/login');await page.evaluate(async()=>{
  const {openPcmCapture}=await import('/src/features/recording/browser-pcm-capture.ts' as string),{createPcmJournal}=await import('/src/features/recording/pcm-journal.ts' as string);
  const actor='worker-'+crypto.randomUUID(),session={sessionId:crypto.randomUUID(),partId:crypto.randomUUID(),writerEpoch:1,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()},journal=createPcmJournal(actor),lost:string[]=[];
  await journal.begin(crypto.randomUUID(),session);
  const capture=await openPcmCapture(async()=>{throw Error('PCM must never pass through the UI');},(cause:Error)=>lost.push(cause.message),undefined,true);
  await capture.configure!({actor,session,snapshot:()=>{},limit:(message:string)=>lost.push(message)});
  (window as any).workerTest={capture,journal,session,lost};await capture.resume();
 });await page.waitForTimeout(2300);
 const result=await page.evaluate(async()=>{const f=(window as any).workerTest,before=f.capture.pipeline.samples(),start=performance.now();while(performance.now()-start<2400){}await new Promise(resolve=>setTimeout(resolve,100));await f.capture.pause();await f.capture.pipeline.flush();const record=await f.journal.load(f.session.sessionId),samples=f.capture.pipeline.samples();await f.capture.release();f.capture.pipeline.dispose();f.journal.close();return{before,samples,recorded:record.parts[0].sampleCount,lost:f.lost};});
 expect(result.lost).toEqual([]);expect(result.samples-result.before).toBeGreaterThan(32000);expect(result.recorded).toBe(result.samples);
});
test('cancelling an unanswered permission prompt resolves without waiting for the browser',async({page})=>{
 await page.goto('/login');const result=await page.evaluate(async()=>{const {openPcmCapture}=await import('/src/features/recording/browser-pcm-capture.ts' as string),original=navigator.mediaDevices.getUserMedia;navigator.mediaDevices.getUserMedia=()=>new Promise(()=>{});const controller=new AbortController(),start=performance.now(),pending=openPcmCapture(async()=>{},()=>{},controller.signal).then(()=>false,(error:Error)=>error.name==='AbortError');controller.abort();try{return{aborted:await pending,elapsed:performance.now()-start};}finally{navigator.mediaDevices.getUserMedia=original;}});
 expect(result.aborted).toBe(true);expect(result.elapsed).toBeLessThan(300);
});
test('duration ceiling pauses autonomously and retains a finishable exact saved range',async({page})=>{
 await page.goto('/login');await page.evaluate(async()=>{
  const {openPcmCapture}=await import('/src/features/recording/browser-pcm-capture.ts' as string),{createPcmJournal}=await import('/src/features/recording/pcm-journal.ts' as string),actor='limit-'+crypto.randomUUID(),session={sessionId:crypto.randomUUID(),partId:crypto.randomUUID(),writerEpoch:1,backendId:'a'.repeat(64),collectorId:crypto.randomUUID()},journal=createPcmJournal(actor),lost:string[]=[];
  await journal.begin(crypto.randomUUID(),session,[{partId:session.partId,finalSequence:3600,sampleCount:115199000,confirmedSequence:3600,confirmedSamples:115199000}]);
  const capture=await openPcmCapture(async()=>{},(cause:Error)=>lost.push(cause.message),undefined,true);const state={capture,journal,session,lost,limit:''};(window as any).limitTest=state;await capture.configure!({actor,session,snapshot:()=>{},limit:(message:string)=>{state.limit=message;}});await capture.resume();
 });await page.waitForFunction(()=>(window as any).limitTest.limit);
 const result=await page.evaluate(async()=>{const f=(window as any).limitTest,record=await f.journal.load(f.session.sessionId);await f.capture.release();f.capture.pipeline.dispose();f.journal.close();return{samples:record.parts[0].sampleCount,lost:f.lost,limit:f.limit};});
 expect(result.lost).toEqual([]);expect(result.samples).toBe(115200000);expect(result.limit).toContain('自动暂停');
});
test('4608 captions mount a bounded window and retain the reading position across revisions',async({page})=>{
 await page.goto('/login');await page.evaluate(async()=>{
  const {createApp,h,ref}=await import('/node_modules/.vite/deps/vue.js' as string),{default:LiveTranscript}=await import('/src/features/recording/LiveTranscript.vue' as string);
  const rows=ref(Array.from({length:4608},(_,i)=>({eventId:String(i+1),segmentId:'segment-'+i,revision:1,status:'temporary',text:'第'+i+'句会议内容。'.repeat(i%5+1),startMs:i*2000,endMs:i*2000+2000}))),container=document.createElement('div');
  container.id='virtual-transcript-test';container.style.cssText='height:480px;overflow-y:auto;width:900px;overflow-anchor:none';document.body.replaceChildren(container);const host=document.createElement('div');container.append(host);const app=createApp({render:()=>h(LiveTranscript,{events:rows.value,presentation:'recording',normalized:true,scrollElement:container})});app.mount(host);(window as any).virtualTest={rows,container,app};
 });await expect(page.locator('.acr-live-segment')).not.toHaveCount(0);expect(await page.locator('.acr-live-segment').count()).toBeLessThan(80);
 await page.evaluate(()=>{const f=(window as any).virtualTest;f.container.scrollTop=140000;});await page.waitForTimeout(200);
 const before=await page.evaluate(()=>{const f=(window as any).virtualTest;const first=document.querySelector('.acr-live-segment')!;return{top:f.container.scrollTop,id:first.getAttribute('data-event-id')};});
 await page.evaluate(()=>{const f=(window as any).virtualTest;const last=f.rows.value.at(-1);f.rows.value=[...f.rows.value.slice(0,-1),{...last,eventId:'4609',revision:2,text:'最后一句稳定修订。',status:'stable'}];});await page.waitForTimeout(150);
 expect(await page.evaluate(()=>(window as any).virtualTest.container.scrollTop)).toBeCloseTo(before.top,0);
 await page.evaluate(()=>{const f=(window as any).virtualTest;f.container.scrollTop=f.container.scrollHeight;});await expect(page.getByText('最后一句稳定修订。',{exact:true})).toBeVisible();expect(await page.locator('.acr-live-segment').count()).toBeLessThan(80);
 const copied=await page.evaluate(()=>{const f=(window as any).virtualTest;f.container.dispatchEvent(new KeyboardEvent('keydown',{key:'a',ctrlKey:true}));const clipboardData=new DataTransfer();f.container.dispatchEvent(new ClipboardEvent('copy',{clipboardData,cancelable:true}));return clipboardData.getData('text/plain');});expect(copied).toContain('第0句会议内容。');expect(copied).toContain('最后一句稳定修订。');expect(copied.split('说话人待识别')).toHaveLength(4609);
});
