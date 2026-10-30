import {test,expect} from '@playwright/test';

test('renders the isolated transcript correction demo without calling product APIs',async({page},info)=>{
 const apiRequests:string[]=[];
 page.on('request',request=>{if(request.url().includes('/api/v1/'))apiRequests.push(request.url());});
 await page.goto('/transcript-correction-demo');
 await expect(page.getByRole('heading',{name:'未命名会议',exact:true})).toBeVisible();
 await expect(page.getByText('转写待处理',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'转写校正',exact:true})).toHaveAttribute('aria-current','step');
 await expect(page.getByText('会议录音',{exact:true})).toBeVisible();
 await expect(page.getByText('本次为文本转写文件，无原录音，不虚构播放时长。',{exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'转写正文',exact:true})).toBeVisible();
 await expect(page.getByText('尚无已保存转写。导入任务完成后重新读取。',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'重新读取转写',exact:true})).toBeVisible();
 await expect(page.getByText('转写历史与原操作恢复',{exact:true})).toBeVisible();
 expect(apiRequests).toEqual([]);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(info.project.use.viewport!.width);
 await page.screenshot({path:info.outputPath('transcript-correction-demo.png'),fullPage:true});
});

test('demo history and notices remain local and truthful',async({page})=>{
 const apiRequests:string[]=[];
 page.on('request',request=>{if(request.url().includes('/api/v1/'))apiRequests.push(request.url());});
 await page.goto('/transcript-correction-demo');
 await page.getByText('转写历史与原操作恢复',{exact:true}).click();
 await expect(page.getByRole('heading',{name:'转写历史',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'读取历史版本',exact:true}).click();
 await expect(page.getByText('当前演示没有可恢复的历史版本。',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'编辑会议信息',exact:true}).click();
 await expect(page.getByRole('dialog')).toContainText('会议服务尚未接入');
 await page.getByRole('button',{name:'关闭',exact:true}).click();
 expect(apiRequests).toEqual([]);
});
