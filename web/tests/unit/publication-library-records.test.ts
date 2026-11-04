import {test} from 'node:test';import assert from 'node:assert/strict';import {useMeetingLibrary} from '../../src/features/meetings/useMeetingLibrary';
test('library-only reader lists provider-authorized publications without acquiring meeting reads',async()=>{
 let meetingCalls=0;const row={meetingId:'00000000-0000-4000-8000-000000000001',publicationId:'00000000-0000-4000-8000-000000000002',meetingName:'冻结会议',documentId:'doc-'+'a'.repeat(32),canRead:true,canManage:false};
 const library=useMeetingLibrary({library:{records:async()=>({items:[row],hasMore:false,nextCursor:null,hasManagedLibraries:false})},meetings:{list:async()=>{meetingCalls++;throw new Error('Meeting ACL is independent');}},publications:{current:async()=>{throw new Error('No meeting scope');}}} as any,()=> 'kb-reader');
 await library.refresh();assert.equal(library.error.value,null);assert.equal(library.rows.value.length,1);assert.equal(library.rows.value[0]?.meeting.name,'冻结会议');assert.equal(meetingCalls,0);library.dispose();
});
test('switching record scope and identity discards late private results',async()=>{
 let actor='a',resolve:any;const library=useMeetingLibrary({library:{records:()=>new Promise(r=>resolve=r)}} as any,()=>actor);
 const pending=library.refresh();actor='b';library.invalidate();resolve({items:[{meetingId:'old'}],hasMore:false,nextCursor:null,hasManagedLibraries:true});await pending;assert.deepEqual(library.rows.value,[]);library.dispose();
});
