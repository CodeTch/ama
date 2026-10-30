import {test,expect} from '@playwright/test';

test('renders the knowledge publish prototype without product requests',async({page},info)=>{
 const productRequests:string[]=[];
 page.on('request',request=>{
  const pathname=new URL(request.url()).pathname;
  if(pathname.startsWith('/api/')||pathname.startsWith('/auth/'))productRequests.push(request.url());
 });
 await page.goto('/knowledge-publish-demo');

 await expect(page.getByRole('heading',{name:'未命名会议',exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'知识库发布',exact:true})).toHaveAttribute('aria-current','step');
 await expect(page.getByRole('heading',{name:'选择发布位置',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'发布配置',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'纪要预览',exact:true})).toBeVisible();
 await expect(page.getByRole('heading',{name:'二、核心结论',exact:true}).first()).toBeVisible();

 const publish=page.getByRole('button',{name:'发布到知识库',exact:true});
 await expect(publish).toHaveCount(2);
 for(const button of await publish.all())await expect(button).toBeDisabled();

 const search=page.getByRole('textbox',{name:'搜索发布知识库'});
 await search.fill('不存在的知识库');
 await expect(page.getByText('没有匹配的知识库',{exact:true})).toBeVisible();
 await search.fill('');
 await expect(page.getByRole('option',{name:'投资研究知识库',exact:true})).toBeVisible();

 await page.getByRole('option',{name:'投资研究知识库',exact:true}).click();
 await expect(page.getByText('请选择文件夹',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'进入 2026 年第三季度',exact:true}).click();
 await page.getByRole('radio',{name:'专题研究'}).check();
 await expect(page.getByText('投资研究知识库 / 2026 年第三季度 / 专题研究',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'返回根目录',exact:true}).click();
 await expect(page.getByText('请选择文件夹',{exact:true})).toBeVisible();
 await expect(page.getByText('请选择发布位置',{exact:true})).toBeVisible();
 for(const button of await publish.all())await expect(button).toBeDisabled();
 await page.getByRole('button',{name:'进入 2026 年第三季度',exact:true}).click();
 await page.getByRole('radio',{name:'专题研究'}).check();
 for(const button of await publish.all())await expect(button).toBeEnabled();

 await page.getByRole('button',{name:'最终文档预览',exact:true}).click();
 await expect(page.getByRole('dialog',{name:'最终文档预览（发布版）'})).toContainText('昆仑 AI·无界浏览器智能助手系统');
 await page.getByRole('button',{name:'关闭预览',exact:true}).click();

 await publish.first().click();
 await expect(page.getByRole('dialog',{name:'正在发布'})).toBeVisible();
 await expect(search).toBeDisabled();
 await expect(page.locator('#demo-document-title')).toBeDisabled();
 await expect(page.getByText('知识库发布失败',{exact:false})).toBeVisible({timeout:5000});
 const retry=page.getByRole('button',{name:'重试发布',exact:true});
 await expect(retry).toHaveCount(2);
 for(const button of await retry.all())await expect(button).toBeEnabled();
 await retry.first().click();
 await expect(page.getByText('发布成功，正式纪要已生成。',{exact:true})).toBeVisible({timeout:5000});
 await expect(page.getByText('当前会议已切换为只读',{exact:true})).toBeVisible();
 await expect(search).toBeDisabled();
 await expect(page.locator('#demo-document-title')).toBeDisabled();
 await expect(page.getByRole('link',{name:'查看正式纪要 ↗',exact:true})).toBeVisible();
 await expect(page.getByText('KD-2026-DEMO-001',{exact:true})).toBeVisible();
 for(const radio of await page.getByRole('radio').all())await expect(radio).toBeDisabled();
 for(const button of await publish.all())await expect(button).toBeDisabled();

 expect(productRequests).toEqual([]);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(info.project.use.viewport!.width);
 await page.screenshot({path:info.outputPath('knowledge-publish-demo.png'),fullPage:true});
});
