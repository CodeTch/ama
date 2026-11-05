"""Governance acceptance tests in a disposable schema on the owned local database."""
import json, os, secrets, subprocess, zipfile
from pathlib import Path
import local_app
from offline_speech_check import database_sql

ROOT=Path('/home/tanren/ama')
OUT=ROOT/os.environ.get('AMA_GOVERNANCE_OUTPUT','.tools/local-app/governance-repair-20261105')
assert OUT.resolve().is_relative_to((ROOT/'.tools/local-app').resolve())
OUT.mkdir(parents=True,exist_ok=True)
BUILD=OUT/'maven'
TESTS=os.environ.get('AMA_GOVERNANCE_TESTS','GovernanceWorkbenchIT,GovernanceLifecycleIT,VoiceprintMatchingIT,CorpusFeedbackIT,StructuredFeedbackIT')

def run(command, name, env):
    with (OUT/name).open('w') as log:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode: raise RuntimeError(name+' failed; see '+str(OUT/name))

def main():
    assert Path.cwd()==ROOT and subprocess.check_output(['git','branch','--show-current'],text=True).strip()=='main'
    assert local_app.owned_container()
    state=json.loads((ROOT/'.tools/local-app/state.json').read_text())
    libraries=OUT/'lib';libraries.mkdir(exist_ok=True)
    with zipfile.ZipFile(BUILD/'ama-api/ama-api-0.1.0-SNAPSHOT-exec.jar') as archive:
        for name in archive.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                (libraries/Path(name).name).write_bytes(archive.read(name))
    schema='ama_c_recording_'+secrets.token_hex(6)
    database_sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON `{schema}`.* TO 'ama_local'@'%';")
    url=f"jdbc:mysql://127.0.0.1:{state['databasePort']}/{schema}"
    cp=':'.join([str(BUILD/f'ama-{module}/classes') for module in ['adapters','contracts','core']]+[str(libraries/'*')])
    passed=False
    try:
        run(['java','-cp',cp,'ama.db.MigrationMain'],'migration.log',os.environ|{'DATABASE_URL':url,'DATABASE_USERNAME':'root','DATABASE_PASSWORD':state['rootPassword']})
        env=os.environ|{'AMA_C_RECORDING_DATABASE_URL':url,'AMA_C_RECORDING_DATABASE_USERNAME':'ama_local','AMA_C_RECORDING_DATABASE_PASSWORD':state['databasePassword']}
        run(['./mvnw','-o','-B','-ntp','-Dama.validation.directory='+str(BUILD),'-pl','backend/integration-tests','-am','-Dtest=__INTEGRATION_ONLY__','-Dsurefire.failIfNoSpecifiedTests=false','-Dit.test='+TESTS,'verify'],'integration-tests.log',env)
        passed=True
    finally:
        database_sql(f'DROP DATABASE `{schema}`;')
        (OUT/'integration-report.json').write_text(json.dumps({'passed':passed,'schemaRemoved':True,'tests':TESTS},indent=2))
    print('PASS '+TESTS)

if __name__=='__main__': main()
