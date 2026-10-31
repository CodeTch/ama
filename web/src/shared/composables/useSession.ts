import {shallowReadonly,shallowRef} from 'vue';
import {createBusinessClient,type Session} from '../api/business-client';
import {ApiError,request} from '../api/client';
function object(value:unknown):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value);}
function parseSession(value:unknown):Session {
 if(!object(value)||Object.keys(value).sort().join(',')!=='actor,csrfToken,expiresAt'||!object(value.actor)||Object.keys(value.actor).sort().join(',')!=='enterpriseId,roles,status')throw new ApiError('response',200,'登录响应不可用。');
 const a=value.actor;
 if(typeof a.enterpriseId!=='string'||!a.enterpriseId||[...a.enterpriseId].length>255||a.status!=='ACTIVE'||!Array.isArray(a.roles)||a.roles.length>2||a.roles.some(role=>role!=='USER'&&role!=='PLATFORM_ADMIN')||new Set(a.roles).size!==a.roles.length||typeof value.csrfToken!=='string'||!value.csrfToken||value.csrfToken.length>256||typeof value.expiresAt!=='string'||!Number.isFinite(Date.parse(value.expiresAt))||Date.parse(value.expiresAt)<=Date.now())throw new ApiError('response',200,'登录响应不可用。');
 const roles:Session['actor']['roles']=a.roles.map(role=>role==='USER'?'USER':'PLATFORM_ADMIN');Object.freeze(roles);
 return Object.freeze({actor:Object.freeze({enterpriseId:a.enterpriseId,status:'ACTIVE',roles}),csrfToken:value.csrfToken,expiresAt:value.expiresAt});
}
/** App-owned single instance; consumers subscribe to invalidation to clear sensitive state and stop polling. */
export function useSession(origin:string) {
 const current=shallowRef<Session|null>(null),status=shallowRef<'UNAVAILABLE'|'LOADING'|'ACTIVE'>('UNAVAILABLE'),error=shallowRef<unknown>(null);
 const listeners=new Set<()=>void>(),logoutListeners=new Set<()=>void>();let epoch=0,controller:AbortController|null=null;
 function invalidate(){epoch++;controller?.abort();controller=null;const had=current.value!==null;current.value=null;status.value='UNAVAILABLE';if(had)for(const listener of listeners)listener();}
 const client=createBusinessClient(origin,()=>current.value,invalidate);
 async function refresh(){invalidate();const generation=epoch;controller=new AbortController();const timeout=globalThis.setTimeout(()=>controller?.abort(),2500);status.value='LOADING';error.value=null;
  try{const value=await client.json<unknown>('/api/v1/session',{signal:controller.signal,maxResponseBytes:16384});if(generation!==epoch)throw new DOMException('Session load superseded','AbortError');current.value=parseSession(value);status.value='ACTIVE';return current.value;}
  catch(cause){if(generation!==epoch)throw new DOMException('Session load superseded','AbortError');current.value=null;status.value='UNAVAILABLE';error.value=cause;throw cause;}
  finally{globalThis.clearTimeout(timeout);if(generation===epoch)controller=null;}
 }
 async function logout(){const previous=current.value;invalidate();for(const listener of logoutListeners)listener();if(!previous)return;await request<void>(new URL('/api/v1/session/logout',origin).href,{method:'POST',headers:{'X-CSRF-Token':previous.csrfToken},maxResponseBytes:16384});}
 function onInvalidate(listener:()=>void){listeners.add(listener);return ()=>listeners.delete(listener);}
 function onLogout(listener:()=>void){logoutListeners.add(listener);return ()=>logoutListeners.delete(listener);}
 return {current:shallowReadonly(current),status:shallowReadonly(status),error:shallowReadonly(error),client,refresh,logout,invalidate,onInvalidate,onLogout};
}
