import {shallowReadonly,shallowRef,toRef} from 'vue';
export type DraftTicket<T>=Readonly<{value:T;baseToken:string;generation:number}>;
/** Independent editable draft, saved snapshot and conflict observation. No observation writes dirty text. */
export function useDraft<T>(initial:T,initialToken:string,canEdit:()=>boolean,maxBytes=8*1024*1024) {
 if(!Number.isSafeInteger(maxBytes)||maxBytes<1||maxBytes>8*1024*1024)throw new TypeError('Invalid draft budget');
 const token=(s:string)=>{if(!/^(0|[1-9][0-9]{0,18})$/.test(s)||(s.length===19&&s>'9223372036854775806'))throw new TypeError('Invalid revision token');return s;};
 function clone(value:T):T {const json=JSON.stringify(value);if(json===undefined||json.length>maxBytes||new TextEncoder().encode(json).length>maxBytes)throw new TypeError('Draft exceeds bound');const result:T=JSON.parse(json);const freeze=(v:unknown)=>{if(typeof v==='object'&&v!==null){Object.values(v).forEach(freeze);Object.freeze(v);}};freeze(result);return result;}
 const value=shallowRef<T>(clone(initial)),saved=shallowRef<T>(clone(initial)),baseToken=shallowRef(token(initialToken)),conflict=shallowRef<{value:T;token:string}|null>(null),generation=shallowRef(0),cleanGeneration=shallowRef(0);
 let epoch=0;const issued=new WeakMap<object,{epoch:number;generation:number}>();
 const advance=()=>{if(generation.value===Number.MAX_SAFE_INTEGER)throw new Error('Draft generation exhausted');generation.value++;};
 function edit(next:T){if(!canEdit())throw new Error('Editor is locked');const copy=clone(next);advance();value.value=copy;}
 function capture():DraftTicket<T>{if(!canEdit())throw new Error('Editor is locked');const ticket=Object.freeze({value:clone(value.value),baseToken:baseToken.value,generation:generation.value});issued.set(ticket,{epoch,generation:generation.value});return ticket;}
 function accept(ticket:DraftTicket<T>,actual:T,revision:string){const own=issued.get(ticket);if(!own||own.epoch!==epoch)throw new Error('Stale or foreign save ACK');const next=clone(actual),nextToken=token(revision);epoch++;saved.value=next;baseToken.value=nextToken;conflict.value=null;cleanGeneration.value=own.generation;if(generation.value===own.generation)value.value=clone(actual);}
 function observe(actual:T,revision:string){const next=clone(actual),nextToken=token(revision);if(generation.value!==cleanGeneration.value){conflict.value={value:next,token:nextToken};return;}reset(next,nextToken);}
 function reset(actual:T,revision:string){const next=clone(actual),nextToken=token(revision);epoch++;advance();value.value=next;saved.value=clone(next);baseToken.value=nextToken;cleanGeneration.value=generation.value;conflict.value=null;}
 function discard(){const latest=conflict.value;if(latest)reset(latest.value,latest.token);else reset(saved.value,baseToken.value);}
 function reapply(){if(!canEdit()||!conflict.value)throw new Error('No editable conflict');epoch++;advance();saved.value=conflict.value.value;baseToken.value=conflict.value.token;conflict.value=null;}
 return {value:shallowReadonly(value),saved:shallowReadonly(saved),baseToken:shallowReadonly(baseToken),conflict:shallowReadonly(conflict),dirty:toRef(()=>generation.value!==cleanGeneration.value),edit,capture,accept,observe,reset,discard,reapply};
}
