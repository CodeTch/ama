import {test} from 'node:test';import assert from 'node:assert/strict';
import {createApplication} from '../../src/application.js';
import {createScopedPrivateJournal} from '../../src/shared/composables/scoped-private-journal';
import {validInfoSuggestionCommand} from '../../src/shared/api/info-suggestions';
const id='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee';
const directory=()=>Response.json({items:[{stableId:'owner',displayName:'负责人',department:null,active:true}],nextCursor:null});
test('session hydration preserves a lost suggestion decision until route scope resolves; explicit exit clears it',async()=>{
 const rows=new Map<string,string>(),storage={getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}},old=fetch;
 const context={tab:'suggestion-application',storage,recovery:'RESUME_NAMESPACE' as const},original={actor:'owner',resource:id,operation:'INFO_SUGGESTION_DECIDE',path:`/api/v1/meetings/${id}/info-suggestions/${id}/decision`,key:'lost-ack-original',body:JSON.stringify({expectedRevisionToken:'7',decision:'ACCEPT'})};
 assert.equal(createScopedPrivateJournal(context,'info-suggestions',validInfoSuggestionCommand).save('owner',id,original),'SAVED');
 globalThis.fetch=async url=>String(url).includes('/directory/people')?directory():String(url).endsWith('/session')?Response.json({actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}):Response.json([]);
 const app=createApplication('https://ama.test',context);
 try{await app.session.refresh();app.setMeetingScope(id);const recovered=createScopedPrivateJournal(context,'info-suggestions',validInfoSuggestionCommand).load('owner',id);assert.equal(recovered.kind,'RESTORED');if(recovered.kind==='RESTORED')assert.deepEqual(recovered.value,original);app.setMeetingScope(null);assert.equal(createScopedPrivateJournal(context,'info-suggestions',validInfoSuggestionCommand).load('owner',id).kind,'EMPTY');}finally{app.dispose();globalThis.fetch=old;}
});
test('application binds actual session/client/create journal and clears it on explicit logout',async()=>{
 const rows=new Map<string,string>(),storage={getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}},old=fetch;
 const session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};let key='',replay=false;
 globalThis.fetch=async(url,options)=>{if(String(url).includes('/directory/people'))return directory();if(String(url).endsWith('/session'))return Response.json(session);if(String(url).endsWith('/logout'))return new Response(null,{status:204});assert.equal(String(url),'https://ama.test/api/v1/meetings');assert.equal(options?.method,'POST');assert.equal(new Headers(options?.headers).get('X-CSRF-Token'),session.csrfToken);assert.equal(options?.body,'{}');assert.equal([...rows.keys()].filter(k=>k.startsWith('ama.journal.v1:')).length,1);if(!replay){key=new Headers(options?.headers).get('Idempotency-Key')!;throw new Error('lost response');}assert.equal(new Headers(options?.headers).get('Idempotency-Key'),key);return Response.json({id,revision:0,revisionToken:'0',stage:'ENTRY'},{status:201});};
 const first=createApplication('https://ama.test',{tab:'tab-1',storage,recovery:'RESUME_NAMESPACE'});let second:ReturnType<typeof createApplication>|null=null;
 try{await first.session.refresh();await assert.rejects(first.create.submit());first.dispose();second=createApplication('https://ama.test',{tab:'tab-1',storage,recovery:'RESUME_NAMESPACE'});await second.session.refresh();assert.equal(second.create.command.state.value,'UNCERTAIN');assert.equal(second.create.command.ack.value,null);replay=true;assert.equal((await second.create.submit()).id,id);await second.session.logout();assert.equal(rows.size,0);assert.equal(second.create.command.snapshot.value,null);}finally{first.dispose();second?.dispose();globalThis.fetch=old;}
});

