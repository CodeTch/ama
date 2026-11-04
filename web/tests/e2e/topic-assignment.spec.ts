import {test,expect,type Page,type Route} from '@playwright/test';

const meeting='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',topic='00000000-0000-4000-8000-000000000001';
async function topicManagement(page:Page,count=0){
 const createdId='00000000-0000-4000-8000-000000000003';
 let rows=[{id:topic,name:'年度复盘',description:null as string|null,revision:0,revisionToken:'0'}];
 const writes:Array<{method:string;body:any}>=[];
 await page.route('**/api/v1/topics',async route=>{
  if(route.request().method()==='POST'){
   const body=route.request().postDataJSON();writes.push({method:'POST',body});
   const created={...body,id:createdId,revision:0,revisionToken:'0'};rows=[created,...rows];await route.fulfill({status:201,json:created});
  }else await route.fulfill({json:rows});
 });
 await page.route(/\/api\/v1\/topics\/[0-9a-f-]{36}(?:\/summary)?$/,async route=>{
  const id=new URL(route.request().url()).pathname.split('/')[4]!,row=rows.find(item=>item.id===id)!;
  if(route.request().url().endsWith('/summary')){await route.fulfill({json:{id,meetingCount:count,revisionToken:row.revisionToken}});return;}
  const method=route.request().method(),body=route.request().postDataJSON();writes.push({method,body});
  if(method==='PUT'){const updated={...row,...body,revision:row.revision+1,revisionToken:String(row.revision+1)};delete updated.expectedRevisionToken;rows=rows.map(item=>item.id===id?updated:item);await route.fulfill({json:updated});}
  else{rows=rows.filter(item=>item.id!==id);await route.fulfill({json:{...row,revision:row.revision+1,revisionToken:String(row.revision+1)}});}
 });
 return {writes,createdId};
}
async function setup(page:Page,send:(route:Route)=>Promise<void>){
 let membership:string|null=null;
 await page.route('**/api/v1/session',route=>route.fulfill({json:{actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}}));
 await page.route('**/api/v1/meetings/categories*',route=>route.fulfill({json:{hasMore:false,items:[],nextCursor:null}}));
 await page.route('**/api/v1/directory/people*',route=>route.fulfill({json:{items:[],nextCursor:null}}));
 await page.route('**/api/v1/topics',route=>route.fulfill({json:[{id:topic,name:'年度复盘',description:null,revision:0,revisionToken:'0'}]}));
 await page.route('**/api/v1/topics/memberships?*',route=>route.fulfill({json:[{meetingId:meeting,topicId:membership}]}));
 await page.route('**/api/v1/meetings?*',route=>route.fulfill({json:{hasMore:false,items:[{accessRevisionToken:'0',category:null,id:meeting,lifecycle:'ACTIVE',name:'创建中会议',occurredOn:null,participants:[],relation:'OWNER',revisionToken:'0',stage:'ENTRY',updatedAt:'2026-11-02T12:00:00Z',workspaceStage:'ENTRY',writeState:'CHECK_WORKSPACE'}],nextCursor:null,total:1,stageCounts:{ENTRY:1,TRANSCRIPT_PENDING:0,TRANSCRIPT_FAILED:0,MINUTES_PENDING:0,MINUTES_FAILED:0,READY_TO_PUBLISH:0,PUBLISH_FAILED:0,PUBLISHED:0},topicGroups:{__none:1}}}));
 await page.route('**/api/v1/topics/assignments',send);
 await page.goto('/meetings');
 await page.getByRole('button',{name:'调整专题：未归入专题',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'调整会议专题',exact:true});
 await dialog.getByText('年度复盘',{exact:true}).click();
 return {dialog,assigned:()=>{membership=topic;}};
}

test('topic selection avoids a second row border and keeps visible keyboard focus',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));
 const row=dialog.getByRole('group',{name:'专题：年度复盘'});
 const radio=row.getByRole('radio');
 await expect(radio).toBeChecked();
 await expect(row).toHaveCSS('border-top-width','1px');
 await expect(row).toHaveCSS('outline-style','none');

 await dialog.getByRole('button',{name:'快速创建一级专题'}).focus();
 await page.keyboard.press('Tab');
 await expect(radio).toBeFocused();
 await expect(row.locator('.radio-dot')).toHaveCSS('outline-width','1px');
 await expect(row.locator('.radio-dot')).toHaveCSS('outline-style','solid');
 await expect(row).toHaveCSS('outline-style','none');
});

