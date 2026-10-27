import assert from 'node:assert/strict';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(resolve('web/package.json'));
const {chromium}=require('playwright');
const base='http://127.0.0.1:5173';
for(const password of ['any-password','',undefined,'任意密码'.repeat(100)]){
 const form=new URLSearchParams({username:'dev-owner'});
 if(password!==undefined)form.set('password',password);
 const response=await fetch(`${base}/api/dev/login`,{method:'POST',headers:{Origin:base},body:form,redirect:'manual'});
 assert.equal(response.status,303,'Arbitrary, empty and omitted passwords must all sign in');
 assert.match(response.headers.get('set-cookie'),/__Host-AMA_SESSION=.*; Path=\/; Secure; HttpOnly;/);
}
for(const origin of ['https://other.invalid',undefined]){
 const response=await fetch(`${base}/api/dev/login`,{method:'POST',headers:origin?{Origin:origin}:{},body:new URLSearchParams({username:'dev-owner'}),redirect:'manual'});
 assert.equal(response.status,403,'The local login still requires its exact browser Origin');
}
const unknown=await fetch(`${base}/api/dev/login`,{method:'POST',headers:{Origin:base},body:new URLSearchParams({username:'unknown',password:'anything'}),redirect:'manual'});
assert.equal(unknown.status,401,'Only the configured development accounts can sign in');
const browser=await chromium.launch({headless:true});
try{
 for(const [username,password] of [['dev-owner',''],['dev-member','anything-at-all']]){
  const context=await browser.newContext();const page=await context.newPage();
  await page.goto(`${base}/api/dev/login`);
  await page.getByLabel('开发账号').selectOption(username);
  if(password)await page.getByLabel('密码').fill(password);
  await page.getByRole('button',{name:'登录本地环境'}).click();
  await page.waitForURL(`${base}/meetings`);
  await page.getByText(username,{exact:true}).waitFor();
  await page.getByRole('link',{name:username==='dev-owner'?'产品周会（本地示例）':'需求评审（本地示例）'}).waitFor();
  const status=await page.evaluate(async()=>{
   const response=await fetch('/api/v1/meetings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:'{}'});
   return response.status;
  });
  assert.equal(status,403,'Business writes still require their session CSRF token');
  await context.close();
 }
 console.log(JSON.stringify({pass:true,checks:['arbitrary-password','empty-password','omitted-password','long-unicode-password','secure-session-cookie','cross-origin-denied','missing-origin-denied','unknown-account-denied','browser-empty-login','browser-arbitrary-login','meeting-data','business-csrf']}));
}finally{await browser.close();}
