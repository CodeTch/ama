#!/usr/bin/env python3
"""Recover only this checkout's failed load fixtures through genuine CAS and finish API."""
from __future__ import annotations
import argparse, hashlib, json, os, re, secrets, sys, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/load'))
sys.path.insert(0,str(ROOT/'tools/dev'))
from transport import Http
import local_app

def execute(case:Path,output:Path):
    case=case.resolve();output=output.resolve()
    if not case.is_relative_to(local_app.RUNTIME/'recording-load') or not output.is_relative_to(local_app.RUNTIME/'recording-recovery') or output.exists():raise ValueError('Fresh owned recovery output and original load case required')
    report=json.loads((case/'report.json').read_text());observations=json.loads((case/'observations.json').read_text());run=report['runId']
    if report['passed'] or not re.fullmatch(r'[0-9a-f]{12}',run):raise ValueError('Failed actual load case required')
    failed=[r for r in observations['recordings'] if r['errors']]
    if not failed or len(failed)>100:raise ValueError('Bounded actual failed recorders required')
    for r in failed:
        if not re.fullmatch('load-'+run+r'-r[0-9]{3}',r['recorderId']) or r['ackedChunks']!=r['sentChunks'] or r['ackedChunks']!=len(r['proofs']) or [p['seq'] for p in r['proofs']]!=list(range(1,r['ackedChunks']+1)):raise ValueError('Original complete acknowledged proof sequence required')
    state=json.loads((local_app.RUNTIME/'state.json').read_text())
    if state.get('owner')!=local_app.OWNER or not all(local_app.alive(state.get(role)) for role in local_app.SERVICE_ROLES) or state['sourceSignature']!=local_app.source_signature():raise ValueError('Current matching owned deployment required')
    output.mkdir(parents=True);os.chmod(output,0o700)
    result=dict(passed=False,scope='POST_CASE_ARCHIVE_RECOVERY_ONLY',originalCase=str(case),originalCaseReportSha256=hashlib.sha256((case/'report.json').read_bytes()).hexdigest(),deployedSourceSignature=state['sourceSignature'],recordings=[])
    try:
        for r in failed:
            actor=r['recorderId'];password=secrets.token_urlsafe(24);admin=Http('http://127.0.0.1:8792')
            # Explicitly owned fixture accounts; the prior ephemeral credentials were never retained.
            admin.json('PUT','/admin/api/cas/users/'+actor,dict(password=password,displayName='协议负载 '+actor[-3:],department='本地协议验证',active=True,attributes={'fixture':'ama.recording-load.v2'}))
            client=Http('http://127.0.0.1:5174','http://127.0.0.1:8182');client.login(actor,password)
            path='/api/v1/recordings/'+r['sessionId'];manifest=client.json('GET',path+'/manifest');current=client.json('GET',path);meeting=client.json('GET','/api/v1/meetings/'+r['meetingId'])
            expected=dict(partId=r['session']['partId'],highestContiguousSeq=r['ackedChunks'],highestReceivedSeq=r['ackedChunks'],sampleCount=r['sampleCount'])
            if manifest.get('sessionId')!=r['sessionId'] or not manifest.get('finished') or manifest.get('parts')!=[expected] or current.get('sessionId')!=r['sessionId'] or meeting.get('id')!=r['meetingId'] or current['backendId']!=r['session']['backendId']:raise ValueError('Frozen persisted recording differs from original acknowledged input')
            before=client.json('GET',path+'/archive');item=dict(actor=actor,meetingId=r['meetingId'],sessionId=r['sessionId'],originalTaskId=r['archiveTaskId'],before=before,manifestVerified=True);result['recordings'].append(item)
            if before['state']=='FAILED':
                accepted=client.json('POST',path+'/finish',{expected['partId']:r['ackedChunks']},headers={'X-Expected-Epoch':str(current['writerEpoch'])})
                if accepted['archiveTaskId']==before['taskId']:raise ValueError('Explicit failed archive retry must create a new attempt generation')
                item['retryTaskId']=accepted['archiveTaskId']
            else:item['retryTaskId']=before['taskId']
            deadline=time.monotonic()+240
            while True:
                status=client.json('GET',path+'/archive')
                if status['taskId']!=item['retryTaskId']:raise ValueError('Archive task generation changed')
                if status['available']:
                    if status['state']!='SUCCEEDED':raise ValueError('Unverified available archive')
                    break
                if status['state'] not in ('READY','RUNNING') or time.monotonic()>deadline:raise ValueError('Archive recovery incomplete: '+status['state'])
                time.sleep(1)
            proof=client.verify_archive(current,r['proofs']);item.update(proof)
            if proof['verifiedChunks']!=r['ackedChunks'] or not proof['hashMatched']:raise ValueError('Original PCM did not verify')
            print(json.dumps({'actor':actor,'verifiedChunks':proof['verifiedChunks'],'retryTaskId':item['retryTaskId']}),flush=True)
        result['passed']=True
    except Exception as failure:
        result['failureType']=type(failure).__name__;result['failure']=str(failure);raise
    finally:
        target=output/'report.json';target.write_text(json.dumps(result,indent=2));target.chmod(0o600)
    return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('case',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args();execute(args.case,args.output)
