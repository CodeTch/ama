import {request,ApiError,type RequestOptions} from './client';
import type {components} from './generated';
export type Session=components['schemas']['Session'];

/** Only same-origin product routes. Cookies and Origin remain browser-owned; CSRF remains memory-only. */
export function createBusinessClient(origin:string,session:()=>Session|null,onUnauthorized:()=>void) {
 const base=new URL(origin);if(!['https:','http:'].includes(base.protocol)||base.username||base.password||base.pathname!=='/'||base.search||base.hash)throw new TypeError('Invalid application origin');
 return {
  async bytes(path:string,options:{signal:AbortSignal;size:number}):Promise<{bytes:ArrayBuffer;name:string|null}>{
   if(!path.startsWith('/api/v1/')||path.includes('\\')||path.includes('#')||!Number.isSafeInteger(options.size)||options.size<0||options.size>32*1024*1024)throw new TypeError('Bounded product source required');
   const target=new URL(path,base);if(target.origin!==base.origin||!target.pathname.startsWith('/api/v1/'))throw new TypeError('Same-origin product source required');
   const current=session();if(!current){onUnauthorized();throw new ApiError('http',401,'登录状态不可用，请重新登录。');}
   const active=()=>{if(options.signal.aborted||session()?.actor.enterpriseId!==current.actor.enterpriseId)throw new DOMException('Source scope changed','AbortError');};
   active();let response:Response;
   try{response=await fetch(target.href,{method:'GET',credentials:'include',redirect:'error',signal:options.signal,headers:{Accept:'application/octet-stream'}});}catch(error){active();if(error instanceof Error&&error.name==='AbortError')throw error;throw new ApiError('network',0,'原文件读取失败，请重试。');}
   try{
    active();if(response.status!==200){await response.body?.cancel();if(response.status===401&&session()===current)onUnauthorized();throw new ApiError('http',response.status,'原文件当前不可用，请重新读取会议。');}
    const length=response.headers.get('Content-Length');if(length!==null&&(!/^(0|[1-9][0-9]*)$/.test(length)||BigInt(length)!==BigInt(options.size)))throw new ApiError('response',200,'原文件大小不匹配。');
    if(!response.body)throw new ApiError('response',200,'原文件未完整返回。');const reader=response.body.getReader(),buffer=new Uint8Array(options.size);let used=0;
    try{while(true){const {done,value}=await reader.read();active();if(done)break;if(value.byteLength>options.size-used)throw new ApiError('response',200,'原文件超过读取限额。');buffer.set(value,used);used+=value.byteLength;}if(used!==options.size)throw new ApiError('response',200,'原文件未完整返回。');}catch(error){await reader.cancel().catch(()=>{});throw error;}finally{reader.releaseLock();}
    let name:string|null=null;const encoded=response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];if(encoded&&encoded.length<=1024){try{name=decodeURIComponent(encoded);}catch{/* No untrusted fallback header text. */}}
    active();return{bytes:buffer.buffer,name};
   }catch(error){if(response.body&&!response.body.locked)await response.body.cancel().catch(()=>{});throw error;}
  },
  async json<T>(path:string,options:RequestOptions={}):Promise<T> {
   if(!path.startsWith('/api/v1/')||path.includes('\\')||path.includes('#'))throw new TypeError('Product API path required');
   const target=new URL(path,base);if(target.origin!==base.origin||!target.pathname.startsWith('/api/v1/'))throw new TypeError('Same-origin product API required');
   const current=session(),method=(options.method??'GET').toUpperCase();const headers=new Headers(options.headers);
   for(const name of ['Authorization','Cookie','Origin'])if(headers.has(name))throw new TypeError('Browser-owned or unavailable credentials');
   if(!current&&!(method==='GET'&&target.pathname==='/api/v1/session')){onUnauthorized();throw new ApiError('http',401,'登录状态不可用，请重新登录。');}
   if(!['GET','HEAD','OPTIONS'].includes(method)) {
    if(!current){onUnauthorized();throw new ApiError('http',401,'登录状态不可用，请重新登录。');}
    headers.set('X-CSRF-Token',current.csrfToken);
   } else headers.delete('X-CSRF-Token');
   if(options.body!==undefined&&options.body!==null&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
   try {
    const value=await request<T>(target.href,{...options,method,headers});
    if(current&&session()?.actor.enterpriseId!==current.actor.enterpriseId)throw new DOMException('Session changed during read','AbortError');
    return value;
   }catch(error){if(error instanceof ApiError&&error.status===401&&session()===current)onUnauthorized();throw error;}
  }
 };
}
