"""Owned B00-1.3 design-only fixture; process-local native AIO override."""
import hashlib, importlib.util, json, pathlib, re, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=ROOT/'artifacts/phase-b/b00-generation-design'
spec=importlib.util.spec_from_file_location('fixture',ROOT/'tools/dev/mysql_fixture.py')
f=importlib.util.module_from_spec(spec); spec.loader.exec_module(f)
original=f.docker
owned=[]
def docker(*args):
    if args[0]=='run':
        name=args[args.index('--name')+1]
        credentials=pathlib.Path(args[args.index('--env-file')+1])
        expected=('run','-d','--name',name,'--label','ama.test-fixture=b01','--publish','127.0.0.1::3306','--env-file',str(credentials),f.IMAGE,'--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_ai_ci','--default-time-zone=+00:00')
        assert args==expected and re.fullmatch('ama-test-b01-[0-9a-f]{12}',name)
        assert credentials.parent.parent==ROOT/'.tools/mysql-fixture'
        assert credentials.parent.name==name and not owned
        args=(*args,'--innodb-use-native-aio=OFF')
        (OUT/'fixture-wrapper.json').write_text(json.dumps({'scope':'design-only; native AIO/HA unqualified','args':args},indent=2)+'\n')
        owned.append(name)
    if args[0]=='rm': raise RuntimeError('Fixture removal forbidden; preserve evidence')
    return original(*args)
f.docker=docker
failure_original=f.failure_command
def failure_command(*args):
    if args[0]=='rm': raise RuntimeError('Failed startup retained; no cleanup authorization')
    return failure_original(*args)
f.failure_command=failure_command
f.start()
name=owned[0]
i=json.loads(original('inspect',name))[0]
q='SELECT VERSION(),@@innodb_use_native_aio,(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()),DATABASE();'
result=subprocess.check_output(['docker','exec',name,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "'+q+'"'],text=True).strip().split('\t')
assert result[0].startswith('8.4.') and result[1:]==['0','0','ama_b01_fixture']
assert i['Config']['Image']==f.IMAGE and i['Config']['Labels']['ama.test-fixture']=='b01'
assert all(x['HostIp']=='127.0.0.1' for x in i['NetworkSettings']['Ports']['3306/tcp'])
record={'owned_worktree':str(ROOT),'container':name,'id':i['Id'],'image_reference':i['Config']['Image'],'image_id':i['Image'],'labels':i['Config']['Labels'],'mounts':i['Mounts'],'ports':i['NetworkSettings']['Ports'],'command':i['Config']['Cmd'],'auto_remove':i['HostConfig']['AutoRemove'],'mysql_version':result[0],'native_aio':int(result[1]),'initial_tables':int(result[2]),'schema':result[3],'environment_source':'/home/tanren/ama/.worktrees/phase-a/.tools/env.sh','environment_sha256':hashlib.sha256(pathlib.Path('/home/tanren/ama/.worktrees/phase-a/.tools/env.sh').read_bytes()).hexdigest(),'aio_max_nr':pathlib.Path('/proc/sys/fs/aio-max-nr').read_text().strip(),'helper_sha256':hashlib.sha256((ROOT/'tools/dev/mysql_fixture.py').read_bytes()).hexdigest()}
(OUT/'fixture-initial.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record,indent=2))
