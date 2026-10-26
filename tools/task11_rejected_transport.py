#!/usr/bin/env python3
"""Owned B6 GET-only delivery relay; no provider administration or body/credential logging."""
import re,json,os,resource,socket,sys,time,threading,signal

def forward_request(raw,expected_path,listen_port):
 """Validate the fixed empty GET and change only its unique next-hop Host field."""
 head,body=raw.split(b'\r\n\r\n',1);lines=head.split(b'\r\n')
 method,path,_=lines[0].decode('ascii').split(' ')
 if method!='GET' or path!=expected_path or body or not any(h.lower()==b'content-length: 0' for h in lines[1:]):
  raise ValueError('FIXED_GET_ONLY')
 hosts=[i for i,h in enumerate(lines[1:],1) if h.partition(b':')[0].lower()==b'host']
 if len(hosts)!=1:raise ValueError('HOST_CARDINALITY')
 index=hosts[0]
 if lines[index].partition(b':')[2].strip(b' \t')!=('127.0.0.1:'+str(listen_port)).encode('ascii'):
  raise ValueError('HOST_AUTHORITY')
 lines[index]=b'Host: 127.0.0.1:45519'
 return b'\r\n'.join(lines)+b'\r\n\r\n'

def upstream_status(response):
 """Return only a valid numeric status; never expose peer-supplied text."""
 if not response:raise ValueError('UPSTREAM_NO_STATUS')
 line,separator,_=response.partition(b'\r\n')
 match=re.fullmatch(rb'HTTP/1\.[01] ([1-5][0-9]{2}) [\t\x20-\x7e\x80-\xff]*',line)
 if not separator or match is None:raise ValueError('UPSTREAM_MALFORMED_STATUS')
 return int(match.group(1))

def delivery(x,mode,row):
 try:_delivery(x,mode,row)
 except (OSError,ValueError) as error:
  # Only internal fixed tokens enter diagnostics; never stringify arbitrary exceptions.
  reasons=('REQUEST_BOUND','FIXED_GET_ONLY','HOST_CARDINALITY','HOST_AUTHORITY','REQUEST_BUDGET',
           'UPSTREAM_RESPONSE_BOUND','UPSTREAM_NO_STATUS','UPSTREAM_MALFORMED_STATUS',
           'UPSTREAM_INCOMPLETE_HEADERS','UPSTREAM_NON_200')
  reason=error.args[0] if type(error) is ValueError and len(error.args)==1 else None
  row['safeFailureStage']=row['stage']
  row['safeFailureReason']=reason if reason in reasons else 'IO_ERROR' if isinstance(error,OSError) else 'VALUE_ERROR'
  row['safeFailureType']=type(error).__name__
  raise

def save():
 t=c['state']+'.tmp';open(t,'w').write(json.dumps(state));os.replace(t,c['state'])
def server():
 s=socket.socket();s.bind(('127.0.0.1',0));s.listen(2);s.settimeout(.2);return s
def sinkloop():
 while not stop.is_set():
  try:x,_=sink.accept();state['sinkRequests']+=1;x.close();save()
  except socket.timeout:pass
  except OSError:break
def halt(*_):
 stop.set()
 for x in active:
  try:x.shutdown(socket.SHUT_RDWR)
  except OSError:pass
  x.close()
 front.close();sink.close()
def send(x,b,row):
 x.sendall(b);row['bytesSent']+=len(b);state['bytesSent']+=len(b)
 if row.get('headerComplete'):row['bodyBytesSent']=row['bytesSent']-row['headerBytesSent']
