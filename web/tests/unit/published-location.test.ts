import {test} from 'node:test';import assert from 'node:assert/strict';import {ref} from 'vue';import {ApiError} from '../../src/shared/api/client';import {usePublishedLocation} from '../../src/features/minutes/usePublishedLocation';
const publication={publication:{publicationId:'publication',documentViewAvailability:'READY',officialDocumentUrl:'/bound-document'}} as any;
test('a late publication lookup cannot request or reveal the previous meeting library',async()=>{
 const actor=ref<string|null>('owner'),meeting=ref('one');let finish!:(v:any)=>void;let contexts=0;
 const view=usePublishedLocation({current:async()=>new Promise(resolve=>finish=resolve),context:async()=>{contexts++;return {} as any;}},()=>actor.value,()=>meeting.value);
 try{const pending=view.reload();meeting.value='two';finish(publication);await pending;assert.equal(contexts,0);assert.equal(view.value.value,null);}finally{view.dispose();}
});
test('current READ refusal clears a previously observed library name',async()=>{
 let refused=false;const view=usePublishedLocation({current:async()=>publication,context:async()=>{if(refused)throw new ApiError('http',403,'READ revoked');return{libraryName:'Authorized library'} as any;}},()=> 'owner',()=> 'meeting');
 const name=()=>view.value.value?.libraryName;
 try{await view.reload();assert.equal(name(),'Authorized library');refused=true;await view.reload();assert.equal(view.value.value,null);assert.ok(view.error.value);}finally{view.dispose();}
});
