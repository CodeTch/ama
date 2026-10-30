# Phase B acceptance evidence

> **2026-10-30 R2 continuation:** [AMA-DEMO-FIRST-20261028 R2](../../superpowers/plans/2026-10-28-ama-demo-first-adjustment.md) distinguishes **M1-Demo (not yet accepted)** from **M1-B formal local acceptance (INCOMPLETE)**. DEMO-01 CAS 登录/回调/会话链已使用 `ama-integration-harness` 的真实 CAS 端点完成受控本地验证；这只关闭该片段的 CAS 接线缺口，不改变整体 Demo/B 状态。Adopted source and local/S3 API results below remain unchanged. First-Demo blockers and deeper B-QUAL obligations are assigned below; unfinished S3/dual-MySQL/active-active work and exclusive dependencies remain pending in final F. Controlled workflow, actual platform capability and production qualification are separate statuses. Historical runtime approvals are consumed.

This document distinguishes source verification from actual environment qualification. It does not declare Phase B accepted. The authoritative active task and review state is `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json`; this document records acceptance boundaries and supporting evidence.

<a id="current-open-obligations-r2"></a>

## 当前开放义务分流表（R2）

这是当前验收缺口的唯一调度表，来源为开发树证据目录 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/` 中的 `bl5-final-qualification-matrix-20261028.md`、原 open-obligations、local/S3 实际结果和本地启动器记录。原件、已通过范围及未通过事实均不改；下列编号只用于跟踪当前待办，不替换原 Task/B-L/F5 等来源编号。本轮新增 DEMO-01 CAS 接线证据，但没有将其扩展为整体 B 或真实企业能力通过；表内“需补”均为未完成。

“条件阻塞”须有明确判断：只有基本路径已有适用证据、当前无已知重要缺陷且未修改相关高风险保护时，深入证明才可在首次 Demo 后完成；否则提前阻塞该片段。证据复用须匹配源码、schema、装配和断言，不能把 S3 的环境结果直接改写为当前 local 通过。发现缺陷立即升级，不等最晚关卡。维护者在对应行附最新证据/版本/判定，避免另建重复台账。

| ID / 原义务 | 已有事实与当前缺口（均按原范围） | 是否阻塞 M1-Demo | 最晚关卡 / 负责任务 / 需补证据 |
| --- | --- | --- | --- |
| B-R01 本地可重复装配 | 日常入口已接本地 MySQL、CAS 登录/回调、会议列表/详情/保存；文件/Worker/模型/发布仍未装配 | 是 | DEMO-02～04；沿现有 local_app 增量接线，启动/状态/停止/重开与数据保留，不依赖 S3 |
| B-R02 浏览器与原型 | B-L1～4 源码已采纳；完整浏览器不合格，纪要/发布等原型参考未完整索引 | 是 | DEMO-01～05；逐页矩阵、两视口关键布局、真实端到端、刷新/IME/双标签代表案例；全产品三视口留 D-08 |
| B-R03 信息并发/幂等 | 旧 S3 批次有 200/409 和 exact replay；精确 interleaving/ambient transaction 未全证 | 基本冲突/幂等与草稿保留必须有证据；深层条件阻塞 | 基本在 01/05；精确 MySQL 并发、同 holder/事务与回滚最晚 M1-B / B-QUAL |
| B-R04 导入原子性 | 双文件单 durable graph、坏第二文件零部分 RAW 已有限定证据；写入后强制异常回滚未证 | 坏输入不得部分成功；深层条件阻塞 | 基本在 02；真实写后异常、关联登记/task/outbox 原子回滚最晚 M1-B |
| B-R05 生成原子性 / S→A→F | 陈旧生成零修改、并发一次接受/一次 503 有证据；503 原因和精确阶段不明 | 旧版/未确认拒绝、无额外候选/副作用必须成立；深层条件阻塞 | 基本在 03；精确 S/A/F 屏障、所有相关状态/候选/task 原子提交及回滚最晚 M1-B；旧 503 不当作完整证明 |
| B-R06 成员私有区与撤权 | saved-only/私有字段省略/名单撤回后拒绝已有证据；确定性锁阶段竞态未全证 | 基本越权/撤权拒绝必需；深层条件阻塞 | 基本在 01～04；身份停用/名单变更/读取中撤权的精确竞态最晚 M1-B |
| B-R07 发布 UNKNOWN/ACK | S3 丢响应后仅查询恢复成功已记录；浏览器 ACK 与 precommit crash/UNKNOWN 边界未全证 | 原请求恢复、未知只查回、重复点击无重复文档是门槛；深层条件阻塞 | 浏览器代表路径在 04/05；关键 precommit crash、原始 ACK/新观察、纠正/refinal/零副作用重试等开放断言最晚 M1-B |
| B-R08 正式文档双重 ACL | owner 200、member provider 403、outsider/admin 403；成员正向读取与精确撤权未证 | 是：需成员双方授权时可读、任一方拒绝时不可读及基本撤权 | DEMO-04 完成受控真实链 allow/deny；读中撤权确定性竞态最晚 M1-B；实际企业 ACL 列 PLATFORM-B |
| B-R09 发布冻结写保护 | MARKED/UNKNOWN/SUCCESS-unapplied 的 12 代表写保护已采纳 | 已有范围保留；当前 UI 成功只读与受影响保护须验证 | 04/05 定向复核界面/直接写负例；不重新宣称旧 guards 缺失，B-QUAL 仅补最终版本受影响项 |
| B-R10 B3 维护/对账共存与饱和 | O5/O6 为限定证据，legacy repair 真实 origin 不可用；全共存/有界饱和未完 | 条件阻塞；普通任务推进与无重复副作用是 Demo 门槛 | M1-B / B-QUAL：必需共存/饱和实际证据；来源不可得保留未验证及范围判断，不伪造 SQL 业务行替代 origin |
| B-R11 B6 应用/Worker/模型/KB 重启、关键 crash | 有 local/S3 live chain、部分 provider fresh-process readback；不是完整重启/crash 资格 | 常规停止/重开保留当前链数据是门槛；所有独立进程/crash 组合条件阻塞 | 05 常规重开；M1-B 补各关键组件持久恢复、在途任务/外部未知与关键崩溃；主机/存储灾难才归 F |
| B-R12 预算、锁序及 genuine authority | 源码/离线与小样本运行不证明读取前预算和全图锁保护 | 基本安全和实际配置必须成立；深层条件阻塞；相关协议若改动须先证 | M1-B / B-QUAL：真实 MySQL 元数据→reserve→正文 transfer、精确上限/Unicode/累计拷贝/取消、同 holder/完整集合/锁序、ambient transaction 与实际竞态 |
| B-R13 行动负责人选择 | `ENTERPRISE_SELECTION_NOT_INSTALLED` 已观察，尚未装配 | 必需审阅/动作依赖它即阻塞；非演示能力须明确不可用 | DEMO-03 复用本地目录最小接线并验证；公司人员来源与权限另列 PLATFORM-B |
| B-R14 最终集成与 Task13 | 既有最终限定审查无新增 Critical/Important，不能覆盖未来改动；B 尚未通过 | Demo 的受影响回归/类型/构建/契约及重要缺陷关闭是门槛 | 05 完成 Demo 交付；M1-B 完成 B-00 差异、全部适用核心义务与全分支审查，保留原审查下界 |
| B-R15 真实模型/SSO/目录/知识库 | CAS 2.0 模拟接口已在 DEMO-01 真实浏览器链验证；受控模型仍产生合成纪要，控制链不证明语义质量或企业环境 | 不阻塞明确标识的受控流程；阻塞真实能力宣称 | PLATFORM-B / B-02/07/08，接口可用即做；真实输入相关性、关键事实/来源/行动项评审及真实发布/ACL；最晚相应生产功能交付前，由 F-05/06 核验 |
| B-R16 S3、双 MySQL、双活及专属依赖 | 已有实现及 S3 API 证据保留；未完场景无生产通过结论 | 否，前期禁止新增这些场景专用实现/配置/测试 | DEP-S3/MYSQL/HA/MIGRATE/MATRIX，全部最后 F-00～06；原要求不得遗漏或标完成 |
| B-R17 历史 PARTIAL/UNKNOWN 与特殊来源 | B01-IMM-01 PARTIAL、B04-Q401 UNKNOWN 和 O5/O6 保持原记录 | 条件阻塞：与当前路径有安全/必需断言依赖则阻塞，否则记录范围依据 | 适用本地 B 义务最晚 M1-B；部署专属归 F；无关历史调查不无期限阻塞 Demo，也不记为修复完成 |

当前结论：M1-Demo **待验收**；M1-B **INCOMPLETE**；真实平台能力、生产资格各按证据独立记录。B-QUAL 是原 B 深度义务的归口，不是删除要求或把共享可靠性推迟到部署阶段。

## Scope

The execution workflow is [AMA-WORKFLOW-20261027](../../superpowers/plans/2026-10-27-ama-development-workflow.md). It combines related checks while preserving the required permissions, transactions, lock order, bounded acquisition, durable recovery, actual adapters and browser behavior. Work stops after B-L1 through B-L5; later phases are outside this authorization.

## Source evidence already adopted

| Feature boundary | Adopted source evidence | What this proves |
| --- | --- | --- |
| Meeting information and roster | B-L1 source adoption and independent final review | Separate save/confirm operations, actual category/people reads, immutable original retry, conflict drafts and identity-scoped recovery |
| Upload and intake | B-L2 source adoption and independent final review | Actual upload discovery/resume/reselection, complete ordered import manifest, bounded parallel uploads, frozen original requests and private recovery |
| Transcript, generation and workspace reads | B-L3 source adoption and combined independent review | Actual saved transcript APIs, per-instance intent/IME/draft guards, generation acceptance/recovery and bounded workspace/task composition |
| Minutes, review, final and publication | B-L4 source adoption and independent five-fix review | Actual saved text/elements/action tuples, evidence anchors/history/candidates/reviews/settings, final/publication contracts, read-only published owner and workspace-wide denial invalidation |

The exact file manifests, report hashes and command results are retained in the corresponding `bl1-source-adoption-20261027.md` through `bl4-source-adoption-20261027.md` records under the evidence work directory. The final B-L4 review closed all five reported defects. Relevant broad source checks include 241 Web tests, TypeScript checking, built frontend, 69 generated response-schema checks, and the prior 879-test offline Java run with its explicitly recorded historical-private-fixture exclusion. These counts are source/offline evidence; none substitutes for real MySQL, adapter, service restart, or browser evidence.

## Runtime evidence boundaries

Historical adopted evidence remains valid only for its recorded version, setup and assertions. The sealed result inventory is `bl5-final-qualification-matrix-20261028.md`; `bl5-open-obligations-20261027.md` retains the original preflight inventory. Current scheduling is the R2 table above. In particular:

- Genuine local reconciliation closure and marker-response-loss UNKNOWN observations do not authorize reusing their historical resources or resending uncertain publication effects.
- The twelve representative write guards at genuine MARKED/UNKNOWN/SUCCESS-unapplied states are already adopted; they must not be relabeled as missing solely because an earlier inventory predates them.
- Actual application-to-controlled-provider TLS success and same-store/fresh-JVM readback do not prove every precommit crash, UNKNOWN or integrated restart scenario.
- Authentic legacy repair origin remains unavailable; synthetic business rows cannot replace that provenance.
- Real MySQL budget-before-transfer, lock ordering, withdrawal/races, generation atomicity and bounded reconciliation coexistence require their own evidence by M1-B, with basic protection and any changed high-risk path validated before Demo. Local full browser flow is required for M1-Demo; the proven loopback development origin may be used without weakening Cookie/Origin/CSRF or internal mTLS. Unfinished S3-specific workflow/recovery and deployment evidence is retained under DEP-S3/F; storage-independent obligations stay in B.

Historical record: the now-settled B-L5 plan was authorized for a maximum of **three hours from the first resource startup**. Human authorization is recorded with an exact plan hash in `bl5-human-authorization-20261027.json`. Other resource, request, memory, disk and identity restrictions remain unchanged. That batch passed its source/harness gate before activation; the authorization cannot be replayed. A runtime cap, an approval, a compiled harness, or a passing source review is not itself a successful runtime test.

## Retained qualifications and exclusions

B01 IMM remains PARTIAL; B04 Q401 remains UNKNOWN. Company SSO/directory/model/knowledge-service readiness, production identity authority, HA/capacity and native I/O failure qualification are not asserted by controlled fixtures. Full-product pixel/viewport closure, host/storage failure qualification and full load/stability matrices retain later-phase dispositions; prototype reproduction and comparison for the current Demo pages are brought forward into B. S3, dual-MySQL and active-active unfinished adaptation and exclusive dependencies are assigned to final F. Core Phase B permissions, transactions, application restart and critical crash/recovery requirements remain in Phase B.

No commit, merge or push is part of the current authorization. Final acceptance requires the applicable actual combined regression, resolution or explicit qualified disposition of required open assertions, and the sole independent final branch review.

## B-L5 local actual API result (2026-10-28)

The local API chain reached published `SUCCESS / APPLIED` through real HTTPS, native mTLS storage, Worker, controlled model HTTP and knowledge-provider TLS. It covered two uploaded files, eight ordered transcript segments, saved/confirmed transcript, generation, five required reviews, final confirmation and official document retrieval. Draft roster save versus confirmation, owner/member/outsider/role-only-admin access, stale generation CAS, member producer-ID omission, editor-intent admission and published-write denial were observed in the fresh batch. Exact scope and evidence are recorded in `bl5-local-runtime-results-20261028.md` under the work directory.

This result is **not browser qualification**. The temporary certificate warning still requires human handling. Enterprise action-owner selection returned `ENTERPRISE_SELECTION_NOT_INSTALLED`; crash/restart, concurrency, SQL rollback/budget and other open qualification items remain separate. Local services were stopped cleanly with all data retained before proceeding to S3. Phase B is still incomplete.

## B-L5 S3 actual API result (2026-10-28)

The fresh S3 API chain also reached SUCCEEDED / SUCCESS / APPLIED, using two actual uploaded files, saved/confirmed transcript, generation, reviews, final confirmation and official document retrieval. A single publication POST deliberately lost its client response; only status queries followed and recovered success. Owner document read succeeded; confirmed member was denied by the controlled provider ACL, while outsider/admin were denied by meeting ACL. Provider member-allow remains unqualified.

Additional actual evidence covers concurrent info saves, exact known-success idempotency replay without new external requests, member private-field omission, overlapping roster withdrawal with post-confirmation denial, concurrent two-file intake with a single durable accepted graph, stale generation with zero SQL mutation, and malformed second-file parsing with no partial RAW output. Concurrent generation yielded one success and one protected503 with no extra durable graph; its exact internal cause/phase is unproven. Test-client mistaken status assumptions and the bounded ingress503 remain retained, not hidden.

See bl5-s3-runtime-results-20261028.md and the final integration review package under the work directory. Browser certificate handoff, exact internal budget/lock/rollback and critical crash/full restart scenarios remain open. These API results do not declare Phase B complete.

## B-L5 batch closure (2026-10-28)

The one-time batch was settled at approximately 01:10 CST, before the original 02:42:33 CST hard deadline. Final monotonic elapsed check: 5272.683 seconds, below the authorized 10800 seconds. Both exact owned containers, all registered business processes and the watchdog were verified stopped; data and evidence were retained. Charged totals: 5 pipelines, 12 model, 4 publication and 1487 HTTP budget units. The browser handoff remained incomplete, and its temporary tab was closed. No commit, merge or push occurred.

The current batch is closed and must not be restarted using its consumed approval. The continuation entry is handoff-bl5-settled-20261028.md. Final independent review adjudicates the qualified results and remaining gaps; Phase B remains incomplete.

Final independent review completed: no new Critical/Important source defects, inherited SOURCE QUALITY PASS retained, actual API evidence adopted only within the report scope. Review SHA256: 10c1d9208b5aee4aa634a7d32d27a53357e8f9602db22648c63d2b77fbf84366. Controller adoption: bl5-final-limited-adoption-20261028.json. Phase B remains INCOMPLETE.