test('application creation discovery remains observational and clears on session invalidation',async()=>{
 const old=fetch,session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};let key='';
 globalThis.fetch=async(url,options)=>{if(String(url).includes('/directory/people'))return directory();if(String(url).endsWith('/session'))return Response.json(session);if(String(url).endsWith('/command-discovery')){assert.deepEqual(JSON.parse(String(options?.body)),{operation:'CREATE',resource:'meetings',key});return Response.json({status:'FOUND',association:{operation:'CREATE',meetingId:id,resultKind:'INFO_REVISION',resultId:id,acceptedRevisionToken:'0',originalHttpStatus:201},current:{meetingId:id,revisionToken:'9',accessRevisionToken:'0',stage:'ENTRY'}});}key=new Headers(options?.headers).get('Idempotency-Key')!;throw new Error('lost');};
 const app=createApplication('https://ama.test');try{await app.session.refresh();await assert.rejects(app.create.submit());await app.createDiscovery.reload();assert.equal(app.createDiscovery.result.value?.status,'FOUND');assert.equal(app.create.command.state.value,'UNCERTAIN');assert.equal(app.create.command.ack.value,null);app.session.invalidate();assert.equal(app.createDiscovery.result.value,null);}finally{app.dispose();globalThis.fetch=old;}
});

