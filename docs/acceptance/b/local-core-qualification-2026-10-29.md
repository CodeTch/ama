# B-QUAL 当前本地核心限定证据

**最终采用：2026-10-29 R2正式本地M1-B已验收。** 原BASE→V2独立全分支APPROVE，无开放P2+；完整范围见[正式验收](formal-local-b-2026-10-29.md)。以下分批记录及其中旧“待审/未完成”作为时间线保留，以最终采用及范围限定为准。

本记录承接已采纳的本地受控 M1-Demo；正式 B 仍 INCOMPLETE。只使用日常 owned MySQL/文件/单机与现有真实业务样本，不恢复 B-L5、不构造 SQL 会议/任务/结果行。此文记录子断言，当前缺口调度仍仅在 summary.md 的 B-R 表。

## B-R12 当前纪要读取：实际 native MySQL 预算

样本 `70605bec-ada4-469a-bf9c-9f146909db66` 来自正常双 TXT 导入、真实生成/采用/人工保存与企业负责人命令。两个独立资格源码文件分别是 `LocalAcquisitionBudgetCheck.java`（生产 Processing/Transcript/Minutes 工厂并 requireCombined）和 `LocalRowBudgetCheck.java`（在同一真实 authority 的完整 READ gate/scope 下观察生产 SavedTranscriptRows 和 Ledger）。无生产预算默认值改动。

实际记录 `bqual-budget-genuine03.log`：

- 当前完整图以相同 ledger 执行 S 捕获、A 源配置/来源准备、F 重获全部当前图并比较。S/A/F UTF-8 累计分别 78,223 / 154,244 / 205,631；owned-copy 字节 810,612 / 1,772,920 / 2,268,828，未重置预算。
- 在该已锁当前纪要行上，先返回原生 MySQL 长度元数据、完成 reserve，再允许驱动 executeQuery 读取正文。实测 row usage 的 UTF-8/scalar/UTF-16 为275/255/257，包含真实中文和补充平面字符；owned-copy 字节/单位为2712/2312，响应预算2694。
- 单行物理生产读取分别验证六种精确预算等于实测值可成功，减1则 BUDGET_EXCEEDED 且不执行该目标正文查询：UTF-8、scalar、UTF-16、owned-copy 字节、owned-copy UTF-16及响应。目标查询必须处于实际持有事务中。
- admission 后取消，在正文 executeQuery 前 CANCELLED；同 ledger 可读两次，第三次在正文查询前拒绝。此处是生产物理读取组件在真实 gate 下的精确行边界，不声称完整 HTTP 请求的最小精确边界。
- 六种各自为1的真实 factory 完整 `saved.current` 配置均在目标 minutes_revision 正文查询前 BUDGET_EXCEEDED；完整查询进入 ambient transaction 立即拒绝，目标正文查询为0。该计数针对目标纪要正文，不推广为所有字段/端点均无传输。
- 完整会议/搜索投影快照、集合/正文/要素/行动/审阅和回执/audit/outbox 计数相同；验证本身无业务修改。

观测发生在 delegate PreparedStatement.executeQuery **之前**，覆盖 Connector/J 在执行时缓存结果的情况。它不是只计 getString。独立审查发现首版混合 `body,OCTET_LENGTH(body)` 会被误判元数据的 P2；`bqual-budget-trace-red.log` 用实际 wrapper 的无文件合成驱动在 executeQuery 前因果失败。修复将原始正文/通配符检测与长度测量分离，仅剔除明确的安全长度表达式，混合投影照样计正文。十种 mixed/alias/case/wildcard/metadata/COUNT/HEX 用例 GREEN；随后上述实际 MySQL 全套在03重跑通过。合成驱动只证明观测器，业务资格来自03。首轮P2报告 `bqual-budget-review-20261029.md` 保留；闭合待 `bqual-budget-trace-closure-20261029.md`。

## B-R06 确定性成员撤权发生在 S 与 F 之间

`LocalReadWithdrawalCheck.java` 使用相同真实工厂和成员 Actor；透明 JDBC 屏障位于当前 saved minutes **实际 S 捕获 COMMIT 后**。随后 Python 编排器运行正常同源/有效 CSRF 的负责人 API，而非 SQL 授权行替换：

