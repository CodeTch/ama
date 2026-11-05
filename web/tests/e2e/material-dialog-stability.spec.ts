import {test,expect,type Page} from '@playwright/test';
import type {MaterialListing} from '../../src/shared/api/materials';

test.use({contextOptions:{reducedMotion:'no-preference'}});
const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',task='00000000-0000-4000-8000-000000000002',input='00000000-0000-4000-8000-000000000003',upload='00000000-0000-4000-8000-000000000004',file='00000000-0000-4000-8000-000000000005',sha='a'.repeat(64);
const names=['架构评审材料.docx','会议补充材料.docx'];
const items:MaterialListing['items']=names.map((name,index)=>({id:`00000000-0000-4000-8000-00000000001${index}`,materialRevisionId:`00000000-0000-4000-8000-00000000002${index}`,parsedDocumentId:`00000000-0000-4000-8000-00000000003${index}`,fileId:`00000000-0000-4000-8000-00000000004${index}`,name,source:'LOCAL',sourceVersion:null,sizeBytesToken:'3',sha256:sha,state:'READY',error:null}));
const listing:MaterialListing={items,materialSnapshotId:input,materialsPending:false,meetingRevision:'1'};
function gate(){let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});return {wait,release};}

async function setup(page:Page,initialState='SUCCEEDED',beforeNavigation?:()=>Promise<void>){
 const opening=gate(),refresh=gate();let reads=0,currentListing=listing;
 await page.route('**/api/v1/**',route=>route.fulfill({status:503,json:{}}));
 await page.route('**/material-adjustments/latest',r=>r.fulfill({json:{stage:'NONE'}}));
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({json:{items:[],nextCursor:null}}));
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route(`**/api/v1/meetings/${meeting}`,route=>route.fulfill({json:{id:meeting,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'TRANSCRIPT_PENDING',name:'背景材料回归会议',category:null,summary:null,occurredOn:null,rosterDraftId:null,rosterDraft:null,confirmedRosterId:null,confirmedRoster:[]}}));
 await page.route(`**/api/v1/meetings/${meeting}/workspace/access`,route=>route.fulfill({json:{meetingId:meeting,allowed:true}}));
 const taskItem={taskId:task,inputId:input,kind:'TRANSCRIPT_IMPORT_V1',state:initialState,attemptToken:'1',businessRevisionToken:'1',detailPath:`/api/v1/meetings/${meeting}/processing-tasks/${task}`};
 await page.route(`**/api/v1/meetings/${meeting}/workspace`,route=>route.fulfill({json:{meetingId:meeting,revisionToken:'1',accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',stage:'TRANSCRIPT_PENDING',relation:'OWNER',navigationState:'CHECK_PRODUCER',saved:{infoRevisionId:meeting,elementSetId:null,minutesRevisionId:null,rosterRevisionId:null,transcriptConfirmationId:null,transcriptRevisionId:null},privateSectionsState:'AVAILABLE',privateSections:{verification:'PERSISTED_REFERENCES_RECHECK_PRODUCER',exclusionRevisionId:null,finalConfirmationId:null,publicationId:null,publicationState:null,savedGenerationInputId:null,savedProcessingInputId:input,templateRevisionId:null,tasks:{items:[taskItem],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'},candidates:{items:[],hasMore:false,detailPath:`/api/v1/meetings/${meeting}/minutes/candidates`}}}}));
 await page.route(`**/api/v1/meetings/${meeting}/processing-inputs/${input}`,route=>route.fulfill({json:{inputId:input,batchId:meeting,kind:'IMPORT',materialMode:'EMPTY',baseRevision:1,revisionToken:'1',inputFingerprint:sha,createdAt:'2026-11-02T00:00:00Z',taskId:task,batchProfile:'ama.input-batch.v1',batchFingerprint:sha,cancelEpochToken:'0',files:[{ordinal:1,fileId:file,uploadId:upload,sha256:sha,sizeBytes:3,sizeBytesToken:'3'}]}}));
 const completed={fileId:file,uploadId:upload,displayName:'meeting.zh-CN.txt',sizeBytes:3,sizeBytesToken:'3',sha256:sha,fileState:'AVAILABLE',originalPurpose:'TRANSCRIPT_INPUT',intakeEligibility:{state:'ELIGIBLE',reason:null},businessReadEligibility:'NOT_EVALUATED'};
 await page.route(`**/api/v1/meetings/${meeting}/uploads/${upload}`,route=>route.fulfill({json:{uploadId:upload,purpose:'TRANSCRIPT_INPUT',displayName:completed.displayName,state:'COMPLETED',expectedSize:3,expectedSizeToken:'3',expectedSha256:sha,createdAt:'2026-11-02T00:00:00Z',updatedAt:'2026-11-02T00:00:00Z',completedFileId:file,accepted:{uploadId:upload,partSize:8,partSizeToken:'8'},allowedActions:[],idempotentRetries:[],blockedReason:null,completed}}));
 await page.route(`**/api/v1/meetings/${meeting}/materials`,async route=>{const current=++reads;if(current===2)await opening.wait;if(current>=3)await refresh.wait;await route.fulfill({json:currentListing});});
 await beforeNavigation?.();
 await page.goto(`/meeting/${meeting}/processing`);
 const background=page.locator('.aci-material-grid>section').nth(1);
 await expect(background.getByText(names[0]!,{exact:true})).toBeVisible();
 await expect(page.locator('.aci-material-grid>section').first().getByText(completed.displayName,{exact:true})).toBeVisible();
 await expect(background).toHaveAttribute('aria-busy','false');
 return {opening,refresh,background,reads:()=>reads,replace(value:MaterialListing){currentListing=value;},release(){opening.release();refresh.release();}};
}

