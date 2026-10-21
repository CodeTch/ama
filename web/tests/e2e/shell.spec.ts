import { test, expect } from '@playwright/test';
test('direct meeting route and root navigation expose unavailable integrations honestly', async ({page})=>{
 await page.goto('/'); await expect(page).toHaveURL(/\/meetings$/);
 await expect(page.getByRole('heading',{name:'我的会议',exact:true})).toBeVisible();
 await expect(page.getByText('会议服务尚未接入')).toBeVisible();
 await expect(page.getByText('企业登录尚未接入')).toBeVisible();
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 await expect(page.getByRole('dialog')).toContainText('导入服务尚未接入');
 await expect(page.getByRole('dialog')).toContainText('未上传文件或创建会议');
 await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
 await expect(page.getByRole('button',{name:'导入会议',exact:true})).toBeFocused();
 await page.getByRole('button',{name:'开始录音',exact:true}).click();
 await expect(page.getByRole('dialog')).toContainText('录音服务尚未接入');
 await expect(page.getByRole('dialog')).toContainText('未访问麦克风或创建会议');
 await page.getByRole('button',{name:'知道了'}).click();
 await page.getByRole('tab',{name:'我参与的'}).click();
 await expect(page.getByRole('tab',{name:'我参与的'})).toHaveAttribute('aria-selected','true');
 await expect(page.getByRole('table')).toHaveCount(0);
});
test('phone width keeps import and record actionable without horizontal page overflow',async({page})=>{
 await page.setViewportSize({width:375,height:812});await page.goto('/meetings');
 for(const name of ['开始录音','导入会议']) {
  const button=page.getByRole('button',{name,exact:true}); await expect(button).toBeInViewport();
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 await expect(page.getByRole('dialog')).toBeInViewport();
});
test('unknown route offers a working meeting-list return',async({page})=>{
 await page.goto('/missing');await expect(page.getByRole('heading',{name:'页面暂不可用'})).toBeVisible();
 await page.getByRole('link',{name:'返回我的会议'}).click();await expect(page).toHaveURL(/\/meetings$/);
});

test('scope tabs support keyboard selection and keep the panel label synchronized',async({page})=>{
 await page.goto('/meetings');
 const owned=page.getByRole('tab',{name:'我负责的'}),joined=page.getByRole('tab',{name:'我参与的'});
 await owned.focus();await page.keyboard.press('ArrowRight');
 await expect(joined).toBeFocused();await expect(joined).toHaveAttribute('aria-selected','true');
 await expect(page.getByRole('tabpanel',{name:'我参与的'})).toContainText('参与的会议');
 await page.keyboard.press('Home');await expect(owned).toBeFocused();
 await expect(owned).toHaveAttribute('aria-selected','true');
});
