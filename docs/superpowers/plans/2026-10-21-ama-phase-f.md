# F：生产部署、性能、恢复与上线交接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实环境证据证明部署组合、容量、安全和自动恢复达到约定目标，并交付可执行的运维/用户手册。

**Architecture:** 应用双活；私有数据单活动资源组先隔离再提升。生产环境通过受控配置、数据库兼容迁移、备份恢复和负载故障报告获得资格。

**Tech Stack:** RHEL 8.10、Podman/systemd、HAProxy、Pacemaker/Corosync/DRBD、MySQL、local/私有S3/公司S3；测试工具沿用 A/C。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 4—5、12、14 节；[部署条件及 HA-01…14](../specs/2026-10-21-ama-deployment-prerequisites.md)；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；私有模式不增加第三台项目节点，外部压测客户端是测试资源，不作为生产服务节点。
- 入口、数据资源不由多个独立控制器抢占；私有服务不能在备用机被 systemd/Podman 擅自启动。
- 恢复后单台仍承载100场实时/300在线；不能降低实时负载后宣布故障容量通过。
- NFR-07 目标≤120秒；NFR-08 仅对健康同步且落盘资格已验证的已确认数据要求 RPO=0；双节点/共同故障不属于同一保证。
- 公司接口未授权时不压测，故障注入仅在安排好的测试环境执行；上线是独立发布决定，不因测试脚本存在就自动部署生产。

## Review Focus

1. 挂载失败后程序写入系统盘同名目录：F-01/F-02 必须拒流。
2. 网络分区双方都误认自己可写：F-02 用独立时间线验证唯一写入与隔离顺序。
3. 恢复旧备份把已删除内容重新开放：F-03 应用删除/撤权抑制清单后才开放访问。
4. 压测只记成功请求或忽略恢复期间缺口：F-04 将错误、缺口、超时纳入报告。
5. 旧新版应用并存时数据库迁移不兼容：F-06 检查扩展/迁移/收缩和回滚边界。

## Task F-01：组合部署包与资源约束

**Files:** Create `deploy/containers/Containerfile.api`、`deploy/containers/Containerfile.realtime`、`deploy/containers/Containerfile.worker`、`deploy/containers/Containerfile.file`、`deploy/systemd/ama-app.container`、`deploy/ha/resources.yaml`、`deploy/config-matrix.json`、`tools/ops/validate_deployment.py`、`tools/ops/tests/test_deployment.py`、`docs/runbooks/deployment.md`。Modify A-04 inventory 和 A-01 runner。

**Interfaces:** CLI `python3 tools/checks/run.py ops validate-deployment`，只验证配置；矩阵为私有/公司 MySQL × local/私有S3/公司S3，共6组合。各模式生成选定组件与服务职责表，local 不要求 S3 密钥；私有 S3 用 A 阶段已资格验证的固定版本。

- [ ] 写六组合配置测试，禁用模式不得隐式启动依赖；错误挂载/无隔离/两套资源管理配置均失败；`assert result['valid'] is False`，平台全外置不要求本地数据组件。
- [ ] 运行 `python3 -m unittest discover -s tools/ops/tests -p test_deployment.py`，确认无效配置检不出时失败。
- [ ] 实现固定镜像摘要、TLS/密钥文件引用、非root账号、内部端口限制、日志/指标、资源上限和单活动数据资源顺序；单台预算以实际可用内存重新核算，初始总预算32GiB不硬套十进制32GB。
- [ ] 在批准测试环境验证全部6组合的启动/关闭/健康/基础闭环，配额和能力不足的组合记录失败或待环境；校验无秘密入镜像、无测试身份进生产配置。
- [ ] 提交 `ops: package configurable two-node deployments`；模板可先完成，真实部署资格不能跳过环境验证。

## Task F-02：自动恢复与文件持久化资格

**Files:** Create `tools/ops/fault_scenarios.py`、`tools/ops/verify_fault_report.py`、`tools/ops/tests/test_fault_report.py`、`deploy/fault-scenarios.json`、`docs/acceptance/f/fault-matrix.md`、`docs/runbooks/failover.md`。

**Interfaces:** `verify_fault_report(events:list,acked_manifest:dict,recovered_manifest:dict)->dict`，结果 includes uniqueWriter/fenceBeforePromotion/rtoSeconds/missingAckedChunks/hashMismatch/pass。故障命令的 VM ID 必须匹配 A-04/F-01 的已批准测试清单；默认 dry-run，不接受自由 shell 注入。

