import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createPendingImportJournal } from '../../src/features/import/pending-import-journal';

test('deferred imports survive remount and remain isolated by meeting, account and request key', () => {
 const data = new Map<string,string>();
 let failWrite = false;
 const context = {
  tab: 'tab', recovery: 'RESUME_NAMESPACE' as const,
  storage: {
   getItem: (key:string) => data.get(key) ?? null,
   setItem: (key:string,value:string) => {
    if (failWrite) throw Error('Storage full');
    data.set(key,value);
   },
   removeItem: (key:string) => { data.delete(key); },
  },
 };
 const journal = createPendingImportJournal(context);
 const entry = (resource:string, actor='owner') => ({
  newImport: true, denied: false,
  command: {actor,resource,operation:'IMPORT_DRAFT_DISCARD',path:`/api/v1/meetings/${resource}/import-discard`,
   key:'534372be-b77a-4c4a-89b7-c592d7ec6b90',body:'{"expectedRevisionToken":"0"}'},
 });
 const first=entry('cd2e160e-6b11-4f4e-871e-bb8aa90ecdee');
 const second=entry('cd2e160e-6b11-4f4e-871e-bb8aa90ecdef');
 assert.equal(journal.put(first),true);
 failWrite=true;
 assert.equal(journal.put(second),false);
 assert.deepEqual(journal.list('owner'),[first]);
 failWrite=false;
 assert.equal(journal.put(second),true);
 const restored=createPendingImportJournal(context);
 assert.deepEqual(restored.list('other'),[]);
 assert.deepEqual(restored.get('owner',first.command.resource),first);
 assert.equal(restored.remove({...first.command,key:crypto.randomUUID()}),true);
 assert.deepEqual(restored.list('owner'),[first,second]);
 assert.equal(restored.remove(first.command),true);
 assert.deepEqual(restored.list('owner'),[second]);
});
