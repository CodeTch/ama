/** Small in-memory dictionary cache. Each subscriber owns its cancellation;
 * it never stores meeting content, commands or authority decisions. */
export function sharedRead(scope:()=>unknown,maximum=64){
 type Slot={controller:AbortController;promise:Promise<unknown>;users:number;settled:boolean;expires:number;value?:unknown};
 let identity=scope();const entries=new Map<string,Slot>();
 function clear(){for(const slot of entries.values())if(!slot.settled)slot.controller.abort();entries.clear();identity=scope();}
 function read<T>(key:string,signal:AbortSignal,run:(signal:AbortSignal)=>Promise<T>,ttl=30000):Promise<T>{
  signal.throwIfAborted();if(scope()!==identity)clear();const owner=identity;
  if(!owner)return run(signal);
  let slot=entries.get(key);
  if(slot?.settled&&slot.expires<=Date.now()){entries.delete(key);slot=undefined;}
  if(slot?.settled)return Promise.resolve(slot.value as T);
  if(!slot){
   if(entries.size>=maximum){for(const [id,entry] of entries){if(entry.settled){entries.delete(id);break;}}}
   // A full set of active reads is shared but never grows the retained cache.
   const controller=new AbortController();
   const created:Slot={controller,promise:Promise.resolve(),users:0,settled:false,expires:0};slot=created;
   created.promise=Promise.resolve().then(()=>{controller.signal.throwIfAborted();return run(controller.signal);}).then(value=>{
    controller.signal.throwIfAborted();if(scope()!==owner)throw new DOMException('Dictionary identity changed','AbortError');
    created.value=value;created.settled=true;created.expires=Date.now()+ttl;return value;
   },cause=>{if(entries.get(key)===created)entries.delete(key);throw cause;});
   if(entries.size<maximum)entries.set(key,created);
  }
  const current=slot;current.users++;
  return new Promise<T>((resolve,reject)=>{
   let finished=false;
   function done(){if(finished)return;finished=true;signal.removeEventListener('abort',abort);current.users--;if(!current.users&&!current.settled){current.controller.abort();if(entries.get(key)===current)entries.delete(key);}}
   function abort(){done();reject(signal.reason);}
   signal.addEventListener('abort',abort,{once:true});
   current.promise.then(value=>{if(finished)return;done();if(scope()!==owner){reject(new DOMException('Dictionary identity changed','AbortError'));return;}resolve(value as T);},cause=>{if(!finished){done();reject(cause);}});
  });
 }
 return {read,clear};
}
