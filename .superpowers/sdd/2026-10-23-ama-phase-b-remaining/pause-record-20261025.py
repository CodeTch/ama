from pathlib import Path
import subprocess,json,hashlib,datetime,shutil,xml.etree.ElementTree as ET
P=Path('/home/tanren/ama/.worktrees/phase-b-completion'); M=Path('/home/tanren/ama'); W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
now=datetime.datetime.now(datetime.timezone.utc); local=now.astimezone(datetime.timezone(datetime.timedelta(hours=8))); tag=local.strftime('%Y%m%d-%H%M%S'); pause='pause-'+tag; A=W/pause; A.mkdir(exist_ok=False)
name='development-pause-b-remaining-2026-10-25.md'; docrel='docs/acceptance/'+name
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(*a,cwd=P):
 r=subprocess.run(a,cwd=cwd,capture_output=True); return {'exit':r.returncode,'stdout':r.stdout.decode('utf-8','replace'),'stderr':r.stderr.decode('utf-8','replace')}
def save(p,v): p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
pre=[]
for root,label,rels in [(P,'product',['DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md','.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json','.superpowers/sdd/2026-10-23-ama-phase-b-remaining/progress.md','.superpowers/sdd/2026-10-23-ama-phase-b-remaining/resume-20261024-active-context.md']),(M,'primary',['DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md'])]:
 for rel in rels:
  p=root/rel
  if p.exists():
   dest=A/'before'/label/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);pre.append({'root':label,'path':rel,'sha256':sha(p),'bytes':p.stat().st_size})
s=json.loads((W/'current-state.json').read_text())
head=run('git','rev-parse','HEAD')['stdout'].strip(); branch=run('git','branch','--show-current')['stdout'].strip(); mainhead=run('git','rev-parse','HEAD',cwd=M)['stdout'].strip(); index=run('git','diff','--cached','--name-only'); status=run('git','status','--porcelain=v1','-uno')
(A/'tracked-status.txt').write_text(status['stdout']); (A/'index-status.txt').write_text(index['stdout'])
paths=[]
for args in [('git','ls-files','-z'),('git','ls-files','--others','--exclude-standard','-z')]:
 r=subprocess.run(args,cwd=P,capture_output=True,check=True)
 paths+=r.stdout.decode().split('\0')
public=[]
for rel in sorted(set(paths)):
 if not rel or not rel.startswith(('backend/','web/','tools/','contracts/','database/','db/','schemas/','scripts/')):continue
 if any(x in rel.split('/') for x in ('target','node_modules','.tools','.git','dist','build','__pycache__')):continue
 p=P/rel
 if p.is_file() and not p.is_symlink():public.append({'path':rel,'bytes':p.stat().st_size,'sha256':sha(p)})
save(A/'current-public-source-manifest.json',{'scope':'Existing public product paths under named source prefixes, tracked plus nonignored untracked; excludes docs/evidence/private/targets. No all-evidence custody claim.','files':public})
diff=subprocess.run(['git','diff','--binary','--','backend','web','tools','contracts','database','db','schemas','scripts'],cwd=P,capture_output=True,check=True);(A/'tracked-product-diff.patch').write_bytes(diff.stdout)
windows=[]
for d in sorted((P/'artifacts/phase-b/b-remaining/task-11').iterdir()):
 if not d.is_dir() or not (d/'result.json').exists():continue
 r=json.loads((d/'result.json').read_text());windows.append({'window':d.name,'exitCode':r.get('exitCode'),'totals':r.get('totals'),'resultSha256':sha(d/'result.json'),'sourceManifestSha256':sha(d/'source-manifest.json') if (d/'source-manifest.json').exists() else None})
