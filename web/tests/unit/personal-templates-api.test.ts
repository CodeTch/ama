import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createPersonalTemplatesApi,PERSONAL_TEMPLATE_WORKSPACE,validPersonalTemplateCommand} from '../../src/shared/api/personal-templates.js';

const id='00000000-0000-4000-8000-000000000001',revision='00000000-0000-4000-8000-000000000002',key='00000000-0000-4000-8000-000000000003';
const template={id,revisionId:revision,revisionToken:'1',name:'例会😀',themes:['结论','行动'],deleted:false};
const create={actor:'owner',resource:PERSONAL_TEMPLATE_WORKSPACE,operation:'PERSONAL_TEMPLATE_CREATE',path:'/api/v1/templates',key,body:JSON.stringify({name:template.name,themes:template.themes})};

test('personal template send preserves original route bytes and key and binds the receipt to that command',async()=>{
 const calls:any[]=[];let response:any=template;
 const api=createPersonalTemplatesApi({json:async(path:string,options:any)=>{calls.push({path,...options});return response;}} as any,()=> 'owner');
 assert.equal(validPersonalTemplateCommand(create,'owner',PERSONAL_TEMPLATE_WORKSPACE),true);
 const saved=await api.send(create);assert.equal(calls[0].body,create.body);assert.equal(calls[0].headers['Idempotency-Key'],key);assert.equal(calls[0].expectedStatus,201);assert.equal(calls[0].method,'POST');assert.ok(Object.isFrozen(saved.themes));
 for(const invalid of [{...template,name:'另一模板'},{...template,themes:['其他主题']},{...template,revisionToken:'2'},{...template,deleted:true}]){response=invalid;await assert.rejects(api.send(create));}
 const edit={...create,resource:id,operation:'PERSONAL_TEMPLATE_UPDATE',path:`/api/v1/templates/${id}`,body:JSON.stringify({expectedRevisionToken:'1',name:template.name,themes:template.themes})};
 response={...template,revisionToken:'2'};await api.send(edit);assert.equal(calls.at(-1).method,'PUT');
 response={...template,revisionToken:'3',deleted:true};const removal={...edit,operation:'PERSONAL_TEMPLATE_DELETE',body:JSON.stringify({expectedRevisionToken:'2'})};await api.send(removal);assert.equal(calls.at(-1).method,'DELETE');
});
test('private recovery rejects foreign routes invalid revisions duplicate fields and theme bounds before transport',async()=>{
 let calls=0;const api=createPersonalTemplatesApi({json:async()=>{calls++;return template;}} as any,()=> 'owner');
 for(const changed of [{...create,actor:'foreign'},{...create,resource:id},{...create,path:'https://evil.invalid/api/v1/templates'},{...create,body:'{"name":"a","name":"b","themes":["c"]}'},{...create,body:JSON.stringify({name:'模板',themes:[]})},{...create,body:JSON.stringify({name:'模板',themes:Array(9).fill('主题')})},{...create,body:JSON.stringify({name:'模板',themes:['\ud800']})}]){
  assert.equal(validPersonalTemplateCommand(changed,'owner',PERSONAL_TEMPLATE_WORKSPACE),false);await assert.rejects(api.send(changed));
 }
 assert.equal(calls,0);
});
test('bounded personal pages and detail validate their private scope and do not release late identity responses',async()=>{
 let actor:string|null='owner',response:any={items:[template],nextCursor:id};const calls:any[]=[];
 const api=createPersonalTemplatesApi({json:async(path:string,options:any)=>{calls.push({path,...options});return response;}} as any,()=>actor);
 const page=await api.list(null,new AbortController().signal);assert.equal(page.items[0]?.id,id);assert.match(calls[0].path,/limit=20/);assert.equal(calls[0].body,undefined);
 for(const invalid of [{items:[template,template],nextCursor:null},{items:[{...template,deleted:true}],nextCursor:null},{items:[template],nextCursor:revision}]){response=invalid;await assert.rejects(api.list(null,new AbortController().signal));}
 response={...template,id:revision};await assert.rejects(api.read(id,new AbortController().signal));
 const late=createPersonalTemplatesApi({json:async()=>{actor='other';return template;}} as any,()=>actor);await assert.rejects(late.read(id,new AbortController().signal),{name:'AbortError'});
});
