import {test} from 'node:test';import assert from 'node:assert/strict';
import {captureEvidenceAnchors,reanchorEvidence} from '../../src/features/minutes/minutes-anchors.js';
const evidenceId='00000000-0000-4000-8000-000000000001';
test('saved evidence anchors preserve Unicode scalars and require an unambiguous quote after edits',async()=>{
 const text='A😀证据Z',hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))),x=>x.toString(16).padStart(2,'0')).join('');
 const found=await captureEvidenceAnchors(text,[{id:evidenceId,target_anchor:JSON.stringify({schemaVersion:'ama.anchor.v1',unit:'UNICODE_SCALAR',start:1,end:4,targetTextSha256:hash})}]);assert.equal(found[0]?.quote,'😀证据');assert.deepEqual(reanchorEvidence('前'+text,found),[{evidenceId,startToken:'2',endToken:'5'}]);assert.throws(()=>reanchorEvidence(text+text,found));await assert.rejects(captureEvidenceAnchors(text,[{id:evidenceId,target_anchor:JSON.stringify({schemaVersion:'ama.anchor.v1',unit:'UNICODE_SCALAR',start:1,end:4,targetTextSha256:'0'.repeat(64)})}]));
});

test('manual offsets must still quote the selected Unicode text',()=>{
 const evidence=[{evidenceId,quote:'😀b',startToken:'1',endToken:'3'}],manual=new Map([[evidenceId,{startToken:'1',endToken:'3'}]]);
 assert.deepEqual(reanchorEvidence('A😀bc',evidence,manual),[{evidenceId,startToken:'1',endToken:'3'}]);
 assert.throws(()=>reanchorEvidence('XXA😀bc',evidence,manual));assert.throws(()=>reanchorEvidence('A😀Xc',evidence,manual));
});
