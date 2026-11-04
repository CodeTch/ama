/** Real isolated confirmation workflow, kept within one existing lifecycle-lock window. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const out=resolve(process.argv[2]);
const env={...process.env,AMA_CONFIRM_OWNER:'confirmation-owner-20261103',AMA_CONFIRM_MEMBER:'confirmation-member-20261103',AMA_CONFIRM_MEMBER_NAME:'会议确认验收参会人'};
async function json(path){try{return JSON.parse(await readFile(path,'utf8'));}catch{return null;}}
async function run(script,directory,extra={}){
 await mkdir(directory,{recursive:true});
 await new Promise((done,reject)=>{const child=spawn(process.execPath,[resolve('tools/dev/'+script),directory],{cwd:process.cwd(),env:{...env,...extra},stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?done():reject(new Error(script+' exited '+code)));});
}
async function seed(directory,kind){
 let result=await json(resolve(directory,'seed-report.json'));
 if(!result?.pass){const state=await json(resolve(directory,'seed-state.json'));await run('meeting_confirmation_seed.mjs',directory,{AMA_CONFIRM_SEED_KIND:kind,...(state?.meetingId?{AMA_CONFIRM_MEETING_ID:state.meetingId}:{})});result=await json(resolve(directory,'seed-report.json'));}
 assert.equal(result?.pass,true);return result.meetingId;
}
await seed(out,'main');
await run('meeting_confirmation_integration.mjs',out);
const recovery=resolve(out,'recovery'),recoveryMeeting=await seed(recovery,'recovery');
await run('meeting_confirmation_recovery.mjs',recovery,{AMA_CONFIRM_MEETING_ID:recoveryMeeting});
const main=await json(resolve(out,'integration-report.json')),restored=await json(resolve(recovery,'report.json'));
assert(main.pass&&restored.pass);
await writeFile(resolve(out,'acceptance-report.json'),JSON.stringify({pass:true,mainMeeting:main.meetingId,recoveryMeeting,checks:[...main.checks,...restored.checks],mainErrors:main.errors,recoveryErrors:restored.errors,externalMode:'LOCAL_SIMULATION',harnessPort:8792},null,2));
