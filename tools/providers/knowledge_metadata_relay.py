"""Read-only C3 test relay: authenticated bytes unchanged, safe response metadata only."""
import argparse,concurrent.futures,json,os,pathlib,resource,socket,threading
from knowledge_response_loss import atomic

class HeaderReadFailure(Exception):
 def __init__(self,code,header_bytes):self.code=code;self.header_bytes=header_bytes

def read_header(incoming):
 head=bytearray()
 while not head.endswith(b'\r\n\r\n'):
  try:b=incoming.recv(1)
  except socket.timeout:raise HeaderReadFailure('HEADER_TIMEOUT',len(head)) from None
  except OSError:raise HeaderReadFailure('HEADER_IO_ERROR',len(head)) from None
  if not b:
   if not head:return None
   raise HeaderReadFailure('HEADER_EOF_PARTIAL',len(head))
  if len(head)>=16384:raise HeaderReadFailure('HEADER_BOUND',len(head))
  head.extend(b)
 return bytes(head)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);args=parser.parse_args();p=pathlib.Path(args.config)
 if p.is_symlink() or p.stat().st_mode&0o777!=0o600:raise RuntimeError('Private config required')
 cfg=json.loads(p.read_text());threading.stack_size(262144);resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864));os.sched_setaffinity(0,set(sorted(os.sched_getaffinity(0))[:2]));lock=threading.Lock()
 allowed={'/v1/knowledge/publications/d629b199-2b36-4ca8-aa4d-7fee86ecdf95/executions/d2c20bfe-d325-47d8-80c5-993ffbf03418','/v1/knowledge/publications/d629b199-2b36-4ca8-aa4d-7fee86ecdf95/executions/a9dede68-688b-4561-af31-818d4f3e9a35'}
 state={'pid':os.getpid(),'pgid':os.getpgrp(),'port':cfg['port'],'upstream':cfg['upstream'],'postPublication':0,'blockedPublicationAttempts':0,'postAuthorization':0,'get':0,'failures':0,'emptyPreRequestCloses':0,'failureDetails':[],'events':[]}
 def serve(incoming):
  phase='HEADER';header_bytes=0;body_bytes=0;response_bytes=0;upstream_connected=False
  try:
   with incoming:
    incoming.settimeout(5);head=read_header(incoming)
    if head is None:
     with lock:
      if len(state['events'])>=512:raise RuntimeError('Telemetry bound')
      state['emptyPreRequestCloses']+=1;state['events'].append({'kind':'EMPTY_PRE_REQUEST_CLOSE','phase':'HEADER','headerBytes':0,'bodyBytes':0,'upstreamConnected':False,'sequence':len(state['events'])+1});atomic(cfg['state'],state)
     return
    header_bytes=len(head);phase='REQUEST_PARSE'
    lines=bytes(head).decode('ascii').split('\r\n');method,path,_=lines[0].split(' ')
    if method=='POST' and path=='/v1/knowledge/publications':
     with lock:state['blockedPublicationAttempts']+=1;atomic(cfg['state'],state)
     incoming.sendall(b'HTTP/1.1 409 Conflict\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}');return
    if not(method=='GET' and path in allowed or method=='POST' and path=='/v1/knowledge/destination-authorizations'):raise RuntimeError('Exact allowed metadata route required')
    lengths=[int(x.split(':',1)[1]) for x in lines[1:] if x.lower().startswith('content-length:')]
    if len(lengths)!=1 or not 0<=lengths[0]<=8192:raise RuntimeError('Request length bound')
    phase='REQUEST_BODY';body=bytearray()
    while len(body)<lengths[0]:
     b=incoming.recv(lengths[0]-len(body))
     if not b:raise RuntimeError('Short request')
     body.extend(b);body_bytes=len(body)
    phase='UPSTREAM_CONNECT'
    with socket.create_connection(('127.0.0.1',cfg['upstream']),timeout=5) as target:
     upstream_connected=True;phase='UPSTREAM_EXCHANGE';target.settimeout(5);target.sendall(bytes(head).replace(('Host: 127.0.0.1:'+str(cfg['port'])).encode(),('Host: 127.0.0.1:'+str(cfg['upstream'])).encode()));target.sendall(body);response=bytearray()
     while True:
      b=target.recv(65536)
      if not b:break
      response.extend(b);response_bytes=len(response)
      if len(response)>98304:raise RuntimeError('Response bound')
    phase='RESPONSE_PARSE'
    h,raw=bytes(response).split(b'\r\n\r\n',1);headers=h.decode('ascii').split('\r\n');status=int(headers[0].split(' ')[1]);lengths=[int(x.split(':',1)[1]) for x in headers[1:] if x.lower().startswith('content-length:')]
    if len(lengths)!=1 or lengths[0]!=len(raw):raise RuntimeError('Exact complete upstream response required')
    event={'kind':'HTTP','method':method,'path':path,'status':status,'contentLength':lengths[0],'receivedBodyBytes':len(raw),'upstreamComplete':True,'forwarded':False}
    with lock:
     if len(state['events'])>=512:raise RuntimeError('Telemetry bound')
     event['sequence']=len(state['events'])+1;state['events'].append(event)
     if method=='GET':state['get']+=1
     else:state['postAuthorization']+=1
     atomic(cfg['state'],state)
    phase='RESPONSE_FORWARD';incoming.sendall(response)
    with lock:event['forwarded']=True;atomic(cfg['state'],state)
  except Exception as error:
   with lock:
    state['failures']+=1
    if len(state['failureDetails'])<16:state['failureDetails'].append({'phase':phase,'errorType':type(error).__name__,'headerFailureCode':error.code if isinstance(error,HeaderReadFailure) else 'NONE','headerBytes':error.header_bytes if isinstance(error,HeaderReadFailure) else header_bytes,'bodyBytes':body_bytes,'upstreamConnected':upstream_connected,'responseBytes':response_bytes})
    atomic(cfg['state'],state)
 listener=socket.socket();listener.bind(('127.0.0.1',cfg['port']));listener.listen(2);atomic(cfg['state'],state)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
  while True:incoming,_=listener.accept();executor.submit(serve,incoming)
if __name__=='__main__':main()
