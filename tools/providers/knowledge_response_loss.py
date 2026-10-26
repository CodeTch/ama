"""Owned test relay: loss/revocation and explicit exact-binding contradiction declarations.
Contradiction mode never changes the provider store or represents a real document effect.
"""
import argparse,concurrent.futures,hashlib,json,os,pathlib,resource,socket,threading,time,subprocess

def atomic(path,value):
 p=pathlib.Path(path);tmp=p.with_suffix('.tmp');data=json.dumps(value,sort_keys=True).encode()
 fd=os.open(tmp,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
 with os.fdopen(fd,'wb') as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
 os.replace(tmp,p)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);args=parser.parse_args();p=pathlib.Path(args.config)
 if p.is_symlink() or p.stat().st_mode&0o777!=0o600:raise RuntimeError('Private owned config required')
 cfg=json.loads(p.read_text());threading.stack_size(262144);resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864));cpus=sorted(os.sched_getaffinity(0));os.sched_setaffinity(0,set(cpus[:2]))
 state={'pid':os.getpid(),'pgid':os.getpgrp(),'port':cfg['port'],'upstream':cfg['upstream'],'postPublication':0,'blockedPublicationAttempts':0,'postAuthorization':0,'get':0,'withheld':False,'failures':0};lock=threading.Lock()
 def serve(incoming):
  try:
   with incoming:
    incoming.settimeout(5);head=bytearray()
    while not head.endswith(b'\r\n\r\n'):
     v=incoming.recv(1)
     if not v or len(head)>=16384:raise RuntimeError('Bounded header')
     head.extend(v)
    lines=bytes(head).decode('ascii').split('\r\n');method,path,_=lines[0].split(' ');length=0
    for line in lines[1:]:
     if line.lower().startswith('content-length:'):length=int(line.split(':',1)[1])
    if not 0<=length<=8454144:raise RuntimeError('Bounded body')
    body=bytearray()
    while len(body)<length:
     part=incoming.recv(min(65536,length-len(body)))
     if not part:raise RuntimeError('Short body')
     body.extend(part)
    if cfg.get('mode')=='CONTRADICT_OLD_SUCCESS' and method=='POST' and path=='/v1/knowledge/publications':
     with lock:state['blockedPublicationAttempts']+=1;atomic(cfg['state'],state)
     incoming.sendall(b'HTTP/1.1 409 Conflict\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}');return
    if cfg.get('mode','LOSS')=='REVOKE_BEFORE_PUBLISH' and method=='POST' and path=='/v1/knowledge/publications':
     # Operator fixture transition happens before the real POST, never inside application code.
     result=subprocess.run(['/usr/bin/python3',cfg['providerScript'],'--config',cfg['providerConfig'],'--grant-actor',cfg['actor'],'--grant-read'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=10)
     if result.returncode!=0:raise RuntimeError('Actual grant revocation failed')
    with socket.create_connection(('127.0.0.1',cfg['upstream']),timeout=5) as target:
     target.settimeout(5)
     forwarded=bytes(head).replace(('Host: 127.0.0.1:'+str(cfg['port'])).encode(),('Host: 127.0.0.1:'+str(cfg['upstream'])).encode());target.sendall(forwarded);target.sendall(body);response=bytearray()
     while True:
      part=target.recv(65536)
      if not part:break
      response.extend(part)
      if len(response)>8486912:raise RuntimeError('Bounded response')
    if cfg.get('mode')=='CONTRADICT_OLD_SUCCESS' and method=='GET' and path.endswith('/executions/'+cfg['execution']) and pathlib.Path(cfg['armFile']).is_file():
     if pathlib.Path(cfg['armFile']).read_text()!=cfg['execution']:raise RuntimeError('Exact armed execution required')
     h,raw=bytes(response).split(b'\r\n\r\n',1);original=json.loads(raw)
     if h.split(b' ')[1]!=b'200' or original.get('state')!='FAILED' or original.get('executionId')!=cfg['execution'] or not original.get('permanentRejection'):raise RuntimeError('Exact actual permanent rejection required')
     declaration=dict(original);declaration['state']='SUCCESS'
     for name in ('permanentRejection','noEffectBasis','errorCode'):declaration.pop(name,None)
     declaration['documentId']='doc-'+hashlib.sha256(('declaration-document:'+cfg['execution']).encode()).hexdigest()[:32]
     declaration['resultId']='result-'+hashlib.sha256(('declaration-result:'+cfg['execution']).encode()).hexdigest()[:32]
     encoded=json.dumps(declaration,sort_keys=True,separators=(',',':')).encode()
     response=b'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: '+str(len(encoded)).encode()+b'\r\nConnection: close\r\n\r\n'+encoded
     with lock:
      state['upstreamReceiptSha256']=hashlib.sha256(raw).hexdigest();state['declarationSha256']=hashlib.sha256(encoded).hexdigest();state['declaration']=declaration;state['realDocumentEffect']=False
    with lock:
     if method=='POST' and path=='/v1/knowledge/publications':
      state['postPublication']+=1
      if cfg.get('mode','LOSS')=='LOSS' and not state['withheld']:
       h,raw=bytes(response).split(b'\r\n\r\n',1);receipt=json.loads(raw)
       if h.split(b' ')[1]!=b'200' or receipt.get('state')!='SUCCESS':raise RuntimeError('Actual success required before loss')
       state['withheld']=True;state['receipt']=receipt;state['responseSha256']=hashlib.sha256(response).hexdigest();state['withheldUtc']=time.time();atomic(cfg['state'],state);return
      if cfg.get('mode')=='REVOKE_BEFORE_PUBLISH':
       h,raw=bytes(response).split(b'\r\n\r\n',1);state['receipt']=json.loads(raw);state['responseSha256']=hashlib.sha256(response).hexdigest()
     elif method=='POST':state['postAuthorization']+=1
     elif method=='GET':state['get']+=1
     atomic(cfg['state'],state)
    incoming.sendall(response)
  except Exception:
   with lock:state['failures']+=1;atomic(cfg['state'],state)
 listener=socket.socket();listener.bind(('127.0.0.1',cfg['port']));listener.listen(2);atomic(cfg['state'],state)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
  while True:incoming,_=listener.accept();executor.submit(serve,incoming)
if __name__=='__main__':main()