1. 原已确认成员读取200。
2. 保存移除名单草稿后仍200；确认移除后403。
3. 释放原读取；F 阶段抛当前 MeetingAccess.AccessDeniedException，未返回捕获的投影。
4. finally 通过正常负责人 API 恢复原名单，确认后成员200，原正文/要素对象相同。

`bqual-read-withdrawal-genuine01.log` 与 read-withdrawal/report.json 记录通过。owner 的保存/确认本身是预期业务版本/事件变化，不将全环境写成零副作用。此证据覆盖 saved minutes 的确定性代表读取，不替代正式文档、所有列表/历史端点、身份停用或全锁序/饱和证明。源码与断言尚待集中独立复核。

## B-R05 生成受理：真实写后强制异常回滚

`LocalGenerationRollbackCheck.java` 使用相同 genuine 组合，在原生 `INSERT INTO generation_input` 已返回后注入 SQLException；原有事务 runner 执行回滚，未创建 accepted task。`bqual-generation-rollback-genuine01.log` 验证命中的确是该已执行写入及指定异常，完整 root/搜索投影、原集合/正文/要素/行动/审阅、generation_input/candidate/task、回执/audit/outbox 计数相同。ambient generation command 也在任何业务改变前拒绝。

该断言关闭生成**受理**在此写入点的原子回滚缺口；不替代 genuine Worker 完成候选/自动采用时的写后回滚、全部 S/A/F 竞态或旧并发503原因。源码与断言待集中独立复核。

## 保留开放范围

尚需按 B-R03/04/05/07/08/10/11/12/14/17 汇合信息/导入/生成完成的其余原子性和锁集合、正式文档读中撤权、关键 precommit crash/UNKNOWN、维护/对账共存/饱和、历史限定项适用判断及最终全分支审查。历史56.85秒领取延迟不因这些成功改写为已解决。真实平台和最后F待部署事项保持原归属。

## B-R12 既有名单身份姓名：实际超预算缺陷与修复

进一步验证既有 `JdbcMeetingAccess.captureIdentities(names=true)`，发现其直接在身份预锁查询中返回 LONGTEXT display_name。`bqual-identity-name-red.log` 真实 MySQL 测试将精确本地测试身份姓名临时设为8,400,000字节（超过默认8MiB acquisition），原实现没有预算拒绝，并完成一次原姓名查询及一次大字符串读取；finally恢复原姓名。此为新增实际发现，不能由既有源码采纳或小样本成功推定安全。

修复仅涉及原身份预锁读及 create/save/confirm 的既有调用：复用 configured WorkspaceReadBudget，以固定有界身份/状态/长度元数据 reservation 在查询前保护 driver；按原身份顺序锁定元数据行，累计预留完整姓名的 UTF8/scalar/UTF16及 owned copies后，读取同一已锁行且测量长度匹配的姓名，再核对实际长度。所有身份仍在 subject/root 前；status-only/historical/fact路径不变，行动选择的有界 SELECT1 保留。没有引入产品姓名字符上限、新部署配置或第二数据源。

`bqual-identity-name-green02.log`：8,400,000字节源姓名在目标正文查询前预算拒绝（rawQueries=0/largeStringReads=0）；正常原生 Unicode、精确累计预算、少一个字节在目标姓名查询前拒绝、预先取消均通过，测试身份恢复。修复后535条 Java 全回归通过。当前源码普通六服务更新后，`bqual-read-withdrawal-genuine02.log` 再次验证真实成员 S提交→owner API撤权→F拒绝→恢复原名单/相同正文要素；`bqual-identity-browser-safety01.log` 再次验证实际创建/信息保存/名单相关操作、200/409草稿重新应用、IME/视图保留和陈旧生成拒绝。独立源码审查 `bqual-identity-name-review-20261029.md` 确认新写入预算路径，但发现 P2：既有幂等结果返回前仍重新获取目录姓名，后来变大的姓名会阻断同原请求恢复。该发现及修正见下，不将小样本新写入通过等同整个分支接受。

## B-R03 信息保存：真实写后异常回滚

