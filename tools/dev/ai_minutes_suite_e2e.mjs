/** Run dependent minutes acceptance scenarios within one lifecycle-lock interval. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const output=resolve(process.env.AMA_MINUTES_E2E_OUTPUT??'artifacts/ai-minutes-repair-2026-11-05/suite');
const stages=(process.env.AMA_MINUTES_E2E_STAGES??'smoke,guard,initial_failure,workflow,editing,templates,recovery,sources,publication').split(',');
const scripts={templates:'templates',smoke:'repair',guard:'repair',initial_failure:'initial_failure',workflow:'workflow',remaining:'remaining',member_tail:'member_tail',editing:'editing',recovery:'recovery',recovery_tail:'recovery_tail',sources:'sources',publication:'publication'};
// Editing follows the workflow's whole-workspace restoration and precedes publication.
if(stages.includes('workflow')&&!stages.includes('editing'))stages.splice(stages.indexOf('workflow')+1,0,'editing');
for(const stage of stages)assert(Object.hasOwn(scripts,stage),`Unknown acceptance stage: ${stage}`);
await mkdir(output,{recursive:true});
const report={startedAt:new Date().toISOString(),stages:[],pass:false};
let meeting=process.env.AMA_MINUTES_E2E_MEETING;
if(!meeting&&process.env.AMA_MINUTES_E2E_MEETING_REPORT){
  const prior=JSON.parse(await readFile(resolve(process.env.AMA_MINUTES_E2E_MEETING_REPORT),'utf8'));
  assert.equal(prior.pass,true,'The prerequisite smoke must have passed');
  meeting=prior.meetingId;
}
for(const stage of stages){
  const stageOutput=resolve(output,stage);
  const env={...process.env,AMA_MINUTES_E2E_OUTPUT:stageOutput};
  if(stage==='guard')env.AMA_MINUTES_E2E_FIRST_GUARD='1';else delete env.AMA_MINUTES_E2E_FIRST_GUARD;
  if(stage==='smoke'||stage==='guard'||stage==='initial_failure'||stage==='sources')delete env.AMA_MINUTES_E2E_MEETING;
  else {assert.match(meeting??'',/^[0-9a-f-]{36}$/);env.AMA_MINUTES_E2E_MEETING=meeting;}
  console.log(`START ${stage}`);
  const code=await new Promise((done,reject)=>{const child=spawn(process.execPath,[`tools/dev/ai_minutes_${scripts[stage]}_e2e.mjs`],{env,stdio:'inherit'});child.on('error',reject);child.on('exit',done);});
  const result=JSON.parse(await readFile(resolve(stageOutput,'report.json'),'utf8'));
  report.stages.push({stage,exitCode:code,pass:result.pass===true,meetingId:result.meetingId,checks:result.checks.length,report:resolve(stageOutput,'report.json')});
  await writeFile(resolve(output,'suite-report.json'),JSON.stringify(report,null,2));
  assert.equal(code,0,`${stage} failed; see ${stageOutput}`);assert.equal(result.pass,true);
  if(stage==='smoke')meeting=result.meetingId;
}
report.pass=true;report.finishedAt=new Date().toISOString();
await writeFile(resolve(output,'suite-report.json'),JSON.stringify(report,null,2));
