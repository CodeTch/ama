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

test('directory department catalogue and exact department filter retain provider identity',async()=>{
 const old=fetch,seen:string[]=[],api=createMeetingOptionsApi(createBusinessClient('https://ama.test',()=>session,()=>{}));try{
 globalThis.fetch=async url=>{seen.push(String(url));return Response.json({items:[{id:'dep+%_甲',name:'完整目录部门',parentId:null,active:true},{id:'off',name:'停用部门',parentId:'dep+%_甲',active:false}]});};const catalogue=await api.departments(new AbortController().signal);assert.equal(catalogue.items[0]!.id,'dep+%_甲');assert.equal(catalogue.items[1]!.active,false);assert.match(seen[0]!,/\/api\/v1\/directory\/departments$/);assert(Object.isFrozen(catalogue.items));
 globalThis.fetch=async url=>{seen.push(String(url));return Response.json({items:[],nextCursor:null});};await api.people('同名','opaque',new AbortController().signal,'dep+%_甲');const url=new URL(seen[1]!);assert.equal(url.searchParams.get('departmentId'),'dep+%_甲');assert.equal(url.searchParams.get('cursor'),'opaque');
 globalThis.fetch=async()=>Response.json({items:[{id:'one',name:'部门',parentId:null,active:1}]});await assert.rejects(api.departments(new AbortController().signal));
 globalThis.fetch=async()=>Response.json({items:[{id:'one',name:'部门',parentId:null,active:true},{id:'one',name:'部门2',parentId:null,active:true}]});await assert.rejects(api.departments(new AbortController().signal));
 }finally{globalThis.fetch=old;}
});