test('assignment actions keep their labels during send, close immediately, and refresh after completion',async({page})=>{
 let release!:()=>void,started!:()=>void,calls=0;
 const waiting=new Promise<void>(resolve=>{started=resolve;}),hold=new Promise<void>(resolve=>{release=resolve;});
 const {dialog,assigned}=await setup(page,async route=>{calls++;started();await hold;assigned();await route.fulfill({status:204});});
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await waiting;
 const success=page.getByRole('status').filter({hasText:'会议专题已调整'});
 await expect(success).toHaveCount(0);
 for(const name of ['关闭','取消','确定'])await expect(dialog.getByRole('button',{name,exact:true})).toBeEnabled();
 await dialog.getByRole('button',{name:'确定',exact:true}).click();
 await expect(dialog.getByRole('alert')).toHaveCount(0);expect(calls).toBe(1);
 await dialog.getByRole('button',{name:'关闭',exact:true}).click();await expect(dialog).toBeHidden();
 release();await expect(page.getByRole('button',{name:'调整专题：年度复盘',exact:true})).toBeVisible();
 await expect(success).toBeVisible();
 await expect(dialog).toBeHidden();expect(calls).toBe(1);
});

test('returning to meetings keeps the last confirmed topic visible during refresh',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));
 await dialog.getByRole('button',{name:'取消',exact:true}).click();
 await page.getByRole('button',{name:'我的语料反馈'}).click();
 await expect(page).toHaveURL(/\/feedback$/);
 let release!:()=>void;
 const pending=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/topics/memberships?*',async route=>{
  await pending;
  await route.fulfill({json:[{meetingId:meeting,topicId:topic}]});
 });
 try{
  const requested=page.waitForRequest('**/api/v1/topics/memberships?*');
  await page.getByRole('link',{name:/智会助手/}).click();
  await requested;
  const row=page.getByRole('row').filter({hasText:'创建中会议'});
  await expect(row).toBeVisible();
  await expect(row.getByRole('button',{name:'调整专题：未归入专题',exact:true})).toBeVisible();
  await expect(row.getByText('正在读取专题')).toHaveCount(0);
  const topicCell=row.getByRole('cell').nth(7);
  const before=await topicCell.boundingBox();
  release();
  await expect(topicCell.getByRole('button',{name:'调整专题：年度复盘',exact:true})).toBeVisible();
  expect(await topicCell.boundingBox()).toEqual(before);
 }finally{release();}
});

test('assignment displays the verified topic before the meeting list and its memberships refresh',async({page})=>{
 let committed=false,verified=false,release!:()=>void;
 const refresh=new Promise<void>(resolve=>{release=resolve;});
 const {dialog,assigned}=await setup(page,async route=>{
  assigned();committed=true;
  await route.fulfill({status:204});
 });
 await page.route('**/api/v1/topics/memberships?*',async route=>{
  if(committed&&verified)await refresh;
  if(committed)verified=true;
  await route.fulfill({json:[{meetingId:meeting,topicId:committed?topic:null}]});
 });
 await page.route('**/api/v1/meetings?*',async route=>{
  if(committed)await refresh;
  await route.fallback();
 });
 try{
  await dialog.getByRole('button',{name:'确定',exact:true}).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status').filter({hasText:'会议专题已调整'})).toBeVisible();
  await expect(page.getByRole('button',{name:'调整专题：年度复盘',exact:true})).toBeVisible();
 }finally{release();}
});