async function frame(page:Page){await page.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));}

test('background material dialog keeps its frame and footer fixed while materials load',async({page})=>{
 const data=await setup(page);
 try{
  await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true}),done=dialog.getByRole('button',{name:'取消',exact:true});
  await expect(dialog).toBeVisible();await expect.poll(data.reads).toBe(2);await frame(page);
  const loading=await dialog.boundingBox(),footer=await done.boundingBox();
  data.opening.release();await expect(dialog.getByText(names[0]!,{exact:true})).toBeVisible();await expect(done).toBeEnabled();await frame(page);
  expect(await dialog.boundingBox()).toEqual(loading);expect(await done.boundingBox()).toEqual(footer);
  await page.screenshot({path:test.info().outputPath('material-dialog.png')});
 }finally{data.release();}
});

test('completing background materials preserves the detail list throughout its refresh',async({page})=>{
 const data=await setup(page);
 try{
  data.opening.release();
  const before=await data.background.boundingBox(),scroll=await page.evaluate(()=>window.scrollY),first=data.background.getByText(names[0]!,{exact:true}),node=await first.elementHandle();
  await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true});
  await expect(dialog.getByText(names[0]!,{exact:true})).toBeVisible();
  await dialog.getByRole('button',{name:'取消',exact:true}).click();
  await expect(dialog).toBeHidden();await expect.poll(data.reads).toBe(3);await frame(page);
  await expect(first).toBeVisible();expect(await data.background.boundingBox()).toEqual(before);
  expect(await node!.evaluate(el=>el.isConnected)).toBe(true);
  data.refresh.release();await expect(data.background).toHaveAttribute('aria-busy','false');await frame(page);
  expect(await data.background.boundingBox()).toEqual(before);expect(await node!.evaluate(el=>el.isConnected)).toBe(true);expect(await page.evaluate(()=>window.scrollY)).toBe(scroll);
 }finally{data.release();}
});

test('a long material list scrolls inside the fixed dialog and keeps the footer visible',async({page})=>{
 const data=await setup(page);
 try{
  const many=Array.from({length:12},(_,index)=>({...items[0]!,id:`00000000-0000-4000-8000-${String(100+index).padStart(12,'0')}`,name:`补充材料${index+1}.docx`}));
  data.replace({...listing,items:many});
  await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true}),done=dialog.getByRole('button',{name:'取消',exact:true});
  await expect.poll(data.reads).toBe(2);await frame(page);const before=await dialog.boundingBox(),footer=await done.boundingBox();
  data.opening.release();await expect(done).toBeEnabled();await frame(page);
  expect(await dialog.boundingBox()).toEqual(before);expect(await done.boundingBox()).toEqual(footer);await expect(done).toBeInViewport();
  const body=dialog.locator(':scope > .modal-b');expect(await body.evaluate(el=>el.scrollHeight>el.clientHeight)).toBe(true);
  await body.evaluate(el=>{el.scrollTop=el.scrollHeight;});await expect(dialog.getByText('补充材料12.docx',{exact:true})).toBeInViewport();
  expect(await done.boundingBox()).toEqual(footer);
 }finally{data.release();}
});

