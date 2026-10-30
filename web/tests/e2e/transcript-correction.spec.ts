import {test,expect} from '@playwright/test';

const meeting='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';
const infoRevision='10000000-0000-4000-8000-000000000001';
const task='10000000-0000-4000-8000-000000000002';
const input='10000000-0000-4000-8000-000000000003';

function fixture(path:string){
 const saved={accessRevision:0,accessRevisionToken:'0',category:null,confirmedRoster:[],confirmedRosterId:null,id:meeting,name:null,occurredOn:null,revision:1,revisionToken:'1',rosterDraft:null,rosterDraftId:null,stage:'TRANSCRIPT_PENDING',summary:null};
 const taskItem={attemptToken:'0',businessRevisionToken:'1',detailPath:`/api/v1/meetings/${meeting}/processing-tasks/${task}`,inputId:input,kind:'TRANSCRIPT_IMPORT_V1',state:'SUCCEEDED',taskId:task};
 const privateSections={candidates:{detailPath:`/api/v1/meetings/${meeting}/minutes/candidates`,hasMore:false,items:[]},exclusionRevisionId:null,finalConfirmationId:null,publicationId:null,publicationState:null,savedGenerationInputId:null,savedProcessingInputId:null,templateRevisionId:null,tasks:{hasMore:false,items:[taskItem],nextCursor:null,verification:'PERSISTED_RELATION'},verification:'PERSISTED_REFERENCES_RECHECK_PRODUCER'};
 if(path.endsWith(`/meetings/${meeting}`))return saved;
 if(path.endsWith(`/meetings/${meeting}/workspace`))return {accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',governanceState:'ACTIVE',lifecycle:'ACTIVE',meetingId:meeting,navigationState:'CHECK_PRODUCER',privateSections,privateSectionsState:'AVAILABLE',relation:'OWNER',revisionToken:'1',saved:{elementSetId:null,infoRevisionId:infoRevision,minutesRevisionId:null,rosterRevisionId:null,transcriptConfirmationId:null,transcriptRevisionId:null},stage:'TRANSCRIPT_PENDING'};
 if(path.endsWith(`/meetings/${meeting}/transcript`))return {capabilities:{canConfirm:false,canReadHistory:true,canReadSources:false,canRestore:false,canSave:false},confirmation:null,confirmationHashProfile:'ama.transcript-confirmation.v1',correction:{mode:'DISABLED_NOT_INSTALLED',qualificationId:null,readiness:'NOT_APPLICABLE'},meetingId:meeting,needsGeneration:false,observation:{accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentSavedRevisionId:null,meetingRevision:1,revisionToken:'1'},parserVersion:'demo',processingConfigVersion:'demo',selectedConfirmation:null,saved:null,sourceBytes:[],sources:[],storedInspection:'AVAILABLE'};
 if(path.endsWith(`/meetings/${meeting}/transcript/history`))return {hasMore:false,items:[],nextCursor:null,observation:{accessRevision:0,accessRevisionToken:'0',currentConfirmationId:null,currentSavedRevisionId:null,meetingRevision:1,revisionToken:'1'}};
 return null;
}

test.beforeEach(async({page})=>{
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url());
  if(url.pathname==='/api/v1/session')return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({actor:{enterpriseId:'demo-owner',roles:['USER'],status:'ACTIVE'},csrfToken:'demo-csrf',expiresAt:'2099-01-01T00:00:00.000Z'})});
  const value=fixture(url.pathname);
  if(value!==null)return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(value)});
  return route.fulfill({status:404,contentType:'application/json',body:'{}'});
 });
});

test('renders the transcript correction empty workspace and keeps history available',async({page},info)=>{
 await page.goto(`/meeting/${meeting}/transcript`);
 await expect(page.getByRole('heading',{name:'未命名会议',exact:true})).toBeVisible();
 await expect(page.getByText('转写待处理',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'转写校正',exact:true})).toHaveAttribute('aria-current','step');
 await expect(page.getByText('会议录音',{exact:true})).toBeVisible();
 await expect(page.getByText('本次为文本转写文件，无原录音，不虚构播放时长。',{exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'转写正文',exact:true})).toBeVisible();
 await expect(page.getByText('尚无已保存转写。导入任务完成后重新读取。',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'重新读取转写',exact:true})).toBeVisible();
 await expect(page.getByText('转写历史与原操作恢复',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(info.project.use.viewport!.width);
 await page.screenshot({path:info.outputPath('transcript-correction-empty.png'),fullPage:true});
});

test('expands transcript history without changing the empty-state layout',async({page})=>{
 await page.goto(`/meeting/${meeting}/transcript`);
 const history=page.getByText('转写历史与原操作恢复',{exact:true});
 await history.click();
 await expect(page.getByRole('heading',{name:'转写历史',exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'读取历史版本',exact:true})).toBeVisible();
 await expect(page.getByText('尚无历史版本',{exact:true})).toHaveCount(0);
});
