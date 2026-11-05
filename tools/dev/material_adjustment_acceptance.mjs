import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
const {chromium,expect:baseExpect}=createRequire(resolve('web/package.json'))('@playwright/test'),expect=baseExpect.configure({timeout:30000});
const [fixtureFile,out]=process.argv.slice(2),{meeting}=JSON.parse(await readFile(fixtureFile,'utf8'));await mkdir(out,{recursive:true});
const file=resolve(out,'术语对照.docx');
execFileSync('python3',['-c',`import zipfile,sys
with zipfile.ZipFile(sys.argv[1],'w') as z:
 z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
 z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
 z.writestr('word/document.xml','<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>术语对照：存储价格走势=存储成本趋势</w:t></w:r></w:p></w:body></w:document>')`,file]);
const browser=await chromium.launch(),context=await browser.newContext(),page=await context.newPage(),other=await context.newPage(),base='http://127.0.0.1:5174',checks=[],errors=[],commands=[];
const pass=s=>{checks.push(s);console.log('PASS',s);};
for(const p of [page,other])p.on('pageerror',e=>errors.push(e.message));
const read=async suffix=>page.evaluate(async url=>{const r=await fetch(url);if(!r.ok)throw Error(String(r.status));return r.json();},'/api/v1/meetings/'+meeting+suffix);
async function open(p){await p.goto(base+'/meeting/'+meeting+'/processing');await p.getByRole('button',{name:'调整背景材料'}).click();const dialog=p.getByRole('dialog',{name:'会议背景材料'});await expect(dialog.getByLabel('选择背景材料')).toBeAttached();return dialog;}
try{
 await page.goto(base+'/login');await page.locator('#login-username').fill('demo-owner');await page.locator('#login-password').fill('demo-password');await page.locator('button[type=submit]').click();await page.waitForURL('**/meetings');
 const a=await open(page),b=await open(other),before=await read('/materials'),work=(await read('/workspace')).saved.transcriptRevisionId;
 await a.getByLabel('选择背景材料').setInputFiles(file);await b.getByLabel('选择背景材料').setInputFiles(file);
 await a.getByRole('button',{name:'从知识库选择'}).click();const picker=page.getByRole('dialog',{name:'从知识库选择材料'});await picker.getByRole('checkbox').first().check();await picker.getByRole('button',{name:'添加所选文件'}).click();
 assert.deepEqual(await read('/materials'),before);pass('two private drafts retain local files and knowledge selection without publishing mutations');
 const batches='**/meetings/'+meeting+'/materials/batches';
 await page.route(batches,async r=>{commands.push([r.request().headers()['idempotency-key'],r.request().postData()]);const reply=await r.fetch();assert.equal(reply.status(),201,await reply.text());await r.abort('failed');},{times:1});
 await a.getByRole('button',{name:'应用材料并重新增强'}).click();await expect(a.getByRole('alert').first()).toBeVisible();
 await page.route(batches,async r=>{commands.push([r.request().headers()['idempotency-key'],r.request().postData()]);await r.continue();},{times:1});
 const replay=page.waitForResponse(r=>r.url().endsWith('/materials/batches'));await a.getByRole('button',{name:'应用材料并重新增强'}).click();assert.equal((await replay).status(),201);assert.deepEqual(commands[0],commands[1]);assert.equal((await read('/materials')).items.length,before.items.length+2);pass('lost acknowledgement replays exact body and key without duplicating the mixed batch');
 const conflict=other.waitForResponse(r=>r.url().endsWith('/materials/batches'));await b.getByRole('button',{name:'应用材料并重新增强'}).click();assert.equal((await conflict).status(),409);await expect(b.getByRole('alert').first()).toBeVisible();assert.equal((await read('/materials')).items.length,before.items.length+2);pass('stale second window cannot replace first commit');
 await expect(a.getByRole('button',{name:'采用增强候选'})).toBeVisible({timeout:90000});assert.equal((await read('/workspace')).saved.transcriptRevisionId,work);assert.equal((await read('/workspace')).processingMaterialsChanged,true);
 await a.getByRole('button',{name:'采用增强候选'}).click();await expect(a.getByText('已采用',{exact:true})).toBeVisible();assert.notEqual((await read('/workspace')).saved.transcriptRevisionId,work);assert.equal((await read('/workspace')).processingMaterialsChanged,false);pass('human review adopts candidate exactly once after checking its wording');
 await page.screenshot({path:resolve(out,'adopted.png')});assert.deepEqual(errors,[]);
}catch(e){console.error(e);for(const [n,p] of [page,other].entries())await writeFile(resolve(out,'page-'+n+'.txt'),p.url()+'\n'+await p.locator('body').innerText());await page.screenshot({path:resolve(out,'failure.png')}).catch(()=>{});process.exitCode=1;}
finally{await writeFile(resolve(out,'result.json'),JSON.stringify({meeting,checks,errors,pass:!process.exitCode},null,2));await browser.close();}