save(A/'task11-result-index.json',windows)
proc=[]
for d in Path('/proc').iterdir():
 if not d.name.isdigit():continue
 try:
  comm=(d/'comm').read_text().strip();raw=(d/'cmdline').read_bytes().replace(b'\x00',b' ').decode('utf-8','replace')
  if comm not in ('java','python3','node','mvn'):continue
  stat=(d/'stat').read_text(); tail=stat[stat.rfind(')')+2:].split(); repo='/home/tanren/ama' in raw; related=('controlled_knowledge.py' in raw or 'Task11' in raw or 'task11_window.py' in raw or 'maven' in raw.lower() or 'surefire' in raw or 'failsafe' in raw)
  proc.append({'pid':int(d.name),'comm':comm,'ppid':int(tail[1]),'pgid':int(tail[2]),'startTicks':tail[19],'repoMention':repo,'task11OrMavenMention':related,'classification':'retained controlled KB provider' if int(d.name)==231675 else 'not terminated: no ownership-based stop authorized from this snapshot'})
 except (FileNotFoundError,PermissionError,ProcessLookupError):pass
save(A/'process-snapshot-redacted.json',{'utc':now.isoformat(),'rawArgvOrEnvRecorded':False,'processes':proc,'limitation':'Limited process snapshot, no DB query/no all-schema zero-debt claim; unrelated existing services preserved.'})
cids=['11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad','eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502']
containers=[]
for cid in cids:containers.append({'cid':cid,'observation':run('docker','inspect','--format','{{.Id}}|{{.State.Running}}|{{.State.OOMKilled}}|{{.State.Pid}}|{{.State.StartedAt}}',cid)})
save(A/'retained-containers.json',containers)
bootstrap=P/'artifacts/phase-b/b-remaining/task-11/replacement-fix01-isolated-bootstrap'
b=json.loads((bootstrap/'result.json').read_text());assert b['exitCode']==0 and b['totals']=={'tests':1,'failures':0,'errors':0,'skipped':0}
report='task-11-replacement-independent-source-review-r1-20261025.md'
text=f'''# B 阶段暂停与精准接续 — {local.strftime('%Y-%m-%d %H:%M:%S')} Asia/Shanghai

用户最新指令为“停止任务，将当前相关任务状态信息记录，编写接续文档”。**开发已暂停**；未来仅在用户明确“继续任务”后恢复。旧 ACTIVE/首final RED/迁移待跑等段落均为历史，以本文件和 W/current-state.json 最新 pause 节点为准。原目标仍是完整完成 B 后停止，不进入 C/D/E/F。

## 唯一工作树与现场

- 产品树 P：`{P}`；分支 `{branch}`；当前 HEAD `{head}`（Task10 采用）。Task11 大量未提交修改完整保留，未提交、未采用，不能 reset/clean/stash/drop 或恢复旧冻结版本覆盖现场。
- 主树：`{M}`，HEAD `{mainhead}`；只更新精准发现/接续文档，不合并 main、不 push、不生产发布、不发外部消息。
- W：`{W}`。暂停前状态/发现文档原字节、当前公开源码清单、tracked diff、index、测试结果索引及脱敏进程观察均保存在 W/`{pause}`。公开源码清单仅限记录的代码前缀，不冒称扫描所有原始证据。
- 暂停时 collaboration.list_agents 只返回 root，无活动子代理。原协调者最后因 usage limit 失败；旧作者/审查者句柄目前也不在线。后续先尝试原身份，实际不可恢复才以完整精确brief接替，同一 fix round1 不重置、不从头实现。不得在受限时循环重派。
- 当前未检测到本轮 Maven/Task11 测试进程，最后 bootstrap 已结束。保留现有数据库、接收端及其他服务；未杀无归属旧 Java/Node。脱敏快照是限定观察，不是所有数据库“无债务”证明。

## 精确首个接续节点（不能直接发起发布）

1. 先读本文件 → W/current-state.json → `task-11-replacement-fix01-author-brief-20261025.md` → `{report}`/coverage → 最新 fix01 的结果索引/源码快照。**Task11 replacement fix round1/5，五项 Important 尚未独立闭合，SEND DENY。**
2. 先诊断新发现的通用回归失败：`artifacts/phase-b/b-remaining/task-11/replacement-fix01-generic-external/result.json` 为 1FAIL/0ERROR，`Task11AffectedExternalIT.genericMarkerFailureRetryReplayAndCurrentSuccessStillUseOriginalProtocol`，断言 expected0/actual2（压缩源line5）。根因尚未定位，不能猜成夹具错误或产品错误、不能改期望掩盖；先读 sealed XML/log/source 与 fixture。这个未处理失败此前未进入协调 state，现显式加入。
3. 隔离新库 **正常16迁移/rerun0已经实际通过**：`replacement-fix01-isolated-bootstrap` exit0/1PASS，完整history文件在该窗。state 原“MIGRATIONS_PENDING”过时；复核现有证据与资源连接即可，**禁止重复 provisioning/bootstrap**。
4. 沿当前源码完成 fix01：真实 marker/跨 operation 矛盾阻断、历史同 key 失败ACK/矛盾、I3 SQL追踪、I4 全链同ledger、I5 完整前置证明、fresh currentSources，补仅欠缺的针对性验证；冻结新源码与修复报告后交同独立 reviewer 增量复审。预期 `task-11-replacement-fix01-report-20261025.md` **目前不存在**，不能引用为已完成报告。
5. 复审明确通过后，协调者再核原接收端进程/loaded SHA 与磁盘版本、同store/keys/port有界重启 ACK，才可考虑解除真实 replacement SEND 门禁。不得用旧R1 provider纯测试替代新源码实际运行资格。

## 必须保护的未发送执行与隔离

原主 Task11 schema `ama_t11_9714255c9e2c_behavior` 存在已接纳待审执行：publication `368767a5-c477-40fc-8e27-13705667c5a0`；task `1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27` READY；operation `ccad27fc-5cf1-432b-83f8-a71341efcff5`；execution `427e9751-2bca-483b-8d91-cb1711ae127c` PREPARED/no2，markedAt/taskAttempt NULL。**禁止 claim/send**，也不能先认领再跳过、改状态假隔离。真实队列只能按pool claim，无合法task选择接口；不要为测试另造生产精确claim捷径。

负例使用已建隔离 schema `ama_t11fix1_49609b94161e_behavior`，owner `task11fix1-49609b94161e4c90b6438ba683bfa3b1`，fixture名 `task11-fix01-isolated`；ACK `task-11-fix01-root-fixture-application-ack-20261025.json` SHA `35e262369af28a4d4ac70604538da152df5372ef5c286c566ebc40694650c027`。helper仅1次CREATE/GRANT，之后mount列表顺序比较失败；排序等同与env-only后验完成已封存，追加DDL0，原失败保留，不能称原helper PASS。新env0600，当前已完成16迁移，勿再假设空库。provider仍原owner，不复制密钥/初始化存储。

## 已完成，不重复

- Tasks1–10均已限定采用（含7/8）；Task9 source591545a4/adoption97e788fe，Task10 source86777940/adoptionba48f35e。原测试/独立审查及资格限制原样保留。
- Task11设计R1、preflight、29项SQL probe已采用准备；V013_4正常16迁移/current model/原15前缀、主库bootstrap及新隔离bootstrap已执行。主库bootstrap旧collector缺beforehash只具后验限定，不重写历史。
- 真实最终确认/冻结读取、主发布 main03（单份远端文档＋本地mapping）已通过；成功原execution GET1/POST0通过；丢响应后真实fair reconciler查询恢复通过，仍同execution/count1。该重启是同JVM应用context，不是fresh-JVM。
- 真实永久签名拒绝及原GET读回通过；原zero-effect-01因日志日期序列化整窗ERROR保留。local-terminal02、local-recovery03、HTTP安全与精确异常映射、maintenance负向扫描通过；maintenance正向/大于64/actual-loop未通过资格。
- 替代 accept04仅事务接纳/no publication POST；privateboundary、真实最终outbox写后整事务rollback、gap纯10项已通过。独立source gate仍失败，不可等同发送资格。
- fix01纯manifest GREEN5，结构compile重复5非新增distinct，privateboundary02/旧failureACK02/read-trace02各1PASS；shared-ledger与transfer-boundary各1PASS。它们仅覆盖具体断言，不关闭整项I1–I5。详见暂停索引，所有RED、ERROR、0tests、失败旧窗保留，不能加总重复测试为单一全绿套件。
- Task12仅静态入口准备完成：`task-12-static-entry-index-prepared-20261025.md/.json`，44限定路径/现有meetings Phase A壳/批准prototype锚点。没有Task12实现、浏览器或端到端验收。

## 五项独立阻断与未完功能

审查 `{report}`（SHA d8eea4beeb90ea9470d2f114a425e28d80b6440cdedfb42c87a361eecbf2f595），SPEC Fail/Quality Needs fixes，0Critical/5Important/0Minor。初审74文件manifest b8012cc81f0e8de43f74e3cf6c4107da8f01029f8863c3cda52e02180af47109是旧candidate，不能覆盖当前修复源码。

|项|要求与暂停状态|
|---|---|
|I1|完整同publication old/current TaskSpec union；旧失败ack已有真实GET通过，contradictory success隔离仍欠|
|I2|跨旧operation矛盾/manual必须冻结新marker/current effect，保留事实记录；已编辑，真实race/marker仍欠|
|I3|metadata-only pre-discovery，持根/父/行锁量度并读取LONGTEXT，锁外parse；read-trace局部通过，全路径仍需证据|
|I4|一次有效累计ledger覆盖发现、S/F/lookup、proof响应、parse/render/transport copies；部分通过，policy.prepare freshledger遗漏刚修，整链未复审|
|I5|三个reader共用严格manifest/transition原绑定＋真实完整predecessors/generation；纯5通过，完整历史语义与运行验证欠|

另 currentSources边界#4必须在真实SEND前闭合：current-effect用fresh source；historical/fenced receipt读取不能因此依赖当前EDIT/source。不擅自更改评审分级。矛盾SUCCESS测试只能按现R1固定endpoint/transport＋精确receipt binding认证；只有永久FAILED要求Ed25519 proof。test-only矛盾声明不等于真实远端文档effect，不能新增未审SUCCESS签名协议、改旧FAILED收据或旧UNKNOWN。

Task11后续仍需：实际replacement消费/改destination同key/new-final new-key/真实local-only PREPARED序号缺口/provider proof-CAS竞态；exact retry HTTP；真实revoked/fenced fairclaim与RecordedFactRecoveryPolicy；maintenance正向旧兼容崩溃/>64游标/actual-loop；owner/member/final/history/views/destinations及same-origin安全HTML relay双ACL；全部mutator冻结/容量/预算/rollback/race/strict parser/auth；fresh-JVM、combined actual runtime/native+S3；关闭Task10 M1三旧资格投影。完整矩阵看 `task-11-implementation-report-20261025.md` Latest checkpoint及R1§11–12，报告旧pending/最新通过冲突以sealed窗口及此暂停补充为准。Task11独立总覆盖/提交/采用尚未完成。

Task11采用后接Task12真实UI/四session/TLS native+S3浏览器/typecheck/build/草稿409幂等只读与重启；之后Task13相关最终gates、最强fresh独立全分支审查（固定下界34cb226aefe99c67d3681af68d748b0aeea4e073）、限定修复、准确验收/交付文档，完整B后停止。

## 永久保留的问题与资源

- 三个原UNKNOWN分别保留：Task10 execution `fd323464-54c7-4d80-ae93-ac0cd7925663` 原provider归属缺失；Task10 `0469c111-f07f-4428-acb8-3447ec0894ad` 原GET UNKNOWN；Task11 `07327d2f-3b2f-4bc1-ba6d-f9f03246d08a` 原GET UNKNOWN/APPLIED、taskRECONCILING/noeffectNULL。**禁止推断NOT_SENT/FAILED_ZERO_EFFECT、重发或由新成功关闭**。精确custody/disposition见pending register与各原fact文档。
- Task9 F5 extra preparation-F Minor、B-FINAL-REPLAY-INTEGRITY-01旧4caller mixedPROFILE、T8SR-M1、Task6/Task4 frontend遗留、Task7 Q01/Q02原未知失败与所有旧限定，均需Task13明确裁定；先读 `task-13-pending-issue-register-20261024.md` 和 cross-task closure map，不以旧采用豁免。
- 已有B01 IMM PARTIAL/B04 Q401 UNKNOWN、企业SSO/目录/model/KB合同缺失、HA/capacity/native physical I/O不合格边界保留；不推进C/D/E/F。
- 原owned MySQL CID11009a6b…，实际loopback46151（旧44727仅历史）；同容器768Mi/1CPU，先前OOM原因细分UNKNOWN，禁止重建/reset/repair/outOfOrder。新测试库均在同资源。原shared MySQL46653/S3资源保持，实际liveness后续read-only核。
- 受控KB当前最后已知PID=PGID231675/46287，原storeInstanceId98863bbd-a519-4553-9a09-1246ab221ec9，原`.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/knowledge-fixture-20261025/runtime/knowledge.sqlite`。本次进程快照仍见同PID；loaded SHA cd80e61d…，磁盘已有后续修改，**活进程不等于新源码已加载**。保留数据库/WAL/SHM/凭据，未来只能精确检查后原store reopen initialize=false，缺库拒绝、不换store/keys。暂停未重启/清理服务。
- 私有env/bearer/key只保留忽略目录0700/0600，不输出值或secret hash。JVM测试512MiB/2CPU，native/provider子进程64MiB/2CPU；重测试串行、单产品写者、审查只读。

## 暂停记录核验

暂停未新增开发/测试/SQL、未提交/merge/push。最后迁移与各结果只读采集。根的第一次只读ps命令因PowerShell转义产生awk语法错误，改为脱敏/proc观察，无业务影响。所有旧文档保留历史，在主树及产品树发现入口顶部指向本文件。后续“继续任务”撤销此用户暂停后，按首个接续节点推进，不能从旧Task9或首final RED重来，也不能跳过fix01、独立审查和剩余11→12→13。
'''
(P/docrel).write_text(text)
header=f'''# 最新权威状态：用户暂停 — {local.strftime('%Y-%m-%d %H:%M:%S')} Asia/Shanghai

用户要求停止开发并精准交接，当前开发已暂停。先读[最新暂停与接续文档]({P/docrel})，再读产品树W/current-state.json。当前HEAD `{head}`，Task1–10已采用，Task11 replacement fix round1未完成/未复审，独立gate 0C/5I/0M、SEND DENY；原READY/PREPARED no2禁止claim/send。新隔离库16迁移已通过，不重跑。首接续先定位generic-external回归1FAIL，然后补fix01真实隔离边界和同reviewer复审，继续全部Task11→12→13。Task12仅静态准备，Task13未完成；旧ACTIVE/迁移待跑/早期RED仅历史。主树仅文档发现入口，不merge/push，不进C/D/E/F。

---

'''
for root in (P,M):
 if root!=P:(root/docrel).write_text(text)
 for rel in ['DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md']:
  p=root/rel;p.write_text(header+p.read_text())
