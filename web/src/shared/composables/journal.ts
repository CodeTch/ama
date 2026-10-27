import {fitsUtf8} from '../bounded-text';
export type JournalScope=Readonly<{actor:string;meeting:string;kind:string;tab:string}>;
type StoragePort=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
type Stored<T>={kind:'RESTORED';value:T}|{kind:'EMPTY'|'INVALID'|'UNAVAILABLE'};
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}

/** Storage is untrusted data. Restoration returns a read-only preview, never editor ACK or command acceptance. */
export function createJournal<T>(storage:StoragePort,scope:JournalScope,validate:(value:unknown)=>value is T,maximumBytes:number) {
 if(!Number.isSafeInteger(maximumBytes)||maximumBytes<1||maximumBytes>8*1024*1024)throw new TypeError('Invalid journal bound');
 if(!scope.actor||!fitsUtf8(scope.actor,1020)||[...scope.actor].length>255||!scope.meeting||!fitsUtf8(scope.meeting,255)||!/^[-a-zA-Z0-9_]{1,64}$/.test(scope.kind)||!/^[-a-zA-Z0-9_]{1,128}$/.test(scope.tab))throw new TypeError('Invalid journal scope');
 const exact=Object.freeze({actor:scope.actor,meeting:scope.meeting,kind:scope.kind,tab:scope.tab}),identity=JSON.stringify([scope.actor,scope.meeting,scope.kind,scope.tab]),key='ama.journal.v1:'+identity;
 function load():Stored<T>{
  let raw:string|null;try{raw=storage.getItem(key);}catch{return {kind:'UNAVAILABLE'};}if(raw===null)return {kind:'EMPTY'};
  try{if(!fitsUtf8(raw,maximumBytes))return {kind:'INVALID'};const value:unknown=JSON.parse(raw);if(!shape(value,'schema,scope,value')||value.schema!==1||!shape(value.scope,'actor,kind,meeting,tab')||value.scope.actor!==exact.actor||value.scope.meeting!==exact.meeting||value.scope.kind!==exact.kind||value.scope.tab!==exact.tab||!validate(value.value))return {kind:'INVALID'};const freeze=(node:unknown)=>{if(typeof node==='object'&&node!==null){Object.values(node).forEach(freeze);Object.freeze(node);}};freeze(value.value);return {kind:'RESTORED',value:value.value};}catch{return {kind:'INVALID'};}
 }
 function save(value:T):{kind:'SAVED'|'INVALID'|'UNAVAILABLE'} {
  let encoded:string;try{if(!validate(value))return {kind:'INVALID'};encoded=JSON.stringify({schema:1,scope:exact,value});if(!fitsUtf8(encoded,maximumBytes))return {kind:'INVALID'};}catch{return {kind:'INVALID'};}
  try{storage.setItem(key,encoded);return {kind:'SAVED'};}catch{return {kind:'UNAVAILABLE'};}
 }
 function clear():{kind:'CLEARED'|'UNAVAILABLE'}{try{storage.removeItem(key);return {kind:'CLEARED'};}catch{return {kind:'UNAVAILABLE'};}}
 return {load,save,clear};
}
