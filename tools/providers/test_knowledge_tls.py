"""Owned temporary TLS sockets; provider business/authentication is an explicit stub."""
import datetime,os,pathlib,socket,ssl,tempfile,threading,time,unittest
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID
import controlled_knowledge as kb
from unittest import mock

class KnowledgeTlsTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);root=pathlib.Path(self.temp.name);key=ec.generate_private_key(ec.SECP256R1());name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Owned temporary provider')]);now=datetime.datetime.now(datetime.timezone.utc)
  cert=x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=1)).not_valid_after(now+datetime.timedelta(days=1)).add_extension(x509.SubjectAlternativeName([x509.IPAddress(__import__('ipaddress').ip_address('127.0.0.1'))]),critical=False).sign(key,hashes.SHA256())
  self.cert=root/'cert.pem';self.key=root/'key.pem';self.cert.write_bytes(cert.public_bytes(serialization.Encoding.PEM));self.key.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()));self.cert.chmod(0o600);self.key.chmod(0o600)
  self.config=dict(mode='CONTROLLED_TLS',bind='127.0.0.1',tlsCertificate=str(self.cert),tlsPrivateKey=str(self.key));self.trust=ssl.create_default_context(cafile=str(self.cert));self.calls=[]
 def start(self,tls=None):
  test=self
  class Provider:
   def serve(self,method,path,headers,body):test.calls.append((method,path,body));return dict(providerId='owned',storeInstanceId='temporary',items=[],cursor=None)
  server=kb.Server(('127.0.0.1',0),Provider(),tls=(None if tls is False else tls or kb.transport_context(self.config)));thread=threading.Thread(target=lambda:server.serve_forever(poll_interval=.02),daemon=True);thread.start()
  def close():server.shutdown();server.server_close();thread.join(2);self.assertFalse(thread.is_alive())
  self.addCleanup(close);return server
 def connect(self,server):
  raw=socket.create_connection(server.server_address,timeout=2)
  try:return self.trust.wrap_socket(raw,server_hostname='127.0.0.1')
  except BaseException:raw.close();raise
 def request(self,server,host=None):
  with self.connect(server) as sock:
   sock.sendall(('GET /v1/knowledge/libraries?limit=20 HTTP/1.1\r\nHost: '+(host or '127.0.0.1:'+str(server.server_port))+'\r\nContent-Length: 0\r\nConnection: close\r\n\r\n').encode());return sock.recv(8192)
 def test_tls_serves_only_after_valid_handshake_and_retains_host_checks(self):
  server=self.start();self.assertIn(b'200 OK',self.request(server));self.assertEqual(1,len(self.calls));self.assertIn(b'400 Bad Request',self.request(server,'wrong.invalid'));self.assertEqual(1,len(self.calls))
 def test_explicit_modes_and_private_key_permissions(self):
  self.assertIsNone(kb.transport_context(dict(mode='ISOLATED_LOOPBACK_TEST',bind='127.0.0.1')))
  for config in [dict(mode='DEFAULT',bind='127.0.0.1'),dict(self.config,bind='0.0.0.0')]:
   with self.assertRaises(RuntimeError):kb.transport_context(config)
  self.key.chmod(0o644)
  with self.assertRaises(RuntimeError):kb.transport_context(self.config)
 def test_stalled_handshake_does_not_block_another_connection(self):
  server=self.start()
  with socket.create_connection(server.server_address,timeout=2):
   start=time.monotonic();self.assertIn(b'200 OK',self.request(server));self.assertLess(time.monotonic()-start,2);self.assertEqual(1,len(self.calls))
 def test_two_stalled_handshakes_enforce_capacity(self):
  server=self.start()
  with socket.create_connection(server.server_address,timeout=2) as first,socket.create_connection(server.server_address,timeout=2) as second:
   # Admission barrier: wait for both owned server workers, not a timing-only assertion.
   end=time.monotonic()+2
   while time.monotonic()<end and len(server.active_connections())<2:time.sleep(.01)
   self.assertEqual(2,len(server.active_connections()))
   with self.assertRaises((ssl.SSLError,OSError)):self.connect(server)
   self.assertEqual([],self.calls)
 def test_close_aborts_stalled_handshakes(self):
  server=self.start()
  with socket.create_connection(server.server_address,timeout=2) as raw:
   end=time.monotonic()+2
   while time.monotonic()<end and not server.active_connections():time.sleep(.01)
   self.assertTrue(server.active_connections());start=time.monotonic();server.server_close();raw.settimeout(2);self.assertEqual(b'',raw.recv(1));self.assertLess(time.monotonic()-start,2)

 def test_slow_header_progress_cannot_extend_connection_deadline(self):
  server=self.start()
  with self.connect(server) as sock:
   start=time.monotonic();closed=False
   while time.monotonic()-start<18:
    try:sock.sendall(b'G')
    except OSError:closed=True;break
    time.sleep(.7)
   self.assertTrue(closed);self.assertGreaterEqual(time.monotonic()-start,14);self.assertLess(time.monotonic()-start,18);self.assertEqual([],self.calls)

 def test_expiry_during_tls_socket_replacement_cannot_admit_request(self):
  callback=[];real=kb.transport_context(self.config)
  class Deadline:
   def __init__(self,seconds,action):callback.append(action)
   def start(self):pass
   def cancel(self):pass
  class ExpiringTls:
   def wrap_socket(self,raw,**options):
    secured=real.wrap_socket(raw,**options);callback[0]();return secured
  with mock.patch.object(kb.threading,'Timer',Deadline):
   server=self.start(ExpiringTls())
   with self.assertRaises((ssl.SSLError,OSError)):self.request(server)
   self.assertEqual([],self.calls)

 def test_explicit_http_test_profile_keeps_existing_wire_behavior(self):
  server=self.start(False)
  with socket.create_connection(server.server_address,timeout=2) as sock:
   sock.sendall(('GET /v1/knowledge/libraries?limit=20 HTTP/1.1\r\nHost: 127.0.0.1:'+str(server.server_port)+'\r\nContent-Length: 0\r\nConnection: close\r\n\r\n').encode());self.assertIn(b'200 OK',sock.recv(8192));self.assertEqual(1,len(self.calls))

 def test_deadline_construction_or_start_failure_releases_admission(self):
  for phase in ('construct','start'):
   with self.subTest(phase=phase):
    failed=threading.Event()
    class FailingDeadline:
     def __init__(self,seconds,action):
      if phase=='construct':failed.set();raise RuntimeError('Owned injected timer construction failure')
     def start(self):failed.set();raise RuntimeError('Owned injected timer start failure')
     def cancel(self):pass
    with mock.patch.object(kb.threading,'Timer',FailingDeadline):
     server=self.start()
     with socket.create_connection(server.server_address,timeout=2) as raw:
      self.assertTrue(failed.wait(2));raw.settimeout(1);self.assertEqual(b'',raw.recv(1))
     end=time.monotonic()+1
     while time.monotonic()<end and server.active_connections():time.sleep(.01)
     self.assertEqual((),server.active_connections());first=server.capacity.acquire(False);second=server.capacity.acquire(False)
     try:self.assertTrue(first and second);self.assertFalse(server.capacity.acquire(False))
     finally:
      if first:server.capacity.release()
      if second:server.capacity.release()
     start=time.monotonic();server.server_close();self.assertLess(time.monotonic()-start,1)

if __name__=='__main__':unittest.main()
