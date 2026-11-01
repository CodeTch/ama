/** Cached meeting content has a bounded display lifetime while authority reads are renewed.
 * This never grants initial access and cannot resurrect content after a failed renewal. */
export function usePublicationReadLease(read:(signal:AbortSignal)=>Promise<unknown>,scope:()=>string,denied:(cause:unknown)=>void,timing={interval:2000,timeout:4500}){
 if(timing.interval<1||timing.timeout<=timing.interval||timing.timeout>4500)throw new TypeError('Bounded published display lease required');
 let epoch=0,tick:ReturnType<typeof setTimeout>|null=null,expiration:ReturnType<typeof setTimeout>|null=null,request:AbortController|null=null;
 function dispose(){epoch++;if(tick)clearTimeout(tick);if(expiration)clearTimeout(expiration);tick=null;expiration=null;request?.abort();request=null;}
 function start(){dispose();const turn=epoch,original=scope();let deadline=performance.now()+timing.timeout;const active=()=>turn===epoch&&scope()===original;
  function fail(cause:unknown){if(!active())return;dispose();denied(cause);}
  function expires(){if(expiration)clearTimeout(expiration);expiration=setTimeout(()=>fail(new Error('会议内容的访问状态暂无法核实，请重新读取。')),Math.max(0,deadline-performance.now()));}
  async function renew(){if(!active())return;const started=performance.now();request=new AbortController();const current=request;try{await read(current.signal);if(!active()||current.signal.aborted)return;if(performance.now()>=deadline){fail(new Error('会议内容的读取授权已过期。'));return;}deadline=started+timing.timeout;expires();tick=setTimeout(renew,Math.max(0,started+timing.interval-performance.now()));}catch(cause){fail(cause);}}
  expires();tick=setTimeout(renew,timing.interval);
 }
 return {start,dispose};
}
