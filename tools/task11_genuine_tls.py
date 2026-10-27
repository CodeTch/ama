"""One released test TLS overlay; preserves the original application identities and all existing store files."""
import datetime as dt,hashlib,ipaddress,json,os,pathlib,stat
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID
P=pathlib.Path('/home/tanren/ama/.worktrees/phase-b-completion')
W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
ROOT=P/'.tools/task11b5-5199ff77e80c4fea8d7f543f9fa4ff/knowledge'
OUT=W/'task-11-b6-genuine-runtime-20261027/tls-genuine-01'
def require(v):
 if not v:raise RuntimeError('Exact genuine TLS fixture gate failed')
def private_file(path,limit):
 require(path.resolve()==path and not path.is_symlink() and path.is_file() and stat.S_IMODE(path.stat().st_mode)==0o600 and path.stat().st_size<=limit)
 return path.read_bytes()
def write(path,raw):
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:f.write(raw)
def prepare():
 require(os.environ.get('AMA_TASK11_EVIDENCE_WINDOW')==str(OUT) and (OUT/'consumed.json').is_file())
 require(ROOT.resolve()==ROOT and ROOT.is_dir() and not ROOT.is_symlink() and stat.S_IMODE(ROOT.stat().st_mode)==0o700)
 original=ROOT/'application/publicidentities.json';raw=private_file(original,8192);application=json.loads(raw);require(application['role']=='application' and 'providerTlsTrustCertificate' not in application['paths'])
 target=ROOT/'tls-genuine-01';require(not target.exists() and not target.is_symlink());target.mkdir(mode=0o700)
 now=dt.datetime.now(dt.timezone.utc);ca_key=ec.generate_private_key(ec.SECP256R1());server_key=ec.generate_private_key(ec.SECP256R1());ca_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Owned genuine application TLS CA')]);server_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Owned genuine application provider')])
 ca=x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name).public_key(ca_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=1)).not_valid_after(now+dt.timedelta(days=2)).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(ca_key,hashes.SHA256())
 leaf=x509.CertificateBuilder().subject_name(server_name).issuer_name(ca_name).public_key(server_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=1)).not_valid_after(now+dt.timedelta(days=2)).add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True).add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]),critical=False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False).sign(ca_key,hashes.SHA256())
 ca_bytes=ca.public_bytes(serialization.Encoding.PEM);leaf_bytes=leaf.public_bytes(serialization.Encoding.PEM)+ca_bytes
 write(target/'ca.pem',ca_bytes);write(target/'server.pem',leaf_bytes);write(target/'server.key',server_key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
 application['paths']['providerTlsTrustCertificate']=str(target/'ca.pem');write(target/'application.json',json.dumps(application,sort_keys=True,separators=(',',':')).encode())
 require(original.read_bytes()==raw)
 report=dict(mode='CONTROLLED_TLS',root=str(target),originalApplicationSha256=hashlib.sha256(raw).hexdigest(),caSha256=hashlib.sha256(ca_bytes).hexdigest(),serverCertificateSha256=hashlib.sha256(leaf_bytes).hexdigest(),preservedOriginalIdentities=True,originalApplicationUnchanged=True,newStoreCreated=False)
 with (OUT/'tls-prepared.json').open('x') as f:json.dump(report,f,indent=2);f.write('\n')
if __name__=='__main__':prepare()