def _delivery(x,mode,row):
 row['stage']='REQUEST_READ';raw=b''
 while b'\r\n\r\n' not in raw:
  part=x.recv(2048)
  if not part or len(raw)+len(part)>16384:raise ValueError('REQUEST_BOUND')
  raw+=part
 row['stage']='REQUEST_VALIDATE'
 try:forwarded=forward_request(raw,c['path'],state['port'])
 except ValueError:
  state['posts']+=int(raw.split(b' ',1)[0]==b'POST');raise
 state['requests']+=1;row['received']=True
 if state['requests']>c['maxRequests']:raise ValueError('REQUEST_BUDGET')
 row['stage']='UPSTREAM_CONNECT'
 with socket.create_connection(('127.0.0.1',45519),3) as up:
  up.settimeout(5);state['forwards']+=1;row['forwarded']=True;save()
  row['stage']='UPSTREAM_SEND';up.sendall(forwarded);response=b''
  row['stage']='UPSTREAM_READ'
  while True:
   b=up.recv(4096)
   if not b:break
   response+=b
   if len(response)>81920:raise ValueError('UPSTREAM_RESPONSE_BOUND')
 row['stage']='UPSTREAM_STATUS';row['upstreamStatus']=upstream_status(response)
 row['stage']='UPSTREAM_HEADERS'
 if b'\r\n\r\n' not in response:raise ValueError('UPSTREAM_INCOMPLETE_HEADERS')
 h,b=response.split(b'\r\n\r\n',1)
 row['stage']='UPSTREAM_STATUS'
 if row['upstreamStatus']!=200:raise ValueError('UPSTREAM_NON_200')
 row['upstream200']=True;row['stage']='MUTATE_RESPONSE'
 if mode=='HEADER_STALL':stop.wait(7);return
 headers=[];status='200 OK'
 if mode=='DECLARED_OVERSIZE':b=b'';headers=['Content-Length: 65537']
 elif mode=='ABSENT_LENGTH':headers=['X-Test: absent']
 elif mode=='DUP_LENGTH':headers=['Content-Length: '+str(len(b)),'Content-Length: '+str(len(b))]
 elif mode=='MALFORMED_LENGTH':headers=['Content-Length: +1']
 elif mode=='DUP_HEADER':headers=['Content-Length: '+str(len(b)),'X-Test: a','X-Test: b']
 elif mode=='TRANSFER_ENCODING':headers=['Content-Length: '+str(len(b)),'Transfer-Encoding: chunked']
 elif mode=='COMPRESSION':headers=['Content-Length: '+str(len(b)),'Content-Encoding: gzip']
 elif mode=='REDIRECT':status='302 Found';b=b'';headers=['Content-Length: 0','Location: http://127.0.0.1:'+str(state['sinkPort'])+'/forbidden']
 else:
  if mode=='DUP_JSON':b=b'{"state":"FAILED",'+b[1:]
  elif mode=='TRAILING_JSON':b+=b' {}'
  elif mode=='INVALID_UTF8':b=b'{"providerId":"\xff"}'
  elif mode=='LONE_SURROGATE':b=b'{"providerId":"\\ud800"}'
  elif mode=='NAME_BOUNDS':b=b'{"'+b'a'*129+b'":0}'
  elif mode in ('IDENTITY_MISMATCH','PROOF_MISMATCH'):
   obj=json.loads(b)
   if mode=='IDENTITY_MISMATCH':obj['storeInstanceId']='00000000-0000-0000-0000-000000000000'
   else:obj['payloadSha256']='0'*64
   b=json.dumps(obj,separators=(',',':')).encode()
  if mode=='ERROR_STALL':status='503 Unavailable';b=b'x'*100
  headers=['Content-Length: '+str(len(b))]
 row['stage']='DOWNSTREAM_SEND'
 send(x,('HTTP/1.1 '+status+'\r\nContent-Type: application/json\r\n'+'\r\n'.join(headers)+'\r\nConnection: close\r\n\r\n').encode(),row)
 row['headerBytesSent']=row['bytesSent'];row['headerComplete']=True;row['bodyBytesSent']=0
 if mode in ('BODY_STALL','ERROR_STALL','CANCEL','CLOSE_BLOCKED','INTERRUPT'):
  send(x,b[:1],row);save();stop.wait(7)
 elif mode=='SLOW_PROGRESS':
  for byte in b[:18]:send(x,bytes([byte]),row);save();stop.wait(1)
 elif mode=='TRUNCATED':send(x,b[:-1],row)
 else:send(x,b,row)

def main():
 global c,root,state,stop,active,front,sink,thread
 os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]);resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864))
 c=json.load(open(sys.argv[1]));assert c['upstreamPort']==45519 and c['maxRequests']==24;root=os.path.dirname(sys.argv[1]);state={'pid':os.getpid(),'requests':0,'forwards':0,'posts':0,'sinkRequests':0,'bytesSent':0,'cases':[]};stop=threading.Event();active=[]
 front=server();sink=server();state.update(port=front.getsockname()[1],sinkPort=sink.getsockname()[1]);save()
 signal.signal(signal.SIGTERM,halt);thread=threading.Thread(target=sinkloop);thread.start()
 try:
  while not stop.is_set():
   try:x,_=front.accept()
   except socket.timeout:continue
   except OSError:break
   row={'mode':json.load(open(c['mode']))['mode'],'received':False,'forwarded':False,'bytesSent':0};state['cases'].append(row);active.append(x);x.settimeout(5)
   try:delivery(x,row['mode'],row)
   except (OSError,ValueError) as e:row['safeFailureType']=type(e).__name__
   finally:x.close();active.remove(x);row['finished']=True;save()
 finally:
  halt();thread.join(3);state['sinkJoined']=not thread.is_alive();save()

if __name__=='__main__':main()
