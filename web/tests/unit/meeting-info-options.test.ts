import {test} from 'node:test';import assert from 'node:assert/strict';
import {createMeetingOptionsApi} from '../../src/shared/api/meeting-options.js';
import {createBusinessClient,type Session} from '../../src/shared/api/business-client.js';
const session:Session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'csrf',expiresAt:'2099-01-01T00:00:00Z'};
test('real directory and category endpoints preserve stable identities and opaque page cursors',async()=>{
 const old=fetch,seen:string[]=[],api=createMeetingOptionsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));
 try{globalThis.fetch=async url=>{seen.push(String(url));return Response.json({items:[{stableId:'a',displayName:'同名',department:'甲',active:true},{stableId:'b',displayName:'同名',department:'乙',active:true}],nextCursor:'opaque'});};const page=await api.people('同名',null,new AbortController().signal);assert.deepEqual(page.items.map(x=>x.stableId),['a','b']);assert.match(seen[0]!,/\/api\/v1\/directory\/people\?/);
 globalThis.fetch=async url=>{seen.push(String(url));return Response.json({items:[{id:'category',label:'类别'}],nextCursor:null,hasMore:false});};assert.equal((await api.categories('cursor',new AbortController().signal)).items[0]!.id,'category');assert.match(seen[1]!,/cursor=cursor/);
 globalThis.fetch=async()=>Response.json({items:[{id:'x',label:'a'},{id:'x',label:'b'}],nextCursor:null,hasMore:false});await assert.rejects(api.categories(null,new AbortController().signal));
 globalThis.fetch=async()=>Response.json({items:[],nextCursor:null},{status:503});await assert.rejects(api.people('',null,new AbortController().signal));
 }finally{globalThis.fetch=old;}
});
