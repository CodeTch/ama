"""Install existing controlled providers for the explicitly opted-in daily local runtime."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import secrets
import subprocess
from local_services import private_dir, write

ROOT=Path(__file__).resolve().parents[2]


def same(path,data):
    if path.is_symlink():
        raise RuntimeError('Owned regular local credential required')
    if path.exists():
        if path.read_bytes()!=data or path.stat().st_mode&0o777!=0o600:
            raise RuntimeError('Retained local identity/configuration conflict')
    else:
        temporary=path.with_name('.'+path.name+'.writing')
        if temporary.is_symlink():raise RuntimeError('Invalid local preparation temporary')
        temporary.unlink(missing_ok=True)
        try:
            with open(temporary,'xb') as stream:
                temporary.chmod(0o600)
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.link(temporary,path)  # Publish complete bytes without replacing an existing identity.
            sync_directory(path.parent)
        finally:temporary.unlink(missing_ok=True)


def sync_directory(path):
    descriptor=os.open(path,os.O_DIRECTORY|os.O_RDONLY)
    try:os.fsync(descriptor)
    finally:os.close(descriptor)


def json_same(path,value):
    same(path,json.dumps(value,sort_keys=True,separators=(',',':')).encode())


def open_knowledge(config,initialize=False):
    spec=importlib.util.spec_from_file_location('ama_local_knowledge',ROOT/'tools/providers/controlled_knowledge.py')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.Provider(config,initialize)


def prepare_knowledge(runtime,state):
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    root=runtime/'knowledge'
    private_dir(root)
    ids={key:prefix+'-'+state['owner'] for key,prefix in [
        ('providerId','local-provider'),('storeInstanceId','local-store'),('clientId','local-app'),
        ('delegationKeyId','local-delegation'),('assertionKeyId','local-assertion')]}
    for name in ('delegation','assertion'):
        path=root/(name+'.key')
        if path.exists():
            if path.is_symlink() or path.stat().st_mode&0o777!=0o600:
                raise RuntimeError('Private retained signing key required')
            key=serialization.load_der_private_key(path.read_bytes(),password=None)
            if not isinstance(key,Ed25519PrivateKey):raise RuntimeError('Retained Ed25519 key required')
        else:
            key=Ed25519PrivateKey.generate()
            same(path,key.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        same(root/(name+'.public'),key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo))
    bearer=root/'bearer'
    if not bearer.exists():same(bearer,secrets.token_urlsafe(32).encode())
    same(root/'bearer.verifier',hashlib.sha256(bearer.read_bytes()).hexdigest().encode())
    config={'identities':ids,'bearerVerifier':str(root/'bearer.verifier'),'delegationPublic':str(root/'delegation.public'),
        'assertionPrivate':str(root/'assertion.key'),'store':str(root/'store.sqlite'),'port':state['knowledgePort'],
        'bind':'127.0.0.1','mode':'ISOLATED_LOOPBACK_TEST','readyFile':str(root/'provider.ready')}
    json_same(root/'provider.json',config)
    json_same(root/'application.json',{'role':'application',**ids,'paths':{
        'serviceBearer':str(bearer),'delegationPrivateKey':str(root/'delegation.key'),
        'providerAssertionPublicKey':str(root/'assertion.public')}})
    # Explicit administration runs once; normal restarts must preserve later grant revocation.
    if not (root/'complete').exists():
        store=root/'store.sqlite'
        staging=root/'.bootstrap.sqlite'
        bootstrap=not store.exists()
        if bootstrap:
            # Only these private, unpublished files are disposable. A retained store is never replaced.
            for suffix in ('','-wal','-shm'):
                scratch=Path(str(staging)+suffix)
                if scratch.is_symlink():raise RuntimeError('Invalid local bootstrap path')
                scratch.unlink(missing_ok=True)
        provider=open_knowledge({**config,'store':str(staging)} if bootstrap else config,bootstrap)
        try:
            provider.administer('dev-owner','controlled-library','controlled-root',True,True)
            provider.administer('dev-member','controlled-library','controlled-root',False,True)
        finally:provider.db.close()
        if bootstrap:
            os.link(staging,store)
            staging.unlink()
            sync_directory(root)
        same(root/'complete',b'Local controlled provider identities and initial grants installed\n')
    else:
        provider=open_knowledge(config)
        provider.db.close()
    return {'publication.enabled':'true','publication.mode':'ISOLATED_LOOPBACK_TEST',
        'publication.client-identities':root/'application.json','publication.origin':'http://127.0.0.1:'+str(state['knowledgePort']),
        'publication.capacity':1,'publication.reconciliation-capacity':1}


def asr_environment(env):
    env.pop('AMA_LOCAL_ASR_RUNTIME',None)
    config=ROOT/'.tools/local-models/asr-runtime.json'
    if config.is_symlink() or config.parent.is_symlink():raise RuntimeError('Owned regular ASR runtime required')
    if not config.exists():return False
    if not config.is_file() or config.stat().st_size>16384:raise RuntimeError('Bounded ASR runtime required')
    env['AMA_LOCAL_ASR_RUNTIME']=str(config)
    return True

def prepare(runtime,state,java,env,classpath):
    root=runtime/'minutes'
    private_dir(root)
    installation={'mode':'CONTROLLED_INSTALLATION','templateId':state['minutesTemplateId'],
        'revisionId':state['minutesTemplateRevisionId'],'release':'ama.local.demo.v1',
        'prompt':'controlled:rich-no-sensitive','port':state['modelPort'],'modelCapacity':2,'modelConfigRevision':1}
    # ControlledMinutesAdmin intentionally requires plain, strict installation properties.
    same(root/'install.properties',''.join(str(k)+'='+str(v)+'\n' for k,v in installation.items()).encode())
    result=subprocess.run([str(java),'-Xmx192m','-cp',classpath,'ama.localdev.LocalMinutesSetup',str(root/'install.properties')],
        env=env,capture_output=True,text=True,check=True,timeout=30)
    values=json.loads(result.stdout)
    document_setup=subprocess.run([str(java),'-Xmx192m','-cp',classpath,'ama.localdev.LocalDocumentSetup',str(root/'archive'),values['minutes.index-sha']],env=env,capture_output=True,text=True,check=True,timeout=30)
    values.update(json.loads(document_setup.stdout))
    if asr_environment(env):
        media_setup=subprocess.run([str(java),'-Xmx192m','-cp',classpath,'ama.localdev.LocalMediaSetup',str(root/'archive'),values['minutes.index-sha'],env['AMA_LOCAL_ASR_RUNTIME']],env=env,capture_output=True,text=True,check=True,timeout=30)
        values.update(json.loads(media_setup.stdout))
    material_setup=subprocess.run([str(java),'-Xmx192m','-cp',classpath,'ama.localdev.LocalMaterialSetup',str(root/'archive'),values['minutes.index-sha'],state['minutesTemplateRevisionId']],env=env,capture_output=True,text=True,check=True,timeout=30)
    values.update(json.loads(material_setup.stdout))
    values.update(prepare_knowledge(runtime,state))
    values.update({'transcript.candidates.installation':'AUTHORITY_CONTROLLED',
        'transcript.publication.installation':'AUTHORITY_CONTROLLED','transcript.transitions.installation-version':'2',
        'processing.bounds.artifacts':64,'processing.bounds.dependencies':128,'processing.bounds.graph-depth':16})
    # Retain the admin's immutable installation files; each process gets its own readiness receipt.
    ready=root/('ready-'+secrets.token_hex(8))
    write(root/'runtime.properties','\n'.join(['mode=CONTROLLED_INSTALLATION','bind=127.0.0.1',
        'port='+str(state['modelPort']),'store='+str(root/'provider-store'),'secretFile='+str(root/'provider.secret'),
        'readyFile='+str(ready)])+'\n')
    return values,ready
