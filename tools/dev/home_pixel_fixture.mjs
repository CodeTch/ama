/** Idempotent fixture creation through real CAS/API; no existing records are edited or removed. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile} from 'node:fs/promises';
process.env.NO_PROXY=process.env.no_proxy='localhost,127.0.0.1,127.0.0.2';
const {chromium}=createRequire(resolve('web/package.json'))('@playwright/test');
const base=process.env.AMA_PARITY_ORIGIN??'http://127.0.0.1:5174',actor='dev-viewer';
const output='.tools/local-app/prototype-parity/home-controlled-fixture';await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true}),page=await browser.newPage();const checks=[];
try{
 await page.goto(`${base}/login`);await page.locator('#login-username').fill(actor);await page.locator('#login-password').fill(process.env.AMA_CAS_PASSWORD??'dev-password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(`${base}/meetings`);
 for(let i=1;i<=3;i++){
  const response=await page.evaluate(async i=>{const session=await(await fetch('/api/v1/session')).json();const r=await fetch('/api/v1/meetings',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':session.csrfToken,'Idempotency-Key':`home-pixel-fixture-v1-dev-viewer-${i}`},body:'{}'});return{status:r.status,value:await r.json()};},i);
  assert.equal(response.status,201,JSON.stringify(response));checks.push({name:'real-idempotent-create',meetingId:response.value.id});
 }
 const listing=await page.evaluate(async()=>await(await fetch('/api/v1/meetings?scope=owned&limit=10')).json());assert.equal(listing.hasMore,false,'Reference fixture must have an observed complete finite list');
 for(const created of checks)assert.ok(listing.items.some(r=>r.id===created.meetingId));
 await writeFile(`${output}/report.json`,JSON.stringify({pass:true,actor,checks,listing,qualification:'Finite real fixture for unchanged prototype render; large opaque continuation cases remain separately recorded'},null,2));console.log('PASS real CAS finite homepage fixture',listing.items.length);
}catch(error){await writeFile(`${output}/report.json`,JSON.stringify({pass:false,actor,checks,error:String(error)},null,2));throw error;}finally{await browser.close();}