`LocalMeetingInfoRollbackCheck.java` 使用 Meeting/Processing/Transcript/Minutes 的真实工厂，并对身份、事务、fence、outbox及authority保留同一实际实例；未伪造状态转换 port。提案使用合法可选的 category=null，不声称公司类别字典已装配。原生 `INSERT INTO meeting_info_revision` 返回后注入指定 SQLException，`bqual-info-rollback-genuine01.log` 证明确实执行该写入再回滚：完整root/搜索投影和原集合/正文/行动/审阅、信息/名单/成员、grant及receipt/audit/outbox快照或计数相同。ambient info save 在写入前拒绝。该代表点不替代所有事务/锁阶段交错或全部版本内容逐字节对比；待同一reviewer集中复核。

### 目录姓名预算修正的同原请求恢复边界

`LocalMeetingReplayNameCheck` 通过同一真实 Processing/Transcript/Minutes/Meeting factory 组合及真实创建、保存、名单确认命令建立样本，未用 SQL 建业务行。之后只对两个本地测试目录身份临时变更姓名/状态并 finally 还原。`bqual-identity-replay-red.log` 实际复现 CREATE/SAVE/CONFIRM 三个原已受理命令均被后来8,400,000字节姓名的预算拒绝阻断，业务快照没有增加写入。

`JdbcMeetingRepository` 现将已知结果的 SAVE/CONFIRM 分支提前至新名单姓名获取之前，仅重验当前 actor 状态及 root EDIT/MANAGE 权限、原请求 hash，再返回原历史结果。CREATE 不生成目录姓名快照，使用当前 actor 状态；已知结果额外重新取得当前 root EDIT 权限。新 SAVE/CONFIRM 继续执行既有按序身份预锁、配置累计预算及 CAS。

`bqual-identity-replay-green02.log` 三个命令返回各自原结果，rawNameQueries=0，root/投影/名单/授权/子图计数/receipt/audit/outbox 快照不变；同 key 改 payload 仍 Conflict，新 key 超预算名单仍拒绝，已停用 actor 的旧 key 仍 AccessDenied，目录身份还原。最新 `bqual-identity-broad-java03.log` 535 tests / 0 failures / 0 errors / 0 skipped；02日志保留为验证命令漏设 `AMA_TASK11_EVIDENCE_WINDOW` 导致的两项输出路径 NPE，修正运行参数后03通过，未修改这两项测试。独立 `bqual-identity-replay-closure-20261029.md` 已复核修正及新增负例、独立核算535条回归，scoped closure 无新增 P2+；该结论仍不等同整个 B 完结。

### TXT 原生进程提交前崩溃

`bqual-native-raw05.log` PASS，证据目录 `.tools/local-app/native-crash/ec0cba9e-2f72-4005-a4c4-f8b02bfd537a`。真实页面创建会议并向本地文件服务上传两份 TXT，在普通 Worker 已停时，通过同一登录会话、有效 CSRF 和真实业务 API 受理任务 `1b48a58b-2c8c-430c-8504-72f2ec07c333`，会议 `ee9042f7-8a4f-4e99-b2d4-7929e1ef0afc`。此资格模式没有绕改 UI 健康门槛：正常 UI 仍在 execution 不可用时拒绝导入，资格模式只记录实际 API canSubmit/intake 与执行观察。

专用新 JVM 使用与普通 Worker 相同 `TranscriptWorkerRuntimeProvider.create()`，完整真实 contextual/external/reconciliation 组合，仅透明 DataSource 在该 meeting 的真实 RAW INSERT 返回1且真实事务 active/autocommit=false 后写 cut 元数据并 `Runtime.halt(86)`。退出后 root/RAW/segments/files/references/input_batch/transcription_input/成功事件与受理时快照一致，没有部分完成，原源文件摘要和关联保留，task attempt1 RUNNING。

恢复普通 Worker，不执行 SQL lease/task/reset；其自身领取处理过期租约，attempt1 EXPIRED、attempt2 SUCCEEDED，原 task 输出唯一 RAW、四条与双文件顺序一致的 Unicode 文本、一次原 task TASK_SUCCEEDED，root和已受理输入/文件/关联不变。所有六服务恢复。只对该 TXT 完成切点成立，不代表 intake 受理所有写入位置、外部模型/发布或整个B通过。

