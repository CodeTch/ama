import {request,ApiError,type RequestOptions} from './client';
import type {components} from './generated';
export type Session=components['schemas']['Session'];

/** Only same-origin product routes. Cookies and Origin remain browser-owned; CSRF remains memory-only. */
export function createBusinessClient(origin:string,session:()=>Session|null,onUnauthorized:()=>void) {
 const base=new URL(origin);if(!['https:','http:'].includes(base.protocol)||base.username||base.password||base.pathname!=='/'||base.search||base.hash)throw new TypeError('Invalid application origin');
 return {
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
