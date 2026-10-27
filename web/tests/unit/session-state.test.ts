import {test} from 'node:test';
import assert from 'node:assert/strict';
import {useSession} from '../../src/shared/composables/useSession.js';
const response={actor:{enterpriseId:' Owner ',status:'ACTIVE',roles:['USER']},csrfToken:'C'.repeat(43),expiresAt:'2030-01-01T00:00:00Z'};
test('session accepts exact active actor and keeps CSRF in memory; logout clears before request',async()=>{
 const old=globalThis.fetch;const paths:string[]=[];let sessionReturn=response;
 globalThis.fetch=async(input,init)=>{paths.push(String(input));if(init?.method==='POST')return new Response(null,{status:204});return Response.json(sessionReturn);};
 try{const s=useSession('https://ama.test');await s.refresh();assert.equal(s.current.value?.actor.enterpriseId,' Owner ');assert.equal(s.status.value,'ACTIVE');const logout=s.logout();assert.equal(s.current.value,null);await logout;assert.equal(s.status.value,'UNAVAILABLE');assert.equal(paths.length,2);}
 finally{globalThis.fetch=old;}
});
test('late session load cannot revive invalidated actor; malformed/disabled sessions fail closed',async()=>{
 const old=globalThis.fetch;let release:(x:Response)=>void=()=>{};globalThis.fetch=()=>new Promise(resolve=>{release=resolve;});
 try{const s=useSession('https://ama.test');const pending=s.refresh();s.invalidate();release(Response.json(response));await assert.rejects(pending,{name:'AbortError'});assert.equal(s.current.value,null);
 for(const value of [{...response,actor:{...response.actor,status:'DISABLED'}},{...response,csrfToken:''},{...response,unexpected:'extra'}]){globalThis.fetch=async()=>Response.json(value);await assert.rejects(s.refresh());assert.equal(s.current.value,null);assert.equal(s.status.value,'UNAVAILABLE');}}
 finally{globalThis.fetch=old;}
});
test('actual401 clears session and notifies dependent state once;403 retains the same actor',async()=>{
 const old=globalThis.fetch;let code=200;globalThis.fetch=async()=>code===200?Response.json(response):new Response('{}',{status:code});
 try{const s=useSession('https://ama.test');await s.refresh();let changed=0;s.onInvalidate(()=>changed++);code=403;await assert.rejects(s.client.json('/api/v1/meetings'));assert.equal(s.current.value?.actor.enterpriseId,' Owner ');code=401;await assert.rejects(s.client.json('/api/v1/meetings'));assert.equal(s.current.value,null);assert.equal(changed,1);}
 finally{globalThis.fetch=old;}
});

test('explicit logout notifies journal cleanup even when the session is already unavailable',async()=>{
 const state=useSession('https://ama.test');let cleared=0;const stop=state.onLogout(()=>{assert.equal(state.current.value,null);cleared++;});await state.logout();assert.equal(cleared,1);stop();await state.logout();assert.equal(cleared,1);
});