失败记录保留：01正常 UI 健康门槛使未发 POST，02观察器错误取已释放响应，03资格脚本错误把包含执行健康的 businessReady 当作 canSubmit；均未到 native cut。04实际 cut 和原 task 恢复成功，但脚本误查询成功事件的 RAW 引用（生产事件引用 task 的输入），末尾断言失败；05按真实事件协议重跑新任务完整通过。独立 `bqual-local-core-native-review-20261029.md` 已审查这六个限定案例，无新增 P2+；其记录的表计数/快照字段/切点/网络唯一性限制保留。

### 外部生成和发布提交前崩溃，以及各自提供方独立重启

`bqual-native-candidate04.log` PASS，证据 `.tools/local-app/native-crash/cfeb883d-5217-4c62-8a2a-f8593ab51423`：正常 API 受理完整重新生成，meeting `a599c52f-0520-4f85-bd57-bd04403d719e`，task `2f5fdd7c-f9af-47ba-ba10-cf8d22f464ff`。真实 production candidate INSERT 返回1/事务active/autocommit=false后 native halt86；候选/纪要正文/集合及root计数/值无部分提交，已经持久 MARKED 的原 execution 与 RECONCILING 原 task 保留。仅 owned model PID879620→881184，使用相同原cmd、配置、secret/store，仅删除该进程原 disposable readiness receipt 供 CREATE_NEW；其他运行角色PID及start ticks不变。恢复普通 Worker 后原task、原execution在attempt1成功。cut→独立重启后→查回后，模型payload/result文件完整SHA映射及journalSHA相同；受理前至cut只增加一份payload/result。不能据文件相同额外宣称网络层零重复HTTP请求。

`bqual-native-mapping01.log` PASS，证据 `.tools/local-app/native-crash/99ad1766-5849-4d2d-8345-cd4e311ffbe7`：同前节真实TXT生成的资格会议 `ee9042f7-8a4f-4e99-b2d4-7929e1ef0afc`，按实际保存字段和脚本明确标识的受控审阅决策冻结，正常API受理 publication `b6d03663-0564-4b2a-8fa0-f445c502e550`，task `7d2fca78-1a3a-4683-9513-143c18e6cf46`。真实 external_document_mapping INSERT 返回1/未COMMIT时 halt86，数据库mapping仍0且root没有部分发布，原MARKED执行和RECONCILING任务保留。远端此时已有1文档/1执行。仅 owned KB PID879621→882831，原cmd/config/store/身份不变，不调用bootstrap或grant；其他运行角色PID/start ticks不变。普通Worker将同原task/execution attempt1完成，最终DB1mapping。cut与查回后远端document ID/hash/result ID、execution ID/no/request hash、完整grant rows及version、identity pinsSHA完全相同。

上述两类独立重启发生于已经有远端效果但本地结果事务未提交的状态；普通全栈重启不能替代这一证据。成功范围仍是具体切点和真实生产runtime组合，不包括全部网络失败、所有写入位置或主机/存储灾难。失败记录保留：candidate01实际cut后脚本错误预期RUNNING（真实协议是RECONCILING）；02重启前漏导入Path，finally普通Worker恢复；03资格准备重复确认相同已确认名单，收到真实409，未到native cut。01/02的原task后来真实查回成功仅作补充，不改写原FAIL。04修正工具后完整通过。六案例合并独立审查无新增 P2+，按所列实际字段/计数及特定切点采纳，不扩大为所有历史图逐字节或全部崩溃窗口。

### 正式文档读取中撤权

计划 `2026-10-29-official-document-withdrawal.md`，真实工厂/真实签名知识库客户端，`bqual-document-withdrawal-genuine01.log` 两种模式首次完整PASS；资格工具入口现接受显式 `--fixture`，未改 Java proof。对象是前节真实已发布文档，原dev-member双方授权可读。

- IDENTITY：在真实网络exchange的registration.close回调暂停，此时响应已完整解析、socket已关闭且没有DB事务。已实际写出1次GET；停用精确dev-member目录身份后释放，F抛当前 MeetingAccess.AccessDeniedException，不返回HTML，未产生业务快照变化。finally恢复原ACTIVE并genuine重读同一文档摘要。
- PROVIDER：在真实网络register入口暂停，此时S/manifest校验已完成且尚未connect/send，没有DB事务；用正常owned provider CLI撤回原READ后释放。实际GET收到403，service抛具体OFFICIAL_DOCUMENT_FORBIDDEN，不返回HTML，无业务快照变化。finally恢复已核验原can_publish0/read1并重读相同摘要。显式grant变更可推进version，不声称version回退。

