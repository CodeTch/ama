import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import type {components} from '../../shared/api/generated';
import {responseMatches} from '../../shared/api/response-schema';
import {exact,object,uuid} from '../../shared/api/minutes-wire';
export type SensitiveDraft={base:components['schemas']['SavedSettings'];chosen:string[];reasons:Record<string,string|null>};
export function createSensitiveJournal(context:TabJournalContext){return createScopedPrivateJournal<SensitiveDraft>(context,'minutes-sensitive',(v):v is SensitiveDraft=>{
 if(!exact(v,'base,chosen,reasons')||!responseMatches('minutes.yaml#/SavedSettings',v.base)||!Array.isArray(v.chosen)||v.chosen.length>10000||new Set(v.chosen).size!==v.chosen.length||!object(v.reasons))return false;
 try{return v.chosen.every(key=>{if(typeof key!=='string')return false;const pair=JSON.parse(key);return Array.isArray(pair)&&pair.length===2&&['INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE'].includes(pair[0])&&uuid(pair[1])&&JSON.stringify(pair)===key;})&&Object.values(v.reasons).every(reason=>reason===null||typeof reason==='string');}catch{return false;}
 });}
