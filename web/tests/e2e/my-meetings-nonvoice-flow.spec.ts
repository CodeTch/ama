import {test,expect} from '@playwright/test';

test('anonymous voice entry renders a disabled state without requesting a device',async({page})=>{
 let calls=0;
 await page.addInitScript(()=>{const original=navigator.mediaDevices?.getUserMedia?.bind(navigator.mediaDevices);if(original)navigator.mediaDevices.getUserMedia=async(...args)=>{(window as any).__mediaCalls=((window as any).__mediaCalls??0)+1;return original(...args);};});
 await page.goto('/recording');
 await expect(page.getByRole('heading',{name:'在线录音',exact:true})).toBeVisible();
 await expect(page.locator('.acr-icon-button').first()).toBeDisabled();
 calls=await page.evaluate(()=>Number((window as any).__mediaCalls??0));
 expect(calls).toBe(0);
});