for(const status of [409,422])test(`assignment rejected with ${status} retries without discard control and cancel allows a new choice`,async({page})=>{
 const requests:Array<{body:string|null;key:string|undefined}>=[];
 let reject=true;
 const {dialog,assigned}=await setup(page,async route=>{
  requests.push({body:route.request().postData(),key:route.request().headers()['idempotency-key']});
  if(reject)await route.fulfill({status,json:{error:{code:status===422?'TOPIC_NOT_PUBLISHED':'MEETING_CONFLICT',message:'assignment rejected',traceId:'00000000-0000-4000-8000-000000000009'}}});
  else{assigned();await route.fulfill({status:204});}
 });
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog.getByRole('alert')).toBeVisible();
 await expect(page.getByRole('status').filter({hasText:'会议专题已调整'})).toHaveCount(0);
 await expect(dialog.getByText('正在恢复原专题请求，当前内容保持只读。')).toHaveCount(0);
 await expect(dialog.getByRole('button',{name:'放弃已拒绝的原请求'})).toHaveCount(0);
 await dialog.getByRole('button',{name:'确定',exact:true}).click();
 await expect.poll(()=>requests.length).toBe(2);expect(requests[1]).toEqual(requests[0]);
 await expect(dialog.getByRole('alert')).toBeVisible();
 await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).toBeHidden();
 await page.getByRole('button',{name:'调整专题：未归入专题',exact:true}).click();
 await expect(dialog.getByRole('radio').first()).toBeEnabled();
 await dialog.getByText('年度复盘',{exact:true}).click();reject=false;
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).toBeHidden();
 await expect(page.getByRole('button',{name:'调整专题：年度复盘',exact:true})).toBeVisible();
 expect(requests).toHaveLength(3);expect(requests[2]?.key).not.toBe(requests[0]?.key);
});

test('cancel after an uncertain assignment preserves the original for recovery',async({page})=>{
 let calls=0;const requests:string[]=[];
 const {dialog,assigned}=await setup(page,async route=>{
  calls++;requests.push(route.request().headers()['idempotency-key']!);
  if(calls===1)await route.abort('failed');else{assigned();await route.fulfill({status:204});}
 });
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog.getByRole('alert')).toBeVisible();
 await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).toBeHidden();
 await expect(page.getByRole('button',{name:'恢复原专题请求',exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:'调整专题：未归入专题',exact:true}).click();
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).toBeHidden();
 expect(calls).toBe(2);expect(requests[1]).toBe(requests[0]);
});

test('confirm keeps the same button, modal and background geometry through send and readback',async({page})=>{
 let sendDone!:()=>void,readDone!:()=>void,committed=false,reading=false,calls=0;
 const sendGate=new Promise<void>(resolve=>{sendDone=resolve;}),readGate=new Promise<void>(resolve=>{readDone=resolve;});
 const topics=[{id:topic,name:'年度复盘',description:null,revision:0,revisionToken:'0'}];
 const {dialog,assigned}=await setup(page,async route=>{calls++;await sendGate;committed=true;assigned();await route.fulfill({status:204});});
 await page.route('**/api/v1/topics',async route=>{if(committed){reading=true;await readGate;}await route.fulfill({json:topics});});
 const measure=()=>page.evaluate(()=>{
  const box=(selector:string)=>{const r=document.querySelector(selector)!.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};};
  return {button:box('.topic-dialog .modal-f .primary'),modal:box('.topic-dialog'),list:box('#meeting-panel'),scroll:window.scrollY};
 });
 const confirm=dialog.getByRole('button',{name:'确定',exact:true});
 const button=await confirm.elementHandle(),before=await measure();
 try{
  await confirm.click();await expect.poll(()=>calls).toBe(1);
  expect(await measure()).toEqual(before);
  await expect(confirm).toBeEnabled();
  await expect(page.getByRole('button',{name:'恢复原专题请求',exact:true})).toHaveCount(0);
  expect(await measure()).toEqual(before);
  expect(await button!.evaluate(el=>el===document.querySelector('.topic-dialog .modal-f .primary'))).toBe(true);
  await confirm.click();await expect(dialog.getByRole('alert')).toHaveCount(0);expect(calls).toBe(1);
  expect(await measure()).toEqual(before);
  sendDone();await expect.poll(()=>reading).toBe(true);
  await expect(confirm).toHaveAttribute('aria-busy','true');
  await expect(confirm).toHaveText('确定');expect(await measure()).toEqual(before);
  readDone();await expect(dialog).toBeHidden();
  await expect(page.getByRole('button',{name:'调整专题：年度复盘',exact:true})).toBeVisible();
 }finally{sendDone();readDone();}
});

