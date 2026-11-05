import {test,expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const prototype=readFileSync(fileURLToPath(new URL('../../../designDocs/智能会议助手1021版.html',import.meta.url)),'utf8');
const labels:Record<string,string>={'人名纠错':'PERSON','部门纠错':'DEPARTMENT','产品纠错':'PRODUCT','金融术语纠错':'FINANCIAL_TERM','产品别名':'PRODUCT_ALIAS','术语读音':'TERM_PRONUNCIATION','声纹样本':'VOICEPRINT'};
const states:Record<string,string>={'待审核':'PENDING','已通过':'ACCEPTED','已驳回':'REJECTED','需要补充':'NEEDS_MORE'};
test('unmodified original three corpus and two voice samples define the feedback layout',async({page,context},info)=>{
 expect(createHash('sha256').update(prototype).digest('hex')).toBe('ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867');
 const reference=await context.newPage();await reference.route('**/__original_feedback.html',r=>r.fulfill({contentType:'text/html; charset=utf-8',body:prototype}));await reference.goto('/__original_feedback.html#/feedback');
 const original=await reference.evaluate(()=>{const S=(0,eval)('S'),u=(0,eval)('curUser()');return {corpus:S.feedbacks.filter((r:any)=>r.by===u.name),voices:S.voiceprints.filter((r:any)=>r.by===u.name)};});
 expect(original.corpus).toHaveLength(3);expect(original.voices).toHaveLength(2);
 const rows=[...original.corpus.map((r:any)=>({kind:'CORPUS',...r})),...original.voices.map((r:any)=>({kind:'VOICEPRINT',...r,type:'声纹样本',old:'会议匿名说话人',sug:r.person,opinion:r.history.at(-1)?.r??''}))].sort((a,b)=>b.at.localeCompare(a.at));
 const items=rows.map(r=>({id:r.id,kind:r.kind,category:labels[r.type],feedbackType:labels[r.type],originalValue:r.old,suggestedValue:r.sug,note:'',comment:'',meetingId:r.meeting,submittedAt:new Date(r.at.replace(' ','T')+':00+08:00').toISOString(),revisionToken:'0',state:states[r.status],reviewReason:r.opinion,usageState:r.kind==='VOICEPRINT'?'NOT_APPLIED':'EFFECTIVE',usageVersion:r.kind==='CORPUS'?'harness-corpus-fixture':null}));
 const body=`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><link rel="stylesheet" href="/src/shared/styles/tokens.css"><link rel="stylesheet" href="/src/shared/styles/prototype.css"><link rel="stylesheet" href="/src/shared/styles/parity.css"></head><body><div style="height:56px"></div><div id="fixture"></div><script type="module">import{createApp,h,ref}from'/node_modules/.vite/deps/vue.js';import View from'/src/features/meetings/FeedbackTableView.vue';const rows=${JSON.stringify(items)},selected=ref(null);createApp({setup(){return()=>h('div',{class:'layout'},h('div',{class:'main'},h('main',{class:'content'},[h(View,{items:rows.filter(r=>!selected.value||r.state===selected.value),counts:{ALL:5,PENDING:0,ACCEPTED:3,REJECTED:1,NEEDS_MORE:1},selected:selected.value,loading:false,error:null,onSelect:s=>selected.value=s})])))}}).mount('#fixture');</script></body></html>`;
 await page.goto('/');await page.route('**/__feedback_fixture.html',r=>r.fulfill({contentType:'text/html; charset=utf-8',body}));await page.goto('/__feedback_fixture.html');
 await expect(page.locator('.tbl tbody tr')).toHaveCount(5);await expect(page.locator('th')).toHaveText(['类型','原始值','建议值 / 人员','来源会议','提交时间','状态','审核意见']);await expect(page.locator('.cnt')).toHaveText(['5','0','3','1','1']);await expect(page.locator('.tag.blue')).toHaveCount(2);
 const columns=(p:typeof page)=>p.locator('.tbl tbody tr').evaluateAll(rows=>rows.map(row=>[...row.querySelectorAll('td')].map(td=>td.textContent?.trim())));
 expect(await columns(page)).toEqual(await columns(reference));
 // Compare the unchanged page body; the separately approved shared account header is outside this fixture.
 const geometry=(p:typeof page)=>p.evaluate(()=>{const root=document.querySelector('main.content')??document.querySelector('.content'),base=root!.getBoundingClientRect();return ['.page-head','.tabs','.card','th'].map(selector=>{const r=root!.querySelector(selector)!.getBoundingClientRect();return {x:r.x-base.x,y:r.y-base.y,width:r.width,height:r.height};});});
 const actual=await geometry(page),expected=await geometry(reference);for(let i=0;i<actual.length;i++)for(const key of ['x','y','width','height'] as const)expect(Math.abs(actual[i]![key]-expected[i]![key]),`${i} ${key}`).toBeLessThanOrEqual(1);
 await page.screenshot({path:info.outputPath('actual-feedback.png'),fullPage:true});await reference.screenshot({path:info.outputPath('original-feedback.png'),fullPage:true});
 for(const label of ['待审核','已通过','已驳回','需要补充']){await page.getByRole('tab',{name:new RegExp('^'+label)}).click();const count=items.filter(i=>i.state===states[label]).length;expect(await page.locator('.tbl tbody tr').count()).toBe(Math.max(1,count));}
 await reference.close();
});