p=W/'resume-20261024-active-context.md';p.write_text(header+p.read_text())
s['updated_utc']=now.isoformat();s['latest_user_instruction']='停止任务并记录精确状态/编写接续文档；未来明确继续任务再恢复。';s['pause_request_active']=True;s['execution_status']='PAUSED_USER_REQUEST_TASK11_REPLACEMENT_FIX01';s['status_precedence']='Latest user STOP supersedes all old ACTIVE continuation headers. Resume only after explicit 继续任务; use pause document first.'
s['pause']={'requested':True,'localTime':local.isoformat(),'handoff':docrel,'recordDirectory':str(A.relative_to(P)),'head':head,'branch':branch,'productChangesCommitted':False,'activeAgentsObserved':['/root'],'agentFailure':'Previous coordinator usage limit; old child handles not live at pause. Preserve role context; do not loop respawn.','firstResume':'Diagnose sealed generic-external 1FAIL; isolated bootstrap already 1PASS; continue fix01 actual isolated marker/contradiction/fullbudget/currentSources then same-reviewer incremental review.','noTaskOrProviderRestartByPause':True}
s['active_implementation']['status']='PAUSED_USER_REQUEST_FIX01';s['active_implementation']['current_milestone']='PAUSED. Fix01 partial tests qualified; generic-external 1FAIL unresolved. Isolated bootstrap16/rerun0 actually PASS. I1-I5 not independently closed; primary no2 unclaimed/unsent.';s['active_implementation']['resumeFirst']=s['pause']['firstResume'];s['active_implementation']['fix01_isolated_fixture']['status']='BOOTSTRAP_16_RERUN_0_PASS_NOT_EMPTY';s['active_implementation']['fix01_isolated_fixture']['result']='artifacts/phase-b/b-remaining/task-11/replacement-fix01-isolated-bootstrap/result.json';s['active_implementation']['replacement_source_gate']['status']='PAUSED_FIX_ROUND_1_SEND_DENY';s['active_implementation']['replacement_source_gate']['fixReportStatus']='MISSING_NOT_YET_WRITTEN';s['active_implementation']['unresolved_generic_regression']={'window':'replacement-fix01-generic-external','totals':{'tests':1,'failures':1,'errors':0,'skipped':0},'assertion':'expected0/actual2','cause':'UNKNOWN_UNDIAGNOSED'}
s['active_review']={'task':11,'status':'PAUSED_AWAIT_FIX01_SCOPED_REVIEW','agent':'/root/b_continuation_coordinator/b11_independent_review','report':report,'priorVerdict':'SPEC Fail / Quality Needs fixes; 0C/5I/0M','preservedPreviousActiveReview':'See exact pre-pause current-state archive for completed Task10 historical review'}
save(W/'current-state.json',s)
with (W/'progress.md').open('a') as f:f.write('\n\n## USER STOP '+local.isoformat()+' — authoritative pause\n\n'+header+'\nActual isolated bootstrap result confirmed1PASS; generic-external1FAIL expected0/actual2 undiagnosed. Fix01 report missing, 5Important not reclosed, primary no2 no claim/send. No new tests/DB/product changes in pause; source/result/process index at '+pause+'.\n')
with (W/'task-13-pending-issue-register-20261024.md').open('a') as f:f.write('\n\n## User pause '+local.isoformat()+'\n\nTask11 fix01 generic external regression OPEN: replacement-fix01-generic-external 1FAIL expected0/actual2, cause undiagnosed. Independent I1–I5 0C5I0M still open until scoped review. Isolated normal16 bootstrap completed1PASS; do not reprovision. See '+docrel+' / current-state pause. User stop overrides old ACTIVE markers.\n')
save(A/'preimage-index.json',pre)
save(W/('pause-'+tag+'-record-index.json'),{'utc':now.isoformat(),'localTime':local.isoformat(),'handoff':docrel,'recordDirectory':str(A.relative_to(P)),'head':head,'branch':branch,'mainHead':mainhead,'sourceManifestSha256':sha(A/'current-public-source-manifest.json'),'sourceFiles':len(public),'trackedDiffSha256':sha(A/'tracked-product-diff.patch'),'indexEmpty':not index['stdout'].strip(),'testsIndexSha256':sha(A/'task11-result-index.json'),'preimages':pre})
print(json.dumps({'pause':pause,'handoff':docrel,'sourceFiles':len(public),'resultWindows':len(windows),'indexEmpty':not index['stdout'].strip(),'head':head,'mainHead':mainhead,'processSnapshot':proc},ensure_ascii=False))