每例真实客户端GET计数只增1、POST不增加；还原后HTML SHA256均为 `e181f75e9ffe42136245d679804cf64ac7a817f4f690390eceb94154b5be9538`。私有证据目录为 `.tools/local-app/read-withdrawal/document/1ba1b0ad-13da-4b45-b7c9-f89f80e6afc3`、`601dce08-fbb9-4903-ad43-02f6738eca29`。临时身份/ACL源变化与零业务写入分别记录，父编排也保留有限等待和finally恢复。该断言不意味着能追回已经返回给调用者的字节，也不覆盖所有读取端点。独立合并审查进行中。


## 最后适用增量：名单命令、导入晚写及生成command F

名单 `JdbcMeetingRepository.members()` LONGTEXT 缺口由正常create/save的84,000byte Unicode外部姓名与配置acquisition262144真实复现。`bqual-roster-budget-red02.log` 使用改动前的不可变application `abc5bcd…` 二进制、与GREEN02相同观察器，SAVE/CONFIRM均有原始姓名读取且未预算拒绝；root UPDATE后的资格故障确保不提交。当前仅repository复用BoundedMeetingMetadata，身份与名单同WorkspaceBudget，身份覆盖再验只取有界固定ID。`bqual-roster-budget-green02.log` 两条命令均WorkspaceUnavailable、1name metadata/0rawname，指定快照不变；正常业务建立84KB源成功。GREEN01旧观察器在INFO INSERT过早拒绝SAVE，未计通过。`bqual-roster-replay-genuine01.log` 当前source正常窄UnicodeSAVE+CONFIRM，再扩大目录姓名后CREATE/SAVE/CONFIRM原ACK与0rawname、改payloadConflict、新请求预算拒绝、disabledactor拒绝及原目录恢复均PASS。独立roster source/evidence复核待最终汇合。

`bqual-intake-rollback-genuine03.log` 同实际Processing/Transcript/Minutes/queue/registry/fence/outbox实例，四case均在真实INSERT返回1且activeTX/autocommitfalse后抛指定SQL异常：input_batch_file、transcription_input、durable_task、task_outbox。到切点已经执行4/6/16/19条实际DML；root/inputs/batch/profile/files/refs/subject/task/receipt/audit/outbox和sequence指定快照相同、零新受理任务，ambient specific standalone guard拒绝。早期01/02只因资格代码误判生产guard消息而未进入主验证，原FAIL保留。

`bqual-generation-F-genuine01.log` 在一次真正公开enqueue完成S/A后、serialized F第一receipt INSERT执行前暂停；该连接真实TX已经开始但物理identity/root NOWAIT在独立连接成功。另一线程通过同genuine factory普通owner信息save使root+1；以owner提交后的快照为基线，释放generation具体返回MINUTES/PROVENANCE_UNAVAILABLE，输入/task/receipt/audit/outbox无额外变化。不是仅旧revision在S之前拒绝，也不是注入SQL失败或包装重试；不将该mid-flight完整图拒绝冒称前置stale409。

## 共存和两类容量一饱和

`bqual-worker-coexistence04.log` / `native-coexistence/82107ed7-aeb5-427f-908a-00524caff158`：minutes capacity1第一个S commit后暂停12.029s，另一个保持READY0，实际HB/rootfree；同时普通TXT和原MARKED publication对账成功，knowledge GET1/POST0。释放后两个minutes原attempt1，各1execution/candidate，publication原execution/单mapping及provider doc/execution/grants含version/identity不变。原01首GET401、02空elements导致observer晚触发、03导航timeout均未改为PASS。