test('quick create sits beside the meeting name and cancel returns to the selected assignment',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));
 const {writes}=await topicManagement(page),create=dialog.getByRole('button',{name:'快速创建一级专题',exact:true});
 const buttonBox=await create.boundingBox(),nameBox=await dialog.getByText('会议：创建中会议',{exact:true}).boundingBox();
 expect(buttonBox!.x).toBeGreaterThan(nameBox!.x+nameBox!.width);
 expect(Math.abs(buttonBox!.y+buttonBox!.height/2-nameBox!.y-nameBox!.height/2)).toBeLessThan(2);
 await create.click();const child=page.getByRole('dialog',{name:'新建会议专题',exact:true});await expect(child).toBeVisible();
 await child.getByRole('button',{name:'取消',exact:true}).click();
 await expect(dialog).toBeVisible();await expect(dialog.getByText('会议：创建中会议',{exact:true})).toBeVisible();
 await expect(dialog.getByRole('radio',{name:'年度复盘',exact:true})).toBeChecked();expect(writes).toHaveLength(0);
});

test('creating a topic returns to assignment with the new topic selected and original meeting retained',async({page})=>{
 const assignments:any[]=[];
 const {dialog}=await setup(page,async route=>{assignments.push(route.request().postDataJSON());await route.fulfill({status:204});});
 const {writes,createdId}=await topicManagement(page);
 await dialog.getByRole('button',{name:'快速创建一级专题',exact:true}).click();
 const child=page.getByRole('dialog',{name:'新建会议专题',exact:true});
 await child.getByLabel('专题名称',{exact:false}).fill('项目复盘');await child.getByLabel('专题说明',{exact:true}).fill('新专题说明');
 await child.getByRole('button',{name:'创建专题',exact:true}).click();
 await expect(dialog).toBeVisible();await expect(dialog.getByText('会议：创建中会议',{exact:true})).toBeVisible();
 await expect(dialog.getByRole('radio',{name:'项目复盘 新专题说明',exact:true})).toBeChecked();
 expect(writes).toEqual([{method:'POST',body:{name:'项目复盘',description:'新专题说明'}}]);
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).toBeHidden();
 expect(assignments).toEqual([{meetings:[{meetingId:meeting,expectedRevisionToken:'0'}],topicId:createdId}]);
});

test('row edit saves topic details and returns without changing the radio selection',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));const {writes}=await topicManagement(page);
 await dialog.getByText('暂不归入专题',{exact:true}).click();
 await dialog.getByRole('button',{name:'编辑专题：年度复盘',exact:true}).click();
 let child=page.getByRole('dialog',{name:'修改专题',exact:true});await child.getByRole('button',{name:'取消',exact:true}).click();
 await expect(dialog.getByRole('radio',{name:'暂不归入专题',exact:true})).toBeChecked();
 await dialog.getByRole('button',{name:'编辑专题：年度复盘',exact:true}).click();child=page.getByRole('dialog',{name:'修改专题',exact:true});
 await child.getByLabel('专题名称',{exact:false}).fill('修改后的专题');await child.getByLabel('专题说明',{exact:true}).fill('修改后的说明');
 await child.getByRole('button',{name:'保存',exact:true}).click();
 await expect(dialog).toBeVisible();await expect(dialog.getByRole('radio',{name:'修改后的专题 修改后的说明',exact:true})).toBeVisible();
 await expect(dialog.getByRole('radio',{name:'暂不归入专题',exact:true})).toBeChecked();
 expect(writes).toEqual([{method:'PUT',body:{expectedRevisionToken:'0',name:'修改后的专题',description:'修改后的说明'}}]);
});

