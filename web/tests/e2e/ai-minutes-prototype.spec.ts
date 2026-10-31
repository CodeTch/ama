import {test,expect} from '@playwright/test';

const demoId='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';

test.describe('existing meeting detail prototype stages',()=>{
 test('renders the transcript empty state through the meeting detail route without APIs',async({page},info)=>{
  const apiRequests:string[]=[];
  page.on('request',request=>{if(request.url().includes('/api/v1/'))apiRequests.push(request.url());});
  await page.goto(`/meeting/${demoId}/transcript`);
  await expect(page.getByRole('heading',{name:'未命名会议',exact:true})).toBeVisible();
  await expect(page.getByText('转写待处理',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'转写校正',exact:true})).toHaveAttribute('aria-current','step');
  await expect(page.getByRole('heading',{name:'转写正文',exact:true})).toBeVisible();
  await expect(page.getByText('尚无已保存转写。导入任务完成后重新读取。',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'重新读取转写',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('已重新读取转写（演示）');
  await page.getByText('转写历史与原操作恢复',{exact:true}).click();
  await page.getByRole('button',{name:'读取历史版本',exact:true}).click();
  await expect(page.getByText('当前演示没有可恢复的历史版本。',{exact:true})).toBeVisible();
  expect(apiRequests).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(info.project.use.viewport!.width);
 });

 test('renders the AI minutes workbench through the meeting detail route with local interactions',async({page},info)=>{
  const apiRequests:string[]=[];
  page.on('request',request=>{if(request.url().includes('/api/v1/'))apiRequests.push(request.url());});
  await page.goto(`/meeting/${demoId}/minutes`);
  await expect(page.getByRole('button',{name:'AI纪要',exact:true})).toHaveAttribute('aria-current','step');
  await expect(page.getByRole('button',{name:'核心要素',exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'会议纪要',exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'会议概述',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'会议纪要',exact:true}).click();
  await expect(page.getByRole('heading',{name:'AI纪要正文',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'重新生成',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('已重新生成纪要（演示）');
  await page.getByRole('dialog').getByRole('button',{name:'关闭',exact:true}).click();
  await page.getByRole('button',{name:'进入会议确认',exact:true}).click();
  await expect(page).toHaveURL(new RegExp(`/meeting/${demoId}/confirm$`));
  await expect(page.getByRole('button',{name:'会议确认',exact:true})).toHaveAttribute('aria-current','step');
  await expect(page.getByRole('heading',{name:'会议确认',exact:true})).toBeVisible();
  expect(apiRequests).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(info.project.use.viewport!.width);
 });

 test('keeps the real meeting route on the API-driven branch',async({page})=>{
  const apiRequests:string[]=[];
  page.on('request',request=>{if(request.url().includes('/api/v1/'))apiRequests.push(request.url());});
  await page.goto('/meeting/00000000-0000-4000-8000-000000000001/transcript');
  await expect.poll(()=>apiRequests.some(url=>url.endsWith('/api/v1/session'))).toBe(true);
 });

 test('keeps the demo layout within a narrow viewport',async({page})=>{
  await page.setViewportSize({width:800,height:812});
  await page.goto(`/meeting/${demoId}/minutes`);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(800);
 });
});
