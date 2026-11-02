import {test,expect,type Page} from '@playwright/test';
import {writeFile} from 'node:fs/promises';
import {installButtonFixtures,auditId,revisionId,elementId,topicId} from '../support/button-fixtures';

test.use({contextOptions:{reducedMotion:'no-preference'}});
const info={value:{name:'按钮检查会议',occurredOn:'2026-11-02',category:'project',summary:'检查说明',rosterDraftIds:['audit-owner'],externalRosterNames:['外部人员 · 合作机构']},dirty:false,busy:false,blocked:false,state:'EMPTY',operation:null,conflict:false,canConfirm:true,categories:[{id:'project',label:'项目会议'}],people:[{stableId:'audit-owner',displayName:'检查账号',department:'产品研发部',active:true}],departments:[{id:'rd',name:'产品研发部',parentId:null,active:true}],peopleReady:true,optionsBusy:false,optionsError:null,categoryMore:true,peopleMore:true,error:null,storageStatus:'SAVED'};
const topic={name:'检查专题',description:'专题说明',selected:'',topics:[{id:topicId,name:'项目专题',description:'说明',revision:1,revisionToken:'1'}],meetingNames:['按钮检查会议'],pending:false,busy:false,composing:false,state:'EMPTY',error:null};
const template={mode:'create',name:'检查模板',themeText:'会议概述\n核心结论',pending:false,busy:false,composing:false,state:'EMPTY',error:'',concealed:false,accepted:false};
const transcript={value:[{segmentId:elementId,body:'检查转写按钮的点击前后状态',marks:[]},{segmentId:topicId,body:'核对第二段会议内容',marks:['DISPUTE']}],editable:true,busy:false,dirty:false,conflict:null,conflictToken:null,state:'EMPTY',blocked:false,storageStatus:'SAVED',error:null,confirmed:false,canConfirm:true,revisionToken:'1',correctionsReady:true,correctionCount:2};
type Sample={name:string;file:string;props:Record<string,unknown>;authenticated?:boolean;show?:boolean;prepare?:(page:Page)=>Promise<unknown>};
const samples:Sample[]=[
 {name:'information',file:'meetings/MeetingInfoForm.vue',props:info},
 {name:'information-directory',file:'meetings/MeetingInfoForm.vue',props:info,prepare:page=>page.getByRole('button',{name:'添加参会人',exact:true}).click()},
 {name:'information-external',file:'meetings/MeetingInfoForm.vue',props:info,prepare:async page=>{await page.getByRole('button',{name:'添加参会人',exact:true}).click();await page.getByRole('tab',{name:'外部人员',exact:true}).click();await page.getByLabel('外部参会人员',{exact:true}).fill('检查人员');await page.getByLabel('外部机构',{exact:true}).fill('合作机构');}},
 {name:'information-conflict',file:'meetings/MeetingInfoForm.vue',props:{...info,conflict:true,state:'CONFLICT',conflictValue:info.value,conflictToken:'2'}},
 {name:'information-recovery',file:'meetings/MeetingInfoForm.vue',props:{...info,state:'UNCERTAIN'}},
 ...['create','update','delete','assign'].map(mode=>({name:'topic-'+mode,file:'meetings/TopicDialogView.vue',props:{...topic,mode,deleteCount:0},show:true})),
 {name:'topic-recovery',file:'meetings/TopicDialogView.vue',props:{...topic,mode:'create',pending:true,state:'CONFLICT'},show:true},
 {name:'topic-batch',file:'meetings/TopicDialogView.vue',props:{...topic,mode:'assign',meetingNames:['第一场会议','第二场会议']},show:true},
 {name:'topic-blocked-delete',file:'meetings/TopicDialogView.vue',props:{...topic,mode:'delete',deleteCount:3},show:true},
 ...['create','update','delete'].map(mode=>({name:'template-'+mode,file:'minutes/PersonalTemplateDialogView.vue',props:{...template,mode},show:true})),
 {name:'template-recovery',file:'minutes/PersonalTemplateDialogView.vue',props:{...template,pending:true,state:'CONFLICT'},show:true},
 {name:'create-meeting',file:'meetings/MeetingCreatePanel.vue',props:{state:'EMPTY',busy:false,blocked:false,storageStatus:'EMPTY',cleanupFailed:false,message:null,discovery:null,discovering:false,discoveryError:false}},
 {name:'create-recovery',file:'meetings/MeetingCreatePanel.vue',props:{state:'UNCERTAIN',busy:false,blocked:true,storageStatus:'SAVED',cleanupFailed:false,message:'等待核对',discovery:null,discovering:false,discoveryError:true}},
 {name:'create-rejected',file:'meetings/MeetingCreatePanel.vue',props:{state:'REJECTED',busy:false,blocked:false,storageStatus:'SAVED',cleanupFailed:false,message:'需重新读取',discovery:null,discovering:false,discoveryError:false}},
 {name:'upload-files',file:'import/UploadImportView.vue',props:{items:[{key:'first',name:'第一段.txt',size:1024,status:'FAILED',error:'重试上传',uploadId:revisionId,fileId:null,canAbort:true,canRemove:true},{key:'second',name:'第二段.txt',size:1024,status:'OBSERVED_COMPLETED',error:null,uploadId:elementId,fileId:topicId,canRemove:true}],ready:true,blocked:false,error:null,importState:'EMPTY',importBusy:false,taskId:null},prepare:async page=>{for(const summary of await page.locator('summary').all())await summary.click();}},
 {name:'upload-recovery',file:'import/UploadImportView.vue',props:{items:[],ready:true,blocked:true,error:'恢复记录需核对',importState:'UNCERTAIN',importBusy:false,taskId:null}},
 {name:'transcript',file:'transcript/TranscriptForm.vue',props:transcript},
 {name:'transcript-conflict',file:'transcript/TranscriptForm.vue',props:{...transcript,conflict:transcript.value,conflictToken:'2',state:'CONFLICT'}},
 {name:'transcript-recovery',file:'transcript/TranscriptForm.vue',props:{...transcript,state:'UNCERTAIN'}},
 {name:'transcript-edit-dialog',file:'transcript/TranscriptForm.vue',props:transcript,prepare:page=>page.getByRole('button',{name:'编辑第 1 段',exact:true}).click()},
 {name:'transcript-replace-dialog',file:'transcript/TranscriptForm.vue',props:transcript,prepare:page=>page.getByRole('button',{name:'批量替换',exact:true}).click()},
 {name:'speakers',file:'transcript/SpeakerEditor.vue',props:{speakers:[{id:elementId,label:'发言人甲',enterpriseId:null,anonymous:true}],readonly:false}},
 {name:'speaker-summary',file:'transcript/TranscriptSpeakerSummary.vue',props:{items:[{id:elementId,label:'发言人甲',enterpriseId:null,segments:2}],disabled:false}},
 {name:'speaker-labels',file:'transcript/TranscriptSpeakerPanel.vue',props:{snapshot:{meetingId:auditId,sourceRevisionId:revisionId,observation:{},items:[{id:elementId,label:'甲',enterpriseId:null,source:'HUMAN',segmentIds:[elementId]},{id:topicId,label:'乙',enterpriseId:null,source:'HUMAN',segmentIds:[topicId]}]},segments:[{segmentId:elementId,body:'检查片段',ordinal:1}],busy:false,editable:true}},
 {name:'corrections',file:'transcript/CorrectionPanel.vue',props:{corrections:[{id:elementId,source:'原词',replacement:'新词',status:'PENDING',canAccept:true,canReject:true},{id:topicId,source:'原词',replacement:'新词',status:'ACCEPTED',canUndo:true}],busy:false}},
 {name:'task-page',file:'meetings/WorkspaceTaskPage.vue',props:{page:{items:[],hasMore:true,nextCursor:'next',verification:'PERSISTED_RELATION'},kind:'ALL',loading:false,error:null}},
 {name:'task-error',file:'meetings/WorkspaceTaskPage.vue',props:{page:null,kind:'ALL',loading:false,error:'请重新读取任务'}},
 {name:'import-shell',file:'import/ImportDialogView.vue',props:{reason:'可开始导入',saveDisabled:false,startDisabled:false},show:true},
 {name:'material-picker',file:'import/KnowledgeMaterialPicker.vue',props:{open:true,picker:{opened:{value:true},libraryError:{value:null},error:{value:null},libraries:{value:[{id:'research',name:'检查知识库'}]},library:{value:'research'},libraryLoading:{value:false},review:{value:false},query:{value:''},loading:{value:false},selected:{value:[{id:'doc',name:'检查材料.pdf',version:'1',sizeBytesToken:'100'}]},documents:{value:[{id:'doc',name:'检查材料.pdf',version:'1',sizeBytesToken:'100'}]},cursor:{value:'next'}}}},
 {name:'owner-selection',file:'minutes/ActionOwnerSelection.vue',props:{meetingId:auditId,elementId,revisionToken:'1',enabled:true,hasIdentity:true},authenticated:true},
 {name:'generation',file:'minutes/MinutesGenerationPanel.vue',props:{meetingId:auditId},authenticated:true},
 {name:'minutes-history',file:'minutes/MinutesHistory.vue',props:{meetingId:auditId,enabled:true,locked:false},authenticated:true},
 {name:'minutes-exports',file:'minutes/MinutesExportPanel.vue',props:{sourceRevision:revisionId,review:true,dirty:false},authenticated:true,prepare:page=>page.getByRole('button',{name:'导出',exact:true}).click()},
];

