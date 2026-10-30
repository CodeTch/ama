# Phase B acceptance evidence

> **当前裁定（用户反馈后）：严格原型一致性 REOPENED / NOT ACCEPTED。** 原型界面和功能验收重新执行。原M1-B受控后端证据保留为其限定范围，不能据此宣称产品界面/功能与原型一致或完成交付。

> **2026-10-29 R2 / 正式本地验收完成：M1-Demo ACCEPTED，M1-B ACCEPTED（fresh/current单MySQL、本地文件、受控模型/签名知识库范围）。** 原BASE→最终V2独立全分支审查APPROVE，当前没有开放P2+；B-R01–14本地启用部分已收口。[正式本地B验收记录](formal-local-b-2026-10-29.md)保留全部证据及限定。B-R15企业平台能力、B-R16最后F部署资格和B-R17历史PARTIAL/UNKNOWN/legacy限定各自保留。

This document distinguishes source/evidence verification, formal R2 local M1-B acceptance, actual enterprise capability and production qualification. Current formal local B is accepted; it does not certify every enterprise/production environment. The final state and review record are `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json` and the linked formal acceptance report.

<a id="current-open-obligations-r2"></a>

## 当前开放义务分流表（R2）

这是当前验收及后续独立关卡的唯一调度表，来源为开发树证据目录 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/` 中的 `bl5-final-qualification-matrix-20261028.md`、原 open-obligations、local/S3 实际结果和本地启动器记录。原件、已通过范围及未通过事实均不改；下列编号只用于跟踪当前待办，不替换原 Task/B-L/F5 等来源编号。新鲜本地切片证据见 [DEMO-01/02 本地记录](demo-local-transcript-2026-10-28.md)；它只更新明确列出的范围，表内其余“需补”仍未完成。

“条件阻塞”须有明确判断：只有基本路径已有适用证据、当前无已知重要缺陷且未修改相关高风险保护时，深入证明才可在首次 Demo 后完成；否则提前阻塞该片段。证据复用须匹配源码、schema、装配和断言，不能把 S3 的环境结果直接改写为当前 local 通过。发现缺陷立即升级，不等最晚关卡。维护者在对应行附最新证据/版本/判定，避免另建重复台账。

| ID / 原义务 | 已有事实与限定（均按原范围） | 最终R2裁定 | 关闭依据 / 保留的独立关卡 |
| --- | --- | --- | --- |
| B-R01 本地可重复装配 | 日常入口已接登录/MySQL/会议、本地 mTLS 文件及独立 TXT Worker；真实浏览器上传→导入→保存确认和普通停止重开通过；受控模型/签名知识库已装配，生成至发布/成员文档与六服务重启有当前证据。旧 local API 全链仍仅属于已关闭夹具 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R02 浏览器与原型 | B-L1～4 源码已采纳；原型八个后续状态已从原件最终渲染索引；十个实际状态两视口、完整流程、409/IME/原请求恢复与权限检查通过，逐页结构/差异矩阵已写，受影响审查 P2 关闭；B-R13 本地选择片段及 LONGTEXT 预算 P2 已独立审查关闭 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R03 信息并发/幂等 | 当前 genuine 信息INSERT后回滚/ambient、真实200/409草稿保留与原请求恢复已证；新增名单命令LONGTEXT预算真实RED02→GREEN02，identity与roster同ledger、完整metadata/reserve前置；当前CREATE/SAVE/CONFIRM正常与已受理原ACK、改payload/fresh auth负例PASS | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R04 导入原子性 | 双TXT有序RAW、坏第二文件无部分RAW、真实RAW INSERT nativehalt→原task普通attempt2唯一恢复；新增 genuine 受理在batch_file/input/task/outbox实际INSERT后强制SQL异常，4/6/16/19条已执行DML的root/引用/输入/task/receipt/audit/outbox/sequence全回滚、ambient拒绝PASS | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R05 生成原子性 / S→A→F | stale409无额外任务、input INSERT后rollback、candidate nativehalt/原执行恢复已证；自身HB限定完整重捕获和失败轮D收费P2修复，DISCOVERY/max3/累计预算/原30s/interrupt限定PASS；真实command S/A后、首serialized F写前owner命令root+1→具体PROVENANCE/零额外gen图PASS | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R06 成员私有区与撤权 | saved-only/私有字段省略/基本名单撤回已有证据；当前 genuine 生产工厂在实际成员 S 捕获 COMMIT 后由 owner API 撤回名单，F 当前会议 ACL 拒绝且不返回投影；API 恢复原名单后原正文/要素相同。其他读路径/身份竞态与全锁序仍按适用范围补证 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R07 发布 UNKNOWN/ACK | S3 丢响应后仅查询恢复成功已记录；当前浏览器丢已受理ACK仅GET恢复已证；新genuine映射INSERT后precommit nativehalt、原MARKED/RECONCILING保留、KB独立重启及普通Worker原task/execution查回、远端一文档一执行和最终一映射有证；其他开放边界按范围补证 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R08 正式文档双重 ACL | owner/member200、provider撤READ403、provider允许但非会议成员403；真实读中post-response身份停用使F拒绝、S后pre-send provider撤READ使真实GET403；无HTML/POST/业务变化，恢复原SHA；独立审查与传输文字P2已关闭 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R09 发布冻结写保护 | MARKED/UNKNOWN/SUCCESS-unapplied 的 12 代表写保护已采纳 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R10 B3 维护/对账共存与饱和 | 共存04真实minutes capacity1占槽时另一READY0，TXT和原MARKED pub GET1/POST0推进；另saturation02真实reconciliation capacity1/Semaphore0、另一原operation未领取，普通minutes/TXT及2次实际维护查询推进，释放后原pub GET2/POST0、唯一exec/mapping/effect | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R11 B6 应用/Worker/模型/KB 重启、关键 crash | 普通六服务/MySQL重开保留同session/确认稿/纪要/冻结/发布与同文档/文件字节；RAW/candidate/mapping实际INSERT precommit nativehalt、原task正常恢复；model/KB各自独立same-store重启；Worker TIME_WAIT真实RED/工具修复/实际立即组件重开通过 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R12 预算、锁序及 genuine authority | 已有原生6维/Unicode/精确边界/current S/A/F单ledger/预transfer tiny配置/ambient、genuine issuer/holder/coverage、自修改/撤权与目录姓名/replay闭环；新D失败轮费用P2独立关闭，原deadline/cancel与名单预算RED→GREEN已证，完整拓扑映射已形成 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R13 行动负责人选择 | 原 422 未安装已保留；最小真实目录选择/服务端 S/F 同 holder 身份校验已实现。实际生产工厂下身份撤回/写后回滚/原请求重放和浏览器选择、清除、刷新、成员拒绝/不授予会议权限通过；独立审查所提姓名传输 P2 已因果修复并复核关闭；修复后已发布 tuple 拒绝/正式文档字节保留通过，详见 [记录](action-owner-local-2026-10-29.md) | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R14 最终集成与 Task13 | 最新545Java/101classes、0failure/error/skip通过；当前受影响browser200/409/IME/stale409和发布冻结/文档SHA通过；B00逐DIFF当前映射与实际16迁移validate/65表部署subset已只读核对；原BASE34cb…到最终工作树1076实现文件冻结独立review中 | **M1-B本地启用范围已关闭** | [最终验收](formal-local-b-2026-10-29.md)与原BASE→V2独立APPROVE；原证据/范围保留，无新增本地待办。 |
| B-R15 真实模型/SSO/目录/知识库 | 受控模型产生合成纪要；控制链不证明语义质量或企业环境 | **PLATFORM-B未完成，独立能力门保留** | PLATFORM-B / B-02/07/08，接口可用即做；真实输入相关性、关键事实/来源/行动项评审及真实发布/ACL；最晚相应生产功能交付前，由 F-05/06 核验 |
| B-R16 S3、双 MySQL、双活及专属依赖 | 已有实现及 S3 API 证据保留；未完场景无生产通过结论 | **最后F未完成，DEP归属保留** | DEP-S3/MYSQL/HA/MIGRATE/MATRIX，全部最后 F-00～06；原要求不得遗漏或标完成 |
| B-R17 历史 PARTIAL/UNKNOWN 与特殊来源 | B01-IMM-01 PARTIAL、B04-Q401 UNKNOWN 和 O5/O6 保持原记录 | **范围裁定完成；原PARTIAL/UNKNOWN/UNQUALIFIED保留** | 适用本地 B 义务最晚 M1-B；部署专属归 F；无关历史调查不无期限阻塞 Demo，也不记为修复完成 |

当前结论：M1-Demo **ACCEPTED（本地受控流程）**；M1-B **INCOMPLETE**；真实平台能力、生产资格各按证据独立记录。B-QUAL 是原 B 深度义务的归口，不是删除要求或把共享可靠性推迟到部署阶段。

当前 DEMO-03/04 详细证据见 [本地纪要与发布记录](demo-local-minutes-publication-2026-10-29.md)。首次发布在 45 秒等待后于任务 attempt 2 成功。生产 preparation 自身续租竞态现有独立因果复现、修复、真实 Worker 和有界负例，详见 [续租记录](publication-renewal-2026-10-29.md)；首次 56.85 秒领取延迟仍未解释，普通重启不替代 crash。浏览器/原型矩阵见 [DEMO-05](demo-browser-prototype-2026-10-29.md)。

## 历史采用与环境边界（保留原记录，以下历史未完成表述不覆盖本页顶部最终裁定）

### Scope

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
