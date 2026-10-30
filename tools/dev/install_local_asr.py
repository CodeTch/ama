"""Explicitly provision the pinned offline ASR in this checkout; never mutates system Python or downloads during inference."""
import hashlib,importlib.util,io,json,os,subprocess,sys,urllib.request,venv,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
REPOSITORY='Systran/faster-whisper-small'
REVISION='536b0662742c02347bc0e980a01041f333bce120'
PIP_VERSION='26.2.2'

def private(path):
    if path.is_symlink():raise RuntimeError('Owned regular path required')
    path.mkdir(parents=True,exist_ok=True,mode=0o700);path.chmod(0o700)

def publish(path,raw):
    if path.is_symlink():raise RuntimeError('Owned regular file required')
    if path.exists():
        if path.read_bytes()!=raw:raise RuntimeError('Retained ASR identity conflict')
    else:
        with path.open('xb') as stream:os.chmod(path,0o600);stream.write(raw);stream.flush();os.fsync(stream.fileno())

def main():
    base=ROOT/'.tools/local-models';private(base);environment=base/'asr-venv';private(environment)
    venv.EnvBuilder(with_pip=False,symlinks=True).create(environment);python=environment/'bin/python'
    prefix=Path(subprocess.check_output([str(python),'-c','import sys;print(sys.prefix)'],text=True).strip())
    if prefix!=environment:raise RuntimeError('Isolated interpreter required')
    if subprocess.run([str(python),'-c','import pip'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
        with urllib.request.urlopen('https://pypi.org/pypi/pip/'+PIP_VERSION+'/json',timeout=30) as response:metadata=json.load(response)
        wheel=next(item for item in metadata['urls'] if item['filename'].endswith('.whl'))
        with urllib.request.urlopen(wheel['url'],timeout=30) as response:raw=response.read(8388608)
        if hashlib.sha256(raw).hexdigest()!=wheel['digests']['sha256']:raise RuntimeError('Bootstrap wheel integrity failure')
        site=Path(subprocess.check_output([str(python),'-c','import sysconfig;print(sysconfig.get_paths()["purelib"])'],text=True).strip())
        if not site.is_relative_to(environment):raise RuntimeError('Isolated package directory required')
        site.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            if any(name.startswith('/') or '..' in Path(name).parts for name in archive.namelist()):raise RuntimeError('Invalid wheel paths')
            archive.extractall(site)
    log=base/'install.log'
    with log.open('a') as stream:
        result=subprocess.run([str(python),'-m','pip','--disable-pip-version-check','--no-input','install','--index-url','https://pypi.org/simple','-r',str(ROOT/'tools/dev/local-asr-requirements.lock')],stdout=stream,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError('ASR dependency installation failed; inspect '+str(log))
    model=base/'whisper-small-ct2';private(model);manifest=base/'asr-model-manifest.json'
    if not manifest.exists():
        with urllib.request.urlopen('https://huggingface.co/api/models/'+REPOSITORY+'/revision/'+REVISION+'?blobs=true',timeout=30) as response:info=json.load(response)
        if info['sha']!=REVISION:raise RuntimeError('Pinned model identity mismatch')
        # The venv module downloads anonymous public weights only, pinned to the immutable revision.
        download="from huggingface_hub import snapshot_download;import sys;snapshot_download(sys.argv[1],revision=sys.argv[2],local_dir=sys.argv[3],cache_dir=sys.argv[4],allow_patterns=['config.json','model.bin','tokenizer.json','vocabulary.txt','README.md'],token=False)"
        with log.open('a') as stream:subprocess.run([str(python),'-c',download,REPOSITORY,REVISION,str(model),str(base/'hub-cache')],stdout=stream,stderr=subprocess.STDOUT,check=True)
        files=[]
        for path in sorted(model.iterdir()):
            if path.is_file():
                if path.is_symlink():raise RuntimeError('Owned model file required')
                path.chmod(0o600)
                with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
                entry=next(item for item in info['siblings'] if item['rfilename']==path.name)
                if 'lfs' in entry and entry['lfs']['sha256']!=digest:raise RuntimeError('Model download integrity failure')
                files.append({'name':path.name,'sha256':digest,'bytes':path.stat().st_size})
        value={'schema':'ama.local-asr-model.v1','repository':REPOSITORY,'revision':REVISION,'engine':'faster-whisper','engineVersion':'1.2.1','device':'cpu','computeType':'int8','files':files}
        publish(manifest,json.dumps(value,sort_keys=True,separators=(',',':')).encode())
    spec=importlib.util.spec_from_file_location('ama_local_asr',ROOT/'tools/providers/local_asr.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);proof=module.verify_model(model,manifest)
    config={'schema':'ama.local-asr-runtime.v1','python':str(python),'runner':str(ROOT/'tools/providers/local_asr.py'),'model':str(model),'manifest':str(manifest),'manifestSha256':proof,'timeoutMillis':1800000}
    publish(base/'asr-runtime.json',json.dumps(config,sort_keys=True,separators=(',',':')).encode())
    print(json.dumps({'ready':True,'repository':REPOSITORY,'revision':REVISION,'manifestSha256':proof,'runtime':str(base/'asr-runtime.json')}))
if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError) as error:raise SystemExit(str(error))
