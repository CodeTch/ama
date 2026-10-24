from pathlib import Path
import json,hashlib,datetime,shutil,re,subprocess
P=Path('/home/tanren/ama/.worktrees/phase-b-completion');M=Path('/home/tanren/ama');W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';A=W/'pause-document-audit-20261025';A.mkdir(exist_ok=False)
now=datetime.datetime.now(datetime.timezone.utc);local=now.astimezone(datetime.timezone(datetime.timedelta(hours=8)));sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
rels=['docs/acceptance/development-pause-b-remaining-2026-10-25.md','DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md']
pre=[]
for root,label in [(P,'product'),(M,'primary')]:
 for rel in rels:
  p=root/rel;d=A/'before'/label/rel;d.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,d);pre.append({'root':label,'path':rel,'sha256':sha(p)})
for rel in ['current-state.json','task-13-pending-issue-register-20261024.md','resume-20261024-active-context.md','progress.md','phase-b-cross-task-closure-map-20261024.md','task-11-implementation-report-20261025.md']:
 p=W/rel;d=A/'before'/'W'/rel;d.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,d);pre.append({'root':'W','path':rel,'sha256':sha(p)})
s=json.loads((W/'current-state.json').read_text());oldpausefields={k:v for k,v in s.items() if k.startswith('pause_')};save(A/'legacy-pause-fields-before-normalization.json',oldpausefields)
R=P/'artifacts/phase-b/b-remaining/task-11';wf=R/'replacement-fix01-generic-external';wb=R/'replacement-fix01-isolated-bootstrap'
a={x['path']:x for x in json.loads((wf/'source-manifest.json').read_text())['files']};b={x['path']:x for x in json.loads((wb/'source-manifest.json').read_text())['files']};delta=[k for k in sorted(set(a)|set(b)) if a.get(k)!=b.get(k)];test='backend/integration-tests/src/test/java/ama/task/Task11AffectedExternalIT.java'
generic={'test':test,'failedWindow':wf.name,'failedResultSha256':sha(wf/'result.json'),'failedSource':a[test],'latestBootstrapSource':b[test],'currentSourceSha256':sha(P/test),'postFailureSourceDeltaPaths':delta,'interpretation':'Original failure cause unproven; test and 7 other paths changed before later bootstrap. Bootstrap did not run this selector. No current-source green for generic regression; inspect exact source revisions before one targeted rerun after diagnosis.'};save(A/'generic-failure-source-window-comparison.json',generic)
h=json.loads((wb/'full-sixteen-history-metadata.json').read_text());assert (h['initialTables'],h['initialMigrations'],h['rerunMigrations'])==(0,16,0) and len(h['before'])==len(h['after'])==16 and h['before']==h['after']
model=P/'docs/database/ama-schema-draft.sql';sql=model.read_text();tables=len(re.findall(r'^CREATE TABLE ',sql,re.M));modelmeta={'path':str(model.relative_to(P)),'sha256':sha(model),'version':sql.splitlines()[0].split(':')[0].removeprefix('-- '),'tablesByCreateStatementCount':tables,'qualification':'Review model, not a full deployed99-table claim; formal application uses exact V013_4 migration, not review SQL.'}
modelpaths=[p for p in (P/'docs/database').iterdir() if p.is_file()];modelpaths += [p for p in (P/'docs/superpowers/specs').iterdir() if p.is_file() and '2026-10-23-ama-phase-b' in p.name];modelpaths += [P/'docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md'];save(A/'additional-model-and-authority-manifest.json',[{'path':str(p.relative_to(P)),'sha256':sha(p),'bytes':p.stat().st_size} for p in sorted(set(modelpaths))])
s['updated_utc']=now.isoformat();s['latest_user_instruction']='整理校验精准接续文档；仅文档修正，开发保持用户暂停。';s['active_context']='resume-20261024-active-context.md (latest user-pause header; canonical entry is pause_handoff)';s['pause_handoff']=s['pause']['handoff'];s['pause_source_head']=s['pause']['head'];s['pause_records_commit']='NONE; pause/audit documents uncommitted, index empty; Task1-10 adopted, Task11 uncommitted/incomplete.';s['pause_before_record_archive']=s['pause']['recordDirectory']+'/before';s['pause_record_index']='.superpowers/sdd/2026-10-23-ama-phase-b-remaining/pause-20261025-063507-record-index.json';s['pause_current_source_manifest']=s['pause']['recordDirectory']+'/current-public-source-manifest.json';s['pause_author_handoff']=s['pause']['handoff'];s['pause_runtime_observation']='Latest pause redacted snapshot retained KB231675; no Task11/Maven runner observed, unrelated services preserved; three original UNKNOWN remain OPEN; no pause-time SQL or all-schema zero-debt assertion.';s['pause_finalization_preimage']=s['pause']['recordDirectory']+'/preimage-index.json'
s['next_steps']='PAUSED. On explicit 继续任务: verify pause source/resource identities read-only, diagnose generic regression at its original source vs changed current source (not green), then continue Task11 fix01 isolated boundaries/ledger/currentSources and independent scoped review before any replacement SEND; finishTask11→12→13 thenstopB. Do not repeat adoptedTasks1–10/provision/bootstrap.'
s['pending_issue_register']='task-13-pending-issue-register-20261024.md';s['pending_issue_summary']='Task11 I1-I5 and generic regression OPEN; T10-M1Task11, T4SR04/05/06 andT6SR03Task12, F5/replay-integrity/T8SRM1/oldqualificationsTask13, three originalUNKNOWN OPEN. See latest pause explicit carry-forward table.'
s['design_gates_remaining']['11']='Adopted R1 design/preflight/29probe reused. FormalV013_4/currentmodel and normal16/rerun0 already executed on two ownedschemas. Implementation incomplete; replacement source gate0C5I0M/fix1 pending scoped review/SEND DENY; fullTask11review not complete.'
s['historical_pause_fields_archive']=str((A/'legacy-pause-fields-before-normalization.json').relative_to(P));s['delegated_coordinator_status']='NOT_LIVE at user pause; previous coordinator usage-limit failure. Role identity historical; future author/reviewer continuation needs actual available handle, not a running-agent assumption.'
s['resource_policy']['current_ports']['task8_owned_mysql']=46151;s['resource_policy']['task8_owned_mysql']['port']=46151;s['resource_policy']['task8_owned_mysql']['historicalProvisionPort']=44727;s['resource_policy']['task8_owned_mysql']['envQualification']='private_env is historical provisioning env and may contain old44727; current Task11 consumers must use their exact ACK/env46151, never rewrite/reconsume old env as current.';s['resource_policy']['historical_shared_env']=s['resource_policy']['private_env'];s['resource_policy']['private_env']=s['active_implementation']['privateEnv'];s['resource_policy']['current_task11_consumers']={'primary':s['active_implementation']['privateEnv'],'isolated':s['active_implementation']['fix01_isolated_fixture']['privateEnv'],'qualification':'Select fixed fixture+ACK through tools/task11_window.py; do not run selector with an implicit schema/env.'}
s['current_generated_model']['historical_task8_version_counts']={k:s['current_generated_model'].get(k) for k in ['version','counts','sha256','task11_ddl_status']};s['current_generated_model']['version']=modelmeta['version'];s['current_generated_model']['sha256']=modelmeta['sha256'];s['current_generated_model']['counts']='Current review SQL99CREATE TABLE statements/B00-1.7; publication_submission19cols. No fullreview-model deployed claim.';s['current_generated_model']['task11_ddl_status']='V013_4 installed via normal16/rerun0 in primaryTask11 and fix01isolated schemas. Migration/current-model acceptance bounded to exact evidence; do not repeat probe/bootstrap or apply review SQL.'
x=s['active_implementation'];x['successful_publication_main03']['qualification']='Happy publication+durable document mapping validated; exact terminal GET separately validated. Different execution lost-response same-JVM context recovery separately validated; fresh-JVM/combined runtime pending.';x['rejected_publication_original']['qualification']='Real durable signed rejection and exact readback; same-publication no2 replacement acceptance only later validated, still held READY/PREPARED/no-send. No live replacement-send qualification.';x['unresolved_generic_regression']['sourceWindowAudit']=str((A/'generic-failure-source-window-comparison.json').relative_to(P));x['unresolved_generic_regression']['currentSourceStatus']='CHANGED_AFTER_FAILED_WINDOW_NO_GREEN';x['fix01_isolated_fixture']['historyProof']={'path':str((wb/'full-sixteen-history-metadata.json').relative_to(P)),'initialMigrations':16,'rerunMigrations':0,'rowsBefore':16,'rowsAfter':16,'tenFieldHistoryEqual':True}
s['pause']['audit']={'status':'DOCUMENT_CORRECTIONS_ONLY_DEVELOPMENT_REMAINS_PAUSED','path':str(A.relative_to(P)),'utc':now.isoformat()};s['skill_runtime_current_note']='Original Superpowers7.0.1 workflow is historical authority; original SKILL.md is absent at audit and not in current available catalog. No replacement installed. Future continuation recheck available workflow; preserve single writer/serial tests/private authority/source-review gates and explicit role settings. Do not silently bypass gates or substitute reviewer model because an old handle/plugin is unavailable.'
save(W/'current-state.json',s)
doc=P/s['pause']['handoff'];body=doc.read_text();body=body.replace('断言 expected0/actual2（压缩源line5）。根因尚未定位','断言 expected0/actual2（压缩源line5）。该失败窗测试源码SHA为6c241fe9…；当前源码已改为89ccd38d…，后续bootstrap未运行此selector，**尚无当前源码GREEN**。根因尚未定位')
body=body.replace('当前未检测到本轮 Maven/Task11 测试进程','暂停快照未检测到本轮 Maven/Task11 测试进程')
add='''
## 文档校验补充（2026-10-25；仅整理，继续暂停）

**先做只读重新定位，再恢复任何测试。** 对照 W/pause-20261025-063507/current-public-source-manifest.json 核当前968份源码、HEAD/index及两个fixture ACK；源码有差异先判断是否是之后合法修改，不回滚覆盖。核当前CID/端口/原store/process-startticks/loaded source（均是暂停时快照，PID/port不能当永久事实）。不得先开全pool worker；原主库no2仍禁claim。资源不可用时保留UNKNOWN/原库，不能重建；旧agent不可恢复或指定模型额度不可用时保留精确brief，不循环重派、不擅自降低评审门禁。

### 原失败与当前源码必须分开

`replacement-fix01-generic-external`失败窗测试原SHA `6c241fe9da6b72788e5ac11d8d17dfbf2e8e4f2456a0b1e758aa557ca94c4fe9`（2249字节），当前为 `89ccd38df03a3e18f114c52143c39d63ec4670a6070cb9945ad5dfcac1f4a61b`（2322字节）。到后续bootstrap共8路径改变，包括两份产品源、测试和launcher，详见 W/pause-document-audit-20261025/generic-failure-source-window-comparison.json。不能把bootstrap成功当generic回归通过，不能把旧失败直接归因于当前源码。后续先读原sealed log/XML/manifest与当前fixture，确认已有改动的目的和失败原因，再只跑必要selector。原窗命令为 `/usr/bin/python3 tools/checks/run.py it Task11AffectedExternalIT`，但**不能脱离固定fixture/ACK/私有env直接执行这条原始命令**；调度入口是 `tools/task11_window.py`，读取其当前接口与对应command.json/ACK，明确schema后再运行，避免默认连错库。此处只登记证据与入口，未修产品或测试。

### 显式遗留分工，避免大类摘要漏项

|负责人窗口|必须接收的具体事项|
|---|---|
|Task11|T7CV04剩余final readiness-before-advance、publication read/application projection；T10-M1三条旧eligibility投影及实际HTTP；独立source I1–I5/fresh currentSources、generic当前源码未GREEN；全R1真实安全/恢复/运行时矩阵|
|Task12|T4SR-04列表limit溢出2147483648应400；T4SR-05 partSize正long定义/原token不丢精度；T4SR-06 Redocly命名告警与外部banner区分；T6SR-03四mutation aliases含unknown；generated.ts机械再生成与校验；web README仍称health-only的过时描述|
|Task13|Task9-F5、B-FINAL-REPLAY-INTEGRITY-01、T8SR-M1实际prefetched-candidate删除竞态；FUP-B-REMAINING-TEST-01/02；T03-IR-M01/M02；T8-HIST-OPTIONAL；T11PROBE-M1 prepared report四/五blob措辞；Task7 Q01/Q02及Task9原首次观察失败原因UNKNOWN；所有旧来源窗口/资源/collector限定与三个原UNKNOWN|

这些事项有的要求修复、有的仅要求最终独立审查裁定/披露，不能全部宣称产品缺陷，也不能自动关闭。精确规则以 pending register、原review、既有adoption及当前R1为准。

### 权威输入与明确文件

- 总计划：`docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md`；Task11：W/`task-11-final-publication-design.md`，重点§8及§11–12；fixbrief、独立报告和coverage分别为 W/`task-11-replacement-fix01-author-brief-20261025.md`、`task-11-replacement-independent-source-review-r1-20261025.md`、`task-11-replacement-independent-source-coverage-r1-20261025.json`。
- Task12批准设计：`docs/superpowers/specs/2026-10-23-ama-phase-b-web-loop.md` 及 W/`task-12-web-business-loop-design-r1.md`；复用 `task-12-deferred-boundary-map-20261024.md`、`task-12-transcript-request-type-followup-20261024.md`、`task-12-static-entry-index-prepared-20261025.md/.json`，最终Task11接口采用后只作增量repin，不重复设计或旧完整preflight。
- 原UNKNOWN精确处置：W/`task-10-f2-original-unknown-custody-ruling-20261024.md`、`task-10-closing02-original-facts-disposition-20261024.md`、`task-11-original-publication-unknown-disposition-20261025.md`。合法fair claim可能只变lease/owner/time，需准确记录；不声称旧行逐字节未变。
- 资源完整CID/路径：MySQL `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`→46151；shared MySQL `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`→46653；S3 `a91f5946d5f619335d5cfb6bf2295b1df46abbb4a75b2db8feccaf637bcb7d51`→45501。完整公开ACK分别在W/`task-11-root-fixture-application-ack-20261025.json`、`task-11-fix01-root-fixture-application-ack-20261025.json`；env分别在`.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/env.sh`、`.tools/task11fix1-49609b94161e4c90b6438ba683bfa3b1/env.sh`。只读路径不输出私有内容。最新已知KB loaded SHA完整值在state/原restart ACK；当前磁盘不是该loaded版本。
- 当前review model为B00-1.7-publication-submission（SQL99张表），`docs/database/ama-schema-draft.sql`及其他当前model/权威文档另记在 W/pause-document-audit-20261025/additional-model-and-authority-manifest.json。**它是review SQL，不能送入Flyway或声称99表全部已部署。** 旧state B00-1.6/Task11 DDL pending与44727已归档更正。
- Task13交付明确包括 `docs/acceptance/b/summary.md`、demo/test qualification、pending integration和DEVELOPMENT_HANDOFF/接续入口；按已固定下界34cb226a做新全分支审查，原W/证据持续保留。角色沿既有授权：核心作者/协调及任务独立审查Astra high，机械Sol xhigh，最终整分支Astra xhigh；实际可用性先核，审查不能由作者自审代替。
- 原Superpowers7.0.1路径在本次文档校验时已不存在，也不在当前available skills列表。此事实不恢复开发；下次继续先核工作流资源，保留已批准的单写者、串行测试、独立审查、DDL/source/send门禁，不因工具/旧handle缺失跳过，也不为文档校验安装或改模型。

### 保存与校验范围

暂停的968份公开源码清单是**哈希定位清单，不是完整文件备份**；tracked diff不包含untracked新源正文，必须保留现有整棵工作树。原64窗口索引也是结果/来源索引，不是最终semantic或全源复审，不可汇总成64窗全绿。原pause-record-verification.json是06:35时点的历史验证，文档更新后其旧hash理应变化，不能覆盖旧验证伪装从未修改；本次另存审计preimages与新verification。所有校验只读源码/既有证据，不运行测试、不查DB、不启动worker、不提交。开发继续暂停。
'''
body+='\n'+add
for root in (P,M):(root/s['pause']['handoff']).write_text(body)
brief='\n文档校验补充：state旧pause_*、next_steps、44727及B00-1.6字段已规范化；generic失败窗源码与当前源码不同，尚无当前GREEN。详见最新暂停文档“文档校验补充”，开发继续暂停。\n'
for root in (P,M):
 for rel in ['DEVELOPMENT_HANDOFF.md','docs/acceptance/development-resume-b-remaining-2026-10-24.md']:
  p=root/rel;t=p.read_text();i=t.find('\n---\n');assert i>=0;p.write_text(t[:i]+brief+t[i:])