- [ ] 写“先提升后隔离”“双写区间”“缺一个已ACK文件”报告反例；`assert report['pass'] is False`；错误场景不能被空样本集视为通过。
- [ ] 运行 `python3 -m unittest discover -s tools/ops/tests -p test_fault_report.py`，确认报告校验失败。
- [ ] 实现 HA-01…14 场景和独立时间线采集；包含进程退出、VM卡死/断电、网络分区、隔离失败、副本落后、重同步、磁盘满、写入中断、发布未知和权限失效。
- [ ] 在安排好的隔离测试环境执行 `python3 tools/checks/run.py ops fault-matrix`；每个私有数据组合验证先隔离后提升、≤120秒目标及全部已ACK分片哈希；隔离失败应安全拒绝提升，不要求在不安全条件下满足正常RTO。
- [ ] 恢复第二台并验证只作备用重同步、不自动切回；报告故障期间不可用、未ACK范围和识别追平，不隐藏中断。
- [ ] 提交 `test: qualify automatic failover and durable file recovery`；证据缺一项则相应资格不通过，不能用本地进程杀死代替VM/存储故障。

## Task F-03：备份恢复、文件迁移与删除后恢复保护

**Files:** Create `tools/ops/backup_manifest.py`、`tools/ops/verify_restore.py`、`tools/ops/migrate_storage.py`、`tools/ops/tests/test_restore.py`、`backend/worker/src/main/java/ama/worker/StorageMigrationHandler.java`、`docs/runbooks/backup-restore.md`、`docs/runbooks/storage-migration.md`、`docs/acceptance/f/restore-report.md`。

**Interfaces:** `verify_restore(database_manifest,file_manifest,suppression_manifest)->dict`；迁移任务固定 sourceBackendId/targetBackendId/fileId/expectedLocationRevision；复制校验后条件更新，读取/删除仍按位置记录路由。备份清单含 DB 恢复点/binlog、不可变文件键与哈希、上传/删除状态、密钥受控引用。

- [ ] 写文件缺失、哈希不符、删后恢复、迁移过程中再删除和在途上传测试；`assert restoredAccess == 'DENIED'; assert location.backendId == originalBackendId`（复制未完成时）；不得仅以文件数量相同通过。
- [ ] 运行 `python3 -m unittest discover -s tools/ops/tests -p test_restore.py`，确认恢复完整性与迁移保护失败。
- [ ] 实现协调恢复点、binlog恢复、本地/S3引用核验、删除/撤权抑制清单；迁移期间保留旧后端服务直到所有引用/在途会话完成，不能仅切默认配置就关闭旧local文件服务。迁移限并发并预算新旧服务/副本共存空间，超过资源余量时安排迁移窗口，不假定满载时可无成本迁移。
- [ ] 在隔离恢复目标执行 `python3 tools/checks/run.py ops restore-and-migrate`，包括 local→s3、s3→local、中断重试与回滚；验证历史版本、有效发布、参会权限、上传续传和已删内容不重新可见。
- [ ] 提交 `ops: add verified backup restore and storage migration`；双机内备份不能证明机房级容灾，企业外部备份如获准另记目标和恢复证据。

## Task F-04：性能、稳定性与故障后容量

**Files:** Create `tools/load/scenarios/production-matrix.json`、`tools/load/tests/test_acceptance_report.py`、`docs/acceptance/f/performance-report.md`、`deploy/observability/alerts.yaml`。Modify C-07 loader/report 和 runner。

**Interfaces:** `python3 tools/checks/run.py load production-matrix`；报告按部署组合、模型模式、健康/故障/恢复阶段分别统计，不混合平均。

- [ ] 写报告判定测试：漏算失败请求、仅99路采集、峰值内存超预算、缺少恢复阶段均不得通过；`assert acceptance['passed'] is False`。
- [ ] 运行 `python3 -m unittest discover -s tools/load/tests -p test_acceptance_report.py`，确认门槛判定失败。
- [ ] 准备10万会议/100万片段样本及脱敏长音文；负载为100录音＋200其他用户，含20回听、10编辑及5离线任务；每组合2小时并另做8小时稳定性测试，真实模型与模拟模型分别运行和标记。
- [ ] 执行满载、私有数据接管后单台满载及第二台重同步负载；收集CPU/内存/GC/连接/磁盘/网络/模型队列、单请求全链路时延、错误和所有 ACK 核验。
- [ ] 根据下表判定并修复实际瓶颈，修复后仅重跑受影响场景及必要回归；提交 `test: validate production load and recovery capacity`。

