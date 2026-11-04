import {shallowRef,shallowReadonly} from 'vue';
type Phase='information'|'roster'|'readiness'|'confirmation';
type Steps={validate:()=>boolean;information:()=>Promise<boolean>;roster:()=>Promise<boolean>;readiness:()=>Promise<boolean>;confirmation:()=>Promise<boolean>;complete:()=>void};

/** Coordinates existing durable commands; never replaces their keys or pretends earlier writes rolled back. */
export function useFinalConfirmation(steps:Steps){
 const busy=shallowRef(false),phase=shallowRef<Phase|null>(null),failedPhase=shallowRef<Phase|null>(null),error=shallowRef<unknown>(null);
 let flight:Promise<boolean>|null=null,epoch=0,disposed=false;
 function confirm():Promise<boolean>{
  if(disposed)return Promise.resolve(false);
  if(flight)return flight;
  let finish!:(result:boolean)=>void;
  const result=new Promise<boolean>(resolve=>finish=resolve),turn=epoch;
  flight=result;busy.value=true;failedPhase.value=null;error.value=null;
  const active=()=>!disposed&&turn===epoch;
  const run=async()=>{
   try{
    if(!steps.validate()||!active())return false;
    for(const next of ['information','roster','readiness','confirmation'] as const){
     phase.value=next;
     const accepted=await steps[next]();
     if(!active())return false;
     if(!accepted){failedPhase.value=next;return false;}
    }
    return true;
   }catch(cause){if(active()){error.value=cause;failedPhase.value=phase.value;}return false;}
   finally{if(active()){flight=null;phase.value=null;busy.value=false;}}
  };
  void run().then(value=>{if(value&&active()){try{steps.complete();}catch(cause){error.value=cause;finish(false);return;}}finish(value);},()=>finish(false));
  return result;
 }
 function invalidate(){epoch++;flight=null;busy.value=false;phase.value=null;failedPhase.value=null;error.value=null;}
 return {busy:shallowReadonly(busy),phase:shallowReadonly(phase),failedPhase:shallowReadonly(failedPhase),error:shallowReadonly(error),confirm,invalidate,dispose(){disposed=true;invalidate();}};
}
