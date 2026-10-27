import {test} from 'node:test';import assert from 'node:assert/strict';import {shallowRef,watch} from 'vue';
import {createWorkspaceApi} from '../../src/shared/api/workspace.js';
import {createBusinessClient} from '../../src/shared/api/business-client.js';
import {useWorkspaceNavigation} from '../../src/features/meetings/useWorkspaceNavigation.js';
import type {components} from '../../src/shared/api/generated.js';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',other='00000000-0000-4000-8000-000000000000';
const navigation:components['schemas']['WorkspaceNavigation']={meetingId:id,revisionToken:'9007199254740993',accessRevisionToken:'7',stage:'ENTRY',relation:'OWNER',navigationState:'CHECK_PRODUCER',contentEligibility:'RECHECK_REQUIRED',saved:{infoRevisionId:other,rosterRevisionId:null,transcriptRevisionId:null,transcriptConfirmationId:null,minutesRevisionId:null,elementSetId:null}};
const session:components['schemas']['Session']={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('workspace facade uses real route, preserves exact nullable references and rejects inconsistent owner/member observations',async()=>{
 const old=fetch;let value:unknown=navigation,count=0;globalThis.fetch=async(url,options)=>{count++;assert.equal(String(url),`https://ama.test/api/v1/meetings/${id}/workspace`);assert.equal(options?.credentials,'include');return Response.json(value);};
 const api=createWorkspaceApi(createBusinessClient('https://ama.test',()=>session,()=>{}));
 try{const v=await api.navigation(id,new AbortController().signal);assert.equal(v.revisionToken,'9007199254740993');assert.equal(v.saved.rosterRevisionId,null);assert.ok(Object.isFrozen(v.saved));
 for(const bad of [{...navigation,meetingId:other},{...navigation,revisionToken:9007199254740992},{...navigation,relation:'MEMBER'},{...navigation,navigationState:'MEMBER_READ_ONLY'},{...navigation,contentEligibility:'READY'},{...navigation,saved:{...navigation.saved,infoRevisionId:null}},{...navigation,saved:{...navigation.saved,transcriptConfirmationId:other}},{...navigation,privateInput:other}]){value=bad;await assert.rejects(api.navigation(id,new AbortController().signal));}
 value={...navigation,relation:'MEMBER',navigationState:'MEMBER_READ_ONLY'};assert.equal((await api.navigation(id,new AbortController().signal)).relation,'MEMBER');const before=count;await assert.rejects(api.navigation('../other',new AbortController().signal));assert.equal(count,before);
 }finally{globalThis.fetch=old;}
});
test('workspace observation clears immediately on new read and ignores old owner data after route or session changes',async()=>{
 const actor=shallowRef<string|null>('owner'),meeting=shallowRef(id);const calls:Array<{signal:AbortSignal,resolve:(v:typeof navigation)=>void,reject:(e:unknown)=>void}>=[];
 const state=useWorkspaceNavigation((_id,signal)=>new Promise((resolve,reject)=>calls.push({signal,resolve,reject})),()=>actor.value,()=>meeting.value);
 const a=state.reload();meeting.value=other;assert.equal(calls[0]!.signal.aborted,true);const b=state.reload();calls[1]!.resolve({...navigation,meetingId:other,relation:'MEMBER',navigationState:'MEMBER_READ_ONLY'});await b;calls[0]!.resolve(navigation);await a;assert.equal(state.value.value?.relation,'MEMBER');
 const c=state.reload();assert.equal(state.value.value,null);calls[2]!.reject(new Error('denied'));await c;assert.equal(state.value.value,null);assert.ok(state.error.value);actor.value=null;assert.equal(state.error.value,null);state.dispose();await state.reload();assert.equal(calls.length,3);
});
test('synchronous workspace loading observer cannot send a stale request or clear the replacement loading state',async()=>{
 const actor=shallowRef<string|null>('old');const calls:Array<(v:typeof navigation)=>void>=[];const state=useWorkspaceNavigation(()=>new Promise(resolve=>calls.push(resolve)),()=>actor.value,()=>id);let newer:Promise<void>|undefined;
 const stop=watch(state.loading,v=>{if(v&&actor.value==='old'){actor.value='new';newer=state.reload();}},{flush:'sync'});
 await state.reload();assert.equal(calls.length,1);assert.equal(state.loading.value,true);calls[0]!(navigation);await newer;assert.equal(state.value.value?.relation,'OWNER');assert.equal(state.loading.value,false);stop();state.dispose();
});
