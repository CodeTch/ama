import {readFileSync,realpathSync,lstatSync} from 'node:fs';
import {resolve,relative,isAbsolute,join} from 'node:path';
/** Only the canonical runner's fresh reservation can redirect Playwright artifacts. */
export function playwrightOutputs(repository:string,environment:NodeJS.ProcessEnv){
 const requested=environment.AMA_CHECK_OUTPUT_ROOT;
 if(requested===undefined)return {report:environment.PLAYWRIGHT_JUNIT_OUTPUT_FILE||'../artifacts/web/playwright.xml',artifacts:'../artifacts/web/test-results'};
 if(!requested)throw new Error('Explicit output root is empty');
 const root=realpathSync(repository),output=realpathSync(requested),rel=relative(root,output);
 if(!rel||rel==='..'||rel.startsWith('..'+(process.platform==='win32'?'\\':'/'))||isAbsolute(rel)||resolve(requested)!==output)throw new Error('Owned output must remain inside this checkout');
 const marker=join(output,'owned-output.json');const stat=lstatSync(marker);if(!stat.isFile()||stat.isSymbolicLink()||stat.size>4096)throw new Error('Invalid output reservation');
 const raw=readFileSync(marker);if(raw.length>4096)throw new Error('Invalid output reservation');const value:unknown=JSON.parse(raw.toString('utf8'));
 if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).sort().join(',')!=='group,output,repository,schema'||!('schema' in value)||value.schema!==1||!('repository' in value)||value.repository!==root||!('output' in value)||value.output!==output||!('group' in value)||(value.group!=='e2e'&&value.group!=='visual'))throw new Error('Invalid output reservation');
 const report=join(output,value.group+'.xml'),artifacts=join(output,'test-results');
 if(environment.PLAYWRIGHT_JUNIT_OUTPUT_FILE&&resolve(environment.PLAYWRIGHT_JUNIT_OUTPUT_FILE)!==report)throw new Error('Foreign JUnit output');
 for(const path of [report,artifacts]){const stat=lstatSync(path,{throwIfNoEntry:false});if(stat?.isSymbolicLink())throw new Error('Symbolic output route');if(stat&&(path===report?!stat.isFile():!stat.isDirectory()))throw new Error('Invalid output type');}
 return {report,artifacts};
}
