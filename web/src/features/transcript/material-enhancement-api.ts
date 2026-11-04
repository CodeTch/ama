import type {createBusinessClient} from '../../shared/api/business-client';
import {ApiError} from '../../shared/api/client';import {revisionToken} from '../../shared/api/intent-contract';import {responseMatches} from '../../shared/api/response-schema';import {materialUuid as uuid} from '../../shared/api/materials';
export type EnhancementInput={expectedRevisionToken:string;sourceRevisionId:string;materialSnapshotId:string|null};
export type MaterialEnhancement=EnhancementInput&{candidateId:string;profile:'ama-material-enhancement.v1';state:'PENDING'|'READY'|'STALE'|'APPLYING'|'APPLIED';changes:{segmentId:string;before:string;after:string}[];canApply:boolean};
export function validEnhancementInput(v:unknown):v is EnhancementInput{if(!v||typeof v!=='object'||Array.isArray(v))return false;const p=v as EnhancementInput;return Object.keys(p).sort().join(',')==='expectedRevisionToken,materialSnapshotId,sourceRevisionId'&&revisionToken(p.expectedRevisionToken)&&uuid(p.sourceRevisionId)&&(p.materialSnapshotId===null||uuid(p.materialSnapshotId));}
const invalid=():never=>{throw new ApiError('response',200,'材料增强结果无法核实，请恢复原请求。');};
function candidate(value:unknown,expected?:EnhancementInput):MaterialEnhancement{
 if(!value||typeof value!=='object'||Array.isArray(value))return invalid();const p=value as MaterialEnhancement;
 const base='candidateId,expectedRevisionToken,materialSnapshotId,profile,sourceRevisionId,state',ready='canApply,candidateId,changes,expectedRevisionToken,materialSnapshotId,profile,sourceRevisionId,state';
 if(!uuid(p.candidateId)||!validEnhancementInput({expectedRevisionToken:p.expectedRevisionToken,sourceRevisionId:p.sourceRevisionId,materialSnapshotId:p.materialSnapshotId})||p.profile!=='ama-material-enhancement.v1'||!['PENDING','READY','STALE','APPLYING','APPLIED'].includes(p.state)||Object.keys(p).sort().join(',')!==(p.state==='READY'?ready:base))return invalid();
 if(expected&&(p.expectedRevisionToken!==expected.expectedRevisionToken||p.sourceRevisionId!==expected.sourceRevisionId||p.materialSnapshotId!==expected.materialSnapshotId))return invalid();
 if(p.state==='READY'&&(!Array.isArray(p.changes)||p.changes.length>4096||typeof p.canApply!=='boolean'||p.canApply!==(p.changes.length>0)||p.changes.some(x=>!x||Object.keys(x).sort().join(',')!=='after,before,segmentId'||!uuid(x.segmentId)||typeof x.before!=='string'||typeof x.after!=='string'||x.before.length>65536||x.after.length>65536||x.before===x.after)||new Set(p.changes.map(x=>x.segmentId)).size!==p.changes.length))return invalid();
 return {...p,changes:p.changes??[],canApply:p.canApply??false};
}
export function createMaterialEnhancementApi(client:ReturnType<typeof createBusinessClient>,actor:()=>string|null){
 function root(meeting:string){if(!uuid(meeting)||!actor())throw new TypeError('Authorized enhancement scope required');return `/api/v1/meetings/${meeting}/transcript/material-enhancements`;}
 return{
 async generate(meeting:string,key:string,input:EnhancementInput){if(!uuid(key)||!validEnhancementInput(input))throw new TypeError('Original enhancement intent required');const who=actor();const value=await client.json(root(meeting),{method:'POST',errorContract:'material-enhancement',headers:{'Idempotency-Key':key},body:JSON.stringify(input),expectedStatus:200,maxResponseBytes:8*1024*1024});if(who!==actor())throw new DOMException('Scope changed','AbortError');return candidate(value,input);},
 async read(meeting:string,id:string){if(!uuid(id))throw new TypeError('Candidate required');const who=actor(),result=candidate(await client.json(root(meeting)+'/'+id,{expectedStatus:200}));if(who!==actor())throw new DOMException('Scope changed','AbortError');if(result.candidateId!==id)return invalid();return result;},
 async adopt(meeting:string,id:string,key:string){if(!uuid(id)||!uuid(key))throw new TypeError('Original decision required');const who=actor(),result=await client.json(root(meeting)+'/'+id+'/adoption',{method:'POST',errorContract:'material-enhancement',headers:{'Idempotency-Key':key},body:'{}',expectedStatus:200});if(who!==actor())throw new DOMException('Scope changed','AbortError');if(!responseMatches('transcripts.yaml#/Receipt',result)||(result as {result:{kind:string;meetingId:string}}).result.kind!=='SAVED_REVISION'||(result as {result:{meetingId:string}}).result.meetingId!==meeting)return invalid();return result;}
 };
}
