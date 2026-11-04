import {responseMatches} from './response-schema';
import {revisionToken} from './intent-contract';
const prefix='/api/v1/operations/';
const object=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v);
/** No unregistered read or write may become a successful protected response. */
export function operationsResponse(path:string,method:string,value:unknown,request?:unknown):boolean{
 let schema:string|undefined;
 const pathname=path.split('?')[0]??'';
 if(method==='GET'){
  const schemas:Record<string,string>={capabilities:'Capabilities',voiceprints:'Voiceprints',datasets:'Datasets',evaluations:'Evaluations',configuration:'Configuration',corpus:'Corpus'};
  schema=schemas[pathname.slice(prefix.length)];
  if(pathname===prefix+'configuration/history')schema='ConfigurationHistoryPage';
  if(/^\/api\/v1\/operations\/candidates\/[0-9a-f-]{36}\/history$/.test(pathname))schema='CandidateHistoryPage';
  if(pathname===prefix+'catalog')schema='Catalog';
  const page=pathname.match(/^\/api\/v1\/operations\/(voiceprints|corpus|datasets|evaluations|candidates)\/page$/)?.[1];
  if(pathname===prefix+'my-voiceprints/page')schema='VoiceprintsPage';
  if(page)schema=page[0]!.toUpperCase()+page.slice(1)+'Page';
  for(const [resource,name] of [['datasets','DatasetDetail'],['candidates','CandidateDetail'],['reports','Report'],['voiceprints','VoiceDetail']] as const)if(new RegExp(`^/api/v1/operations/${resource}/[0-9a-f-]{36}$`).test(pathname))schema=name;
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/speaker-candidates$/.test(pathname))schema='SpeakerCandidates';
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}$/.test(pathname))schema='EvaluationDetail';
  if(/^\/api\/v1\/operations\/mail\/[0-9a-f-]{36}$/.test(pathname))schema='MailJob';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail$/.test(pathname))schema='MailJobs';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail-context$/.test(pathname))schema='MailContext';
 }else if(method==='POST'){
  if(path===prefix+'voiceprints'||/^\/api\/v1\/operations\/voiceprints\/[0-9a-f-]{36}\/(?:review|revocation)$/.test(path))schema='Voiceprint';
  if(/^\/api\/v1\/operations\/voiceprints\/[0-9a-f-]{36}\/resampling$/.test(path))schema='SubmissionReceipt';
  if(path===prefix+'voiceprints/match')schema='CandidateMatches';
  if(path===prefix+'datasets')schema='DatasetReceipt';
  if(/^\/api\/v1\/operations\/datasets\/[0-9a-f-]{36}\/copies$/.test(path))schema='DatasetReceipt';
  if(path===prefix+'sample-assets')schema='AssetReceipt';
  if(path===prefix+'candidates'||path===prefix+'rollback-candidates'||/^\/api\/v1\/operations\/candidates\/[0-9a-f-]{36}\/(?:edits|approval)$/.test(path))schema='Candidate';
  if(/^\/api\/v1\/operations\/candidates\/[0-9a-f-]{36}\/evaluations$/.test(path))schema='EvaluationReceipt';
  if(/^\/api\/v1\/operations\/candidates\/[0-9a-f-]{36}\/publication$/.test(path))schema='ConfigurationReceipt';
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}\/cancellation$/.test(path))schema='CancellationReceipt';
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}\/retries$/.test(path))schema='EvaluationReceipt';
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}\/samples\/[-A-Za-z0-9_.:]+\/review$/.test(path))schema='SampleReviewReceipt';
  if(path===prefix+'evaluations')schema='EvaluationReceipt';
  if(/^\/api\/v1\/operations\/evaluations\/[0-9a-f-]{36}\/review$/.test(path))schema='EvaluationReviewReceipt';
  if([prefix+'configuration-releases',prefix+'configuration-rollbacks'].includes(path))schema='ConfigurationReceipt';
  if(/^\/api\/v1\/operations\/corpus\/[0-9a-f-]{36}\/withdrawal$/.test(path))schema='WithdrawalReceipt';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/(?:feedback|voiceprint-applications)$/.test(path))schema='SubmissionReceipt';
  if(/^\/api\/v1\/meetings\/[0-9a-f-]{36}\/mail$/.test(path))schema='MailJob';
 }
 if(!schema||!responseMatches('operations.yaml#/Operations'+schema,value))return false;
 if(method==='GET'&&object(value)){
  const id=pathname.match(/^\/api\/v1\/operations\/(?:evaluations|reports|datasets|candidates|voiceprints)\/([0-9a-f-]{36})$/)?.[1];if(id&&value.id!==id)return false;
  const meeting=pathname.match(/^\/api\/v1\/meetings\/([0-9a-f-]{36})\/speaker-candidates$/)?.[1];if(meeting&&value.meetingId!==meeting)return false;
 }
 if(method!=='POST'||!object(value))return true;
 const target=path.match(/\/(?:evaluations|voiceprints|corpus)\/([0-9a-f-]{36})\/(?:review|revocation|withdrawal|resampling)$/)?.[1];
 if(target&&value.id!==target)return false;
 const meeting=path.match(/\/meetings\/([0-9a-f-]{36})\//)?.[1];
 if(meeting&&value.meetingId!==meeting)return false;
 if(!object(request))return true;
 const candidateId=path.match(/^\/api\/v1\/operations\/candidates\/([0-9a-f-]{36})\//)?.[1];
 if(schema==='Candidate'){
  if(candidateId&&value.id!==candidateId)return false;
  if(candidateId&&revisionToken(request.expectedRevisionToken)&&value.revisionToken!==(BigInt(request.expectedRevisionToken)+1n).toString())return false;
  if(path.endsWith('/approval')&&(value.state!=='APPROVED'||value.evaluationId!==request.evaluationId||value.approvalReason!==request.reason))return false;
  if(request.title!==undefined&&value.title!==request.title)return false;
  if(object(request.configuration)&&object(value.configuration))for(const key of ['modelVersion','promptVersion','voiceThreshold'])if(request.configuration[key]!==value.configuration[key])return false;
  if(object(request.rules)&&object(value.rules))for(const key of Object.keys(request.rules))if(request.rules[key]!==value.rules[key])return false;
 }
 if(schema==='SampleReviewReceipt'){
  const parts=path.match(/evaluations\/([0-9a-f-]{36})\/samples\/([^/]+)\/review$/);if(!parts||value.id!==parts[1]||value.sampleId!==parts[2]||value.reason!==request.reason||!object(value.rubric)||!object(request.rubric))return false;
  for(const name of ['factuality','actions','evidence'])if(value.rubric[name]!==request.rubric[name])return false;
 }
 if(schema==='CancellationReceipt'&&value.id!==path.split('/')[5])return false;
 if(candidateId&&schema==='ConfigurationReceipt'&&revisionToken(request.currentConfigurationRevisionToken)&&value.revisionToken!==(BigInt(request.currentConfigurationRevisionToken)+1n).toString())return false;
 if(schema==='DatasetReceipt'&&value.version!==request.version)return false;
 if(schema==='EvaluationReviewReceipt'&&value.reviewReason!==request.reason)return false;
 if(schema==='SubmissionReceipt'&&value.category!==((path.endsWith('/voiceprint-applications')||path.endsWith('/resampling'))?'VOICEPRINT':request.feedbackType??request.category))return false;
 if(path.endsWith('/resampling')&&(value.state!=='PENDING'||!revisionToken(request.expectedRevisionToken)||value.revisionToken!==(BigInt(request.expectedRevisionToken)+1n).toString()))return false;
 if(schema==='Voiceprint'&&target&&value.state!==(path.endsWith('/revocation')?'REVOKED':request.decision))return false;
 if((schema==='ConfigurationReceipt'&&!candidateId||schema==='WithdrawalReceipt'||schema==='Voiceprint'&&target)&&revisionToken(request.expectedRevisionToken)){
  if(!revisionToken(value.revisionToken)||BigInt(value.revisionToken)!==BigInt(request.expectedRevisionToken)+1n)return false;
 }
 if(path===prefix+'configuration-releases'&&object(request.configuration)&&object(value.configuration)){
  for(const name of ['modelVersion','promptVersion','voiceThreshold'])if(value.configuration[name]!==request.configuration[name])return false;
 }
 if(schema==='MailJob'&&(value.publicationId!==request.publicationId||value.sourceRevisionToken!==request.sourceRevisionToken))return false;
 return true;
}
