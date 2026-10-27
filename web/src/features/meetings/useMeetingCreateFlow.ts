import {shallowReadonly,shallowRef,watch} from 'vue';
import type {useMeetingCreate} from './useMeetingCreate';
/** Navigation follows only this tab's original create ACK. Lookup observations cannot enter this flow. */
export function useMeetingCreateFlow(create:ReturnType<typeof useMeetingCreate>,actor:()=>string|null,navigate:(meeting:string)=>Promise<boolean>){
 const busy=shallowRef(false),error=shallowRef<unknown>(null);
 let epoch=0,flight:Promise<void>|null=null,disposed=false,navigating=false;
 function invalidate(){const generation=++epoch;flight=null;navigating=false;error.value=null;if(generation===epoch)busy.value=false;}
 const stop=watch(actor,invalidate,{flush:'sync'});
 function open():Promise<void>{
  if(disposed)return Promise.resolve();if(flight)return flight;const who=actor();if(!who)return Promise.resolve();const generation=++epoch;
  const sameScope=()=>generation===epoch&&actor()===who;
  const active=()=>!disposed&&sameScope();
  const run=async()=>{
   try{
    if(!active())return;error.value=null;if(!active())return;busy.value=true;if(!active())return;
    const request=create.submit(),key=create.command.snapshot.value?.key;const result=await request;if(!active())return;
    const acknowledged=()=>create.command.state.value==='ACKNOWLEDGED'&&create.command.snapshot.value?.key===key&&create.command.snapshot.value?.actor===who&&create.command.ack.value?.id===result.id;
    if(!acknowledged())throw new Error('Original creation acknowledgment changed');
    navigating=true;const arrived=await navigate(result.id);
    // A successful router navigation unmounts the source page. Its original ACK may still be cleared,
    // but only after arrival is verified and while the same actor/command remain current.
    if(!sameScope()||!acknowledged())return;
    if(!arrived)throw new Error('Meeting navigation did not complete');
    if(!create.navigationComplete())throw new Error('Creation recovery record could not be cleared');
   }catch(cause){if(sameScope())error.value=cause;}
   finally{if(sameScope()){navigating=false;busy.value=false;}if(disposed)stop();}
  };
  let finish!:()=>void;const pending=new Promise<void>(resolve=>{finish=resolve;});flight=pending;void pending.then(()=>{if(generation===epoch)flight=null;});void run().then(finish,finish);return pending;
 }
 function dispose(){disposed=true;if(!navigating){invalidate();stop();}}
 return {busy:shallowReadonly(busy),error:shallowReadonly(error),open,dispose};
}