`bqual-reconciliation-saturation02.log` / `native-saturation/df5a33b5-cbe0-4e4c-ab34-1ea4c690651f`：两个真正发布受理并在各mapping INSERT precommit halt86的原MARKED；建立夹具时明确仅启动genuine provider的external WorkerLoop模块以保留前一个UNKNOWN，未启动reconciler；其后验证完整RuntimeProvider。实际reconciliation capacity1/Semaphore0，第一个original receipt protected S commit后rootfree有界暂停（原报告17.219s字段含释放后的恢复，不作精确占槽时长），另一operation owner/epoch未变/未领取；普通minutes和TXT继续成功，maintainLocalFailures调用栈内额外2次实际维护查询（不从计数推导两轮扫描；5s节流与cadence沿用已采纳证明）。释放后原publication共GET2/POST0，各原execution、单mapping、远端单doc/exec和grant/identity相同。01因CUT/COEX三个资格实例误复用同owner而无法判断claim数量，保留FAIL；02各mode独立owner且保存entry。

本次维护调度证明不冒称选中无法正常产生的legacy terminal UNDECIDED的正向repair；现代atomic终止是否排除该origin由sole final审查给明确裁定。完整当前锁拓扑映射见[authority/holder/锁集合](local-authority-lock-coverage-2026-10-29.md)，区分SQL attempt provenance与成功holder/seal/完整集合。

## 实际迁移前缀与B00部署subset

`bqual-schema-readback02.log` / `schema-readback/869dcd3f-9bc3-43a7-9ce9-d3c059a337f5` 对实际日常库执行Flyway validateWithResult（不migrate/DDL/repair）通过，当前16个正式SQL对应16条success history，保存所有SHA256；只读information_schema：65业务表/501列/133secondary indexes/128FK/83CHECK。B00-1.7 review模型99表。table/column集合差异明确：旧identity.department/platform_admin和旧meeting_acl保留，当前授权从未读取旧ACL；review中两列source_context_id尚属D01未启用的一般材料来源设计。未来实体不要求提前部署，实际类型/index/constraint/FK完整metadata另存，不从表/列计数推导所有类型相同。01编译时误取Flyway字段的工具FAIL保留。独立schema/adoption核对待final review。

最终当前源码Java `bqual-final-java01.log` 543项/100classes，failure/error/skip均0、BUILD SUCCESS；未更改的Web原249/type/build与原型/共享shell基准保留，受影响浏览器重验及最终source/evidence review继续汇合。正式B在独立最终审查前仍INCOMPLETE。


## 最后Unicode ID预留审查P2

sole reviewer `bqual-roster-command-acquisition-review-20261029.md`指出新增固定ID预留误把255 code points同时作为byte/unit上界。合法utf8mb4 VARCHAR(255)可含255个补充字符（1020byte/510UTF16）；已接受此发现。提取实际生产reserveRosterIdentityIds并保留旧行为后，2个边界测试真实RED：owned copies+row费用期望4208byte实际1148，UTF16期望2040实际1020，低1byte/unit亦错误放行。只将实际预留改为count×1020 UTF8/count×255 scalar/count×510 UTF16，copies4，仍在raw-ID query之前、同ledger/holder/原锁序；没有限制ID为ASCII。

`bqual-roster-id-unicode-green.log` 2tests全绿，`bqual-final-java02.log` 最终BUILD SUCCESS（加入2项后545项/101classes/0fail/error/skip）。原543回归保留为其当时源码，最终采用V2；`bqual-final-branch-review-v2-20261029/manifest.json`1077实现文件，较V1仅repository和新测试2paths不同，原BASE不变。最新genuine名单预算/原请求恢复及最终装配/受影响browser验证进行中，sole reviewer P2 closure待汇合。Web249/type/build/check-api/OpenAPI、lifecycle10/provider2均已完成成功，前端源码未改。


## 独立增量已关闭，等待最终分支结论

`bqual-roster-unicode-bound-closure-20261029.md`已经独立关闭最后Unicode ID上界P2，确认V2 1077源文件和545回归、GREEN03及replay02。`bqual-authority-atomicity-schema-closure-20261029.md`已经在当前启用拓扑关闭L2 holder/完整集合/锁序、L3 intake/finalF原子性、L4真实维护/对账有界饱和与B00部署subset映射；明确仅维护调用栈内实际查询继续推进，不由该计数声称恰两轮scan，不把累计17.219s误称纯暂停，也不把schema元数据说成新CHECK/FK-rule全文语义相等证明。legacy/company/F限定保持。

当前源码和最终运行验证无新增变更；原BASE34cb…的最终独立branch verdict仍是正式M1-B最后门。
