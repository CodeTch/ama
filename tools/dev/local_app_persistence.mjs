import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(resolve('web/package.json'));
const {chromium}=require('playwright');
const base='http://127.0.0.1:5173';
const report=JSON.parse(await readFile('.tools/local-app/smoke.json','utf8'));
const browser=await chromium.launch({headless:true});
try{
 const context=await browser.newContext({storageState:'.tools/local-app/browser-state.json'});
 const page=await context.newPage();await page.goto(`${base}/meetings`);
 await page.getByText('dev-owner',{exact:true}).waitFor();
 const ownedIds=await page.evaluate(async()=>(await (await fetch('/api/v1/meetings?scope=owned')).json()).items.map(row=>row.id).sort());
 if(report.ownedIds)assert.deepEqual(ownedIds,report.ownedIds,'Restart must neither lose meetings nor duplicate examples');
 assert.ok(ownedIds.includes(report.createdId));
 await page.goto(`${base}/meeting/${report.createdId}`);
 await page.getByRole('heading',{name:'联通验证会议（本地示例）',exact:true}).waitFor();
 const outcome={pass:true,createdId:report.createdId,checks:['session-survives-restart','created-meeting-survives-restart','saved-title-survives-restart','no-duplicate-seeds']};
 await writeFile('.tools/local-app/persistence.json',JSON.stringify(outcome,null,2));
 console.log(JSON.stringify(outcome));await context.close();
}finally{await browser.close();}
