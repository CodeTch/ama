"""Owned test relay: one durable provider success response is withheld; never generates receipts."""
import argparse,concurrent.futures,hashlib,json,os,pathlib,resource,socket,threading,time

def atomic(path,value):
 p=pathlib.Path(path);tmp=p.with_suffix('.tmp');data=json.dumps(value,sort_keys=True).encode()
 fd=os.open(tmp,os.O_CREAT|os.O_TRUNC|os.O_WRONLY,0o600)
 with os.fdopen(fd,'wb') as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
 os.replace(tmp,p)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);args=parser.parse_args();p=pathlib.Path(args.config)
 if p.is_symlink() or p.stat().st_mode&0o777!=0o600:raise RuntimeError('Private owned config required')
 cfg=json.loads(p.read_text());threading.stack_size(262144);resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864));cpus=sorted(os.sched_getaffinity(0));os.sched_setaffinity(0,set(cpus[:2]))
 state={'pid':os.getpid(),'pgid':os.getpgrp(),'port':cfg['port'],'upstream':cfg['upstream'],'postPublication':0,'postAuthorization':0,'get':0,'withheld':False,'failures':0};lock=threading.Lock()
 def serve(incoming):
  try:
   with incoming,socket.create_connection(('127.0.0.1',cfg['upstream']),timeout=5) as target:
    incoming.settimeout(5);target.settimeout(5);head=bytearray()
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
    forwarded=bytes(head).replace(('Host: 127.0.0.1:'+str(cfg['port'])).encode(),('Host: 127.0.0.1:'+str(cfg['upstream'])).encode());target.sendall(forwarded);target.sendall(body);response=bytearray()
    while True:
     part=target.recv(65536)
     if not part:break
     response.extend(part)
     if len(response)>8486912:raise RuntimeError('Bounded response')
    with lock:
     if method=='POST' and path=='/v1/knowledge/publications':
      state['postPublication']+=1
      if not state['withheld']:
       h,raw=bytes(response).split(b'\r\n\r\n',1);receipt=json.loads(raw)
       if h.split(b' ')[1]!=b'200' or receipt.get('state')!='SUCCESS':raise RuntimeError('Actual success required before loss')
       state['withheld']=True;state['receipt']=receipt;state['responseSha256']=hashlib.sha256(response).hexdigest();state['withheldUtc']=time.time();atomic(cfg['state'],state);return
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
