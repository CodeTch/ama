import {computed,shallowRef,watch} from 'vue';
import type {Libraries,Folders,createPublicationsApi} from '../../shared/api/publications';
import {destinationId} from '../../shared/api/publications';
import {ApiError} from '../../shared/api/client';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
type Library=Libraries['items'][number];
type Folder=Folders['items'][number];
export type DestinationSelection={providerId:string;libraryId:string;folderId:string;libraryName:string;folderName:string;path:Folder[];visibility?:string;description?:string};
type Draft={actor:string;meeting:string;providerId:string;libraryId:string;pathIds:string[]};
type Branch={page:Folders;expanded:boolean};
type Api=Pick<ReturnType<typeof createPublicationsApi>,'libraries'|'folders'>;
const branchKey=(library:string,parent:string)=>JSON.stringify([library,parent]);
export function validPublicationDestinationDraft(v:unknown,actor:string,meeting:string):v is Draft {
 if(typeof v!=='object'||v===null||Array.isArray(v))return false;const d=v as Record<string,unknown>;
 return Object.keys(d).sort().join(',')==='actor,libraryId,meeting,pathIds,providerId'&&d.actor===actor&&d.meeting===meeting&&typeof d.providerId==='string'&&/^[A-Za-z0-9_.-]{1,64}$/.test(d.providerId)&&destinationId(d.libraryId)&&Array.isArray(d.pathIds)&&d.pathIds.length<=128&&d.pathIds.every(destinationId)&&new Set(d.pathIds).size===d.pathIds.length;
}
/** Directory replies are the only source of selectable IDs and path names. A restored choice is revalidated before use. */
export function usePublicationDestinations(api:Api,actor:()=>string|null,meeting:()=>string,eligible:()=>boolean,locked:()=>boolean,onDenied:(cause:unknown)=>void,journals?:TabJournalContext){
 const libraries=shallowRef<Libraries|null>(null),branches=shallowRef<Record<string,Branch>>({}),selection=shallowRef<DestinationSelection|null>(null),error=shallowRef<unknown>(null),busy=shallowRef(false),pending=shallowRef<Draft|null>(null),storageMessage=shallowRef('');
 const journal=journals?createScopedPrivateJournal(journals,'publication-destination',validPublicationDestinationDraft):null;
 let queryText='';let epoch=0,controller:AbortController|null=null,disposed=false,lastRetry:(()=>Promise<void>)|null=null;
 const available=()=>!disposed&&!!actor()&&eligible()&&!locked();
 function clear(){epoch++;controller?.abort();controller=null;libraries.value=null;branches.value={};selection.value=null;pending.value=null;error.value=null;busy.value=false;storageMessage.value='';queryText='';lastRetry=null;journal?.synchronize(actor(),meeting());}
 const stop=watch(()=>[actor(),meeting(),eligible()],(current,previous)=>{if(!current[0]&&previous?.[0])journal?.clear();clear();},{flush:'sync'});
 async function read<T>(request:(signal:AbortSignal)=>Promise<T>,accept:(value:T)=>void):Promise<boolean>{
  if(!available())return false;const turn=++epoch,who=actor(),id=meeting();controller?.abort();const c=new AbortController();controller=c;busy.value=true;error.value=null;
  const active=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&eligible()&&!c.signal.aborted;
  try{const value=await request(c.signal);if(!active())return false;accept(value);return true;}catch(cause){if(active()){if(cause instanceof ApiError&&[401,403].includes(cause.status)){clear();onDenied(cause);}else error.value=cause;}return false;}finally{if(active())busy.value=false;}
 }
 function persist(){const s=selection.value,who=actor();if(!s||!who)return;pending.value=null;storageMessage.value='';if(journal&&journal.save(who,meeting(),{actor:who,meeting:meeting(),providerId:s.providerId,libraryId:s.libraryId,pathIds:s.path.map(f=>f.id)})!=='SAVED')storageMessage.value='当前选择未能在此标签页保存；刷新后请重新选择。';}
 async function loadLibraries(cursor:string|null=null,query=queryText){
  if(!available())return;const searching=query!==queryText;queryText=query;lastRetry=()=>loadLibraries(cursor,query);
  if(cursor===null){libraries.value=null;if(!searching){selection.value=null;branches.value={};}const who=actor();if(!searching&&who&&journal){const saved=journal.load(who,meeting());pending.value=saved.kind==='RESTORED'?saved.value:null;if(['INVALID','UNAVAILABLE'].includes(saved.kind))storageMessage.value='上次位置暂不可恢复，请重新选择。';}}
  const accepted=await read(s=>api.libraries(meeting(),cursor,s,query),page=>{
   const previous=cursor?libraries.value:null;if(previous&&previous.providerId!==page.providerId)throw new Error('知识库服务已变化，请重新读取目录。');
   const items=[...(previous?.items??[]),...page.items];if(new Set(items.map(x=>x.id)).size!==items.length)throw new Error('目录分页重复，请重新读取目录。');libraries.value={...page,items};
  });
  if(accepted&&!searching)await restore();
 }
 async function loadBranch(lib:Library,parent:string,cursor:string|null=null){
  if(!available()||!libraries.value?.items.some(x=>x.id===lib.id&&x.rootFolderId===lib.rootFolderId))return false;
  lastRetry=()=>loadBranch(lib,parent,cursor).then(()=>{});const key=branchKey(lib.id,parent);
  return read(s=>api.folders(meeting(),lib.id,parent,cursor,s),page=>{
   if(page.providerId!==libraries.value?.providerId)throw new Error('目录服务已变化，请重新读取知识库。');
   const previous=cursor?branches.value[key]?.page:null,items=[...(previous?.items??[]),...page.items];if(new Set(items.map(x=>x.id)).size!==items.length)throw new Error('文件夹分页重复，请重新读取目录。');
   branches.value={...branches.value,[key]:{page:{...page,items},expanded:true}};
  });
 }
 async function restore(){
  const draft=pending.value,lib=libraries.value?.items.find(x=>x.id===draft?.libraryId);if(!draft||!lib||draft.providerId!==libraries.value?.providerId)return;if(lib.canPublish===false){storageMessage.value=lib.unavailableReason||'当前知识库没有发布权限，请重新选择。';pending.value=null;return;}
  const path:Folder[]=[];let parent=lib.rootFolderId,requestsLeft=50;
  for(const id of draft.pathIds){
   if(--requestsLeft<0||!await loadBranch(lib,parent))return;let page=branches.value[branchKey(lib.id,parent)]?.page,item=page?.items.find(f=>f.id===id);const cursors=new Set<string>();
   while(!item&&page?.hasMore&&page.nextCursor){
    if(--requestsLeft<0||cursors.has(page.nextCursor)){storageMessage.value='已保存的位置尚未核实，请展开目录重新选择。';return;}
    cursors.add(page.nextCursor);if(!await loadBranch(lib,parent,page.nextCursor))return;page=branches.value[branchKey(lib.id,parent)]?.page;item=page?.items.find(f=>f.id===id);
   }
   if(!item){storageMessage.value='已保存的位置尚未核实，请展开目录重新选择。';return;}path.push(item);parent=item.id;
  }
  if(!available()||pending.value!==draft)return;
  selection.value={providerId:libraries.value!.providerId,libraryId:lib.id,libraryName:lib.name,visibility:lib.visibility,description:lib.description,folderId:path.at(-1)?.id??lib.rootFolderId,folderName:path.at(-1)?.name??'根目录',path};pending.value=null;
  if(!branches.value[branchKey(lib.id,lib.rootFolderId)])await loadBranch(lib,lib.rootFolderId);
 }
 async function selectLibrary(lib:Library){
  if(!available()||lib.canPublish===false||!libraries.value?.items.some(x=>x.id===lib.id&&x.rootFolderId===lib.rootFolderId))return;
  epoch++;controller?.abort();busy.value=false;error.value=null;branches.value={};selection.value={providerId:libraries.value.providerId,libraryId:lib.id,libraryName:lib.name,visibility:lib.visibility,description:lib.description,folderId:lib.rootFolderId,folderName:'根目录',path:[]};persist();await loadBranch(lib,lib.rootFolderId);
 }
 function selectFolder(lib:Library,item:Folder,path:Folder[]){
  if(!available()||selection.value?.libraryId!==lib.id)return;const parent=path.at(-1)?.id??lib.rootFolderId;
  if(path.some((entry,index)=>!branches.value[branchKey(lib.id,index?path[index-1]!.id:lib.rootFolderId)]?.page.items.includes(entry)))return;
  if(!branches.value[branchKey(lib.id,parent)]?.page.items.includes(item)||path.some(x=>x.id===item.id))return;
  selection.value={providerId:libraries.value!.providerId,libraryId:lib.id,libraryName:lib.name,visibility:lib.visibility,description:lib.description,folderId:item.id,folderName:item.name,path:[...path,item]};persist();
 }
 async function toggle(lib:Library,item:Folder){
  if(!available()||selection.value?.libraryId!==lib.id||!rows(lib).some(row=>row.folder===item))return;const key=branchKey(lib.id,item.id),current=branches.value[key];
  if(current){branches.value={...branches.value,[key]:{...current,expanded:!current.expanded}};return;}await loadBranch(lib,item.id);
 }
 function rows(lib:Library){const rows:{folder:Folder;path:Folder[];depth:number;expanded:boolean;knownEmpty:boolean}[]=[];
  function walk(parent:string,path:Folder[]){const branch=branches.value[branchKey(lib.id,parent)];if(!branch?.expanded)return;for(const folder of branch.page.items){if(path.some(x=>x.id===folder.id)||folder.id===lib.rootFolderId)continue;const child=branches.value[branchKey(lib.id,folder.id)];rows.push({folder,path,depth:path.length,expanded:!!child?.expanded,knownEmpty:!!child&&!child.page.items.length&&!child.page.hasMore});if(child?.expanded&&path.length<128)walk(folder.id,[...path,folder]);}}
  walk(lib.rootFolderId,[]);return rows;
 }
 async function restoreSaved(value:{providerId:string;libraryId:string;pathIds:string[]}){
  if(!actor()||!available())return;await loadLibraries(null,'');pending.value={actor:actor()!,meeting:meeting(),...value};let remaining=50;
  while(pending.value&&!libraries.value?.items.some(x=>x.id===value.libraryId)&&libraries.value?.hasMore&&libraries.value.nextCursor&&remaining-->0)await loadLibraries(libraries.value.nextCursor,'');
  if(pending.value)await restore();if(pending.value){storageMessage.value='已保存的位置当前不可用，请重新选择；其他配置已保留。';selection.value=null;}
 }
 const canSubmit=computed(()=>available()&&!!selection.value&&!busy.value&&!error.value);
 return{restoreSaved,libraries,branches,selection,error,busy,pending,storageMessage,canSubmit,loadLibraries,loadBranch,selectLibrary,selectFolder,toggle,rows,saveSelection(){if(!canSubmit.value)return false;persist();return !storageMessage.value;},branch:(lib:Library,parent:string)=>branches.value[branchKey(lib.id,parent)],retry:()=>lastRetry?.()??Promise.resolve(),clear,dispose(){disposed=true;stop();clear();}};
}
