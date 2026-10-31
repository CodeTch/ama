import {computed,shallowRef,shallowReadonly} from 'vue';import type {createMeetingsApi,MeetingPage,MeetingScope} from '../../shared/api/meetings';import type {createPublicationsApi} from '../../shared/api/publications';import type {createMeetingLibraryApi,LibraryContext} from '../../shared/api/meeting-library';import {ApiError} from '../../shared/api/client';
export type LibraryRow=Readonly<{meeting:Pick<MeetingPage['items'][number],'id'|'name'>;context:LibraryContext}>;
type Dependencies={meetings:ReturnType<typeof createMeetingsApi>;publications:ReturnType<typeof createPublicationsApi>;library:ReturnType<typeof createMeetingLibraryApi>};
/** Each explicit read consumes one real page, with two admitted context requests and no inferred total. */
export function useMeetingLibrary(deps:Dependencies,actor:()=>string|null){
 const rows=shallowRef<readonly LibraryRow[]>([]),loading=shallowRef(false),error=shallowRef<string|null>(null),issues=shallowRef<readonly string[]>([]),moreAvailable=shallowRef(true);let turn=0,request:AbortController|null=null,disposed=false;
 type Scan={scope:MeetingScope;cursor:string|null;done:boolean;seen:Set<string>};const scans=():Scan[]=>[{scope:'owned',cursor:null,done:false,seen:new Set()},{scope:'joined',cursor:null,done:false,seen:new Set()}];let pages=scans();
 function invalidate(){turn++;request?.abort();request=null;rows.value=[];issues.value=[];error.value=null;loading.value=false;pages=scans();moreAvailable.value=true;}
 async function more(){if(disposed||loading.value||!actor())return;const page=pages.find(p=>!p.done);if(!page){moreAvailable.value=false;return;}const who=actor(),epoch=++turn,controller=new AbortController();request=controller;loading.value=true;error.value=null;const current=()=>!disposed&&epoch===turn&&actor()===who&&!controller.signal.aborted;
  try{
   const result=await deps.meetings.list(page.scope,page.cursor,controller.signal,{stages:['PUBLISHED']});if(!current())return;
   if(result.hasMore&&(!result.nextCursor||result.nextCursor===page.cursor||page.seen.has(result.nextCursor)))throw new ApiError('response',200,'会议库分页暂无法继续，请重新读取。');
   const admitted:LibraryRow[]=[],warnings:string[]=[];let position=0,transient:unknown=null;
   async function work(){while(current()){
    const meeting=result.items[position++];if(!meeting)return;
    try{const publication=(await deps.publications.current(meeting.id,controller.signal)).publication;if(!current())return;if(!publication||publication.documentViewAvailability!=='READY'||!publication.officialDocumentUrl){warnings.push('部分会议的正式成果索引尚未就绪，请稍后重新读取。');continue;}
     const context=await deps.library.context(meeting.id,publication.publicationId,controller.signal);if(current())admitted.push(Object.freeze({meeting,context}));
    }catch(cause){if(!current())return;if(cause instanceof ApiError&&[401,403,404].includes(cause.status??0)){if(cause.status===401)throw cause;warnings.push('部分正式成果当前不可访问，已隐藏相关条目。');}else transient??=cause;}
   }}
   await Promise.all([work(),work()]);if(!current())return;
   const unique=new Map(rows.value.map(row=>[row.meeting.id,row]));for(const meeting of result.items)unique.delete(meeting.id);for(const row of admitted)unique.set(row.meeting.id,row);rows.value=Object.freeze([...unique.values()]);issues.value=Object.freeze([...new Set([...issues.value,...warnings])]);
   if(transient)throw transient;
   if(result.nextCursor)page.seen.add(result.nextCursor);page.cursor=result.nextCursor;page.done=!result.hasMore;moreAvailable.value=pages.some(p=>!p.done);
  }catch(cause){if(current()){if(cause instanceof ApiError&&[401,403].includes(cause.status??0)){rows.value=[];issues.value=[];}error.value=cause instanceof Error?cause.message:'会议库暂无法读取，请重试。';controller.abort();}}
  finally{if(epoch===turn){loading.value=false;request=null;}}
 }
 async function refresh(){invalidate();await more();}function dispose(){invalidate();disposed=true;}
 return {rows:shallowReadonly(rows),loading:shallowReadonly(loading),error:shallowReadonly(error),issues:shallowReadonly(issues),hasMore:computed(()=>moreAvailable.value),refresh,more,invalidate,dispose};
}
