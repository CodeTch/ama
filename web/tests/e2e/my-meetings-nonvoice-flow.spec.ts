import {test,expect} from '@playwright/test';

test('deferred voice entry does not request a device before an authenticated nonvoice flow',async({page})=>{
 let calls=0;
 await page.addInitScript(()=>{const original=navigator.mediaDevices?.getUserMedia?.bind(navigator.mediaDevices);if(original)navigator.mediaDevices.getUserMedia=async(...args)=>{(window as any).__mediaCalls=((window as any).__mediaCalls??0)+1;return original(...args);};});
 await page.goto('/meetings');
 const login=page.getByRole('link',{name:'企业 CAS 登录'});
 if(await login.count()) {await expect(login).toBeVisible();return;}
 await page.getByRole('button',{name:'开始录音',exact:true}).click();
 await expect(page.getByRole('heading',{name:'会议录音',exact:true})).toBeVisible();
 calls=await page.evaluate(()=>Number((window as any).__mediaCalls??0));
 expect(calls).toBe(0);
});
