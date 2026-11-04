import {computed,shallowRef,shallowReadonly} from 'vue';import type {createMeetingLibraryApi,LibraryRecord} from '../../shared/api/meeting-library';import {ApiError} from '../../shared/api/client';
export type LibraryRow=Readonly<{meeting:{id:string;name:string};context:LibraryRecord}>;
type Dependencies={library:ReturnType<typeof createMeetingLibraryApi>};
/** One real provider-authorized page, independent of meeting ownership or participation. */
export function useMeetingLibrary(deps:Dependencies,actor:()=>string|null,view:()=> 'read'|'manage'=()=> 'read'){
 const rows=shallowRef<readonly LibraryRow[]>([]),loading=shallowRef(false),error=shallowRef<string|null>(null),issues=shallowRef<readonly string[]>([]),moreAvailable=shallowRef(true),hasManagedLibraries=shallowRef(false);let turn=0,request:AbortController|null=null,disposed=false,cursor:string|null=null;let seen=new Set<string>();
 function invalidate(){turn++;request?.abort();request=null;rows.value=[];issues.value=[];error.value=null;loading.value=false;cursor=null;seen=new Set();moreAvailable.value=true;hasManagedLibraries.value=false;}
 async function more(){if(disposed||loading.value||!actor()||!moreAvailable.value)return;const who=actor(),mode=view(),epoch=++turn,controller=new AbortController();request=controller;loading.value=true;error.value=null;const current=()=>!disposed&&epoch===turn&&actor()===who&&view()===mode&&!controller.signal.aborted;
  try{const result=await deps.library.records(mode,cursor,controller.signal);if(!current())return;
   if(result.hasMore&&(!result.nextCursor||result.nextCursor===cursor||seen.has(result.nextCursor)))throw new ApiError('response',200,'正式文档分页暂无法继续，请重新读取。');
   const unique=new Map(rows.value.map(row=>[row.context.documentId,row]));for(const context of result.items)unique.set(context.documentId,Object.freeze({meeting:{id:context.meetingId,name:context.meetingName},context}));rows.value=Object.freeze([...unique.values()]);hasManagedLibraries.value=result.hasManagedLibraries;if(result.nextCursor)seen.add(result.nextCursor);cursor=result.nextCursor;moreAvailable.value=result.hasMore;
  }catch(cause){if(current()){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){rows.value=[];hasManagedLibraries.value=false;}error.value=cause instanceof Error?cause.message:'正式文档暂无法读取，请重试。';controller.abort();}}
  finally{if(epoch===turn){loading.value=false;request=null;}}
 }
 async function refresh(){invalidate();await more();}function dispose(){invalidate();disposed=true;}
 return {rows:shallowReadonly(rows),loading:shallowReadonly(loading),error:shallowReadonly(error),issues:shallowReadonly(issues),hasManagedLibraries:shallowReadonly(hasManagedLibraries),hasMore:computed(()=>moreAvailable.value),refresh,more,invalidate,dispose};
}
