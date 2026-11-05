import {test, expect, type Page} from '@playwright/test';

const id=(n:number)=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const meeting=id(1),file=id(2),first=id(3),second=id(4),candidate=id(5);
const body='核对计划 A+[1] 的范围。', amended='核对计划 A+[1] 的日期。';
type Scenario={member?:boolean;pending?:boolean;proposals?:number;holdCorrections?:boolean;total?:number};

async function workbench(page:Page,scenario:Scenario={}){
 const member=!!scenario.member,actor=member?'member':'owner';
 let revision=1,workspaceReads=0,currentReads=0,correctionReads=0,speakerReads=0;
 const writes:string[]=[];
 let release=()=>{};
 const ready=new Promise<void>(resolve=>{release=resolve;});
 let rows=[
  {segmentId:first,ordinal:0,body,sourceFileId:file,sourceParagraph:0,startMs:null,endMs:null,speakerId:null,marks:[] as string[]},
  {segmentId:second,ordinal:1,body:'下周汇报产出。',sourceFileId:file,sourceParagraph:1,startMs:null,endMs:null,speakerId:null,marks:['KEY']}
 ];
 for(let i=rows.length;i<(scenario.total??2);i++)rows.push({...rows[0]!,ordinal:i,segmentId:id(100+i),body:'会议记录 '+(i+1),marks:[]});
 const observation=()=>({meetingRevision:revision,revisionToken:String(revision),accessRevision:0,accessRevisionToken:'0',currentSavedRevisionId:id(10+revision),currentConfirmationId:member?id(6):null});
 const current=()=>({meetingId:meeting,observation:observation(),storedInspection:'AVAILABLE',sourceBytes:[],sources:[],correction:{mode:'CONTROLLED_SPEECH',readiness:'READY',qualificationId:'test'},
  capabilities:{canReadHistory:!member,canSave:!member,canRestore:!member,canConfirm:!member&&!scenario.pending,canReadSources:true},needsGeneration:false,
  confirmation:member?{id:id(6),revisionId:id(10+revision),confirmedBy:'owner',confirmedAt:'2026-09-01T00:00:00Z',contentHash:'a'.repeat(64),profile:'ama.transcript-confirmation.v1',isCurrent:true}:null,
  saved:{id:id(10+revision),kind:'WORKING',revision,revisionToken:String(revision),segments:rows}});
 await page.route('**/api/v1/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname,method=request.method();
  const respond=(json:unknown)=>route.fulfill({json});
  if(method!=='GET')writes.push(method+' '+path);
  if(path==='/api/v1/session')return respond({actor:{enterpriseId:actor,status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'});
  const root='/api/v1/meetings/'+meeting;
  if(path===root)return respond({id:meeting,name:'转写回归会议',category:null,summary:null,occurredOn:null,revision,revisionToken:String(revision),accessRevision:0,accessRevisionToken:'0',stage:'TRANSCRIPT_PENDING',rosterDraftId:null,rosterDraft:null,confirmedRosterId:null,confirmedRoster:[]});
  if(path===root+'/workspace/access')return respond({meetingId:meeting,allowed:true});
  if(path===root+'/workspace'){
   workspaceReads++;
   return respond({meetingId:meeting,revisionToken:String(revision),accessRevisionToken:'0',contentEligibility:'RECHECK_REQUIRED',stage:'TRANSCRIPT_PENDING',relation:member?'MEMBER':'OWNER',navigationState:member?'MEMBER_READ_ONLY':'CHECK_PRODUCER',
    saved:{infoRevisionId:meeting,elementSetId:null,minutesRevisionId:null,rosterRevisionId:null,transcriptConfirmationId:observation().currentConfirmationId,transcriptRevisionId:id(10+revision)},
    ...(member?{}:{privateSectionsState:'AVAILABLE',privateSections:{verification:'PERSISTED_REFERENCES_RECHECK_PRODUCER',exclusionRevisionId:null,finalConfirmationId:null,publicationId:null,publicationState:null,savedGenerationInputId:null,savedProcessingInputId:null,templateRevisionId:null,candidates:{items:[],hasMore:false,detailPath:root+'/minutes/candidates'},tasks:{verification:'PERSISTED_RELATION',hasMore:false,nextCursor:null,items:[]}}})});
  }
  if(path===root+'/transcript'&&method==='GET'){currentReads++;return respond(current());}
  if(path===root+'/transcript'&&method==='PUT'){
   const change=request.postDataJSON();
   expect(change.expectedRevisionToken).toBe(String(revision));
   expect(change.sourceRevisionId).toBe(id(10+revision));
   rows=rows.map(row=>({...row,...change.segments.find((s:{segmentId:string})=>s.segmentId===row.segmentId)}));
   revision++;
   return respond({result:{kind:'SAVED_REVISION',meetingId:meeting,revisionId:id(10+revision),historyRevision:revision,historyRevisionToken:String(revision)},replayed:false,resultIsCurrent:true,observation:observation()});
  }
  if(path===root+'/transcript/corrections'){
   correctionReads++;
   if(scenario.holdCorrections)await ready;
   return respond({meetingId:meeting,sourceRevisionId:id(10+revision),observation:observation(),
    items:scenario.pending?[{id:candidate,segmentId:first,source:body,replacement:amended,operationId:null,status:'PENDING',canAccept:true,canReject:true,canUndo:false}]:[],
    pendingCount:scenario.pending?1:0,availableProposalCount:scenario.proposals??0,canPropose:!member});
  }
  if(path.endsWith('/speakers'))speakerReads++;
  return route.fulfill({status:404,json:{}});
 });
 await page.goto('/meeting/'+meeting+'/transcript');
 return {release,counts:()=>({workspaceReads,currentReads,correctionReads,speakerReads,writes:[...writes]})};
}

test('slow suggestions preserve the edit field and a popover leaves neighbouring paragraphs in place',async({page})=>{
 const fixture=await workbench(page,{pending:true,holdCorrections:true});
 const form=page.locator('.transcript-section');
 await expect(form).toBeVisible();
 await expect(form.getByText('核对纠错中')).toBeVisible();
 const edit=form.getByRole('button',{name:'编辑第 1 段'});
 await edit.click();
 const modal=page.getByRole('dialog',{name:'编辑转写片段'});
 const field=modal.getByRole('textbox',{name:'转写内容'}),text='仍在输入，待核对';
 await field.fill(text);
 const fieldNode=await field.elementHandle();
 fixture.release();
 await expect(form.locator('.inline-suggestion')).toHaveCount(1);
 await expect(field).toHaveValue(text);
 await expect(field).toBeFocused();
 expect(await fieldNode!.evaluate(node=>node.isConnected)).toBe(true);
 await modal.getByRole('button',{name:'取消'}).click();
 const search=form.getByRole('textbox',{name:'搜索转写'});
 await search.fill('A+[1]');await expect(form.locator('.seg')).toHaveCount(1);
 await expect(form.locator('.s-text mark')).toHaveText('A+[1]');
 await search.fill('的范围');await expect(form.locator('.s-text mark')).toHaveText('的范围');
 await search.fill('说话人 2');await expect(form.locator('.s-spk mark')).toHaveText('说话人 2');
 await search.fill('');
 const issue=form.locator('.inline-suggestion'),neighbour=form.locator('#seg-'+second);
 await issue.scrollIntoViewIfNeeded();
 const before=await neighbour.evaluate(el=>el.getBoundingClientRect().y+scrollY);
 await issue.click();
 const details=page.getByRole('dialog',{name:'纠错详情'});
 await expect(details).toBeVisible();
 expect(await neighbour.evaluate(el=>el.getBoundingClientRect().y+scrollY)).toBe(before);
 await page.keyboard.press('Escape');await expect(details).toHaveCount(0);
 await search.fill('汇报');
 await page.getByRole('button',{name:'确认转写并生成纪要',exact:true}).click();
 const confirmation=page.getByRole('dialog',{name:'确认转写并生成纪要'});
 await expect(confirmation.getByRole('button',{name:'确认并生成'})).toBeDisabled();
 await confirmation.getByRole('button',{name:'定位待确认纠错'}).click();
 await expect(search).toHaveValue('');await expect(form.locator('.seg')).toHaveCount(1);await expect(form.locator('#seg-'+first)).toBeFocused();
 expect(fixture.counts()).toEqual({workspaceReads:1,currentReads:1,correctionReads:1,speakerReads:0,writes:[]});
});

test('clean drafts produce no writes, edits persist once and a missing model review blocks confirmation',async({page})=>{
 const fixture=await workbench(page,{proposals:2});
 const form=page.locator('.transcript-section'),save=page.locator('.ac-workspace-actions').getByRole('button',{name:'保存',exact:true});
 await expect(save).toBeDisabled();
 await expect(form.getByRole('button',{name:'载入待核对建议'})).toBeVisible();
 await form.getByRole('button',{name:'编辑第 1 段'}).click();
 await page.getByRole('dialog',{name:'编辑转写片段'}).getByRole('button',{name:'保存',exact:true}).click();
 expect(fixture.counts().writes).toEqual([]);
 await form.locator('#seg-'+first).getByRole('button',{name:'结论',exact:true}).click();
 await expect(save).toBeEnabled();await save.click();await expect(save).toBeDisabled();
 await expect(page.locator('.ac-workspace-actions')).toContainText('已保存');
 await expect(form.locator('#seg-'+first).getByRole('button',{name:'结论',exact:true})).toHaveAttribute('aria-pressed','true');
 await expect.poll(()=>fixture.counts().currentReads).toBe(2);
 expect(fixture.counts().writes).toEqual(['PUT /api/v1/meetings/'+meeting+'/transcript']);
 await page.getByRole('button',{name:'确认转写并生成纪要',exact:true}).click();
 const modal=page.getByRole('dialog',{name:'确认转写并生成纪要'});await expect(modal).toContainText('2 条语音建议未载入');await expect(modal.getByRole('button',{name:'确认并生成'})).toBeDisabled();
});

test('a confirmed member may search on a narrow screen without receiving owner controls or private reviews',async({page})=>{
 await page.setViewportSize({width:390,height:844});
 const fixture=await workbench(page,{member:true});
 const form=page.locator('.transcript-section');await expect(form).toBeVisible();
 const search=form.getByRole('textbox',{name:'搜索转写'});await search.fill('汇报');
 await expect(form.locator('.seg')).toHaveCount(1);await expect(form.locator('.s-text mark')).toHaveText('汇报');
 await expect(form.getByText('关键',{exact:true})).toBeVisible();
 await expect(form.getByRole('button',{name:'关键',exact:true})).toHaveCount(0);
 await expect(form.getByRole('button',{name:/编辑第|原稿与审阅|确认转写并生成纪要/})).toHaveCount(0);
 await expect(page.getByRole('region',{name:'当前说话人'})).toHaveCount(0);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(390);
 expect(fixture.counts().correctionReads).toBe(0);expect(fixture.counts().writes).toEqual([]);
});

test('a long transcript bounds live DOM nodes while searching and editing across the full version',async({page})=>{
 const fixture=await workbench(page,{total:3200});
 const form=page.locator('.transcript-section');
 await expect(form.locator('.seg')).toHaveCount(80);
 await expect(page.getByRole('region',{name:'当前说话人'}).getByRole('button',{name:'编辑',exact:true})).toHaveCount(24);
 const search=form.getByRole('textbox',{name:'搜索转写'});
 await search.fill('会议记录 3200');await expect(form.locator('.seg')).toHaveCount(1);
 await expect(form.locator('.s-text mark')).toHaveText('会议记录 3200');
 await form.getByRole('button',{name:'编辑第 3200 段'}).click();const modal=page.getByRole('dialog',{name:'编辑转写片段'});await expect(modal.getByRole('textbox')).toHaveValue('会议记录 3200');
 await modal.getByRole('button',{name:'取消'}).click();
 await search.fill('');const pager=form.getByRole('navigation',{name:'转写段落分页'});await pager.getByRole('button',{name:'下一页'}).click();
 await expect(form.getByRole('button',{name:'编辑第 81 段'})).toBeVisible();await expect(form.locator('.seg')).toHaveCount(80);
 expect(fixture.counts().currentReads).toBe(1);expect(fixture.counts().writes).toEqual([]);
});
