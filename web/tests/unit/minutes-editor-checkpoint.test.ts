import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createMinutesEditorJournal,type MinutesRecovery} from '../../src/features/minutes/minutes-editor-journal';
import type {TabJournalContext} from '../../src/shared/composables/tab-journal';
const actor='editor',meeting=crypto.randomUUID();
function fixture(){
 const values=new Map<string,string>(),writes:{key:string;size:number}[]=[];
 let fail=false;
 const context:TabJournalContext={tab:'isolated-editor',recovery:'NEW_NAMESPACE',storage:{
  getItem:key=>values.get(key)??null,removeItem:key=>{values.delete(key);},
  setItem:(key,v)=>{if(fail&&key.startsWith('ama.journal.v1'))throw Error('quota');writes.push({key,size:v.length});values.set(key,v);}
 }};
 const body='会议议题🙂'.repeat(6000),saved={title:'会议纪要',body};
 const record:MinutesRecovery={base:'1',target:{kind:'BODY',id:meeting},sourceId:meeting,saved,value:saved,dirty:false,anchors:[],pending:null};
 return {context,record,values,writes,fail:(value:boolean)=>{fail=value;}};
}
test('a large draft persists each Unicode edit synchronously and reopens the exact last acknowledged edit',()=>{
 const f=fixture(),j=createMinutesEditorJournal(f.context);
 assert.equal(j.save(actor,meeting,f.record),'SAVED');f.writes.length=0;
 const value={title:'人工标题',body:f.record.value.body.replace('🙂','🙃')+'。'};
 assert.equal(j.save(actor,meeting,{...f.record,value,dirty:true}),'SAVED');
 const latest={...f.record,value:{...value,body:f.record.value.body+'尾注'},dirty:true};
 assert.equal(j.save(actor,meeting,latest),'SAVED');assert(f.writes.at(-1)!.size<1024);
 assert.deepEqual(createMinutesEditorJournal(f.context).load(actor,meeting),{kind:'RESTORED',value:latest});
 f.fail(true);
 assert.equal(j.save(actor,meeting,{...latest,base:'2'}),'UNAVAILABLE');
 assert.deepEqual(createMinutesEditorJournal(f.context).load(actor,meeting),{kind:'RESTORED',value:latest});
 f.fail(false);const replacement={...latest,base:'2',value:{title:'第二版',body:f.record.saved.body}};
 assert.equal(j.save(actor,meeting,replacement),'SAVED');
 assert.deepEqual(createMinutesEditorJournal(f.context).load(actor,meeting),{kind:'RESTORED',value:replacement});
 assert(j.synchronize('another-user',meeting));assert.equal(f.values.size,0);
});
test('legacy drafts keep their original wire shape, and malformed incremental data fails closed',()=>{
 const f=fixture(),j=createMinutesEditorJournal(f.context);
 const small={...f.record,value:{title:'旧版',body:'文字'}};
 assert.equal(j.save(actor,meeting,small),'SAVED');assert.deepEqual(j.load(actor,meeting),{kind:'RESTORED',value:small});
 assert.equal(j.save(actor,meeting,f.record),'SAVED');
 assert.equal(j.save(actor,meeting,{...f.record,dirty:true}),'SAVED');
 const key=[...f.values.keys()].find(k=>k.startsWith('ama.journal.v1:')&&k.includes('"minutes-editor-change"'))!;
 const wire=JSON.parse(f.values.get(key)!);wire.value.end=f.record.value.body.length+1;f.values.set(key,JSON.stringify(wire));
 assert.deepEqual(createMinutesEditorJournal(f.context).load(actor,meeting),{kind:'INVALID'});
 assert(j.clear());assert.equal(f.values.size,0);
});
