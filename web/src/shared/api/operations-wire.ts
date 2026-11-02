import {responseMatches} from './response-schema';
const prefix='/api/v1/operations/';
/** Bind each protected read to its closed response schema before displaying private content. */
export function operationsResponse(path:string,method:string,value:unknown):boolean{
 let schema:string|undefined;
 if(method==='GET'){
  const schemas:Record<string,string>={capabilities:'Capabilities',voiceprints:'Voiceprints',datasets:'Datasets',evaluations:'Evaluations',configuration:'Configuration',corpus:'Corpus'};schema=schemas[path.slice(prefix.length)];
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}$/.test(path))schema='EvaluationDetail';
  if(/^\/api\/v1\/operations\/mail\/[0-9a-f-]{36}$/.test(path))schema='MailJob';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail$/.test(path))schema='MailJobs';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail-context$/.test(path))schema='MailContext';
 }else{
  if(path===prefix+'voiceprints'||/^\/api\/v1\/operations\/voiceprints\/[0-9a-f-]{36}\/(?:review|revocation)$/.test(path))schema='Voiceprint';
  if(path===prefix+'voiceprints/match')schema='CandidateMatches';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail$/.test(path))schema='MailJob';
 }
 if(schema)return responseMatches('operations.yaml#/Operations'+schema,value);
 return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).length<=12;
}
