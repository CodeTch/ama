"""Owned loopback HTTPS front door for the actual API plus built frontend. No mock responses."""
import bl5_monitor
from contextlib import closing
import argparse, functools, fcntl, os, threading, http.client, http.server, ipaddress, json, mimetypes, pathlib, ssl, urllib.parse
MAXIMUM=16*1024*1024
BUDGET_LOCK=threading.Lock()
def consume(root,name,amount,maximum):
 if amount<0:raise ValueError('Invalid amount')
 bl5_monitor.admitted(root)
 path=root/('budget-'+name)
 if path.is_symlink():raise ValueError('Foreign counter')
 with BUDGET_LOCK:
  fd=os.open(path,os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
  with os.fdopen(fd,'r+') as f:
   fcntl.lockf(f,fcntl.LOCK_EX);bl5_monitor.admitted(root);raw=f.read(32);count=int(raw or '0')
   if count<0 or count+amount>maximum:
    try:
     fd=os.open(root/'stopping.json',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
     with os.fdopen(fd,'w') as stopped:json.dump({'owner':root.name,'reason':'BUDGET_'+name},stopped)
    except FileExistsError:pass
    raise ValueError('B-L5 request budget exhausted')
   f.seek(0);f.truncate();f.write(str(count+amount));f.flush();os.fsync(f.fileno())
HOP={'connection','keep-alive','proxy-authenticate','proxy-authorization','te','trailer','transfer-encoding','upgrade','host'}
def asset_path(root,path):
 parsed=urllib.parse.urlsplit(path)
 if parsed.scheme or parsed.netloc or '\\' in parsed.path:raise ValueError('Local relative path required')
 decoded=urllib.parse.unquote(parsed.path,errors='strict')
 if '\x00' in decoded or '\\' in decoded or any(x in ('.','..') for x in decoded.split('/')):raise ValueError('Invalid asset path')
 result=(root/decoded.lstrip('/')).resolve()
 if not result.is_relative_to(root.resolve()):raise ValueError('Outside asset root')
 return result
def routing_path(target):
 parsed=urllib.parse.urlsplit(target)
 if parsed.scheme or parsed.netloc:raise ValueError('Absolute request target rejected')
 path=urllib.parse.unquote(parsed.path,errors='strict')
 if '\\' in path or ';' in path or '%' in path or '//' in path or any(x in ('.','..') for x in path.split('/')):raise ValueError('Ambiguous route')
 return path.rstrip('/')
class Handler(http.server.BaseHTTPRequestHandler):
 protocol_version='HTTP/1.1'
 def log_message(self,*args):pass
 def setup(self):super().setup();self.connection.settimeout(30)
 def handle_request(self):
  if not self.server.admissions.acquire(blocking=False):self.send_error(503);return
  try:
   consume(self.server.ownedRoot,'http',2,11000)
   path=routing_path(self.path)
   if self.path.startswith('/__bl5/login/') and self.command=='GET':
    role=self.path.removeprefix('/__bl5/login/')
    if role not in ('owner','member','outsider','admin'):self.send_error(404);return
    identity=json.loads(self.server.identities.read_text())['identities'][role];token=identity['token']
    if not __import__('re').fullmatch('[A-Za-z0-9_-]{43}',token):raise ValueError('Invalid synthetic session')
    self.send_response(303);self.send_header('Location','/');self.send_header('Set-Cookie','__Host-AMA_SESSION='+token+'; Secure; HttpOnly; SameSite=Strict; Path=/');self.send_header('Cache-Control','no-store');self.send_header('Content-Length','0');self.end_headers()
   elif self.path.startswith('/api/v1/'):
    raw=self.headers.get('Content-Length','0')
    if not raw.isdecimal() or int(raw)>MAXIMUM or self.headers.get('Transfer-Encoding'):self.send_error(413);return
    length=int(raw)
    if self.command in ('POST','PUT','PATCH'):consume(self.server.ownedRoot,'ingress-bytes',length,256*1024*1024)
    if self.command=='POST' and path=='/api/v1/meetings':consume(self.server.ownedRoot,'pipelines',1,12)
    if self.command=='PUT' and '/parts/' in path:consume(self.server.ownedRoot,'file-bytes',length,256*1024*1024)
    body=self.rfile.read(length)
    if len(body)!=length:self.send_error(400);return
    headers={k:v for k,v in self.headers.items() if k.lower() not in HOP};headers['Host']='127.0.0.1:'+str(self.server.upstream)
    with closing(http.client.HTTPConnection('127.0.0.1',self.server.upstream,timeout=30)) as upstream:
     upstream.request(self.command,self.path,body=body if length else None,headers=headers);response=upstream.getresponse();data=response.read(MAXIMUM+1)
     if len(data)>MAXIMUM:self.send_error(502);return
     self.send_response(response.status)
     for k,v in response.getheaders():
      if k.lower() not in HOP|{'content-length'}:self.send_header(k,v)
     self.send_header('Content-Length',str(len(data)));self.end_headers()
     if self.command!='HEAD':self.wfile.write(data)
   elif self.command in ('GET','HEAD'):
    path=asset_path(self.server.assets,self.path)
    if not path.is_file():path=self.server.assets/'index.html'
    if path.is_symlink() or not path.resolve().is_relative_to(self.server.assets.resolve()) or path.stat().st_size>MAXIMUM:self.send_error(404);return
    data=path.read_bytes();self.send_response(200);self.send_header('Content-Type',mimetypes.guess_type(path.name)[0] or 'application/octet-stream');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(data)));self.end_headers()
    if self.command!='HEAD':self.wfile.write(data)
   else:self.send_error(405)
  except (ValueError,UnicodeError):self.send_error(400)
  except (TimeoutError,OSError,http.client.HTTPException):self.send_error(502)
  finally:self.server.admissions.release()
 do_GET=handle_request;do_HEAD=handle_request;do_POST=handle_request;do_PUT=handle_request;do_DELETE=handle_request;do_PATCH=handle_request;do_OPTIONS=handle_request

def main():
 p=argparse.ArgumentParser();p.add_argument('--config',required=True);args=p.parse_args();config=json.loads(pathlib.Path(args.config).read_text());root=pathlib.Path(config['ownedRoot']).resolve()
 for key in ('certificate','privateKey','readyFile','identitiesFile'):
  if not pathlib.Path(config[key]).resolve().is_relative_to(root):raise ValueError('Foreign HTTPS runtime path')
 assets=pathlib.Path(config['assets']).resolve()
 if not (assets/'index.html').is_file() or not 1<=config['upstreamPort']<=65535:raise ValueError('Built frontend and upstream required')
 server=http.server.ThreadingHTTPServer(('127.0.0.1',config['port']),Handler);server.daemon_threads=True;server.admissions=threading.BoundedSemaphore(4);server.upstream=config['upstreamPort'];server.assets=assets;server.ownedRoot=root;server.identities=pathlib.Path(config['identitiesFile'])
 context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(config['certificate'],config['privateKey']);server.socket=context.wrap_socket(server.socket,server_side=True)
 ready=pathlib.Path(config['readyFile']);ready.write_text(json.dumps({'port':server.server_port,'upstreamPort':server.upstream,'assets':str(assets),'mockResponses':False})+'\n')
 try:server.serve_forever(poll_interval=.25)
 finally:server.server_close()
if __name__=='__main__':main()
