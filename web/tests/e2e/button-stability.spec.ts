import {test,expect,type Page,type Locator} from '@playwright/test';
import {buttonInventory} from '../support/button-inventory';
import {writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {installButtonFixtures,auditId} from '../support/button-fixtures';

const demo='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';
const fixture='/tests/fixtures/button-audit.html';
type Box={x:number;y:number;width:number;height:number};
async function box(control:Locator):Promise<Box>{const rect=await control.boundingBox();expect(rect).not.toBeNull();const scroll=await control.page().evaluate(()=>({x:scrollX,y:scrollY}));return {...rect!,x:rect!.x+scroll.x,y:rect!.y+scroll.y};}
function sameBox(before:Box,after:Box){for(const key of ['x','y','width','height'] as const)expect(Math.abs(after[key]-before[key]),key).toBeLessThanOrEqual(1);}
async function mount(page:Page,file:string,props:Record<string,unknown>){
 await page.goto(fixture);
 await page.waitForFunction(()=>Boolean((window as any).uiAudit));
 await page.evaluate(async({file,props})=>{(window as any).auditView=await (window as any).uiAudit.mount(file,props);},{file,props});
}

test.use({contextOptions:{reducedMotion:'no-preference'}});

test('every source button and expander avoids interpolating geometry or opacity',async({page},info)=>{
 test.setTimeout(60000);
 const inventory=buttonInventory();
 const report=info.outputPath('all-source-controls.json');await writeFile(report,JSON.stringify(inventory,null,2));
 await info.attach('all-source-controls.json',{path:report,contentType:'application/json'});
 expect(inventory.filter(row=>row.tag==='button').length).toBeGreaterThan(500);
 await page.goto(fixture);await page.waitForFunction(()=>Boolean((window as any).uiAudit));
 await page.evaluate(rows=>(window as any).uiAudit.specimens(rows),inventory);
 const failures=await page.locator('[data-source]').evaluateAll(elements=>elements.flatMap(element=>{
  const style=getComputedStyle(element),properties=style.transitionProperty.split(',').map(p=>p.trim()),duration=style.transitionDuration.split(',').map(p=>parseFloat(p));
  const bad=properties.filter((property,index)=>(duration[index%duration.length]??0)>0&&!['none','color','background-color','background','border-color','box-shadow','text-decoration-color','outline-color'].includes(property));
  return bad.length?[{source:(element as HTMLElement).dataset.source,properties:bad}]:[];
 }));
 expect(failures).toEqual([]);
});

test('opening a command dialog does not scale or fade its buttons',async({page})=>{
 await mount(page,'features/meetings/TopicDialogView.vue',{mode:'create',name:'稳定性专题',description:'',selected:'',topics:[],meetingNames:[],pending:false,busy:false,composing:false,state:'EMPTY',error:null});
 const animation=await page.evaluate(async()=>{
  (window as any).auditView.show();
  await new Promise(requestAnimationFrame);
  return document.querySelector('dialog')!.getAnimations({subtree:true}).flatMap(animation=>animation.effect instanceof KeyframeEffect?animation.effect.getKeyframes().map(keyframe=>({transform:keyframe.transform,opacity:keyframe.opacity})):[]);
 });
 expect(animation.filter(frame=>frame.transform&&frame.transform!=='none'||frame.opacity&&frame.opacity!=='1')).toEqual([]);
 await expect(page.getByRole('button',{name:'创建专题',exact:true})).toBeVisible();
});

test('opening and closing an information drawer keeps a scrollable page horizontally fixed',async({page})=>{
 await mount(page,'features/meetings/MeetingInfoDrawer.vue',{open:false});
 await page.evaluate(()=>{const content=document.createElement('main');content.id='scroll-content';content.style.cssText='width:80%;margin:auto;height:1800px';document.body.append(content);});
 const content=page.locator('#scroll-content'),before=await box(content);
 await page.evaluate(()=>(window as any).auditView.update({open:true}));
 await expect(page.getByRole('dialog')).toBeVisible();sameBox(before,await box(content));
 await page.getByRole('button',{name:'关闭编辑会议信息',exact:true}).click();sameBox(before,await box(content));
});

test('speaker lookup keeps both its button and adjacent actions fixed while pending',async({page})=>{
 await mount(page,'features/meetings/MeetingInfoForm.vue',{value:{name:'会议',category:null,summary:null,occurredOn:null,rosterDraftIds:[],externalRosterNames:[]},categories:[],people:[],speakerBusy:false,busy:false,blocked:false,state:'EMPTY',dirty:false,conflict:false,canConfirm:false,optionsBusy:false,categoryMore:false,peopleMore:false,storageStatus:'EMPTY'});
 const button=page.getByRole('button',{name:'从发言人获取',exact:true});
 const before=await box(button);
 await page.evaluate(()=>(window as any).auditView.update({speakerBusy:true}));
 sameBox(before,await box(page.getByRole('button',{name:'读取发言人…',exact:true})));
});

test('publish and retry labels preserve the two publish action widths',async({page})=>{
 await page.goto('/knowledge-publish-demo');
 await page.getByRole('option',{name:'投资研究知识库',exact:true}).click();
 await page.getByRole('radio',{name:'年度经营分析',exact:true}).check();
 const buttons=page.getByRole('button',{name:'发布到知识库',exact:true});
 const before=await Promise.all((await buttons.all()).map(box));
 await buttons.first().click();
 await expect(page.getByRole('dialog',{name:'正在发布'})).toBeHidden();
 const retry=page.getByRole('button',{name:'重试发布',exact:true});
 for(const [index,button] of (await retry.all()).entries()){
  const after=await box(button);expect(Math.abs(after.width-before[index]!.width)).toBeLessThanOrEqual(1);expect(Math.abs(after.x-before[index]!.x)).toBeLessThanOrEqual(1);
 }
});

for(const route of [`/meeting/${demo}/transcript`,'/transcript-correction-demo'])test(`transcript refresh keeps its history entry in place: ${route}`,async({page})=>{
 await page.goto(route);
 const history=page.getByText('转写历史与原操作恢复',{exact:true}),before=await box(history);
 await page.getByRole('button',{name:'重新读取转写',exact:true}).click();
 sameBox(before,await box(history));
});

test('import state updates preserve the focused footer button node',async({page})=>{
 await mount(page,'features/import/ImportDialogView.vue',{reason:'准备导入',saveDisabled:false,startDisabled:false});
 await page.evaluate(()=>(window as any).auditView.show());
 const button=page.getByRole('button',{name:'开始处理',exact:true});
 await button.focus();const before=await box(button),handle=await button.elementHandle();
 await page.evaluate(()=>(window as any).auditView.update({reason:'导入任务已受理',acknowledged:true}));
 expect(await handle!.evaluate(node=>node.isConnected)).toBe(true);
 expect(await handle!.evaluate(node=>node===document.activeElement)).toBe(true);
 sameBox(before,await box(page.getByRole('button',{name:'查看处理进度',exact:true})));
});

const changingLabels=[
 {file:'features/meetings/TopicDialogView.vue',props:{mode:'create',name:'专题',description:'',selected:'',topics:[],meetingNames:[],pending:true,busy:false,composing:false,state:'UNCERTAIN',error:null},patch:{state:'ACKNOWLEDGED'},before:'重试原专题请求',after:'重新核实已受理专题'},
 {file:'features/minutes/PersonalTemplateDialogView.vue',props:{mode:'create',name:'模板',themeText:'主题',pending:false,busy:false,composing:false,state:'EMPTY',error:'',concealed:false,accepted:false},patch:{accepted:true},before:'保存并使用',after:'使用已保存模板'},
 {file:'features/transcript/TranscriptOperationsDialog.vue',props:{matches:null,segments:[],pending:false,busy:false,searching:false,canMutate:true,state:'EMPTY',original:null,error:null},patch:{searching:true},before:'查找全部位置',after:'查找中…'},
 {file:'features/meetings/MeetingCreatePanel.vue',props:{state:'EMPTY',busy:false,blocked:false,storageStatus:'EMPTY',cleanupFailed:false,message:null,discovery:null,discovering:false,discoveryError:false},patch:{state:'ACKNOWLEDGED'},before:'创建并打开',after:'打开已创建会议'},
];
for(const sample of changingLabels)test(`changing operation labels keep their width: ${sample.file}`,async({page})=>{
 await mount(page,sample.file,sample.props);await page.evaluate(()=>(window as any).auditView.show());
 const button=page.getByRole('button',{name:sample.before,exact:true}),before=await box(button);
 await page.evaluate(patch=>(window as any).auditView.update(patch),sample.patch);
 sameBox(before,await box(page.getByRole('button',{name:sample.after,exact:true})));
});

test('feedback submission keeps its action width while the original request is pending',async({page})=>{
 await installButtonFixtures(page);let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/feedback',async route=>{await wait;await route.fulfill({status:503,json:{}});});
 try{
  await page.goto('/feedback?meetingId='+auditId);
  const dialog=page.getByRole('dialog',{name:'提交语料反馈',exact:true});await expect(dialog).toBeVisible();
  await dialog.getByLabel('反馈内容',{exact:true}).fill('核对提交按钮的尺寸');
  const before=await box(dialog.getByRole('button',{name:'提交',exact:true}));
  await dialog.getByRole('button',{name:'提交',exact:true}).click();
  sameBox(before,await box(dialog.getByRole('button',{name:'重试原反馈',exact:true})));
 }finally{release();}
});

test('playback speed changes preserve the playback button and seek bar geometry',async({page})=>{
 await installButtonFixtures(page);
 const wave=Buffer.alloc(32044);wave.write('RIFF');wave.writeUInt32LE(wave.length-8,4);wave.write('WAVEfmt ',8);wave.writeUInt32LE(16,16);wave.writeUInt16LE(1,20);wave.writeUInt16LE(1,22);wave.writeUInt32LE(16000,24);wave.writeUInt32LE(32000,28);wave.writeUInt16LE(2,32);wave.writeUInt16LE(16,34);wave.write('data',36);wave.writeUInt32LE(wave.length-44,40);
 await page.route('**/transcript/revisions/*/sources/*',route=>route.fulfill({body:wave,contentType:'audio/wav',headers:{'Content-Length':String(wave.length),'Content-Disposition':'inline; filename="audit.wav"'}}));
 await page.goto(fixture);await page.waitForFunction(()=>Boolean((window as any).uiAudit));
 const scope={meeting:auditId,revisionId:auditId,accessRevisionToken:'0',sources:[{fileId:auditId,revisionId:auditId,ordinal:0,sizeBytes:wave.length,sha256:createHash('sha256').update(wave).digest('hex')}]};
 await page.evaluate(async scope=>(window as any).uiAudit.mount('features/transcript/TranscriptPlayer.vue',{scope,active:true},true),scope);
 const speed=page.locator('.p-speed');await expect(speed).toBeEnabled();await expect(page.locator('audio')).toHaveJSProperty('readyState',4);
 const before=await box(speed),seek=await box(page.getByRole('slider',{name:'播放进度'}));
 for(let index=0;index<4;index++){await speed.click();sameBox(before,await box(speed));sameBox(seek,await box(page.getByRole('slider',{name:'播放进度'})));}
});

test('selecting and clearing meetings leaves the clicked row in place',async({page})=>{
 await installButtonFixtures(page);await page.goto('/meetings');
 const row=page.getByRole('button',{name:'稳定性检查会议',exact:true});await expect(row).toBeVisible();
 const before=await box(row);
 await page.getByRole('checkbox',{name:'选择会议 稳定性检查会议',exact:true}).check();
 sameBox(before,await box(row));
 await page.getByRole('button',{name:'取消选择',exact:true}).click();sameBox(before,await box(row));
});

test('reloading the same meetings retains their topic labels until the fresh membership arrives',async({page})=>{
 await installButtonFixtures(page);let paused=false,release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/topics/memberships?*',async route=>{if(paused)await wait;const ids=new URL(route.request().url()).searchParams.get('meetingIds')!.split(',');await route.fulfill({json:ids.map(meetingId=>({meetingId,topicId:meetingId===auditId?'00000000-0000-4000-8000-000000000004':null}))});});
 try{
  await page.goto('/meetings');const topic=page.getByRole('button',{name:'调整专题：检查专题',exact:true});await expect(topic).toBeVisible();const before=await box(topic);
  paused=true;const requested=page.waitForRequest('**/api/v1/topics/memberships?*');await page.getByRole('button',{name:'第 1 页',exact:true}).click();await requested;
  expect(await topic.count()).toBe(1);sameBox(before,await box(topic));
 }finally{release();}
});

test('a denied topic refresh removes the previously displayed topic labels',async({page})=>{
 await installButtonFixtures(page);await page.goto('/meetings');
 const topic=page.getByRole('button',{name:'调整专题：检查专题',exact:true});await expect(topic).toBeVisible();
 await page.route('**/api/v1/topics/memberships?*',route=>route.fulfill({status:403,json:{}}));
 await page.getByRole('button',{name:'第 1 页',exact:true}).click();
 await expect(page.getByRole('button',{name:'重新读取专题',exact:true})).toBeVisible();
 await expect(topic).toHaveCount(0);
});

test('each minutes element edit action stays inside its own paragraph and remains clickable',async({page})=>{
 await installButtonFixtures(page);await page.goto(`/meeting/${auditId}/minutes`);
 const paragraph=page.locator('.minutes-elements>.am-para').first();await expect(paragraph).toBeVisible();
 const edit=paragraph.getByRole('button',{name:'编辑要素',exact:true}),parent=await box(paragraph),button=await box(edit);
 expect(button.x).toBeGreaterThanOrEqual(parent.x);expect(button.y).toBeGreaterThanOrEqual(parent.y);
 expect(button.x+button.width).toBeLessThanOrEqual(parent.x+parent.width);
 await paragraph.hover();const before=await box(edit);await edit.hover();sameBox(before,await box(edit));
 await edit.click();await expect(page.locator('dialog.workspace-dialog[open]')).toBeVisible();
});

for(const route of ['/feedback','/meeting-library',`/meeting/${auditId}/published`])test(`profile lookup keeps toolbar actions fixed: ${route}`,async({page})=>{
 const data=await installButtonFixtures(page);data.stage='published';
 let release!:()=>void;const wait=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/directory/people*',async route=>{await wait;await route.fulfill({json:{items:[{stableId:'audit-owner',displayName:'检查账号',department:'产品研发部',active:true}],nextCursor:null}});});
 try{
  await page.goto(route);const button=page.locator('.topbar-right>button').first();
  await expect(button).toBeVisible();
  const before=await box(button);release();
  await expect(page.locator('.rc-name')).toHaveText('检查账号');
  sameBox(before,await box(button));
 }finally{release();}
});
