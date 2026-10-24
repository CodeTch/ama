from pathlib import Path
import json,hashlib,shutil,subprocess,datetime
P=Path('/home/tanren/ama/.worktrees/phase-b-completion');M=Path('/home/tanren/ama');W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';A=W/'pause-20261025-063507';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
extra=[]
for rel in ['task-11-implementation-report-20261025.md','phase-b-cross-task-closure-map-20261024.md']:
 p=W/rel
 if p.exists():
  d=A/'before'/'additional'/rel;d.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,d);extra.append({'path':rel,'sha256':sha(p)})
  old=p.read_text();p.write_text('# 最新权威节点：用户已暂停（2026-10-25 06:35 Asia/Shanghai）\n\n先读 docs/acceptance/development-pause-b-remaining-2026-10-25.md 与 W/current-state.json。Task11 replacement fix01部分验证通过，generic-external 1FAIL未诊断，独立5Important未复审闭合、SEND DENY；原no2禁止claim/send。新隔离16迁移已完成1PASS，不重跑。Task12仅静态准备、Task13待完成；旧pending/ACTIVE等逐段为历史，以最新sealed结果及暂停节点为准。\n\n---\n\n'+old)
(A/'additional-preimages.json').write_text(json.dumps(extra,indent=2)+'\n')
files=json.loads((A/'current-public-source-manifest.json').read_text())['files'];bad=[x['path'] for x in files if not (P/x['path']).is_file() or sha(P/x['path'])!=x['sha256']]
s=json.loads((W/'current-state.json').read_text());assert s['pause_request_active'] is True;assert s['execution_status']=='PAUSED_USER_REQUEST_TASK11_REPLACEMENT_FIX01';assert not bad
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=P,text=True).strip()=='ba48f35e7ef97c1e162a2d373453bd1e9689529c'
assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=P,text=True).strip()
doc='docs/acceptance/development-pause-b-remaining-2026-10-25.md';assert (P/doc).read_bytes()==(M/doc).read_bytes()
for root in (P,M):
 for rel in ['DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md']:
  assert '用户暂停' in (root/rel).read_text().split('\n')[0]
report=W/'task-11-replacement-independent-source-review-r1-20261025.md';assert sha(report)=='d8eea4beeb90ea9470d2f114a425e28d80b6440cdedfb42c87a361eecbf2f595'
outputs=[P/doc,M/doc,W/'current-state.json',W/'progress.md',W/'resume-20261024-active-context.md',W/'task-11-implementation-report-20261025.md',W/'phase-b-cross-task-closure-map-20261024.md',W/'task-13-pending-issue-register-20261024.md',P/'DEVELOPMENT_HANDOFF.md',M/'DEVELOPMENT_HANDOFF.md',P/'docs/acceptance/development-resume-b-remaining-2026-10-24.md',M/'docs/acceptance/development-resume-b-remaining-2026-10-24.md']
r={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'publicSourceFilesRechecked':len(files),'sourceMismatches':bad,'headUnchanged':True,'indexEmpty':True,'primaryMirrorEqual':True,'independentReviewOriginalHashUnchanged':True,'noTestsRerun':True,'documents':[{'path':str(p),'sha256':sha(p),'bytes':p.stat().st_size} for p in outputs]}
(A/'pause-record-verification.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in r.items() if k!='documents'},ensure_ascii=False))