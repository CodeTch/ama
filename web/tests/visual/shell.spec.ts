import { test, expect } from '@playwright/test';
// Catches displaced prototype shell regions and actions lost at the approved narrower viewport.
test('shellMatchesPrototypeAndKeepsNarrowActionsVisible', async ({page}, info) => {
 await page.goto('/meetings');
 await page.evaluate(()=>document.fonts.ready);
 await expect(page.getByRole('button',{name:'导入会议',exact:true})).toBeVisible();
 await expect(page).toHaveScreenshot('meeting-shell.png', {
  clip:{x:0,y:0,width:info.project.use.viewport!.width,height:204},
  stylePath: 'tests/visual/shared-regions.css'
 });
 await page.screenshot({path:info.outputPath('actual-full.png'), fullPage:true});
});
