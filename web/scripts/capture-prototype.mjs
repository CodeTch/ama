import { chromium } from '@playwright/test';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { mkdir, writeFile, access, readFile } from 'node:fs/promises';
const reference=resolve('../designDocs/智能会议助手1021版.html');
const hash=data=>createHash('sha256').update(data).digest('hex');
try {await access(resolve('../docs/acceptance/a/prototype/1440x900/original-meetings.png'));if(!process.argv.includes('--refresh-reference')) throw new Error('Existing original references are preserved. Explicit --refresh-reference is required to recapture the original prototype.');}catch(error){if(error.code!=='ENOENT')throw error;}
const browser = await chromium.launch();
const provenance={source:'designDocs/智能会议助手1021版.html',sha256:hash(await readFile(reference)),browser:browser.version(),playwright:'1.58.2',revision:'1208',capturedAt:new Date().toISOString(),locale:'zh-CN',timezone:'Asia/Shanghai',scale:1,fonts:{}};
for(const font of ['/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc','/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'])provenance.fonts[font]=hash(await readFile(font));
provenance.fontPackages=execFileSync('dpkg-query',['-W','fonts-noto-cjk','fonts-liberation','fonts-dejavu-core'],{encoding:'utf8'}).trim();
for (const [width,height] of [[1440,900],[1024,768]]) {
 const page = await browser.newPage({viewport:{width,height},deviceScaleFactor:1,locale:'zh-CN',timezoneId:'Asia/Shanghai',reducedMotion:'reduce'});
 await page.goto('file://'+resolve('../designDocs/智能会议助手1021版.html')+'#/meetings');
 await page.evaluate(()=>document.fonts.ready);
 const dir=resolve('../docs/acceptance/a/prototype/'+width+'x'+height); await mkdir(dir,{recursive:true});
 await page.screenshot({path:dir+'/original-meetings.png', fullPage:true, animations:'disabled'});
 await writeFile(dir+'/rendered-shell.json',JSON.stringify(await page.evaluate(()=>({text:document.body.innerText,header:document.querySelector('.topbar')?.outerHTML,head:document.querySelector('.page-head')?.outerHTML,styles:[...document.querySelectorAll('.layout,.topbar,.content,.page-head,.page-title,.page-desc,.page-actions,.btn')].slice(0,12).map(e=>({class:e.className,rect:e.getBoundingClientRect().toJSON(),style:Object.fromEntries(['backgroundColor','color','fontSize','borderRadius','height','padding','lineHeight'].map(k=>[k,getComputedStyle(e)[k]]))}))})),null,2));
 const baseline=resolve('tests/visual/baselines/'+width+'x'+height);await mkdir(baseline,{recursive:true});
 await page.screenshot({path:baseline+'/meeting-shell.png',clip:{x:0,y:0,width,height:204},style:'.topbar-right {visibility:hidden!important}',animations:'disabled'});
 await page.getByRole('button',{name:'导入会议',exact:true}).click();
 await page.locator('.aci-import-modal').waitFor();
 await page.screenshot({path:dir+'/original-import-modal.png',fullPage:false,animations:'disabled'});
 await page.goto('file://'+resolve('../designDocs/智能会议助手1021版.html')+'#/meetings');
 await page.locator('.m19-name').first().click();
 await page.waitForURL('**#/meeting/**');
 await page.locator('[data-act="acOpenInfo"]').first().waitFor();
 await page.screenshot({path:dir+'/original-transcript-page.png',fullPage:false,animations:'disabled'});

 const info=page.locator('[data-act="acOpenInfo"], [data-act="aciOpenInfo"]');
 if(await info.count()){await info.first().click();await page.locator('.drawer').waitFor();await page.screenshot({path:dir+'/original-info-drawer.png',fullPage:false,animations:'disabled'});}
 await page.close();
}
await writeFile(resolve('../docs/acceptance/a/prototype/provenance.json'),JSON.stringify(provenance,null,2)+'\n');
await browser.close();
