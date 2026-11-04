import {test} from 'node:test';import assert from 'node:assert/strict';import {ref} from 'vue';
import {usePublicationDestinations} from '../../src/features/minutes/usePublicationDestinations.js';
import {ApiError} from '../../src/shared/api/client.js';
const meeting='00000000-0000-4000-8000-000000000001',a={id:'a',name:'同名知识库',rootFolderId:'ar'},b={id:'b',name:'同名知识库',rootFolderId:'br'};
const libs=(items:any[],nextCursor:string|null=null)=>({providerId:'kb',items,nextCursor,hasMore:nextCursor!==null});
const folders=(items:any[],nextCursor:string|null=null)=>({providerId:'kb',items,nextCursor,hasMore:nextCursor!==null});
const f=(id:string,parentFolderId:string,libraryId='a')=>({id,libraryId,parentFolderId,isRoot:false,name:'同名文件夹'});
const deferred=()=>{let resolve!:(value:any)=>void;const promise=new Promise<any>(r=>resolve=r);return{promise,resolve};};
test('readable library without publish permission stays visible but cannot become a target',async()=>{
 const disabled={...a,canPublish:false,description:'仅供查询',visibility:'项目成员',unavailableReason:'没有发布权限'};let folderReads=0;
 const d=usePublicationDestinations({libraries:async()=>libs([disabled]),folders:async()=>{folderReads++;return folders([]);}},()=> 'owner',()=>meeting,()=>true,()=>false,()=>{});
 await d.loadLibraries();await d.selectLibrary(disabled);assert.equal(d.libraries.value?.items.length,1);assert.equal(d.selection.value,null);assert.equal(d.canSubmit.value,false);assert.equal(folderReads,0);d.dispose();
});
test('provider search includes later pages and keeps the selected path while browsing no results',async()=>{
 const searches:string[]=[];const d=usePublicationDestinations({libraries:async(_m,_c,_s,search='')=>{searches.push(search);return libs(search?[]:[a]);},folders:async()=>folders([])},()=> 'owner',()=>meeting,()=>true,()=>false,()=>{});
 await d.loadLibraries();await d.selectLibrary(a);await d.loadLibraries(null,'说明关键词');assert.equal(searches.at(-1),'说明关键词');assert.equal(d.libraries.value?.items.length,0);assert.equal(d.selection.value?.libraryId,'a');assert.equal(d.canSubmit.value,true);d.dispose();
});
test('directory paging appends genuine stable IDs and a library switch clears the old folder before loading',async()=>{
 const gate=deferred();let slow=false;const api={libraries:async(_m:string,c:string|null)=>c?libs([b]):libs([a],'next'),folders:async()=>slow?gate.promise:folders([])};const d=usePublicationDestinations(api,()=> 'owner',()=>meeting,()=>true,()=>false,()=>{});
 await d.loadLibraries();await d.loadLibraries('next');assert.deepEqual(d.libraries.value?.items.map(x=>x.id),['a','b']);await d.selectLibrary(a);assert.equal(d.selection.value?.folderId,'ar');assert.equal(d.canSubmit.value,true);slow=true;const next=d.selectLibrary(b);assert.equal(d.selection.value?.folderId,'br');assert.equal(d.canSubmit.value,false);gate.resolve(folders([]));await next;assert.equal(d.canSubmit.value,true);d.dispose();
});
test('late folders from another library cannot leak into the new selection',async()=>{
 const gate=deferred();const d=usePublicationDestinations({libraries:async()=>libs([a,b]),folders:async(_m,lib)=>lib==='a'?gate.promise:folders([f('b-child','br','b')])},()=> 'owner',()=>meeting,()=>true,()=>false,()=>{});
 await d.loadLibraries();const first=d.selectLibrary(a);await d.selectLibrary(b);gate.resolve(folders([f('secret-a','ar')]));await first;assert.equal(d.selection.value?.libraryId,'b');assert.deepEqual(d.rows(b).map(x=>x.folder.id),['b-child']);assert.deepEqual(d.rows(a),[]);d.dispose();
});
test('nested stable folder paths survive expand/collapse and forged rows cannot be selected',async()=>{
 const top=f('top','ar'),child=f('child','top');const d=usePublicationDestinations({libraries:async()=>libs([a]),folders:async(_m,_l,parent)=>folders(parent==='ar'?[top]:parent==='top'?[child]:[])},()=> 'owner',()=>meeting,()=>true,()=>false,()=>{});
 await d.loadLibraries();await d.selectLibrary(a);await d.toggle(a,top);assert.equal(d.rows(a).length,2);d.selectFolder(a,child,[top]);assert.deepEqual(d.selection.value?.path.map(x=>x.id),['top','child']);d.selectFolder(a,{...child,id:'forged'},[top]);assert.equal(d.selection.value?.folderId,'child');await d.toggle(a,top);assert.equal(d.rows(a).length,1);assert.equal(d.selection.value?.folderId,'child');await d.selectLibrary(a);assert.deepEqual(d.selection.value?.path,[]);d.dispose();
});
test('failed directory is retryable, never an empty success, and current 403 clears all private state',async()=>{
 let failure=503,denied=0;const d=usePublicationDestinations({libraries:async()=>libs([a]),folders:async()=>{if(failure)throw new ApiError('http',failure,'directory');return folders([]);}},()=> 'owner',()=>meeting,()=>true,()=>false,()=>denied++);
 await d.loadLibraries();await d.selectLibrary(a);assert(d.error.value);assert.equal(d.canSubmit.value,false);failure=0;await d.retry();assert.equal(d.error.value,null);assert.equal(d.canSubmit.value,true);failure=403;await d.selectLibrary(a);assert.equal(denied,1);assert.equal(d.selection.value,null);assert.equal(d.libraries.value,null);assert.deepEqual(d.branches.value,{});d.dispose();
});
test('actual actor/meeting changes abort directory replies and erase cached names and selection',async()=>{
 const who=ref<string|null>('owner'),id=ref(meeting),gate=deferred();const d=usePublicationDestinations({libraries:async()=>libs([a]),folders:async()=>gate.promise},()=>who.value,()=>id.value,()=>true,()=>false,()=>{});
 await d.loadLibraries();const pending=d.selectLibrary(a);who.value='foreign';gate.resolve(folders([f('old','ar')]));await pending;assert.equal(d.selection.value,null);assert.equal(d.libraries.value,null);assert.equal(d.error.value,null);id.value='00000000-0000-4000-8000-000000000002';assert.deepEqual(d.branches.value,{});d.dispose();
});
test('reload restores a scoped choice only after fresh genuine path reads; removed folders cannot become targets',async()=>{
 const store=new Map<string,string>(),context:any={tab:'00000000-0000-4000-8000-000000000009',recovery:'RESUME_NAMESPACE',storage:{getItem:(k:string)=>store.get(k)??null,setItem:(k:string,v:string)=>store.set(k,v),removeItem:(k:string)=>store.delete(k)}};const top=f('top','ar');let removed=false,reads=0;const api={libraries:async()=>libs([a]),folders:async(_m:string,_l:string,parent:string|null)=>{reads++;return folders(parent==='ar'&&!removed?[top]:[]);}};
 const make=()=>usePublicationDestinations(api,()=> 'owner',()=>meeting,()=>true,()=>false,()=>{},context);const first=make();await first.loadLibraries();await first.selectLibrary(a);first.selectFolder(a,top,[]);first.dispose();const second=make();await second.loadLibraries();assert.equal(second.selection.value?.folderId,'top');assert(reads>=2);second.dispose();removed=true;const third=make();await third.loadLibraries();assert.equal(third.selection.value,null);assert.equal(third.canSubmit.value,false);assert.match(third.storageMessage.value,/尚未核实/);third.dispose();
});
test('restoring a choice follows actual folder cursors before accepting an item on a later page',async()=>{
 const store=new Map<string,string>(),context:any={tab:'00000000-0000-4000-8000-000000000009',recovery:'RESUME_NAMESPACE',storage:{getItem:(k:string)=>store.get(k)??null,setItem:(k:string,v:string)=>store.set(k,v),removeItem:(k:string)=>store.delete(k)}};const top=f('last-page','ar');let paged=false;const cursors:(string|null)[]=[];
 const api={libraries:async()=>libs([a]),folders:async(_m:string,_l:string,_p:string|null,cursor:string|null)=>{cursors.push(cursor);return paged&&!cursor?folders([],'page-two'):folders([top]);}};
 const make=()=>usePublicationDestinations(api,()=> 'owner',()=>meeting,()=>true,()=>false,()=>{},context);const first=make();await first.loadLibraries();await first.selectLibrary(a);first.selectFolder(a,top,[]);first.dispose();paged=true;const second=make();await second.loadLibraries();assert.equal(second.selection.value?.folderId,top.id);assert(cursors.includes('page-two'));second.dispose();
});
