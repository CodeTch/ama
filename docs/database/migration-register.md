# AMA 统一 migration-register

版本 B00-1.2-review（拟议上传补充，待独立增量评审），2026-10-22；已采用 B00-1.1-review 的 R1—R6 独立关闭结论不变。这是迁移所有权/顺序登记，不是执行记录。实现仍须按统一新版本修正差异、回归并独立评审后验收。Flyway执行器单实例运行；已应用/已共享/已冻结脚本不可改字节或重编号。DDL多语句不是可整体ROLLBACK的事务，MySQL atomic DDL只保证单条语句原子；中途失败检查实际schema并前滚修复。[MySQL atomic DDL](https://dev.mysql.com/doc/refman/8.4/en/atomic-ddl.html)

## 冻结输入与原哈希

|版本/原文件|SHA-256|执行/共享状态|所有者/输入|
|---|---|---|---|
|V001__foundation.sql|`1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335`|B01已应用独有fixture，已共享提交；冻结|B-01；phase-a HEAD e8c88d2；所有输入树同字节|
|V002__identity_acl.sql|`68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0`|原字节已归集；来源 checkpoint e9990c8 → 集成 4e35683；此前仅应用 B02 独有 fixture|B-02；原输入 HEAD 3445972；保存提交 e9990c8|
|V005__tasks_outbox.sql|`55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7`|原字节已归集；来源 checkpoint daa1c3d → 集成 bcf040b；此前未应用；冻结|B-05；原输入 HEAD 3445972；保存提交 daa1c3d|

路径均为对应工作树 `backend/adapters/src/main/resources/db/migration/`。协调者保全副本在 phase-a 的 `.superpowers/sdd/2026-10-21-ama-phase-b/b00-frozen-migrations/`。不采用任何已有fixture，不改变原 migration 的 checksum。根当前已有工作不等于相应任务最终通过。

## 冻结旧集合归集记录（2026-10-22）

设计采用提交 `6dd2cdd` 后，协调者已将仅含 V002/V005 原 SQL 的保存提交分别归集为 `4e35683`、`bcf040b`；V001/V002/V005 三个文件的完整 SHA256 均与上表冻结输入逐字节一致。正式 location 只含这三个版本，没有 V003/V004，没有夹带未验证 Java。此记录完成首次 V006+ 的**代码归集前置门**，不表示已在任何库执行新的迁移。各任务仍必须在自己的受控 fixture 验证 001→002→005→后续登记版本的真实升级路径及 Flyway history；旧设计实现差异仍未关闭。

## 分配规则和计划占位映射

首次执行任何 V006 或更高版本前，协调者必须先完成**冻结旧集合归集门**：B-02/B-05 拥有者各提交仅含原字节 V002/V005 的保存commit（不夹带未验证Java）；协调者将 V001/V002/V005 归集到同一正式migration location并复核本表SHA。新空库按V001→V002→V005顺序执行；只有V001的既有库先补V002再V005，B02库已有V001/V002则先V005。B05输入库只有V001且V005从未应用，同样先V002再V005。以上归集不表示旧设计获实现验收，服务/Worker在V006—V013修正并检查前不得开启新业务写。各领域开发/CI也使用相同完整集合，禁止在未包含V005的B02树先运行V009，再事后插入低号。若发现某库已应用高版本却缺冻结低版本，停止该库迁移并由DB负责人提供可审阅恢复/新库迁移方案；不打开outOfOrder、不repair checksum、不改旧脚本。归集是设计通过后由协调者安排的实施动作，本B00不提交旧SQL到产品路径。

冻结高水位 V005；V003/V004 永久留空，不补低号。以下 V006…V027 为一次性连续预留的依赖顺序，在登记中即明确所有者。实施者只消费自己的预留号；新增需求从**V028**向上由协调者登记后分配，禁止并行猜号。预留不代表脚本存在或已应用；已登记的版本不得转让其他领域。若某预留无结构变更，标记“未使用、永久保留”，不能在更高版本应用后回填。B/C/D/E 原计划文件中的文件名占位不改写历史，只由后续taskbrief消费此映射。

|目标版本|拥有任务|原计划占位|计划内容/依赖|当前状态|
|---|---|---|---|---|
|V006|B-01|V001不变|审计binary标识/安全动作码与索引目标；审计脏数据后增量ALTER|预留，未编写|
|V007|B-02|V002不变|目录部门、身份generation、角色、会话时间/代际及binary精确语义；保留旧ACL待切换|预留，未编写|
|V008|B-03＋B-02授权切换/D-02消费|原V003|meeting/info/roster/member/idempotency/meeting_access_grant及R1 meeting_search_projection/person_search、更新时间索引；依赖V007，验证旧ACL迁移来源|预留，未编写|
|V009|B-04|原V004|backend/file/reference/upload/part/cleanup；含1.2拟议 upload_session.display_name LONGTEXT NOT NULL、purpose ASCII CODE NOT NULL及冻结/恢复不变量；依赖V008|预留，未编写；上传补充待独立评审后消费|
|V010|B-05|V005不变|修正subject/task/outbox/permit并补attempt、稳定external_operation及R4 external_execution、consumer；permit关联具体执行；依赖V008/009|预留，未编写|
|V011|B-06|原V006|批次/文件、R5 transcription_input、转写/片段/确认及R6 segment_mark、speaker；依赖V008—010；处理输入material FK延至V017，B只EMPTY/NULL|预留，未编写|
|V012|B-07/D-04消费|原V007|R2 minutes_template逻辑根/规范名称摘要桶、模板版本、生成输入/候选、正文、要素、排除、来源/证据；依赖V011；material指针B只能NULL|预留，未编写|
|V013|B-08|原V008|final_confirmation、publication/attempt/mapping；attempt关联R4具体execution，同确认安全重试复用逻辑操作；依赖V012|预留，未编写|
|V014|C-01/C-02|原V009|media_source/span；依赖V009/011|预留，未编写|
|V015|C-03|原V010|correction/edit_operation；speaker基础已在V011，任何必要扩展另在本号|预留，未编写|
|V016|C-04/C-06|原V011|recording/session/part/chunk、subtitle；依赖V009/010/014|预留，未编写|
|V017|D-01|原V012|material/reference/revision/snapshot/items/parsed_document/unit；为已有transcription_input/generation/final/locator加材料FK后才允许非空，不能用可变meeting当前指针代旧输入|预留，未编写|
|V018|D-02/D-03|原无固定号（必要增量）|info_suggestion；列表/来源完整性加强；既有要素/排除不重复建|预留，未编写|
|V019|D-04|原V013|消费V012模板/版本/候选；必要索引或功能扩展需增量评审；若无DDL则永久保留空号|预留，未编写|
|V020|D-05|原无固定号（必要增量）|publication_governance、external_event_receipt；依赖V013/010|预留，未编写|
|V021|D-06|原V014|export_job；依赖V009/010/012/013|预留，未编写|
|V022|D-07|原无固定号（必要增量）|meeting_deletion；消费V009清理；依赖V008/010|预留，未编写|
|V023|D-08|原V015|topic/meeting_topic含R2完整规范名/非唯一摘要桶、R3可选description；依赖V008/013，命名空间锁复用V007 identity行|预留，未编写|
|V024|E-01|原V016|feedback/feedback_segment/corpus_revision；依赖V009/011；治理权限门|预留，未编写|
|V025|E-02|原V017|voiceprint_consent/sample；依赖V007/009；用途/保留安全门|预留，未编写|
|V026|E-03|原V018|evaluation_dataset/run、configuration_revision/release；依赖V009/010；配置白名单/评测门|预留，未编写|
|V027|E-04|原V019|mail_job/recipient；依赖V007/010/012/013；邮件平台门|预留，未编写|

B-09、C-05、C-07 无专属新DDL；C-02复用媒体/转写/任务，D-03复用V012；E/F只在对应能力启用时迁移。迁移的实际内容须从目标设计计算与各自既有schema的增量，不能把审阅CREATE全文复制成V006。完整审阅库的跨域FK统一在所有表后建立；正式环境每阶段仅建立可满足依赖的FK，应用不允许写尚未部署的非空引用。speaker基础提前进入B-06，C-03复用而不重建。

## 兼容/回滚和验收义务

### B00-1.1评审修正的迁移边界

R1→V008/D-02列表共同F及当前确认姓名投影；R2→V012模板逻辑根、V023专题名称，owner身份锁＋完整规范名碰撞复核，不加名称上限；R3→V023 description LONGTEXT NULL；R4→V010逻辑操作/执行分离及quota执行关联、V013发布attempt引用，新task可以在同business_key/confirmation下追加执行；R5→V011转写输入与结果双向追溯，V017才启用非空材料FK；R6→V011片段标记，C-03复用保存/确认失效/hash规则。这些预留均尚无正式SQL/应用记录，因此是在评审前完善设计，不修改任何已应用/冻结脚本。若接收方已有更高版本或此前草案结构，先报告实际元数据并另从V028分配前向增量，不覆盖旧版本。B00-1.0不可采用，不能把旧79表证据替代1.1完整新库验证。

### B00-1.2 上传补充的迁移边界（待独立评审）

唯一结构增量为 upload_session 追加 display_name 与 purpose，两列无默认且非空，其他表/索引/CHECK/FK不变；现有 token/hash/part 字段的业务语义与写入/恢复协议同步明确。它们属于尚未实施的 B-04/V009，不新增迁移号，不修改已冻结 V001/V002/V005，不重开 1.1 的 R1—R6。独立评审通过后，B-04 才按完整 V001→002→005→006→007→008→009 路径在自有库实现并验证；不得复制本审阅完整 CREATE 为正式迁移。若接收方已执行缺列的 V009 或其他较高版本，先报告实际元数据及旧会话的真实 name/purpose 来源，由协调者从 V028+ 登记前向增量和回填/隔离方案；不得猜值、截断名称、用默认用途伪造历史或修改已应用脚本。本次仅在新建独有空审阅库验证完整目标 DDL，旧 1.1 fixture/证据不改。

每次登记实际脚本补文件名、commit、SHA256、执行库/时间（不记凭证）、Flyway history、前后DDL元数据diff、应用版本窗口和回归链接。独立评审通过设计不等于允许跳过各任务真实持久化/并发/权限测试。

扩展→回填审计→约束→读写切换→受控收缩。所有FK指向显式PK/unique，不启用非唯一键兼容开关；目标schema的跨表同会议关系仍需应用事务验证。[MySQL 8.4 FK](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) 旧身份表或ACL中无法证明确认来源的数据不得转成有效授权；旧task未知效果必须隔离对账，不能默认READY。新增schema保持旧应用可读期间也必须禁止旧ACL/旧worker语义继续写入；若不能兼容，先停止业务写与worker、升级全部实例后恢复。

仅做文档的 B-00 不执行上述任何正式迁移。不对公司/生产数据库操作，不重写冻结脚本，不启用自动历史清除。回滚应用不能恢复旧权限缺陷/未知效果重试语义；新数据已写入后前滚修复优先，恢复备份还须核对文件和外部效果，不依赖数据库单独回滚跨资源副作用。
