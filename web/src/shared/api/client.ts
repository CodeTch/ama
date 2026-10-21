/** Transport boundary for future same-origin business APIs. No management health probe is called by the UI. */
export class ApiError extends Error {
 constructor(public readonly kind:'http'|'network'|'response', public readonly status:number, message:string) { super(message); this.name='ApiError'; }
}
export async function request<T>(path:string, init:RequestInit={}):Promise<T> {
 const headers=new Headers(init.headers);
 if(!headers.has('Accept')) headers.set('Accept','application/json');
 let response:Response;
 try { response=await fetch(path,{...init,headers,credentials:'include'}); }
 catch(error) {
  if(init.signal?.aborted || (error instanceof Error && error.name==='AbortError')) throw error;
  throw new ApiError('network',0,'网络连接不可用，请检查网络后重试。');
 }
 if(!response.ok) throw new ApiError('http',response.status,response.status===401?'登录状态不可用，请重新登录。':response.status===403?'暂无访问权限。':'服务暂不可用，请稍后重试。');
 if(response.status===204||response.status===205) return undefined as T;
 try {return await response.json() as T;}
 catch(error) {
  if(init.signal?.aborted || (error instanceof Error && error.name==='AbortError')) throw error;
  throw new ApiError('response',response.status,'服务响应格式异常，请稍后重试。');
 }
}
