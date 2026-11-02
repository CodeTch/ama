// Real browser -> AMA API/worker -> sibling harness -> pending human correction.
// Run after offline_speech_check.py and local_app.py start, with .tools/env.sh loaded.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';

assert.equal(process.cwd(),'/home/tanren/ama');
assert.equal(execFileSync('git',['branch','--show-current'],{encoding:'utf8'}).trim(),'main');
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test');
const expect=baseExpect.configure({timeout:30000});
const run=randomUUID(),out=resolve('.tools/offline-speech/e2e-'+run);
await mkdir(out,{recursive:true});
const state=JSON.parse(await readFile('.tools/local-app/state.json','utf8'));
const origin=`http://127.0.0.1:${state.ports.web}`,harness='http://127.0.0.1:8792';
const original=`离线联调 ${run} 因该按时复核`,punctuated=original+'。',corrected=punctuated.replace('因该','应该');
const scenarios=[],checks=[],errors=[],proofs=[];
let meeting,rawId,passed=false,browser,page;
const check=name=>{checks.push(name);console.log('PASS',name);};
async function admin(path,body,method=body===undefined?'GET':'POST'){
 const response=await fetch(harness+path,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(5000)});
 assert.equal(response.status,method==='POST'?201:200,`${method} ${path}`);return response.json();
}
async function scenario(module,path,match,response){
 const item=await admin('/admin/api/scenarios',{module,path,match,name:'ama-offline-e2e-'+run,enabled:true,priority:2000,method:'POST',status_code:200,delay_ms:0,response_type:'json',response_body:response,response_headers:{}});
 scenarios.push(item.id);
}
async function get(path){
 const value=await page.evaluate(async path=>{const response=await fetch(path);return {status:response.status,body:await response.json()};},path);
 assert.equal(value.status,200,path);return value.body;
}
async function review(){
 await page.getByRole('button',{name:'查看会议状态与处理记录',exact:true}).click();
 await page.getByRole('button',{name:'转写版本与语音审阅',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'转写版本与语音审阅',exact:true});await expect(dialog).toBeVisible();
 await dialog.getByRole('button',{name:'读取纠错建议',exact:true}).click();return dialog;
}
try{
 // A unique, canonical eight-second WAV prevents interference with other harness scenarios.
 const samples=128000,wav=Buffer.alloc(44+samples*2);
 wav.write('RIFF',0);wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8);wav.writeUInt32LE(16,16);
 wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22);wav.writeUInt32LE(16000,24);wav.writeUInt32LE(32000,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34);wav.write('data',36);wav.writeUInt32LE(samples*2,40);
 const frequency=300+Number.parseInt(run.slice(0,6),16)/65536;
 for(let n=0;n<samples;n++)wav.writeInt16LE(Math.round(Math.sin(n*2*Math.PI*frequency/16000)*3000),44+n*2);
 await writeFile(out+'/fixture.wav',wav);
 // Ask the installed client formatter for the exact prompt; the fixture never duplicates it.
 const probe=out+'/CorrectionFixture.java';
 await writeFile(probe,'import java.util.*; class CorrectionFixture {public static void main(String[] args)throws Exception{var method=Class.forName("ama.model.OfflineCorrectionProtocol").getDeclaredMethod("request",String.class,String.class,List.class);method.setAccessible(true);System.out.print(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(method.invoke(null,"deepseek-v4-flash",args[0],List.of())));}}');
 const classpath=['backend/adapters/target/classes','backend/contracts/target/classes','backend/core/target/classes','.tools/offline-speech/lib/*'].map(x=>resolve(x)).join(':');
 const payload=JSON.parse(execFileSync('java',['-cp',classpath,probe,punctuated],{encoding:'utf8'}));
 await scenario('asr','/inference/asr-nano/modelserving/asr',{binary_sha256:createHash('sha256').update(wav).digest('hex')},{text:original});
 await scenario('punctuation','/inference/asr-punc/modelserving/punc',{text_equals:original},{text:punctuated});
 await scenario('llm','/v1/chat/completions',{field_equals:{messages:payload.messages}},{choices:[{message:{role:'assistant',content:JSON.stringify([{seg:0,old:'因该',new:'应该',why:'同音错字'}])},finish_reason:'stop'}]});

 browser=await chromium.launch();const context=await browser.newContext({viewport:{width:1440,height:1000}});page=await context.newPage();page.setDefaultTimeout(30000);
 page.on('pageerror',error=>errors.push(error.message));
 await page.goto(origin+'/login');await page.locator('#login-username').fill('1');await page.locator('#login-password').fill('1');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/meetings');
 await expect(page.locator('#meeting-panel')).toHaveAttribute('aria-busy','false');
 await page.getByRole('button',{name:'导入会议',exact:true}).click();await page.waitForURL(/import=[0-9a-f-]+/);meeting=new URL(page.url()).searchParams.get('import');assert.match(meeting,/^[0-9a-f-]{36}$/);
 const upload=page.getByRole('dialog',{name:'导入会议',exact:true});await expect(upload.getByLabel('选择会议文件',{exact:true})).toBeEnabled();await upload.getByLabel('选择会议文件',{exact:true}).setInputFiles(out+'/fixture.wav');
 await expect(upload.getByRole('button',{name:'开始处理',exact:true})).toBeEnabled();await upload.getByRole('button',{name:'开始处理',exact:true}).click();
 const enterTranscript=page.getByRole('button',{name:'进入转写校正',exact:true});
 await expect.poll(async()=>await page.locator('.transcript-section .s-text').first().isVisible()||await enterTranscript.isVisible()&&await enterTranscript.isEnabled(),{timeout:120000}).toBe(true);
 if(await enterTranscript.isVisible()){
  // Navigation may complete automatically between observing and clicking this button.
  try{await enterTranscript.click({timeout:3000});}
  catch{await expect(page.locator('.transcript-section .s-text').first()).toBeVisible();}
 }
 await expect(page.locator('.transcript-section .s-text')).toHaveText([original]);check('browser-upload-worker-raw-without-reload');
 const root=`/api/v1/meetings/${meeting}/transcript`,history=await get(root+'/history?limit=20');rawId=history.items.find(item=>item.kind==='RAW').id;
 const raw=await get(root+'/revisions/'+rawId);assert.equal(raw.selected.segments[0].body,original);

 // Read only this newly created meeting's execution ledger, then verify it with the provider.
 const sql=`SELECT JSON_OBJECT('id',e.id,'path',e.path,'state',e.state,'requestSha256',e.request_sha256,'responseSha256',e.response_sha256,'settled',e.settled_at IS NOT NULL) FROM speech_execution e JOIN durable_task t ON t.id=e.lease_task_id WHERE t.subject_id='${meeting}' ORDER BY e.created_at;`;
 const ledger=JSON.parse(execFileSync('python3',['-c',
  'import sys,json,subprocess;sys.path.insert(0,"tools/dev");import local_app;assert local_app.owned_container();r=subprocess.run(["docker","exec","-i",local_app.NAME,"sh","-c",\'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --raw --skip-column-names ama_local_dev\'],input=sys.argv[1],text=True,capture_output=True,check=True);print(json.dumps([json.loads(s) for s in r.stdout.splitlines() if s]))',sql],{encoding:'utf8'}));
 assert.equal(ledger.length,6);assert.deepEqual(ledger.map(x=>x.path).sort(),['/vad','/diarize','/embed','/inference/asr-nano/modelserving/asr','/inference/asr-punc/modelserving/punc','/v1/chat/completions'].sort());
 for(const item of ledger){assert.equal(item.state,'COMPLETED');assert.equal(item.settled,true);const proof=await admin('/v1/harness/speech-executions/'+item.id);assert.equal(proof.state,'COMPLETED');assert.equal(proof.requestSha256,item.requestSha256);assert.equal(proof.responseSha256,item.responseSha256);proofs.push(proof);}
 check('all-six-live-http-executions-and-digests');
 await page.getByRole('button',{name:'建立人工工作版本',exact:true}).click();await expect(page.getByRole('button',{name:'编辑第 1 段',exact:true})).toBeVisible();
 let dialog=await review();await expect(dialog.getByRole('button',{name:'检查语音增强建议',exact:true})).toBeEnabled();await dialog.getByRole('button',{name:'检查语音增强建议',exact:true}).click();
 const panel=dialog.getByRole('complementary',{name:'纠错处理'});await expect(panel.locator('b')).toHaveText(corrected);await expect(panel.getByRole('button',{name:'接受',exact:true})).toBeEnabled();
 assert.equal((await get(root)).saved.segments[0].body,original);check('nonempty-llm-proposal-pending-with-raw-preserved');await page.screenshot({path:out+'/pending.png',fullPage:true});
 await panel.getByRole('button',{name:'接受',exact:true}).click();await expect(panel.getByText('已接受',{exact:true})).toBeVisible();assert.equal((await get(root)).saved.segments[0].body,corrected);
 assert.deepEqual((await get(root+'/revisions/'+rawId)).selected.segments,raw.selected.segments);check('explicit-accept-creates-working-revision');
 await dialog.getByRole('button',{name:'关闭转写审阅',exact:true}).click();await page.reload();await expect(page.locator('.transcript-section .s-text')).toHaveText([corrected]);check('accepted-result-survives-browser-refresh');await page.screenshot({path:out+'/accepted.png',fullPage:true});
 dialog=await review();const restoredPanel=dialog.getByRole('complementary',{name:'纠错处理'});await expect(restoredPanel.getByRole('button',{name:'撤销',exact:true})).toBeEnabled();await restoredPanel.getByRole('button',{name:'撤销',exact:true}).click();await expect(restoredPanel.getByText('已撤销',{exact:true})).toBeVisible();
 assert.equal((await get(root)).saved.segments[0].body,original);assert.deepEqual((await get(root+'/revisions/'+rawId)).selected.segments,raw.selected.segments);check('undo-restores-original-and-keeps-raw-history');
 await page.screenshot({path:out+'/undone.png',fullPage:true});assert.deepEqual(errors,[]);passed=true;
}catch(error){errors.push(String(error));if(page)await page.screenshot({path:out+'/failure.png',fullPage:true});throw error;}
finally{
 if(browser)await browser.close();
 const cleanupErrors=[];for(const id of scenarios)try{await admin('/admin/api/scenarios/'+id,undefined,'DELETE');}catch(error){cleanupErrors.push(String(error));}
 await writeFile(out+'/report.json',JSON.stringify({passed:passed&&cleanupErrors.length===0,simulatedModels:true,meeting,rawId,checks,errors,proofs,ownedScenariosRemoved:scenarios.length-cleanupErrors.length,cleanupErrors},null,2)+'\n');
 console.log('Evidence:',out+'/report.json');assert.deepEqual(cleanupErrors,[]);
}
