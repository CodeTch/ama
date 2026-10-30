"""Provision this launcher's persistent local files, mTLS and existing TXT configuration."""
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def private_dir(path):
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink() or path.resolve() != path:
        raise RuntimeError('Local service path must be a real owned directory')
    path.chmod(0o700)


def write(path, value):
    if path.is_symlink():
        raise RuntimeError('Local service file must not be a symlink')
    with open(path, 'w', encoding='utf-8') as stream:
        os.chmod(path, 0o600)
        stream.write(value)


def properties(path, values):
    # Java Properties.load(InputStream) expects Latin-1 plus escaped UTF-16 units.
    def escape(value):
        return ''.join('\\u%04x' % int.from_bytes(value.encode('utf-16-be')[i:i+2], 'big')
                       for i in range(0, len(value.encode('utf-16-be')), 2))
    write(path, ''.join(escape(str(k))+'='+escape(str(v))+'\n' for k, v in sorted(values.items())))


def provision_pki(runtime, java, env, password):
    root=runtime/'pki'
    private_dir(root)
    if (root/'complete').is_file():
        return root
    def run(*args):
        with open(runtime/'pki.log','a') as log:
            subprocess.run(list(map(str,args)),cwd=root,env={**env,'LOCAL_PKI_PASSWORD':password},
                           stdout=log,stderr=subprocess.STDOUT,check=True,timeout=30)
    previous=os.umask(0o077)
    try:
        run('openssl','req','-x509','-newkey','rsa:2048','-noenc','-keyout','ca.key','-out','ca.pem',
            '-days','365','-subj','/CN=AMA isolated local development CA')
        for name in ('server','writer'):
            write(root/(name+'.ext'),'subjectAltName='+('DNS:localhost,IP:127.0.0.1,URI:spiffe://ama/local-server' if name=='server' else 'URI:spiffe://ama/local-writer')+'\nextendedKeyUsage='+('serverAuth' if name=='server' else 'clientAuth')+'\nbasicConstraints=CA:FALSE\n')
            run('openssl','req','-newkey','rsa:2048','-noenc','-keyout',name+'.key','-out',name+'.csr','-subj','/CN=AMA local '+name)
            run('openssl','x509','-req','-in',name+'.csr','-CA','ca.pem','-CAkey','ca.key','-CAcreateserial','-out',name+'.pem','-days','365','-extfile',name+'.ext')
            run('openssl','pkcs12','-export','-in',name+'.pem','-inkey',name+'.key','-certfile','ca.pem','-name',name,'-out',name+'.p12','-passout','env:LOCAL_PKI_PASSWORD')
        run(java.with_name('keytool'),'-importcert','-noprompt','-alias','local-ca','-file','ca.pem','-keystore','trust.p12','-storetype','PKCS12','-storepass:env','LOCAL_PKI_PASSWORD')
        write(root/'complete','Local development mTLS provisioned\n')
    finally:
        os.umask(previous)
    return root


def prepare(runtime, state, java, env, classpath):
    private_dir(runtime/'files')
    private_dir(runtime/'processing-archive')
    root=runtime/'files'
    state.setdefault('filePort',port())
    state.setdefault('workerHealthPort',port())
    origin='https://localhost:'+str(state['filePort'])
    volume='ama-local-'+state['owner']
    for path,value in [(root/'.volume-id',volume),(runtime/'file-role',state['owner'])]:
        if path.exists() and path.read_text()!=value:
            raise RuntimeError('Local volume identity conflict')
        write(path,value)
    candidates=[]
    for line in Path('/proc/self/mountinfo').read_text().splitlines():
        fields=line.split();sep=fields.index('-')
        unescape=lambda text:text.replace('\\040',' ').replace('\\011','\t').replace('\\134','\\')
        mount=Path(unescape(fields[4]))
        if root.is_relative_to(mount):
            candidates.append((mount,fields[2],fields[sep+1],unescape(fields[sep+2])))
    mount,device,filesystem,source=max(candidates,key=lambda item:len(str(item[0])))
    relative=str(root.relative_to(mount))
    pki=provision_pki(runtime,java,env,state['pkiPassword'])
    result=subprocess.run([str(java),'-cp',classpath,'ama.localdev.LocalProcessingSetup',str(runtime/'processing-archive'),origin,volume,relative,filesystem],env=env,capture_output=True,text=True,check=True,timeout=30)
    installation=json.loads(result.stdout)
    properties(runtime/'file-service.properties',dict(root=root,mountpoint=mount,device=device,filesystem=filesystem,source=source,volumeId=volume,roleFile=runtime/'file-role',activeRole=state['owner'],backendId=installation['backendId'],serviceOrigin=origin,keyStore=pki/'server.p12',trustStore=pki/'trust.p12',writerIdentities='spiffe://ama/local-writer',bindAddress='127.0.0.1',port=state['filePort']))
    values={'files.backends':'local','files.default':'local','files.part-size':262144,
        'files.local.type':'local','files.local.id':installation['backendId'],'files.local.revision':1,
        'files.local.service-origin':origin,'files.local.volume-id':volume,'files.local.relative-root':relative,
        'files.local.filesystem':filesystem,'files.local.key-store':pki/'writer.p12','files.local.trust-store':pki/'trust.p12',
        'files.local.key-store-password':state['pkiPassword'],'files.local.trust-store-password':state['pkiPassword'],
        'processing.archive-root':runtime/'processing-archive','processing.index-sha':installation['indexSha'],
        'processing.config-version':installation['configVersion'],'processing.config-sha':installation['configSha']}
    cert=subprocess.run(['openssl','x509','-in',str(pki/'server.pem'),'-outform','DER'],capture_output=True,check=True).stdout
    for key,value in {'health-url':'https://localhost:'+str(state['workerHealthPort']),'key-store':pki/'writer.p12','trust-store':pki/'trust.p12','key-store-password':state['pkiPassword'],'trust-store-password':state['pkiPassword'],'server-cert-sha256':hashlib.sha256(cert).hexdigest(),'max-age-ms':15000}.items():
        values['processing.worker.'+key]=value
    values.update({'processing.bounds.total-utf16':1048576,'processing.bounds.total-segments':4096,'processing.bounds.batch-deadline-ms':60000,
        'transcript.enabled':'true','transcript.correction.mode':'DISABLED_NOT_INSTALLED','transcript.correction.installation':'DISABLED_NOT_INSTALLED','transcript.correction.installation-version':'1','transcript.candidates.installation':'DISABLED_NOT_INSTALLED','transcript.publication.installation':'DISABLED_NOT_INSTALLED','transcript.transitions.installation-version':'1'})
    for key,value in zip(['utf8','scalars','utf16','copy-bytes','copy-utf16','response-bytes','revision-depth','revision-nodes','tasks'],[16777216,16777216,16777216,67108864,67108864,33554432,128,256,64],strict=True):
        values['transcript.bounds.'+key]=value
    return values
