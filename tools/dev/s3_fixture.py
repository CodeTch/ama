"""Solely owned RustFS 1.0.3 fixture. Secrets/data remain under ignored .tools/."""
import json, os, pathlib, secrets, socket, subprocess, sys, time
ROOT = pathlib.Path(__file__).resolve().parents[2]
NAME = os.environ.get('AMA_S3_FIXTURE_NAME', 's3-java')
assert NAME.replace('-', '').isalnum()
HOME = ROOT / '.tools' / NAME
IMAGE = 'rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922'
OWNER = 'ama-b04-java-s3-' + ROOT.name + ('' if NAME == 's3-java' else '-' + NAME)
def run(*args):
    return subprocess.check_output(args, text=True).strip()
def inspect_owned():
    record = json.loads((HOME/'fixture.json').read_text())
    state = json.loads(run('docker','inspect',record['id']))[0]
    assert state['Id'] == record['id']
    assert state['Config']['Labels']['ama.owner'] == OWNER
    assert state['Config']['Image'] == IMAGE
    assert state['HostConfig']['Memory'] == 2*1024**3
    assert state['HostConfig']['MemorySwap'] == 2*1024**3
    assert state['HostConfig']['NanoCpus'] == 2_000_000_000
    assert state['HostConfig']['PidsLimit'] == 256
    assert state['HostConfig']['PortBindings']['9000/tcp'] == [{'HostIp':'127.0.0.1','HostPort':str(record['port'])}]
    assert {(m['Source'],m['Destination']) for m in state['Mounts']} == {(str(HOME/'data'),'/data'),(str(HOME/'logs'),'/logs')}
    return record
def main():
    action = sys.argv[1]
    if action == 'create':
        HOME.mkdir(parents=True, exist_ok=True)
        assert not (HOME/'fixture.json').exists(), 'fixture already exists'
        os.chmod(HOME,0o700)
        access, secret = secrets.token_hex(12), secrets.token_hex(24)
        with socket.socket() as s:
            s.bind(('127.0.0.1',0)); port=s.getsockname()[1]
        for name in ('data','logs'):
            # The image runs as its documented non-root UID. Only these new owned directories.
            if not (HOME/name).exists():
                subprocess.run(['sudo','-n','install','-d','-o','10001','-g','10001','-m','750',str(HOME/name)],check=True)
            assert (HOME/name).stat().st_uid == 10001
        env = HOME/'server.env'
        env.write_text(f'RUSTFS_ACCESS_KEY={access}\nRUSTFS_SECRET_KEY={secret}\nRUSTFS_CONSOLE_ENABLE=false\nRUSTFS_VOLUMES=/data\nRUSTFS_OBS_LOG_DIRECTORY=/logs\nRUST_LOG=warn\n')
        os.chmod(env,0o600)
        name='ama-b04-java-'+secrets.token_hex(5)
        cid=run('docker','run','-d','--name',name,'--platform','linux/amd64','--cpus','2','--memory','2g','--memory-swap','2g','--pids-limit','256','--log-opt','max-size=10m','--log-opt','max-file=2','--label','ama.owner='+OWNER,'-p',f'127.0.0.1:{port}:9000','--env-file',str(env),'--mount',f'type=bind,source={HOME}/data,target=/data','--mount',f'type=bind,source={HOME}/logs,target=/logs',IMAGE)
        (HOME/'fixture.json').write_text(json.dumps(dict(id=cid,name=name,port=port,image=IMAGE,owner=OWNER,workspace=str(ROOT)),indent=2)+'\n')
        client=HOME/'env.sh'
        client.write_text(f'export AMA_S3_ENDPOINT=http://127.0.0.1:{port}\nexport AMA_S3_ACCESS={access}\nexport AMA_S3_SECRET={secret}\n')
        os.chmod(client,0o600)
        inspect_owned()
        print('owned fixture created; fixed loopback port',port)
    elif action in ('restart','stop'):
        r=inspect_owned()
        run('docker',action,r['id'])
        print('owned fixture',action)
    elif action=='inspect':
        r=inspect_owned()
        print(json.dumps(r,indent=2))
    else: raise ValueError('unknown action')
if __name__=='__main__': main()
