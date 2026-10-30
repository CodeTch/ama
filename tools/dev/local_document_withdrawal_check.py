#!/usr/bin/env python3
"""Genuine official-document authority withdrawal with bounded local identity/grant cleanup."""
import argparse,json,sqlite3,subprocess,uuid
import local_app


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--fixture',required=True,help='Published genuine native MAPPING qualification fixture JSON');args=parser.parse_args()
    fixture_path=(local_app.ROOT/args.fixture).resolve();assert fixture_path.is_relative_to(local_app.RUNTIME/'native-crash') and fixture_path.is_file()
    fixture=json.loads(fixture_path.read_text());assert fixture.get('mode')=='MAPPING' and fixture.get('queueOnlyAccepted')
    meeting,publication=fixture['meetingId'],fixture['publicationId'];uuid.UUID(meeting);uuid.UUID(publication)
    java,_,env=local_app.toolchain();cp=local_app.application(java,env);classes=local_app.RUNTIME/'qualification-classes'
    with (local_app.RUNTIME/'bqual-document-withdrawal-compile.log').open('w') as log:
        subprocess.run([str(java.with_name('javac')),'--release','21','-encoding','UTF-8','-cp',str(classes)+':'+cp,'-d',str(classes),str(local_app.ROOT/'tools/dev/qualification-java/ama/api/LocalOfficialDocumentWithdrawalCheck.java')],cwd=local_app.ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    with (local_app.RUNTIME/'bqual-document-withdrawal-start.log').open('w') as log:
        subprocess.run(['python3','tools/dev/local_app.py','start'],cwd=local_app.ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=120,check=True)
    def grant():
        with sqlite3.connect((local_app.RUNTIME/'knowledge/store.sqlite').as_uri()+'?mode=ro',uri=True) as db:
            return db.execute("SELECT can_publish,can_read FROM grant_record WHERE actor='dev-member' AND library='controlled-library' AND folder='controlled-root'").fetchone()
    original_grant=grant();assert original_grant==(0,1),'Exact original daily member READ-only grant required'
    reports=[]
    for mode in ['IDENTITY','PROVIDER']:
        output=local_app.RUNTIME/'read-withdrawal/document'/str(uuid.uuid4());output.mkdir(parents=True,mode=0o700)
        (output/'original-provider-grant.json').write_text(json.dumps({'actor':'dev-member','canPublish':False,'canRead':True})+'\n')
        try:
            with (output/'genuine.log').open('w') as log:
                subprocess.run([str(java),'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',str(classes)+':'+cp,'ama.api.LocalOfficialDocumentWithdrawalCheck',str(local_app.RUNTIME/'worker.properties'),meeting,publication,mode,str(output)],cwd=local_app.ROOT,env={**env,'AMA_LOCAL_DEV':'1'},stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=100,check=True)
            report=json.loads((output/'report.json').read_text());assert report['pass'];reports.append({**report,'evidenceDirectory':str(output)})
        finally:
            original=output/'original-identity.json'
            if original.exists():
                assert json.loads(original.read_text())=={'enterprise_id':'dev-member','status':'ACTIVE'}
                assert local_app.owned_container() is not None
                result=local_app.docker('exec',local_app.NAME,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "UPDATE enterprise_identity SET status=\'ACTIVE\' WHERE enterprise_id=\'dev-member\' AND status=\'DISABLED\'"',check=False)
                assert result.returncode==0,'Exact test identity cleanup must succeed'
            if grant()!=original_grant:
                with (output/'parent-grant-restore.log').open('w') as log:
                    subprocess.run(['python3','tools/providers/controlled_knowledge.py','--config',str(local_app.RUNTIME/'knowledge/provider.json'),'--grant-actor','dev-member','--grant-read'],cwd=local_app.ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=20,check=True)
            assert grant()==original_grant
    report={'pass':True,'meetingId':meeting,'publicationId':publication,'cases':reports,'allOriginalAuthorityRestored':True}
    (local_app.RUNTIME/'bqual-document-withdrawal-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':main()
