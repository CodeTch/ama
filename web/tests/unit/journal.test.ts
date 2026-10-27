import {test} from 'node:test';import assert from 'node:assert/strict';
import {createJournal} from '../../src/shared/composables/journal.js';
import {useCommand} from '../../src/shared/composables/useCommand.js';
const scope={actor:' Owner ',meeting:'cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',kind:'minutes-body',tab:'tab-1'};
const valid=(x:unknown):x is {body:string;baseToken:string}=>typeof x==='object'&&x!==null&&Object.keys(x).sort().join(',')==='baseToken,body'&&'body' in x&&typeof x.body==='string'&&'baseToken' in x&&typeof x.baseToken==='string';
function memory(){const rows=new Map<string,string>();return {rows,getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}};}
test('journal is scoped to exact actor/meeting/kind/tab and never restores authority',()=>{
 const storage=memory(),a=createJournal(storage,scope,valid,4096);assert.equal(a.save({body:'draft',baseToken:'9007199254740993'}).kind,'SAVED');const loaded=a.load();assert.equal(loaded.kind,'RESTORED');if(loaded.kind==='RESTORED')assert.equal(loaded.value.baseToken,'9007199254740993');assert.equal(createJournal(storage,{...scope,actor:'owner'},valid,4096).load().kind,'EMPTY');assert.equal(createJournal(storage,{...scope,tab:'tab-2'},valid,4096).load().kind,'EMPTY');assert.ok(![...storage.rows.values()][0]!.includes('ACKNOWLEDGED'));a.clear();assert.equal(a.load().kind,'EMPTY');
});
test('untrusted oversized/foreign envelopes and storage failures are visible without replacing memory',()=>{
 const storage=memory(),a=createJournal(storage,scope,valid,512);a.save({body:'draft',baseToken:'1'});const key=[...storage.rows.keys()][0]!;const original=JSON.parse(storage.rows.get(key)!);storage.rows.set(key,JSON.stringify({...original,scope:{...scope,actor:'other'}}));assert.equal(a.load().kind,'INVALID');storage.rows.set(key,'x'.repeat(513));assert.equal(a.load().kind,'INVALID');
 const denied=createJournal({getItem(){throw new Error('private');},setItem(){throw new Error('quota');},removeItem(){throw new Error('private');}},scope,valid,4096);assert.equal(denied.load().kind,'UNAVAILABLE');assert.equal(denied.save({body:'still in memory',baseToken:'1'}).kind,'UNAVAILABLE');assert.equal(denied.clear().kind,'UNAVAILABLE');
});
test('restored uncertain command retries the exact serialized bytes with no persisted ACK permission',async()=>{
 const body='{ "expectedRevisionToken" : "9007199254740993" }';let sent='';const c=useCommand(async r=>{sent=r.body;return {};});c.restore({operation:'EDIT_INTENT',actor:scope.actor,resource:scope.meeting,path:`/api/v1/meetings/${scope.meeting}/minutes/editor-intents`,key:'original',body});assert.equal(c.state.value,'UNCERTAIN');assert.equal(c.ack.value,null);await c.send();assert.equal(sent,body);
});

test('restored command UTF8 bound is checked before parsing untrusted JSON',()=>{
 const c=useCommand(async()=>({}),24);const parse=JSON.parse;let parsed=false;JSON.parse=(...args:Parameters<typeof JSON.parse>)=>{parsed=true;return parse(...args);};
 try{assert.throws(()=>c.restore({operation:'EDIT_INTENT',actor:scope.actor,resource:scope.meeting,path:'/api/v1/meetings',key:'original',body:'{"x":"汉汉汉汉汉汉汉汉"}'}));assert.equal(parsed,false);}finally{JSON.parse=parse;}
});

test('journal projects structurally typed scope to its four persisted fields',()=>{
 const storage=memory(),extended={...scope,extra:true};const journal=createJournal(storage,extended,valid,4096);const draft={body:'recoverable',baseToken:'1'};
 assert.equal(journal.save(draft).kind,'SAVED');assert.deepEqual(journal.load(),{kind:'RESTORED',value:draft});assert.deepEqual(Object.keys(JSON.parse([...storage.rows.values()][0]!).scope).sort(),['actor','kind','meeting','tab']);
});