for rel in ['resume-20261024-active-context.md','phase-b-cross-task-closure-map-20261024.md','task-11-implementation-report-20261025.md']:
 p=W/rel;t=p.read_text();i=t.find('\n---\n');assert i>=0;p.write_text(t[:i]+brief+t[i:])
with (W/'progress.md').open('a') as f:f.write('\n\n### Document audit '+local.isoformat()+' — no development resumed\n'+brief+'Audit source-window difference8paths;16history full10fields same; oldactive state pointers/resources/model corrected with beforearchive. Details pause-document-audit-20261025; no tests/SQL/worker/product mutation.\n')
with (W/'task-13-pending-issue-register-20261024.md').open('a') as f:f.write('\n\nDocument audit: generic regression original test source6c241fe9 differs current89ccd38d (8paths changed by bootstrap); original cause undiagnosed/no currentGREEN. Explicit carry-forward table in latestpause includes T7CV04/T4SR04-06/T6SR03/T11PROBEM1/T8HIST/Q01Q02/optional and all priorUNKNOWN. No closures by documentation.\n')
checked=[]
for rel in [s['pause_handoff'],s['pause_current_source_manifest'],s['pause_record_index'],s['pause_finalization_preimage'],'tools/task11_window.py','docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md','docs/superpowers/specs/2026-10-23-ama-phase-b-web-loop.md']:
 checked.append({'path':rel,'exists':(P/rel).exists()})
