import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { request, ApiError } from '../../src/shared/api/client.js';
let origin='';
const server=createServer((req,res)=>{
 if(req.url==='/ok'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({value:42,accept:req.headers.accept,custom:req.headers['x-example']}));}
 else if(req.url==='/slow'){res.writeHead(200,{'Content-Type':'application/json'});res.flushHeaders();setTimeout(()=>res.end('{}'),100);}
 else if(req.url==='/empty'){res.writeHead(204);res.end();}
 else if(req.url==='/bad-json'){res.setHeader('Content-Type','application/json');res.end('{broken');}
 else if(req.url==='/conflict'){res.writeHead(409,{'Content-Type':'application/json'});res.end(JSON.stringify({error:{code:'MEETING_CONFLICT',message:'private provider details',traceId:'00000000-0000-0000-0000-000000000001'}}));}
 else if(req.url==='/oversized'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({value:'x'.repeat(5000)}));}
 else if(req.url==='/redirect'){res.writeHead(302,{Location:'/ok'});res.end();}
 else if(req.url==='/private'){res.writeHead(401,{'Content-Type':'application/json'});res.end(JSON.stringify({message:'secret db password',code:'secret'}));}
 else {res.writeHead(503,{'Content-Type':'text/html'});res.end('<h1>private upstream stack</h1>');}
});
before(async()=>{await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));origin=`http://127.0.0.1:${(server.address() as any).port}`;});
after(()=>server.close());
test('JSON response preserves caller headers while sending JSON accept and cookie credentials',async()=>{
 const actualFetch=globalThis.fetch;let credentials: RequestCredentials|undefined;
 globalThis.fetch=(input,init)=>{credentials=init?.credentials;return actualFetch(input,init);};
 try {assert.deepEqual(await request(origin+'/ok',{headers:new Headers({'X-Example':'value'})}),{value:42,accept:'application/json',custom:'value'});assert.equal(credentials,'include');}
 finally{globalThis.fetch=actualFetch;}
});
test('empty successful response resolves without a JSON parse error',async()=>assert.equal(await request(origin+'/empty'),undefined));
test('unauthorized response returns a safe typed error without private backend messages',async()=>{
 await assert.rejects(request(origin+'/private'),(e:unknown)=>e instanceof ApiError&&e.status===401&&e.kind==='response'&&e.message==='登录状态不可用，请重新登录。');
});
test('non-JSON service error is normalized without leaking response body',async()=>{
 await assert.rejects(request(origin+'/down'),(e:unknown)=>e instanceof ApiError&&e.status===503&&e.message==='服务暂不可用，请稍后重试。');
});
test('malformed successful JSON rejects with a response error',async()=>{
 await assert.rejects(request(origin+'/bad-json'),(e:unknown)=>e instanceof ApiError&&e.kind==='response');
});
test('network failure is normalized for consumers',async()=>{
 await assert.rejects(request('http://127.0.0.1:1/unreachable'),(e:unknown)=>e instanceof ApiError&&e.kind==='network'&&e.status===0);
});
test('caller cancellation remains an AbortError',async()=>{
 const c=new AbortController();c.abort();await assert.rejects(request(origin+'/ok',{signal:c.signal}),{name:'AbortError'});
});

test('cancellation while reading the response body remains an AbortError',async()=>{
 const c=new AbortController();const pending=request(origin+'/slow',{signal:c.signal});
 const timer=setTimeout(()=>c.abort(),25);
 try{await assert.rejects(pending,{name:'AbortError'});}finally{clearTimeout(timer);}
});

test('conflict keeps only declared code and safe trace, never the backend message',async()=>{
 await assert.rejects(request(origin+'/conflict'),(error:unknown)=>error instanceof ApiError&&error.status===409&&error.code==='MEETING_CONFLICT'&&error.traceId==='00000000-0000-0000-0000-000000000001'&&!error.message.includes('private'));
});
test('successful responses have a bounded transport allocation',async()=>{
 await assert.rejects(request(origin+'/oversized',{maxResponseBytes:1024}),(error:unknown)=>error instanceof ApiError&&error.kind==='response');
});
test('redirects cannot move business transport to another endpoint',async()=>{
 await assert.rejects(request(origin+'/redirect'),(error:unknown)=>error instanceof ApiError&&error.kind==='network');
});

test('endpoint-specific success status rejects a different 2xx without treating it as ACK',async()=>{
 await assert.rejects(request(origin+'/ok',{expectedStatus:201}),(error:unknown)=>error instanceof ApiError&&error.kind==='response'&&error.status===200);
 await assert.rejects(request(origin+'/empty',{expectedStatus:200}),(error:unknown)=>error instanceof ApiError&&error.kind==='response');assert.equal(await request(origin+'/empty',{expectedStatus:204}),undefined);
});