test('application binds workspace navigation and tasks to the active cookie session transport',async()=>{
 const old=fetch,session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};const paths:string[]=[];
 globalThis.fetch=async(url,options)=>{const path=new URL(String(url)).pathname;paths.push(path);assert.equal(options?.credentials,'include');if(path==='/api/v1/directory/people')return directory();if(path.endsWith('/session'))return Response.json(session);if(path.endsWith('/workspace'))return Response.json({meetingId:id,revisionToken:'9',accessRevisionToken:'2',stage:'ENTRY',relation:'OWNER',navigationState:'CHECK_PRODUCER',contentEligibility:'RECHECK_REQUIRED',saved:{infoRevisionId:id,rosterRevisionId:null,transcriptRevisionId:null,transcriptConfirmationId:null,minutesRevisionId:null,elementSetId:null}});assert.ok(path.endsWith('/tasks'));return Response.json({items:[],hasMore:false,nextCursor:null,verification:'PERSISTED_RELATION'});};
 const app=createApplication('https://ama.test');try{await app.session.refresh();assert.equal((await app.workspace.navigation(id,new AbortController().signal)).relation,'OWNER');assert.equal((await app.tasks.list(id,'ALL',null,new AbortController().signal)).verification,'PERSISTED_RELATION');assert.deepEqual(paths,['/api/v1/session','/api/v1/directory/people',`/api/v1/meetings/${id}/workspace`,`/api/v1/meetings/${id}/tasks`]);}finally{app.dispose();globalThis.fetch=old;}
});
test('application restores the original personal template command and clears its private journal on logout',async()=>{
 const rows=new Map<string,string>(),storage={getItem:(key:string)=>rows.get(key)??null,setItem:(key:string,value:string)=>{rows.set(key,value);},removeItem:(key:string)=>{rows.delete(key);}},old=fetch;
 const session={actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
 const result={id,revisionId:id,revisionToken:'1',name:'原模板',themes:['决定'],deleted:false};let originalBody:BodyInit|null|undefined,originalKey='',retry=false;
 globalThis.fetch=async(url,options)=>{const target=new URL(String(url));if(target.pathname==='/api/v1/directory/people')return directory();if(target.pathname.endsWith('/session'))return Response.json(session);if(target.pathname.endsWith('/logout'))return new Response(null,{status:204});if(target.pathname.endsWith('/topics'))return Response.json([]);
  assert.equal(target.pathname,'/api/v1/templates');if((options?.method??'GET')==='GET')return Response.json({items:[result],nextCursor:null});
  assert.equal(new Headers(options?.headers).get('X-CSRF-Token'),session.csrfToken);if(!retry){originalBody=options?.body;originalKey=new Headers(options?.headers).get('Idempotency-Key')!;throw Error('lost template ACK');}
  assert.equal(options?.body,originalBody);assert.equal(new Headers(options?.headers).get('Idempotency-Key'),originalKey);return Response.json(result,{status:201});
 };
 const context={tab:'template-application',storage,recovery:'RESUME_NAMESPACE' as const};const first=createApplication('https://ama.test',context);let next:ReturnType<typeof createApplication>|null=null;
 try{await first.session.refresh();await assert.rejects(first.personalTemplateCommands.create('原模板',['决定']));first.dispose();next=createApplication('https://ama.test',context);await next.session.refresh();assert.equal(next.personalTemplateCommands.command.state.value,'UNCERTAIN');retry=true;await next.personalTemplateCommands.retry();assert.equal(next.personalTemplateCommands.pending.value,false);await next.session.logout();assert.equal(rows.size,0);assert.equal(next.personalTemplateCommands.original.value,null);}finally{first.dispose();next?.dispose();globalThis.fetch=old;}
});

test('recording import original command survives session hydration before the meeting route resolves and logout clears it',async()=>{
 const {validRecordingImportCommand}=await import('../../src/features/recording/useRecordingTranscription');
 const rows=new Map<string,string>(),storage={getItem:(k:string)=>rows.get(k)??null,setItem:(k:string,v:string)=>{rows.set(k,v);},removeItem:(k:string)=>{rows.delete(k);}},old=fetch;
 const context={tab:'recording-import-application',storage,recovery:'RESUME_NAMESPACE' as const};
 const original={actor:'owner',resource:id,operation:'TRANSCRIPT_IMPORT',path:`/api/v1/meetings/${id}/transcript-imports`,key:id,body:JSON.stringify({expectedRevisionToken:'17',orderedFileIds:[id]})};
 const journal=()=>createScopedPrivateJournal(context,'recording-transcript-import',validRecordingImportCommand);
 assert.equal(journal().save('owner',id,original),'SAVED');
 globalThis.fetch=async url=>String(url).includes('/directory/people')?directory():String(url).endsWith('/session')?Response.json({actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}):String(url).endsWith('/logout')?new Response(null,{status:204}):Response.json([]);
 const app=createApplication('https://ama.test',context);
 try{await app.session.refresh();let result=journal().load('owner',id);assert.equal(result.kind,'RESTORED');app.setMeetingScope(id);result=journal().load('owner',id);assert.equal(result.kind,'RESTORED');if(result.kind==='RESTORED')assert.deepEqual(result.value,original);await app.session.logout();assert.equal(journal().load('owner',id).kind,'EMPTY');}finally{app.dispose();globalThis.fetch=old;}
});

test('sensitive recovery is cleared by application logout even when its component is unmounted',async()=>{
 const rows=new Map<string,string>(),storage={getItem:(k:string)=>rows.get(k)??null,setItem:(k:string,v:string)=>{rows.set(k,v);},removeItem:(k:string)=>{rows.delete(k);}},old=fetch;
 const context={tab:'sensitive-application',storage,recovery:'RESUME_NAMESPACE' as const};
 const seed=()=>createScopedPrivateJournal(context,'minutes-sensitive',(v):v is {private:string}=>typeof v==='object'&&v!==null&&'private' in v);
 assert.equal(seed().save('owner',id,{private:'排除选择的恢复数据'}),'SAVED');
 globalThis.fetch=async url=>String(url).includes('/directory/people')?directory():String(url).endsWith('/session')?Response.json({actor:{enterpriseId:'owner',status:'ACTIVE',roles:['USER']},csrfToken:'X'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'}):String(url).endsWith('/logout')?new Response(null,{status:204}):Response.json([]);
 const app=createApplication('https://ama.test',context);try{await app.session.refresh();assert.equal(seed().load('owner',id).kind,'RESTORED');await app.session.logout();assert.equal(seed().load('owner',id).kind,'EMPTY');}finally{app.dispose();globalThis.fetch=old;}
});
