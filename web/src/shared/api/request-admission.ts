/** Keep browser HTTP/1 connection capacity available for short display-authority reads.
 * A slot belongs to the whole response body, not just the response headers. */
export function requestAdmission(maximum=4){
 let active=0;
 const queue:Array<{signal?:AbortSignal|null;abort:()=>void;grant:()=>void}>=[];
 function advance(){while(active<maximum&&queue.length)queue.shift()!.grant();}
 function permit(){active++;let released=false;return ()=>{if(released)return;released=true;active--;advance();};}
 function acquire(signal?:AbortSignal|null):(()=>void)|Promise<()=>void>{
  signal?.throwIfAborted();
  if(active<maximum&&queue.length===0)return permit();
  return new Promise((resolve,reject)=>{
   const entry={signal,abort:()=>{},grant:()=>{}};
   entry.abort=()=>{const index=queue.indexOf(entry);if(index<0)return;queue.splice(index,1);signal?.removeEventListener('abort',entry.abort);reject(signal?.reason);};
   entry.grant=()=>{signal?.removeEventListener('abort',entry.abort);resolve(permit());};
   queue.push(entry);signal?.addEventListener('abort',entry.abort,{once:true});advance();
  });
 }
 return {acquire};
}
