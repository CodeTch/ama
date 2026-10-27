import {readonly,shallowRef} from 'vue';
import {ApiError} from '../api/client';
import {fitsUtf8} from '../bounded-text';
export type CommandState='EMPTY'|'PREPARED'|'SENDING'|'UNCERTAIN'|'ACKNOWLEDGED'|'CONFLICT'|'DENIED'|'REJECTED';
export type CommandInput={operation:string;actor:string;resource:string;path:string;key:string;body:unknown};
export type CommandSnapshot=Readonly<Omit<CommandInput,'body'> & {body:string}>;
/** One immutable command. ACK is historical fact, never a substitute for a fresh saved/readiness read. */
export function useCommand<T>(sendRequest:(request:CommandSnapshot)=>Promise<T>,maxBodyBytes=8*1024*1024) {
 if(!Number.isSafeInteger(maxBodyBytes)||maxBodyBytes<1||maxBodyBytes>8*1024*1024)throw new TypeError('Invalid command budget');
 const state=shallowRef<CommandState>('EMPTY'),snapshot=shallowRef<CommandSnapshot|null>(null),ack=shallowRef<T|null>(null),error=shallowRef<unknown>(null);
 let flight:Promise<T>|null=null,epoch=0;
 function ensure(generation:number){if(generation!==epoch)throw new DOMException('Command scope changed','AbortError');}
 function install(input:Omit<CommandInput,'body'>,body:string,next:'PREPARED'|'UNCERTAIN') {
  if(flight||(state.value!=='EMPTY'&&state.value!=='ACKNOWLEDGED'))throw new Error('Resolve the prior command first');
  for(const text of [input.operation,input.actor,input.resource,input.path,input.key])if(typeof text!=='string'||!text||text.length>2048)throw new TypeError('Invalid command scope');
  if(!input.path.startsWith('/api/v1/')||input.path.includes('#')||input.path.includes('\\'))throw new TypeError('Product API path required');
  if(typeof body!=='string'||!fitsUtf8(body,maxBodyBytes))throw new TypeError('Command body too large');
  const generation=++epoch;snapshot.value=Object.freeze({operation:input.operation,actor:input.actor,resource:input.resource,path:input.path,key:input.key,body});ensure(generation);ack.value=null;ensure(generation);error.value=null;ensure(generation);state.value=next;ensure(generation);
 }
 function prepare(input:CommandInput){install(input,JSON.stringify(input.body),'PREPARED');}
 function restore(input:CommandSnapshot){
  if(state.value!=='EMPTY'||typeof input.body!=='string'||!fitsUtf8(input.body,maxBodyBytes))throw new Error('Cannot restore command');
  const parsed:unknown=JSON.parse(input.body);if(typeof parsed!=='object'||parsed===null||Array.isArray(parsed))throw new TypeError('Command object required');
  install(input,input.body,'UNCERTAIN');
 }

 function send():Promise<T> {
  if(flight)return flight;
  if(!snapshot.value||!['PREPARED','UNCERTAIN'].includes(state.value))throw new Error('Command cannot be sent');
  const current=snapshot.value,generation=epoch;
  const run=async()=>{
   try {ensure(generation);const result=await sendRequest(current);ensure(generation);ack.value=result;ensure(generation);state.value='ACKNOWLEDGED';ensure(generation);return result;}
   catch(cause) {
    ensure(generation);error.value=cause;ensure(generation);
    state.value=cause instanceof ApiError&&cause.kind==='http'&&cause.status!==null&&cause.status>=400&&cause.status<500
      ?cause.status===409?'CONFLICT':[401,403].includes(cause.status)?'DENIED':'REJECTED':'UNCERTAIN';
    ensure(generation);throw cause;
   }
  };
  // Install the latch before SENDING/error publication can trigger synchronous Vue watchers.
  let accept!:(value:T)=>void,decline!:(cause:unknown)=>void;
  const settled=new Promise<T>((resolve,reject)=>{accept=resolve;decline=reject;});flight=settled;
  const clear=()=>{if(generation===epoch)flight=null;};void settled.then(clear,clear);
  try{error.value=null;ensure(generation);state.value='SENDING';ensure(generation);void run().then(accept,decline);}catch(cause){decline(cause);}
  return settled;
 }
 function reconcileRejected(){if(!['CONFLICT','REJECTED'].includes(state.value))throw new Error('Acceptance is unresolved');invalidate();}
 function invalidate(){const generation=++epoch;flight=null;snapshot.value=null;if(generation!==epoch)return;ack.value=null;if(generation!==epoch)return;error.value=null;if(generation!==epoch)return;state.value='EMPTY';}
 return {state:readonly(state),snapshot:readonly(snapshot),ack:readonly(ack),error:readonly(error),prepare,restore,send,reconcileRejected,invalidate};
}