| 指标 | 通过标准 |
| --- | --- |
| NFR-01/10 | 100场实时＋总计300在线，2小时及8小时稳定性；恢复后单台同负载，不降低录音数 |
| NFR-02 | 普通接口浏览器发起至完成 p95≤500ms、p99≤1500ms；文件/长任务单列 |
| NFR-03 | 持久化任务受理 p95≤1秒 |
| NFR-04 | 分片封口发送至 durable ACK p95≤2秒，另报告首采样至确认 |
| NFR-05 | 语音片段结束至临时字 p95≤3秒、稳定字 p95≤5秒；真实模型通过 |
| NFR-06 | 服务健康后重连 p95≤10秒；DB可用后失效任务30秒内可重领 |
| NFR-07/08 | F-02 的≤120秒恢复与条件化 RPO=0 证据通过 |
| NFR-09 | 月可用性目标99.9%；外部依赖故障计入用户可用性并另归因。上线前验告警/探测/统计口径，上线后用完整月观测评价，8小时测试不能证明月SLA |

## Task F-05：安全、兼容和最终功能覆盖

**Files:** Create `backend/integration-tests/src/test/java/ama/security/SecurityAcceptanceIT.java`、`web/tests/e2e/security-access.spec.ts`、`web/tests/visual/governance-pages.spec.ts`、`tools/checks/verify_coverage.py`、`docs/acceptance/f/security-report.md`、`docs/acceptance/f/requirements-evidence.csv`。

**Interfaces:** 证据表每行 requirementId/taskId/commit/command/report/status/environment；验证器对未覆盖、失败、仅mock却标真实通过的行返回非零。

- [ ] 写跨会议/文件/候选/专题越权、CSRF/WSS Origin、离职/撤权、Markdown XSS、材料 SSRF/解压炸弹/路径逃逸、日志密钥泄露和生产测试后门检查；`assertEquals(403, crossMeetingRead.status());`，恶意材料不得执行指令/联网/发布。
- [ ] 运行 `python3 tools/checks/run.py it SecurityAcceptanceIT` 和 `python3 tools/checks/run.py e2e security-access.spec.ts`；已有保护应通过，先加入能证明缺口的负例再修复，不为了 RED 人为破坏正确安全代码。
- [ ] 修复证据确认的问题；按 A-05 原型索引完成所有业务及 E 治理页面视觉对比、支持浏览器录音/缓存验证、中文长文档和导出检查。
- [ ] 运行安全/视觉/覆盖验证，确认全部60项功能和10项架构需求均有实现及适用验收证据；不以综合百分比掩盖一项关键权限/保存失败。
- [ ] 提交 `test: close security browser and requirements acceptance gaps`。

## Task F-06：发布、回滚、交接与试运行

**Files:** Create `docs/runbooks/release-rollback.md`、`docs/runbooks/incident-response.md`、`docs/user-guide/meeting-assistant.md`、`docs/acceptance/f/release-checklist.md`、`tools/ops/release_preflight.py`、`tools/ops/tests/test_release_preflight.py`。Modify `README.md`。

**Interfaces:** `release_preflight(evidence_manifest,release_manifest)->dict`，输出 ready/blockers，不直接执行生产发布。release manifest 包含镜像摘要、配置版本、迁移版本、回滚边界、外部契约版本和负责人。

- [ ] 写缺故障/真实模型证据、旧新版schema不兼容、无备份/回滚资料的反例；`assert result['ready'] is False`；仅架构/计划批准不能作为生产验收证据。
- [ ] 运行 `python3 -m unittest discover -s tools/ops/tests -p test_release_preflight.py`，确认门槛判定失败。
- [ ] 实现发布预检，编写演练过的摘流/停领新任务/租约交接/兼容迁移/回滚操作；不可逆迁移明确回滚范围，不能承诺任意版本一键回滚。
- [ ] 在测试环境演练旧新应用并存、失败回滚及恢复验证；交付告警值班、故障归因、备份恢复、删除/发布对账、配置/密钥轮换、用户完整操作手册。
- [ ] 整理可审阅发布包，产品/运维完成上线决定后安排受控试运行；记录月99.9%观测口径、缺陷处理和移交结果，不在计划编写时部署或安排自动提醒。
- [ ] 提交 `docs: deliver release rollback and operational handover`；`docs/acceptance/f/summary.md` 明确已验收/待环境/未通过，不以“功能基本可用”替代验收。

## 最终交付判定

- [ ] A—E 全部范围完成，真实外部契约及公司授权验证通过；没有功能被 mock 永久替代。
- [ ] 6组合配置及适用实测、HA-01…14、性能与安全、备份/迁移/回滚证据齐全；不适用项需说明依据。
- [ ] 生产发布经过具体发布包审阅；试运行与长期可用性观测分别记录，不提前宣称月SLA已经实现。