for rel in ['task-11-final-publication-design.md','task-11-replacement-fix01-author-brief-20261025.md','task-11-replacement-independent-source-review-r1-20261025.md','task-11-replacement-independent-source-coverage-r1-20261025.json','task-12-web-business-loop-design-r1.md','task-12-deferred-boundary-map-20261024.md','task-12-transcript-request-type-followup-20261024.md','task-12-static-entry-index-prepared-20261025.md','task-10-f2-original-unknown-custody-ruling-20261024.md','task-10-closing02-original-facts-disposition-20261024.md','task-11-original-publication-unknown-disposition-20261025.md']:
 checked.append({'path':str((W/rel).relative_to(P)),'exists':(W/rel).exists()})
assert all(c['exists'] for c in checked)
public=json.loads((W/'pause-20261025-063507/current-public-source-manifest.json').read_text())['files'];bad=[e['path'] for e in public if not (P/e['path']).is_file() or sha(P/e['path'])!=e['sha256']];assert not bad
assert (P/s['pause_handoff']).read_bytes()==(M/s['pause_handoff']).read_bytes();assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=P,text=True).strip()
verification={'utc':now.isoformat(),'developmentPaused':s['pause_request_active'],'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=P,text=True).strip(),'primaryHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=M,text=True).strip(),'indexEmpty':True,'publicSourceFilesChecked':len(public),'sourceMismatches':bad,'primaryMirrorEqual':True,'bootstrapHistory16TenFieldsEqual':True,'references':checked,'knownMissingExpectedOutput':'task-11-replacement-fix01-report-20261025.md','noTestsOrSqlOrWorkers':True,'documents':[{'path':str(p),'sha256':sha(p)} for p in [P/s['pause_handoff'],M/s['pause_handoff'],W/'current-state.json',P/'DEVELOPMENT_HANDOFF.md',M/'DEVELOPMENT_HANDOFF.md']]}
save(A/'verification.json',verification);save(A/'preimages.json',pre)
report=f'''# 精准接续文档审计 — {local.isoformat()}

开发保持暂停。文档有疏漏/过时字段，已在原入口补正并同步主树副本。

1. current-state的next_steps和9个旧pause字段仍指向Task9/旧HEAD/2026-10-24，且resource_policy端口仍44727、model仍B00-1.6/DDL待执行。已保存前字节及原字段，统一到当前pause/HEAD、46151、B00-1.7与实际16迁移事实，不改历史ACK/env。
2. 通用回归失败窗源6c241fe9与当前89ccd38d不同（8路径变化），旧文档只写原因未知，未提示后续源码已变/无当前GREEN。已补来源比较与先诊断、明确fixture入口后有针对性验证；本次未调试或改产品/测试。
3. 原文大类描述未逐项列出T7CV04、T4SR04–06、T6SR03、T11PROBEM1及历史可选/未知资格；已补精确分工表与权威文件路径，不能自动当缺陷或关闭。
4. 资源恢复只给缩写/CID片段，且未明确恢复先只读核验、全pool worker禁启动、旧模型reviewSQL不可部署、哈希清单不是完整备份。已补完整公开定位、私有路径引用和证据范围。
5. 原技能/agent可用性不能按历史假设；已记原Superpowers路径当前缺失/旧agent不可用，既有独立审查与指定角色门禁继续保留，未安装/切模型或恢复开发。

核验：原968份公开源码无差异；HEAD/index保持；新库bootstrap history16行×10字段before/after完全一致；{len(checked)}个关键引用均存在；主树/产品树暂停文档一致。旧独立审查仍0C/5I/0M、SEND DENY，generic仍无当前GREEN，原no2与UNKNOWN状态未触碰。原暂停verification作为历史保留，本次另存verification.json。

材料：before/与preimages.json；generic-failure-source-window-comparison.json；additional-model-and-authority-manifest.json；legacy-pause-fields-before-normalization.json；verification.json。未新增开发、测试、SQL、进程启停或提交。本文不等于Task11源码复审或B验收。
'''
(A/'report.md').write_text(report)
print(json.dumps({'audit':str(A.relative_to(P)),'sourceFiles':len(public),'sourceMismatches':bad,'criticalReferences':len(checked),'changedSourcePathsSinceFail':len(delta),'model':modelmeta,'paused':True},ensure_ascii=False))