for(const sample of samples)test(`component controls: ${sample.name}`,async({page},info)=>{
 test.setTimeout(60000);page.setDefaultTimeout(5000);
 await installButtonFixtures(page);
 const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto('/tests/fixtures/button-audit.html');await page.waitForFunction(()=>Boolean((window as any).uiAudit));
 async function mount(){await page.evaluate(async config=>{const picker=(config.props as any).picker;if(picker)Object.assign(picker,{chooseLibrary:(id:string)=>{picker.library.value=id;},moreDocuments:()=>{},search:()=>{},toggle:()=>{}});(window as any).auditView=await (window as any).uiAudit.mount('features/'+config.file,config.props,config.authenticated);if(config.show)(window as any).auditView.show();await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));},{file:sample.file,props:sample.props,authenticated:sample.authenticated,show:sample.show});if(sample.prepare)await sample.prepare(page);}
 const selector='button:visible,summary:visible,[role="button"]:visible,a:visible';
 async function controls(){const dialogs=page.locator('dialog[open]');return await dialogs.count()?dialogs.last().locator(selector):page.locator(selector);}
 await mount();const count=await (await controls()).count(),results:Array<Record<string,unknown>>=[];
 for(let index=0;index<count;index++){
  if(index)await mount();const button=(await controls()).nth(index),label=await button.getAttribute('aria-label')??await button.innerText();
  if(await button.isDisabled()){results.push({index,label,result:'disabled'});continue;}
  await button.scrollIntoViewIfNeeded();const original=await button.elementHandle(),before=await button.boundingBox();
  await button.click();await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(resolve)));
  let after=null;if(await original!.evaluate(node=>node.isConnected))after=await original!.boundingBox();
  if(before&&after&&after.width&&after.height){expect.soft(Math.abs(before.width-after.width),`${sample.name}: ${label} width`).toBeLessThanOrEqual(1);expect.soft(Math.abs(before.height-after.height),`${sample.name}: ${label} height`).toBeLessThanOrEqual(1);}
  results.push({index,label,result:'clicked',before,after});
 }
 const path=info.outputPath('button-clicks.json');await writeFile(path,JSON.stringify({component:sample.file,sample:sample.name,results,errors},null,2));await info.attach('button-clicks.json',{path,contentType:'application/json'});
 expect(count).toBeGreaterThan(0);expect(errors).toEqual([]);
});