test('row delete confirms an empty topic and returns with a valid selection',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));const {writes}=await topicManagement(page);
 await dialog.getByRole('button',{name:'删除专题：年度复盘',exact:true}).click();
 const child=page.getByRole('dialog',{name:'删除空专题',exact:true});await expect(child.getByText('确定删除专题「年度复盘」吗？该操作仅删除归集入口。')).toBeVisible();
 await child.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog.getByRole('radio',{name:'年度复盘',exact:true})).toBeChecked();
 await dialog.getByRole('button',{name:'删除专题：年度复盘',exact:true}).click();
 await child.getByRole('button',{name:'删除',exact:true}).click();await expect(dialog).toBeVisible();
 await expect(dialog.getByRole('radio',{name:'年度复盘',exact:true})).toHaveCount(0);
 await expect(dialog.getByRole('radio',{name:'暂不归入专题',exact:true})).toBeChecked();
 expect(writes).toEqual([{method:'DELETE',body:{expectedRevisionToken:'0'}}]);
});

test('row delete explains a nonempty topic and returns to assignment without deleting it',async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));const {writes}=await topicManagement(page,2);
 await dialog.getByRole('button',{name:'删除专题：年度复盘',exact:true}).click();
 const child=page.getByRole('dialog',{name:'无法删除专题',exact:true});await expect(child).toContainText('仍有 2 场会议');
 await expect(child.getByRole('button',{name:'删除',exact:true})).toHaveCount(0);
 await child.getByRole('button',{name:'知道了',exact:true}).click();await expect(dialog).toBeVisible();
 await expect(dialog.getByRole('radio',{name:'年度复盘',exact:true})).toBeChecked();expect(writes).toHaveLength(0);
});

for(const mode of ['create','update'] as const)test(`${mode} keeps its action label during submission and rejects blank names`,async({page})=>{
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));await topicManagement(page);
 await dialog.getByRole('button',{name:mode==='create'?'快速创建一级专题':'编辑专题：年度复盘',exact:true}).click();
 const child=page.getByRole('dialog',{name:mode==='create'?'新建会议专题':'修改专题',exact:true}),input=child.locator('#topic-name'),button=child.locator('.modal-f .primary');
 await expect(button).toHaveAccessibleName(mode==='create'?'创建专题':'保存');
 await expect(input).toHaveAttribute('required','');
 let calls=0,release!:()=>void;const gate=new Promise<void>(resolve=>{release=resolve;});
 await page.route(mode==='create'?'**/api/v1/topics':'**/api/v1/topics/'+topic,async route=>{if(route.request().method()==='GET'){await route.fallback();return;}calls++;await gate;await route.fallback();});
 for(const value of ['', '   ']){await input.fill(value);await button.click();await expect(child.getByRole('alert')).toHaveText('请输入专题名称');expect(calls).toBe(0);}
 await input.fill('稳定专题');const handle=await button.elementHandle();await button.click();
 try{await expect.poll(()=>calls).toBe(1);await expect(button).toHaveText(mode==='create'?'创建专题':'保存');await expect(button).toHaveAccessibleName('重试原专题请求');await expect(child.getByText('正在恢复原专题请求，当前内容保持只读。')).toHaveCount(0);expect(await handle!.evaluate(el=>el.isConnected)).toBe(true);}finally{release();}
 await expect(dialog).toBeVisible();
});

test('an uncertain quick-create topic can close and recover the original request', async ({page}) => {
 const {dialog}=await setup(page,route=>route.fulfill({status:204}));
 const requests:string[][]=[];
 await page.route('**/api/v1/topics',async route=>{
  const request=route.request();
  if(request.method()==='GET')return route.fulfill({json:[]});
  requests.push([request.headers()['idempotency-key']!,request.postData()!]);
  if(requests.length===1)return route.fulfill({status:503,json:{}});
  return route.fulfill({status:201,json:{...request.postDataJSON(),id:topic,revision:0,revisionToken:'0'}});
 });
 await dialog.getByRole('button',{name:'快速创建一级专题'}).click();
 const create=page.getByRole('dialog',{name:'新建会议专题'});
 await create.locator('#topic-name').fill('待确认专题');
 await create.getByRole('button',{name:'创建专题',exact:true}).click();
 await expect(create.getByRole('button',{name:'重试原专题请求'})).toBeEnabled();
 await page.keyboard.press('Escape');
 await expect(create).toBeHidden();
 await page.getByRole('button',{name:'恢复原专题请求',exact:true}).click();
 await create.getByRole('button',{name:'重试原专题请求'}).click();
 await expect(create).toBeHidden();
 expect(requests).toEqual([requests[0],requests[0]]);
});
