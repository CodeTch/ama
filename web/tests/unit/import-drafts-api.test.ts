import assert from 'node:assert/strict';
import { setTimeout } from 'node:timers/promises';
import { test } from 'node:test';
import type { createBusinessClient } from '../../src/shared/api/business-client';
import { createImportDraftsApi } from '../../src/shared/api/import-drafts';
import { useCommand } from '../../src/shared/composables/useCommand';

test('a timed out discard preserves its original request for replay', async () => {
 const meetingId='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
 const calls: unknown[]=[];
 const client={
  async json(path:string,options:RequestInit){
   calls.push([path,options.method,options.body,new Headers(options.headers).get('Idempotency-Key')]);
   if(calls.length===1) await setTimeout(1_000,undefined,{signal:options.signal!});
   return {meetingId,state:'DISCARDED',revisionToken:'1'};
  },
 } as unknown as ReturnType<typeof createBusinessClient>;
 const api=createImportDraftsApi(client,()=> 'owner',10);
 const command=useCommand(api.send);
 command.prepare({actor:'owner',resource:meetingId,operation:'IMPORT_DRAFT_DISCARD',
  key:crypto.randomUUID(),path:`/api/v1/meetings/${meetingId}/import-discard`,body:{expectedRevisionToken:'0'}});
 await assert.rejects(command.send(),/等待超时/);
 assert.equal(command.state.value,'UNCERTAIN');
 assert.deepEqual(await command.send(),{meetingId,state:'DISCARDED',revisionToken:'1'});
 assert.deepEqual(calls,[calls[0],calls[0]]);
});