test('closing refresh replaces the retained materials with the updated directory',async({page})=>{
 const data=await setup(page);
 try{
  data.opening.release();await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true});await expect(dialog.getByRole('button',{name:'取消',exact:true})).toBeEnabled();
  data.replace({...listing,items:items.slice(1),meetingRevision:'2'});
  await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).toBeHidden();
  await expect(data.background).toHaveAttribute('aria-busy','true');await expect(data.background.getByText(names[0]!,{exact:true})).toBeVisible();
  data.refresh.release();await expect(data.background).toHaveAttribute('aria-busy','false');
  await expect(data.background.getByText(names[0]!,{exact:true})).toHaveCount(0);await expect(data.background.getByText(names[1]!,{exact:true})).toBeVisible();
  await expect(data.background.locator('.aci-progress-file')).toHaveCount(1);
 }finally{data.release();}
});

test('selecting knowledge and local files keeps changes private until apply',async({page})=>{
 const data=await setup(page),parsing=gate();let batchStarted=false,uploadStarted=false;
 try{
  data.release();
  await page.route('**/material-libraries*',route=>route.fulfill({json:{items:[{id:'lib',name:'资料库'}],cursor:null}}));
  await page.route('**/material-libraries/lib/documents*',route=>route.fulfill({json:{items:[{id:'doc',libraryId:'lib',name:'解析材料.docx',version:sha,sha256:sha,sizeBytesToken:'3'}],cursor:null}}));
  await page.route(`**/meetings/${meeting}/materials/batches`,async route=>{batchStarted=true;await parsing.wait;await route.fulfill({status:201,json:{items:[],materialSnapshotId:null,meetingRevision:'2'}});});
  await page.route(`**/meetings/${meeting}/uploads`,async route=>{uploadStarted=true;await route.fulfill({status:503,json:{}});});
  await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true});
  await dialog.getByRole('button',{name:'从知识库选择',exact:true}).click();
  const picker=page.getByRole('dialog',{name:'从知识库选择材料',exact:true});
  await picker.getByRole('checkbox').check();await picker.getByRole('button',{name:'添加所选文件',exact:true}).click();
  await expect(picker).toBeHidden();expect(batchStarted).toBe(false);
  await expect(dialog.getByRole('button',{name:'上传本地文件',exact:true})).toBeEnabled();
  await expect(dialog.getByRole('button',{name:'从知识库选择',exact:true})).toBeEnabled();
  await dialog.getByLabel('选择背景材料',{exact:true}).setInputFiles({name:'继续上传.pdf',mimeType:'application/pdf',buffer:Buffer.from('pdf bytes')});
  expect(uploadStarted).toBe(false);
  await expect(dialog.getByText(/继续上传.pdf/)).toBeVisible();
  await dialog.getByRole('button',{name:'从知识库选择',exact:true}).click();await expect(picker).toBeVisible();
 }finally{parsing.release();data.release();}
});

test('background material dialog can close during a pending read',async({page})=>{
 const data=await setup(page);
 try{
  await page.getByRole('button',{name:'调整背景材料',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'会议背景材料',exact:true});
  await expect.poll(data.reads).toBe(2);
  await dialog.getByRole('button',{name:'关闭背景材料',exact:true}).click();
  await expect(dialog).toBeHidden();
 }finally{data.release();}
});

test('reopening a running import observes its exact task until success without uploading or leaving the selected step',async({page})=>{
 let taskReads=0,writes=0,inputReads=0;
 page.on('request',request=>{if(!request.url().includes('/api/v1/'))return;if(request.method()==='POST')writes++;if(request.url().endsWith('/processing-inputs/'+input))inputReads++;});
 const data=await setup(page,'RUNNING',()=>page.route(`**/api/v1/meetings/${meeting}/processing-tasks/${task}`,route=>{
  taskReads++;const state=taskReads<3?'RUNNING':'SUCCEEDED';
  return route.fulfill({json:{taskId:task,type:'TRANSCRIPT_IMPORT_V1',pool:'TRANSCRIPT_IMPORT',meetingId:meeting,inputId:input,batchId:meeting,state,
   businessVersion:1,businessVersionToken:'1',cancelEpochToken:'0',attempt:1,attemptToken:'1',maxAttempts:3,
   availableAt:'2026-01-01T00:00:00Z',createdAt:'2026-01-01T00:00:00Z',errorCode:null,output:state==='SUCCEEDED'?{kind:'TRANSCRIPT_REVISION',id:file}:null}});
 }));
 data.release();
 const next=page.getByRole('button',{name:'进入转写校正',exact:true});
 await expect(next).toBeDisabled();
 await expect(next).toBeEnabled({timeout:9000});
 expect(taskReads).toBe(3);expect(inputReads).toBe(1);expect(writes).toBe(0);
 await expect(page.getByRole('button',{name:'材料导入',exact:true})).toHaveAttribute('aria-current','step');
});
