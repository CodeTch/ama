import {test} from 'node:test';import assert from 'node:assert/strict';
import {createTabJournalContext} from '../../src/shared/composables/tab-journal.js';
function storage(){const rows=new Map<string,string>();return {rows,getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}};}
test('only same-tab reload reuses journal identity; fresh navigation gets a new identity',()=>{
 const store=storage(),first=createTabJournalContext(()=>store,'navigate'),reload=createTabJournalContext(()=>store,'reload');assert.equal(reload.tab,first.tab);assert.equal(reload.storage,store);const duplicate=createTabJournalContext(()=>store,'navigate');assert.notEqual(duplicate.tab,first.tab);
});
test('denied sessionStorage or malformed tab identity never pretends recovery is available',()=>{
 const denied=createTabJournalContext(()=>{throw new Error('denied');},'reload');assert.equal(denied.storage,null);assert.equal(denied.recovery,'UNAVAILABLE');
 const store=storage();store.rows.set('ama.tab.v1','x'.repeat(4096));const result=createTabJournalContext(()=>store,'reload');assert.notEqual(result.tab,'x'.repeat(4096));assert.match(result.tab,/^[a-f0-9-]{36}$/);assert.equal(result.recovery,'UNAVAILABLE');
});

test('readable valid reload namespace survives write-only storage failure',()=>{
 const store=storage(),first=createTabJournalContext(()=>store,'navigate');const next=createTabJournalContext(()=>({...store,setItem(){throw new Error('quota');}}),'reload');assert.equal(next.tab,first.tab);assert.notEqual(next.storage,null);
});
