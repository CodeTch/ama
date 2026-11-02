import type {Page,Route} from '@playwright/test';

export const auditId='00000000-0000-4000-8000-000000000001';
export const revisionId='00000000-0000-4000-8000-000000000002';
export const elementId='00000000-0000-4000-8000-000000000003';
export const topicId='00000000-0000-4000-8000-000000000004';
export const feedbackId='00000000-0000-4000-8000-000000000005';
const stamp='2026-11-02T02:00:00.000000Z';
const categories=['OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT'];
const stageNames=['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED'];
const member={memberId:elementId,memberKind:'ENTERPRISE',enterpriseId:'audit-owner',displayName:'检查账号'};
export const auditMeeting={id:auditId,revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',stage:'ENTRY',name:'稳定性检查会议',category:'project',summary:'逐页按钮检查',occurredOn:'2026-11-02',confirmedRosterId:revisionId,confirmedRoster:[member],rosterDraftId:revisionId,rosterDraft:[member]};
const transcriptObservation={meetingRevision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',currentSavedRevisionId:revisionId,currentConfirmationId:null};
export const minutesObservation={revision:1,revisionToken:'1',accessRevision:0,accessRevisionToken:'0',currentConfirmationId:revisionId,currentMinutesRevisionId:revisionId,currentBodyId:revisionId,currentSetId:elementId,lifecycle:'ACTIVE',templateRevisionId:null,exclusionRevisionId:null,needsGeneration:false,readOnlyReason:null};
const taskPage={items:[],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'};
const topic={id:topicId,name:'检查专题',description:'用于浏览器回归检查',revision:1,revisionToken:'1'};
const feedback={id:feedbackId,meetingId:auditId,category:'WORDING',comment:'按钮反馈检查',state:'PENDING',revisionToken:'1'};

/** All commands stay in this browser context; no local database or external provider is changed. */
export async function installButtonFixtures(page:Page){
 const state={stage:'minutes',feedbackState:'PENDING' as 'PENDING'|'ACCEPTED'|'REJECTED'|'NEEDS_MORE',feedbackRevision:'1',failFeedback:false,profilePending:false,unknown:[] as string[],calls:[] as string[]};
 await page.route('**/api/v1/**',async(route:Route)=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname,method=request.method();
  state.calls.push(`${method} ${path}`);
  const json=(value:unknown,status=200)=>route.fulfill({status,json:value});
  if(path==='/api/v1/session')return json({actor:{enterpriseId:'audit-owner',status:'ACTIVE',roles:['USER','PLATFORM_ADMIN']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'});
  if(path==='/api/v1/directory/people')return json({items:[{stableId:'audit-owner',displayName:'检查账号',department:'产品研发部',active:true},{stableId:'audit-member',displayName:'参会人员',department:'产品研发部',active:true}],nextCursor:null});
  if(path==='/api/v1/directory/departments')return json({items:[{id:'rd',name:'产品研发部',parentId:null,active:true}]});
  if(path==='/api/v1/meetings/categories')return json({items:[{id:'project',label:'项目会议'},{id:'risk',label:'风险评审'}],hasMore:false,nextCursor:null});
  if(path==='/api/v1/topics/memberships')return json((url.searchParams.get('meetingIds')??'').split(',').map(meetingId=>({meetingId,topicId:meetingId===auditId?topicId:null})));
  if(path==='/api/v1/topics')return json(method==='GET'?[topic]:topic,method==='POST'?201:200);
  if(path===`/api/v1/topics/${topicId}/summary`)return json({id:topicId,meetingCount:0,revisionToken:'1'});
  if(path===`/api/v1/topics/${topicId}`)return json({...topic,revision:2,revisionToken:'2'});
  if(path.includes('/topics')&&method!=='GET')return route.fulfill({status:204});
  if(path==='/api/v1/meetings'){
   if(method==='POST')return json({id:auditId,revision:1,revisionToken:'1',stage:'ENTRY'},201);
   const joined=url.searchParams.get('scope')==='joined',published=url.searchParams.get('stages')==='PUBLISHED',cursor=url.searchParams.has('cursor');
   const stages=published?['PUBLISHED']:stageNames;
   const items=stages.map((stage,index)=>({id:index===0?auditId:`00000000-0000-4000-8000-${String(index+20).padStart(12,'0')}`,accessRevisionToken:'0',category:'project',lifecycle:'ACTIVE',name:index===0?'稳定性检查会议':`状态检查 ${stage}`,occurredOn:'2026-11-02',participants:['检查账号','参会人员'],relation:joined?'MEMBER':'OWNER',revisionToken:'1',stage,updatedAt:stamp,workspaceStage:stage,writeState:joined?'MEMBER_READ_ONLY':stage==='PUBLISHED'?'PUBLISHED_READ_ONLY':'CHECK_WORKSPACE'}));
   return json({items,hasMore:!cursor,nextCursor:cursor?null:'second',total:items.length*2,stageCounts:Object.fromEntries(stageNames.map(stage=>[stage,stages.filter(s=>s===stage).length*2])),topicGroups:joined?{}:{[topicId]:2,__none:items.length*2-2}});
  }
  if(path==='/api/v1/feedback/summaries')return state.failFeedback?json({},503):json({items:[{...feedback,state:state.feedbackState,revisionToken:state.feedbackRevision,submittedAt:stamp,reviewReason:null}],nextCursor:url.searchParams.has('cursor')?null:revisionId,counts:{ALL:1,PENDING:state.feedbackState==='PENDING'?1:0,ACCEPTED:state.feedbackState==='ACCEPTED'?1:0,REJECTED:state.feedbackState==='REJECTED'?1:0,NEEDS_MORE:state.feedbackState==='NEEDS_MORE'?1:0}});
  if(path==='/api/v1/feedback'&&method==='POST')return json({...feedback,...request.postDataJSON()},201);
  if(path===`/api/v1/feedback/${feedbackId}`)return json({feedback:{...feedback,state:state.feedbackState,revisionToken:state.feedbackRevision},submittedAt:stamp,reviewReason:null,reviewedBy:null,history:[]});
  if(path.startsWith(`/api/v1/feedback/${feedbackId}/`)){const operation=path.endsWith('/supplements')?'SUPPLEMENT':path.endsWith('/undo')?'UNDO_REVIEW':'REVIEW';state.feedbackRevision=String(BigInt(request.postDataJSON().expectedRevisionToken)+1n);if(operation==='REVIEW')state.feedbackState=request.postDataJSON().decision;else if(operation==='UNDO_REVIEW')state.feedbackState='PENDING';return json({feedbackId,historyId:revisionId,revisionToken:state.feedbackRevision,state:state.feedbackState,operation});}
  const root=`/api/v1/meetings/${auditId}`;
  if(path===root)return json({...auditMeeting,stage:state.stage==='published'?'PUBLISHED':'ENTRY'});
  if(path===root+'/workspace')return json({meetingId:auditId,revisionToken:'1',accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',relation:'OWNER',navigationState:state.stage==='published'?'PUBLISHED_READ_ONLY':'CHECK_PRODUCER',stage:state.stage==='published'?'PUBLISHED':'MINUTES_PENDING',saved:{infoRevisionId:revisionId,rosterRevisionId:revisionId,transcriptRevisionId:state.stage==='processing'?null:revisionId,transcriptConfirmationId:['processing','transcript'].includes(state.stage)?null:revisionId,minutesRevisionId:['processing','transcript'].includes(state.stage)?null:revisionId,elementSetId:['processing','transcript'].includes(state.stage)?null:elementId},privateSectionsState:'AVAILABLE',privateSections:{verification:'PERSISTED_REFERENCES_RECHECK_PRODUCER',tasks:taskPage,candidates:{items:[],hasMore:false,detailPath:root+'/minutes/candidates'},exclusionRevisionId:null,finalConfirmationId:['publish','published'].includes(state.stage)?revisionId:null,publicationId:null,publicationState:null,savedGenerationInputId:null,savedProcessingInputId:null,templateRevisionId:null}});
  if(path===root+'/tasks')return json(taskPage);
  if(path===root+'/minutes')return json({body:{id:revisionId,title:'按钮检查纪要',body:'逐页检查所有按钮。\n核对点击前后的布局和加载状态。',titleOrigin:'HUMAN',revisionToken:'1'},setId:elementId,elements:Object.fromEntries(categories.map((kind,index)=>[kind,[{id:`00000000-0000-4000-8000-${String(index+100).padStart(12,'0')}`,body:`${kind} 的检查内容`,ordinalToken:'1',action:null,suggestion:null}]])),evidence:[],sensitive:[],background:[],observation:{...minutesObservation,readOnlyReason:state.stage==='published'?'PUBLISHED':null}});
  if(path===root+'/transcript')return json({meetingId:auditId,observation:transcriptObservation,storedInspection:'AVAILABLE',needsGeneration:false,correction:{mode:'DISABLED_NOT_INSTALLED',qualificationId:null,readiness:'NOT_APPLICABLE'},capabilities:{canConfirm:true,canReadHistory:true,canReadSources:false,canRestore:true,canSave:true},confirmation:null,sources:[],sourceBytes:[],saved:{id:revisionId,kind:'WORKING',revision:1,revisionToken:'1',segments:[{segmentId:elementId,ordinal:0,body:'会议内容，用于验证按钮交互和转写编辑。',sourceFileId:topicId,sourceParagraph:1,speakerId:null,startMs:null,endMs:null,marks:[]}]}});
  if(path===root+'/transcript/history')return json({items:[],hasMore:false,nextCursor:null,observation:transcriptObservation});
  if(path===root+'/materials')return json({items:[],materialSnapshotId:null,materialsPending:false,meetingRevision:'1'});
  if(path===root+'/completed-files')return json({items:[],nextCursor:null});
  if(path===root+'/final-confirmations/current')return json({confirmation:null});
  if(path===root+'/final-confirmations/readiness')return json({ready:false,businessReady:false,correction:{mode:'DISABLED_NOT_INSTALLED',readiness:'NOT_APPLICABLE',qualificationId:null},required:[],requiredReviewIds:[],optionalReviewIds:[],blockers:['请核对会议内容'],pendingCandidateIds:[],outstandingTaskIds:[],observation:minutesObservation});
  if(path===root+'/publications/current')return json({publication:state.stage==='published'?{publicationId:revisionId,confirmationId:revisionId,rawState:'SUCCEEDED',remoteOutcome:'SUCCESS',applicationOutcome:'APPLIED',manualLocked:false,lookupPending:false,officialDocumentUrl:root+`/publications/${revisionId}/document`,documentViewAvailability:'READY',allowedActions:['VIEW_FROZEN','REFRESH_STATUS','VIEW_ATTEMPTS'],readOnlyReason:'PUBLISHED',eligibilityAvailability:'RECHECK_REQUIRED'}:null});
  if(path.endsWith('/library-context'))return json({meetingId:auditId,publicationId:revisionId,providerId:'audit',storeInstanceId:'audit',libraryId:'research',libraryName:'检查知识库',libraryDescription:'检查已发布会议',folderId:'root',folderPath:['会议纪要'],documentId:'doc-'+'a'.repeat(32),payloadSha256:'a'.repeat(64),versionToken:'b'.repeat(64),documentUrl:root+`/publications/${revisionId}/document`});
  if(path===root+'/publication-destinations/libraries')return json({providerId:'audit',items:[{id:'research',name:'检查知识库',rootFolderId:'root'}],hasMore:false,nextCursor:null});
  if(path.includes('/publication-destinations/libraries/research/folders'))return json({providerId:'audit',items:[{id:'root',libraryId:'research',parentFolderId:null,isRoot:true,name:'会议纪要'}],hasMore:false,nextCursor:null});
  // Unmodelled commands deliberately return a recoverable error rather than pretending a save succeeded.
  state.unknown.push(`${method} ${path}`);return json({},503);
 });
 return state;
}
