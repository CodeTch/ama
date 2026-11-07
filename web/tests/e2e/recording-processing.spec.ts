import {test,expect,type Page} from '@playwright/test';
import type {RecordingProcessing} from '../../src/features/recording/processing-api';

const id=(n:number)=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const meeting=id(1),session=id(2),part=id(3),task=id(4),input=id(5),version='a'.repeat(64);
async function recording(page:Page,initial:RecordingProcessing['state'],existing=false,confirmed=false,materialsChanged=false){
 let state=initial,reads=0,parts=0,directories=0,retries=0,posts=0;
 const result=()=>({sessionId:session,meetingId:meeting,state,materialSnapshotId:input,taskId:task,errorCode:state==='FAILED'?'RECORDING_TRANSCRIPTION_FAILED':null,materialsChanged,
  materialFiles:[{materialRevisionId:id(7),name:'议程.pdf',state:'READY',source:'KNOWLEDGE',sourceLibraryId:'project',sourceVersion:version,error:null}]});
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route(`**/meetings/${meeting}`,route=>route.fulfill({json:{id:meeting,name:'材料回归会议',category:null,summary:null,occurredOn:null,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'TRANSCRIPT_PENDING',rosterDraftId:null,rosterDraft:null,confirmedRosterId:null,confirmedRoster:[]}}));
 await page.route(`**/meetings/${meeting}/workspace/access`,route=>route.fulfill({json:{meetingId:meeting,allowed:true}}));
 await page.route(`**/meetings/${meeting}/workspace`,route=>route.fulfill({json:{
  meetingId:meeting,revisionToken:'1',accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',stage:'TRANSCRIPT_PENDING',relation:'OWNER',navigationState:'CHECK_PRODUCER',
  saved:{infoRevisionId:meeting,elementSetId:null,minutesRevisionId:null,rosterRevisionId:null,transcriptConfirmationId:confirmed?id(9):null,transcriptRevisionId:existing||state==='SUCCEEDED'?id(8):null},
  privateSectionsState:'AVAILABLE',privateSections:{verification:'PERSISTED_REFERENCES_RECHECK_PRODUCER',exclusionRevisionId:null,finalConfirmationId:null,publicationId:null,publicationState:null,savedGenerationInputId:null,savedProcessingInputId:null,templateRevisionId:null,candidates:{items:[],hasMore:false,detailPath:`/api/v1/meetings/${meeting}/minutes/candidates`},
   tasks:{verification:'PERSISTED_RELATION',hasMore:false,nextCursor:null,items:[{kind:'RECORDING_ARCHIVE_V1',taskId:task,inputId:input,state:'SUCCEEDED',attemptToken:'1',businessRevisionToken:'1',detailPath:`/api/v1/meetings/${meeting}/recording-archives/${input}`}]}}
 }}));
 await page.route(`**/meetings/${meeting}/recordings`,route=>route.fulfill({json:{session:{sessionId:session,partId:part,writerEpoch:1,backendId:'a'.repeat(64)}}}));
 await page.route(`**/recordings/${session}/part-sources`,route=>{parts++;return route.fulfill({json:{sessionId:session,parts:[{partId:part,partNo:1,startSample:0,sampleCount:48000,state:'AVAILABLE'}]}});});
 await page.route(`**/recordings/${session}/archive`,route=>route.fulfill({json:{taskId:task,state:'SUCCEEDED',available:true,sampleCount:48000}}));
 await page.route(`**/recordings/${session}/processing`,route=>{if(route.request().method()==='GET')reads++;else posts++;return route.fulfill({json:result()});});
 await page.route(`**/recordings/${session}/processing/retry`,route=>{retries++;state='ENHANCING';return route.fulfill({json:result()});});
 await page.route(`**/meetings/${meeting}/materials`,route=>{directories++;return route.fulfill({json:{items:[],materialSnapshotId:null,materialsPending:false,meetingRevision:'1'}});});
 await page.goto(`/meeting/${meeting}/processing`);
 // Cold Vite compilation is shared by all parallel browser projects.
 await expect(page.getByText('第1段录音.wav',{exact:true})).toBeVisible({timeout:15000});
 return {counts:()=>({reads,parts,directories,retries,posts}),set(next:RecordingProcessing['state']){state=next;}};
}

test('stale recording preserves the previous draft, frozen knowledge version and an idle terminal page',async({page})=>{
 const fixture=await recording(page,'STALE',true);
 const next=page.getByRole('link',{name:'核对已有转写'});
 await expect(next).toBeVisible();await expect(page.getByText('结果已过期',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'材料导入',exact:true})).toHaveAttribute('data-complete','false');
 await expect(page.getByText('议程.pdf',{exact:true})).toBeVisible();await expect(page.getByTitle(version)).toContainText(version.slice(0,12));
 await expect(page.getByText('识别及转写增强完成',{exact:true})).toHaveCount(0);
 await page.waitForTimeout(3200);
 expect(fixture.counts()).toEqual({reads:1,parts:1,directories:0,retries:0,posts:0});
});

test('an explicit retry resumes observation and only success unlocks the result',async({page})=>{
 const fixture=await recording(page,'FAILED');
 await page.getByRole('button',{name:'重试处理',exact:true}).click();
 await expect(page.getByRole('button',{name:'进入转写校正'})).toBeDisabled();
 fixture.set('SUCCEEDED');
 await expect(page.getByRole('link',{name:'进入转写校正'})).toBeVisible();
 expect(fixture.counts()).toEqual({reads:2,parts:1,directories:0,retries:1,posts:0});
});

test('unapplied materials keep the current step open while a confirmed prior transcript remains accessible',async({page})=>{
 await recording(page,'SUCCEEDED',true,true,true);
 await expect(page.getByRole('button',{name:'材料导入',exact:true})).toHaveAttribute('data-complete','false');
 await expect(page.getByRole('region',{name:'当前步骤确认操作'})).toContainText('已有转写使用上次提交的材料');
 await expect(page.getByRole('link',{name:'核对已有转写'})).toBeVisible();
 await expect(page.getByRole('link',{name:'进入转写校正'})).toHaveCount(0);
});

test('a confirmed recording uses the prototype single-column small screen and releases the completed footer',async({page})=>{
 await page.setViewportSize({width:390,height:844});
 await recording(page,'SUCCEEDED',true,true);
 await expect(page.getByRole('button',{name:'查看会议状态与处理记录'})).toHaveText('待生成纪要');
 const grid=page.locator('.aci-material-grid');
 await expect(grid).toBeVisible();
 expect(await grid.evaluate(el=>getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).length)).toBe(1);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(390);
 await expect(page.getByRole('region',{name:'当前步骤确认操作'})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'调整背景材料'})).toBeInViewport();
});
