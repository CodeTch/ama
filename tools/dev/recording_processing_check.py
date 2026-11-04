"""Actual main sources, isolated build outputs, disposable MySQL, sibling HTTP 8792."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
import local_app
from offline_speech_check import database_sql, preflight
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('AMA_RECORDING_EVIDENCE','.tools/local-app/recording-strict-20261103/processing-it')).resolve()
TESTS='RecordingProcessingIT,MaterialEnhancementIT,RecordingTranscriptSourceIT#actualReadyCopyEntersTheConfinedImportQueueAndProducesIndependentRawFromFullArchive+lostPartReplyReusesTheOriginalRealUploadAndPartOnRetry+secondIdentityCannotStartOrReadAndLateCopyCannotReviveADeletedMeeting'

def run(command,name,env=None):
    with (OUT/name).open('w') as log:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError(f'{name} failed; inspect {OUT/name}')

def build_model():
    # The repository's validation-output profile gives each module its own
    # classes/resources/reports while retaining the actual main source tree.
    base=OUT/'build';base.mkdir(parents=True,exist_ok=True)
    return base

def main():
    if Path.cwd()!=ROOT or subprocess.check_output(['git','branch','--show-current'],text=True).strip()!='main':raise RuntimeError('Project root/main required')
    if not OUT.is_relative_to(ROOT/'.tools/local-app/recording-strict-20261103'):raise ValueError('Owned evidence path required')
    OUT.mkdir(parents=True,exist_ok=True);preflight();build=build_model()
    state=json.loads((ROOT/'.tools/local-app/state.json').read_text());schema='ama_c_recording_'+secrets.token_hex(6)
    libraries=OUT/'lib';libraries.mkdir(exist_ok=True);jars=[]
    with zipfile.ZipFile(ROOT/'backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar') as archive:
        for name in archive.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar') and not Path(name).name.startswith('ama-'):
                target=libraries/Path(name).name;target.write_bytes(archive.read(name));jars.append(str(target))
    launcher=OUT/'RecordingSchema.java';launcher.write_text('''
import org.flywaydb.core.Flyway;
class RecordingSchema {public static void main(String[] args){
 Flyway.configure().dataSource(System.getenv("DATABASE_URL"),System.getenv("DATABASE_USERNAME"),System.getenv("DATABASE_PASSWORD"))
 .locations("filesystem:backend/adapters/src/main/resources/db/migration").initSql("SET NAMES utf8mb4; SET time_zone = '+00:00'").cleanDisabled(true).load().migrate();
}}
''')
    database_sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON `{schema}`.* TO 'ama_local'@'%';")
    started=time.time();report={'pass':False,'harness':'http://127.0.0.1:8792','schema':schema,'tests':TESTS,'buildOutputs':str(build)}
    try:
        url=f"jdbc:mysql://127.0.0.1:{state['databasePort']}/{schema}"
        run(['java','-cp',':'.join(jars),str(launcher)],'migration.log',os.environ|{'DATABASE_URL':url,'DATABASE_USERNAME':'root','DATABASE_PASSWORD':state['rootPassword']})
        env=os.environ|{'AMA_C_RECORDING_DATABASE_URL':url,'AMA_C_RECORDING_DATABASE_USERNAME':'ama_local','AMA_C_RECORDING_DATABASE_PASSWORD':state['databasePassword'],'AMA_SPEECH_HARNESS_ORIGIN':'http://127.0.0.1:8792','AMA_LOCAL_SPEECH_RUNTIME':str(ROOT/'.tools/local-models/speech-runtime-v1.json'),'AMA_LOCAL_ASR_RUNTIME':str(ROOT/'.tools/local-models/asr-runtime.json'),'AMA_TASK11_REPOSITORY_ROOT':str(ROOT)}
        run(['./mvnw','-o','-B','-ntp','-f',str(ROOT/'pom.xml'),'-Dama.validation.directory='+str(build),'-pl','backend/integration-tests','-am','-Dtest=__AMA_INTEGRATION_SELECTION_ONLY__','-Dsurefire.failIfNoSpecifiedTests=false',f'-Dit.test={TESTS}','verify'],'verify.log',env)
        suites=[]
        for file in build.glob('*/*-reports/TEST-*.xml'):
            item=ET.parse(file).getroot();suites.append({'name':item.get('name'),**{key:int(item.get(key,'0')) for key in ('tests','failures','errors','skipped')}})
        for name in TESTS.split(','):
            matches=[s for s in suites if s['name'].endswith('.'+name.split('#')[0])]
            if len(matches)!=1 or matches[0]['tests']==0 or any(matches[0][k] for k in ('failures','errors','skipped')):raise RuntimeError('Required suite did not pass: '+name)
        report.update({'pass':True,'suites':suites,'executed':sum(s['tests'] for s in suites)})
    finally:
        database_sql(f'DROP DATABASE `{schema}`;');report.update(schemaRemoved=True,elapsedSeconds=round(time.time()-started,2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PASS',report['executed'],OUT/'report.json')
if __name__=='__main__':main()
