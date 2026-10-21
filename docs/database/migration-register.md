# AMA 统一 migration-register

版本 B00-1.3-review（纪要目录及复核增量已独立评审；原1.2上传补充保留），2026-10-22；已采用 B00-1.1-review 的 R1—R6 独立关闭结论不变。这是迁移所有权/顺序登记，不是执行记录。实现仍须按统一新版本修正差异、回归并独立评审后验收。Flyway执行器单实例运行；已应用/已共享/已冻结脚本不可改字节或重编号。DDL多语句不是可整体ROLLBACK的事务，MySQL atomic DDL只保证单条语句原子；中途失败检查实际schema并前滚修复。[MySQL atomic DDL](https://dev.mysql.com/doc/refman/8.4/en/atomic-ddl.html)

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

冻结高水位 V005；V003/V004 永久留空，不补低号。以下 V006…V027 为一次性连续预留的依赖顺序，在登记中即明确所有者。实施者只消费自己的预留号；新的整数阶段版本从**V028**向上由协调者登记后分配；已冻结版本的必要修正可由协调者在下一整数版本尚未应用前统一登记该阶段小数补丁号（详见V011.1裁定），实施者不得自行使用，禁止并行猜号。预留不代表脚本存在或已应用；已登记的版本不得转让其他领域。若某预留无结构变更，标记“未使用、永久保留”，不能在更高版本应用后回填。B/C/D/E 原计划文件中的文件名占位不改写历史，只由后续taskbrief消费此映射。

|目标版本|拥有任务|原计划占位|计划内容/依赖|当前状态|
|---|---|---|---|---|
|V006|B-01|V001不变|审计binary标识/安全动作码与索引目标；审计脏数据后增量ALTER|来源 f5b4495+0e8f698 → 集成751d31c+95a264e；冻结f36…；独立复审支持新建确认空白fixture限定采用，历史偏差保留|
|V007|B-02|V002不变|目录部门、身份generation、角色、会话时间/代际及binary精确语义；保留旧ACL待切换|来源39b447d → 集成bec9393；SHA859970b8…冻结；前置实现独立评审通过，正例/隔离拒绝证据已保全；完整B02授权切换待V008|
|V008|B-03＋B-02授权切换/D-02消费|原V003|meeting/info/roster/member/idempotency/meeting_access_grant及R1 meeting_search_projection/person_search、更新时间索引；依赖V007，验证旧ACL迁移来源|来源ff22197 → 集成dac4cc1；SHAfa44424a…冻结，结构前置独立Approved0findings；正例与两类拒绝已保全，完整B03/B02授权仍OPEN|
|V009|B-04|原V004|backend/file/reference/upload/part/cleanup；含已采用1.2 upload_session.display_name LONGTEXT NOT NULL、purpose ASCII CODE NOT NULL及冻结/恢复不变量；依赖V008|来源45715b6+fd3c3d8 → 集成1a00ca4；SHA79f04898…冻结，结构前置独立Approved0findings/95已选用例通过；完整B04仍OPEN|
|V010|B-05|V005不变|修正subject/task/outbox/permit并补attempt、稳定external_operation及R4 external_execution、consumer；permit关联具体执行；依赖V008/009|03e89cc8…冻结/首次成功；完整框架及fix1已独立采用（8a3b77b/e9d2eca/a79ce67），真实公司/领域消费者门另计|
|V011|B-06|原V006|批次/文件、R5 transcription_input、转写/片段/确认及R6 segment_mark、speaker；依赖V008—010；处理输入material FK延至V017，B只EMPTY/NULL|a926c8b5…冻结；首次success/旧缺索引失败原件保留，配合V011.1结构独立PASS，归集022f68a/71c7c5c/1d710f9；完整B06仍OPEN|
|V011.1|B-06 前向修正|V011不变|补齐B00-1.2已评审material_snapshot_id支撑索引；依赖成功V011，必须在预留V012前应用；正式文件V011_1__transcript_material_index.sql|9efc688e…冻结；ACK后rank10首次success/checksum-570494170；实际11suite全通过/五组元数据一致，独立PASS采用；仅纠正索引|
|V012|B-07/D-04消费|原V007|R2 minutes_template逻辑根/规范名称摘要桶、模板版本、生成输入/候选、正文、要素、排除、来源/证据；1.3新增敏感/背景目录及来源关联、action_suggestion、result_review/evidence共七表，排除PK保留双标签；依赖纠正后V011.1；material指针B只能NULL|10742ca2…完整21260bytes候选已中央完整读取/静态逐字节比对，首次应用前冻结；仅自有b62b9b2b…:44915，须本记录commit后另行ACK；尚未应用|
|V013|B-08|原V008|final_confirmation、1.3 final_result_review双physicalFK、publication/attempt/mapping；attempt关联R4具体execution，同确认安全重试复用逻辑操作；依赖V012|预留，未编写|
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

## V006 当前冻结、限定采用与时间线偏差保留

来源 `codex/phase-b-audit-delta` 的 `f5b44955318564ecc68ce6b2d5a2274c46d35cc2` 包含 `V006__audit_metadata.sql`，当前共享/最终实测 SHA256 为 `f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f`，Flyway checksum `-1744360556`。成功回归后保留的元数据快照来自拥有者 fixture `ama-test-b01-fb4e04459580`（127.0.0.1:45443），该快照 history 为001→002→005→006，均success=1。随后日志修复的聚焦负例测试将同一自有一次性 fixture 留在预期 failed V006 状态；旧成功快照不冒称当前库状态。当前文件从该提交起冻结，不得因评审修改字节、改名、重编号或repair。

执行时间线另有一项已向用户报告的偏差：作者首次私有 fixture GREEN 使用 `e1af32204c0b75d6ef922ca1e5677e259b0d4c4c47b43c3945f52bde314d8e8d`，之后增加冗余的 `CHAR_LENGTH(result)>64` 预检，再重置该自有一次性 fixture 并验证当前 `f36b…`。这不符合“已应用或共享的迁移保持不变”的要求；不能因尚未提交而豁免，也不能事后改称 review-only。作者正在保全原字节、原始运行日志和两份哈希并准确记录可观察影响；独立复审支持当前冻结版本仅用于新建、独立所有且确认空白的 fixture；历史违规仍保留为 PARTIAL，不能重述为规则合规。原始首次执行哈希的来源是作者恢复的源码与说明，最终哈希及当前数据库元数据有直接实测证据；不把恢复说明当独立现场观察。

不得将当前 V006 自动用于持有旧 checksum 的库。发现旧版本 history/未知来源时立即停止，保留库与证据并提交单独可审阅的恢复方案；不更改 checksum、不启用 outOfOrder、不覆盖原脚本。当前共享文件也不通过恢复成旧字节来制造第二次修改。后续所有正式迁移首次应用前记录 SHA，首次应用（包括私有 fixture）后即冻结；结构修正必须先由协调者登记有序新版本，不由实施者自行猜号。

## V006 实施采用记录（2026-10-22，限定新空测试库）

完整初审 `45ce136..f5b4495` spec compliant/quality Approved，无 Critical/Important，预期负例日志为 Minor；修复 `f5b4495..0e8f698` 经独立增量复审日志问题 ADDRESSED、无新增破坏，历史 B01-IMM-01 保留 PARTIAL。采用精确当前 SHA `f36b…` 用于新建、独立所有且先确认空白的受控测试库；旧 checksum、已有失败 history、未知来源及未确认空白环境均不在该批准范围，先保全并单独审阅恢复方案。不会对作者当前失败 fixture 执行重置或修复来伪装成功。

实现与修复集成 `751d31c`、`95a264e`；完整 source/测试/helper/归档与作者 `0e8f698` 一致。当前 V001/V002/V005/V006 SHA 全部复核，e1af 历史源码已逐字节保存在 `docs/database/migration-history/V006-e1af3220.sql.txt`，位于所有 Flyway location 之外。真实测试、前后元数据和 Flyway history 见 [实施记录](../acceptance/b/audit-metadata-delta.md)、[初审](../acceptance/b/audit-metadata-delta-independent-review.md)、[增量复审](../acceptance/b/audit-metadata-delta-fix-independent-review.md)；原始日志/XML/两份SQL字节与SHA清单另保存在 `artifacts/phase-b/b01-audit-delta/`。记录不批准任何旧库 repair、重置或 checksum 替换。

## 兼容/回滚和验收义务

### B00-1.1评审修正的迁移边界

R1→V008/D-02列表共同F及当前确认姓名投影；R2→V012模板逻辑根、V023专题名称，owner身份锁＋完整规范名碰撞复核，不加名称上限；R3→V023 description LONGTEXT NULL；R4→V010逻辑操作/执行分离及quota执行关联、V013发布attempt引用，新task可以在同business_key/confirmation下追加执行；R5→V011转写输入与结果双向追溯，V017才启用非空材料FK；R6→V011片段标记，C-03复用保存/确认失效/hash规则。这些预留均尚无正式SQL/应用记录，因此是在评审前完善设计，不修改任何已应用/冻结脚本。若接收方已有更高版本或此前草案结构，先报告实际元数据并另从V028分配前向增量，不覆盖旧版本。B00-1.0不可采用，不能把旧79表证据替代1.1完整新库验证。

### B00-1.2 上传补充的迁移边界（已独立评审采用）

唯一结构增量为 upload_session 追加 display_name 与 purpose，两列无默认且非空，其他表/索引/CHECK/FK不变；现有 token/hash/part 字段的业务语义与写入/恢复协议同步明确。它们属于尚未实施的 B-04/V009，不新增迁移号，不修改已冻结 V001/V002/V005，不重开 1.1 的 R1—R6。独立评审通过后，B-04 才按完整 V001→002→005→006→007→008→009 路径在自有库实现并验证；不得复制本审阅完整 CREATE 为正式迁移。若接收方已执行缺列的 V009 或其他较高版本，先报告实际元数据及旧会话的真实 name/purpose 来源，由协调者从 V028+ 登记前向增量和回填/隔离方案；不得猜值、截断名称、用默认用途伪造历史或修改已应用脚本。本次仅在新建独有空审阅库验证完整目标 DDL，旧 1.1 fixture/证据不改。

每次登记实际脚本补文件名、commit、SHA256、执行库/时间（不记凭证）、Flyway history、前后DDL元数据diff、应用版本窗口和回归链接。独立评审通过设计不等于允许跳过各任务真实持久化/并发/权限测试。

扩展→回填审计→约束→读写切换→受控收缩。所有FK指向显式PK/unique，不启用非唯一键兼容开关；目标schema的跨表同会议关系仍需应用事务验证。[MySQL 8.4 FK](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) 旧身份表或ACL中无法证明确认来源的数据不得转成有效授权；旧task未知效果必须隔离对账，不能默认READY。新增schema保持旧应用可读期间也必须禁止旧ACL/旧worker语义继续写入；若不能兼容，先停止业务写与worker、升级全部实例后恢复。

仅做文档的 B-00 不执行上述任何正式迁移。不对公司/生产数据库操作，不重写冻结脚本，不启用自动历史清除。回滚应用不能恢复旧权限缺陷/未知效果重试语义；新数据已写入后前滚修复优先，恢复备份还须核对文件和外部效果，不依赖数据库单独回滚跨资源副作用。

## V007 首次正式应用前冻结（2026-10-22）

拥有者工作树 `phase-b-identity`，执行通知时 HEAD `a4ed859bb7d678d7c821646ab75ee959163bb767`，文件 `V007__identity_sessions.sql` 的 SHA256 **`859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`** 已由协调者读文件重新核对。单独标记的 raw-DDL 设计验证使用新 sandbox `ama-test-b01-5e2d9b66e6c2:44735`，没有 Flyway/history，不当成正式迁移应用记录。首次正式正例目标为新独有 `ama-test-b01-c8517df9ef69:45905`，拥有者已只读确认所有权/loopback/0表。此版本从首次正式应用或共享起永久冻结；后续修正先统一登记有序新版本，不改字节。正式执行/history/metadata/回归和独立实现评审仍待登记，不提前验收。

## V008 候选冻结与依赖门（2026-10-22）

文件 `V008__meeting_schema.sql`，拥有者工作树 `phase-b-meeting-schema`，SHA256 **`fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`** 已由协调者读文件核对。八表及15个FK约束/16个FK列为作者候选说明，尚非实现验收；已正式迁移的低集合只在新独有 fixture `ama-test-b01-7b3ec2548012:45527` 做了缺表RED。V008尚未执行，必须等待独立评审通过的V007并获得协调者执行通知；没有占位V007、越序或未审peer脚本。候选从此次交付冻结，任何修正先报告协调者登记新版本；正式执行/history/metadata/回归/独立评审待记录。

## V007 实施评审采用记录（2026-10-22，身份前置范围）

来源 `39b447d43a91cc0692a5d43ee109fcc82d37d6db`，完整 owned 评审基线 `a4ed859..39b447d`（之前机械合入的已审根内容不重复计为本任务），独立评审 spec compliant / quality Approved，Critical/Important/Minor 均无。集成 `bec93930657c349a9662eea5a347c55186fa18fe` 的29个交付路径与来源逐字节一致。V007 SHA `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d` 再次复核，Flyway checksum `157746768` 不变。

首次执行前冻结记录保留。新独有正例 `ama-test-b01-c8517df9ef69:45905` 的001/002/005/006/007全部success=1；另建负例 `ama-test-b01-768977c381e0:46617` 保留007 success=0、原三张身份/会话/ACL表DDL及行不变，不重置或repair。正式升级拒绝无权威来源的旧身份/会话/ACL数据；没有猜测日期、部门、权限或默认READY。旧B02库、B01失败库均未触碰。

实际身份18、迁移1、隔离拒绝1通过；回归Python74/web8/Java28/OpenAPI2及生成类型校验通过，最后Java修改后重跑完整身份18和Java单元，未重复不变套件。当前采用仅身份/会话/目录接口及安全前置；MeetingAccess仍为契约，公司SSO/人员目录仍不可用，完整B02对象授权待V008后独立实现评审，不据此验收完整B阶段。见 [独立评审](../acceptance/b/identity-prerequisite-independent-review.md)、[实现报告](../acceptance/b/identity-prerequisite-implementation-report.md)。45个原始证据文件与SHA清单已逐字节保存在 `artifacts/phase-b/b02-prerequisite/`，未复制凭证。此次集成不操作数据库或重复已有测试。

## V008 实施评审采用记录（2026-10-22，八表结构前置）

来源 `ff22197f5f6231aeb689e035f4435ddbbcbcf114`，完整owned基线 `62a8ec5..ff22197`，独立 spec compliant / quality Approved，Critical/Important/Minor均无。集成 `dac4cc11bf07c8a2c81eeafed541687d5beb3a69`，5个交付路径与来源逐字节一致。V008 SHA `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3` 再次复核，Flyway checksum `-1747387036`；此前9572b5a预执行冻结记录保留，应用后无字节修改。

实际8表/71列/24索引定义及47索引列/12CHECK/15FK及16FK列，与已审B00-1.2独立实测子集完全一致。正例 `ama-test-b01-7b3ec2548012:45527` 保留001→002→005→006→007→008全部success1；独立draft拒绝 `ama-test-b01-90171737c2cf:46377` 和timestamped拒绝 `ama-test-b01-79de6fc2da7a:45069` 各保留008 success0及原DDL/ACL行，不reset/repair。首成功和后续正例快照分别标注，不冒称同一时刻。

实际结构6、隔离拒绝1+1、Foundation正常路径13、IdentityAccess18、后端单元28全部0failure/error/skip；首升级1与结构6重叠不重复计数。45原始证据及SHA清单已核对/保全于 `artifacts/phase-b/b03-schema/`。未重复原V006破坏失败history的参数负例，历史IdentityMigrationIT的latest007假定未运行且另行修正显式target007，绝不把未选测试计通过。见 [实施报告](../acceptance/b/meeting-schema-prerequisite-implementation-report.md)、[独立审查](../acceptance/b/meeting-schema-prerequisite-independent-review.md)。

仅解锁V009结构依赖，完整B03会议业务/版本/幂等/投影/subject/outbox和B02对象授权待V010后实施评审；不启用业务写，不创造旧ACL来源，不批准实际有数据安装升级或公司生产环境。此次机械集成只验证字节与范围，未重复已通过且未改变的测试，未操作数据库。

## V009 首次正式应用前冻结（2026-10-22）

拥有者 `phase-b-file-schema`，文件 `V009__file_schema.sql`，SHA256 **`79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d`**，7239bytes。协调者已完整读取文件、重新计算SHA，并程序核对六CREATE及9FK与已审B00-1.2审阅DDL对应块逐字节一致；仅部署注释/SET是前置，不含seed/额外表/IF NOT EXISTS。原冻结低集合不变；V008已独立采用，见2a569eb。

作者已在新独有确认空白 `ama-test-b01-204cc11baabc:45041` 执行已审低集合到008，报告六表missing真实RED1failure/0error/0skip并保留低集合快照。较早helper路径错误另存不算RED。当前V009未执行、未实施评审；此处仅完成首次正式应用前冻结及受控执行依赖门，不提前验收。此次共享后的字节永久冻结；任何修正先由协调者登记有序新版本，不改此SQL或repair。正式执行checksum/history/meta/回归/独立评审待另行登记。

## V009 实施评审采用记录（2026-10-22，六表结构前置）

完整owned基线 `2a569eb..fd3c3d86e1c7998f95ded65704c3453579116d06` 包含准备提交45715b6和最终交付，独立 SPEC compliant / quality Approved，Critical/Important/Minor均无。协调者先接入准备提交再最终提交，未把机械merge dcc5302当review BASE；接入完成 `1a00ca41775303da6637eba74b2b5d633616fc83` 时63交付路径均与作者finalHEAD逐字节相同。V009 SHA `79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d` 不变，Flyway checksum `-287948759`。b3c00ce预执行冻结早于01:51:49首次应用，原低集合不变。

实际6表/53列/16索引定义与36索引列/7CHECK/9FK，独立B00-1.2源、期望、首成功/最终/独立Foundation快照完全匹配。正例 `ama-test-b01-204cc11baabc:45041` 从未reset，27表及001→002→005→006→007→008→009全部success1；独立新Foundation `ama-test-b01-2281b6270fc4:46305` 只运行已选正常路径，保留相同7版本successhistory。真实缺表RED与配置错误分别保全；没有修改冻结SQL、repair或失败history清除。

最终schema8/Foundation正常13/local contract7/local durability39/Java units28均0failure/error/skip，95不同已选用例；重复首成功8和附带单元不重加。55原始证据及SHA清单保留在 `artifacts/phase-b/b04-schema/`，完整独立核对通过；源/raw报告另存 `b04-schema-coordinator-reports/` 以保全换行前原始字节。见 [实施报告](../acceptance/b/file-schema-implementation-report.md)、[独立审查](../acceptance/b/file-schema-independent-review.md)。

仅解锁V010结构依赖；公共注册/权限/回执冻结/文件先持久化再DB登记、孤儿不可读及引用epoch清理、双后端闭环、完整B03/B04与生产写入仍OPEN。不重复已通过未改变的测试来制造集成通过，也未在此机械集成操作数据库。

## V010 首次正式应用前冻结（2026-10-22）

拥有者 `phase-b-tasks`，文件 `V010__reliable_execution.sql`，SHA256 **`03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db`**，35136bytes/225lines。协调者已完整读取SQL并重新计算SHA；程序解码全部五组hex JSON预检，与先前已独立采用的B04低集合历史快照逐项核对：6表/33列/16索引列/4CHECK/4FK列完全一致。单独标记的raw-review-only sandbox `ama-test-b01-5833aa5fe461:45009` 设计实测十表/91列/43索引列/16CHECK/10FK列与B00-1.2独立完整元数据子集相等；它没有正式V010 history，原raw候选及哈希/过程保留，不改称正式应用。

正式脚本在所有永久DDL之前验证完整已审低版本history、旧task/outbox/reservation/capacity为空、outbox序列恰为(1,1)、保留subject只对应当前ACTIVE且version/epoch精确匹配的MEETING；精确结构预检拒绝漂移/额外目标表。只对已证空表删除旧列并增量ALTER，不DROP整个旧表、不猜输入/额度、不重置序列或fabricate旧来源。新建四表并建立已部署FK；多语句DDL并非整体可回滚，失败须保全history/schema后单独审阅前滚恢复。

本次只允许该精确冻结候选在拥有者新建、独立所有、先确认空白且按001→002→005→006→007→008→009成功顺序初始化的拟正例 `127.0.0.1:45979`（精确容器名须拥有者执行前捕获核对）及后续明确独有受控fixture验证；正式首次执行时间、checksum、history/前后元数据与行为回归另登记。拟正例已报告只执行temporary preflight成功、没有永久V010 DDL。本记录不提前验收实现/消费者或开启生产业务写。若容器名与实际不符，以拥有者捕获的精确身份更新后才执行，不凭端口认领。

从此次共享起字节永久冻结；任何缺陷先由协调者统一登记有序新版本，不修改此文件、repair或重置失败history。当前主机AIO资源耗尽下，新独有功能SQL fixture的process-local native-AIO-off例外须记录实际参数和@@值，仅证明功能，不代表原I/O或100录音/300在线容量资格。完整B05并发/崩溃/模型占额/Outbox独立评审仍OPEN。

### V010 首次正式成功观察（非完整B05验收）

实际正例为 `ama-test-b01-3ee46f689f9a:45979`。中央冻结提交2f621d6时间02:37:14(+08)，执行前快照02:38:11、首次应用日志02:38:18—02:38:25、首成功快照02:38:44，顺序一致。001/002/005/006/007/008/009/010全部success1，V010 Flyway checksum **`-1187397378`**；当前03e89cc8…字节仍冻结。协调者独立程序比较正式十表全部5组元数据与B00-1.2原始独立完整快照，10表/91列/43索引列/16CHECK/10FK列全部相等。原低版本checksum未变。

两个时间点快照和各自SHA分别保存在 `artifacts/phase-b/b05-first-formal/`，它们是历史观察，不冒称后续行为测试后的当前库。实际@@innodb_use_native_aio=0，按上述仅新独有功能fixture的process-local例外，不作I/O/容量资格。并发、外部未知查回、额度、Outbox、崩溃与所有领域消费者仍待完整实现回归和独立评审，当前不据此开启生产写入或验收B05。

## V010 实施评审采用记录（2026-10-22）

完整owned基线0daecad85..682a7bf，初审两处Important经fix682a7bf..b126db2关闭；fresh scoped SPEC PASS / quality APPROVED、0新问题。采用8a3b77b/e9d2eca/a79ce67。235路径中234逐字节一致，父POM仅保留S3BOM并插入worker的机械合并，另经独立评审；合并后真实canonical unit62/0/0/0。

V01003e89cc8…/35136bytes/checksum-1187397378不变，首次冻结和成功观察保留，旧001/002/005/006/007/008/009校验不改。原201Git对象/144sealed证据/原始预备stash和29项新fix证据均保全。修复只改运行时代码，没有重写SQL、repair、reset或新增DBrestart。详细采用范围、原47IT与fix5/受影响22的执行区别、实际本地DB恢复1476ms、独立observe与同事实重交规则见[框架采用记录](../acceptance/b/task-execution-adoption.md)。

只解锁兼容B03/B02ACL业务开发和不变V011子集正式迁移的受控验证。V011首次正式应用仍需root完整SQL读取、SHA登记与ACK；完整B04/B06以后消费者及B阶段验收不提前通过。真实SSO/模型/知识库/邮件/额度cutoff与RHEL原生I/O/HA/容量前置继续保留。

## V011 首次正式应用前冻结（2026-10-22）

拥有者phase-b-transcript-schema，正式文件V011__transcript_schema.sql，SHA256 **a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb**，10941bytes。root已完整读取，并独立程序核对8CREATE逐字及21deployed FK逐行等于独立采用B00-1.2 DDL402def65…；只有fk_transcription_input_3→material_snapshot延至V017。除UTF8/UTC两个SET外无额外DDL/DML/seed/IF NOT EXISTS。八CREATE全部早于FK，processing/source循环用先建表后外键处理。

中央冻结记录时间：2026-10-22T04:08:19.900881+08:00。完整候选字节及比较JSON保存在artifacts/phase-b/b06-schema-freeze；原准备raw-review-only副本及compile-only记录不改称正式应用。作者报告新独有ama-test-b01-59f108f79cad:45591 initial0tables、实际nativeAIO0、001/002/005/006/007/008/009/010八版本success；真实缺八表RED1assertfailure/0error/0skip，早先Flyway target011无SQL的配置错误单独保留不算RED。中央ACK需核对既有安全capture后单独发出，不能因本记录提前执行。

自正式共享起字节永久冻结；任何后续问题先登记新有序版本，不修改SQL、repair/reset/outOfOrder。此处不提前验收V011/B06业务/格式/确认/标记服务，也不表示公司或容量/原生I/O资格。仅B00-1.2不变八表子集；V012目录/复核增量设计仍待独立评审，不随本冻结获采用。

### V011 首次成功及未通过的结构验收

精确ownfixture487a1cd59…:45591在ACK后首次V011成功，installed_rank9、success1、actualchecksum-286330771，原SQL a926c8b5…不变。UTC原生clock/history补证为2026-10-21 20:10:05，epoch1792613405；原snapshot的04:10:05字符串来自JDBC Timestamp.toString按Asia/Shanghai渲染，不能误标为server UTC。两份原始/校正解释证据均保留，不改原snapshot。

首次完整suite11tests/1failure/0errors/0skips：结构比对发现唯一缺失fk_transcription_input_3(material_snapshot_id)索引。原因是完整B00 DDL由材料FK隐式创建该索引，FK延后时正式V011没有显式保留索引；不能把预期索引从oracle删除来宣称通过。root独立比较确认tables8/8、columns63/63、CHECK13/13、FKcolumns22/22一致，而indexes38/39，只有这一个missing、无extra。其余10项通过不替代全suite验收；首次失败和旧SQL/history全部保留。

### 中央前向修正裁定：V011.1

统一登记**V011.1**（文件V011_1__transcript_material_index.sql）归B06，只补已评审而遗漏的索引；V011与所有低版本不可重写或repair。V012—V027原整数预留和所有者保持不变，下一新增整数仍V028。不得为了补此索引提前应用V028后再outOfOrder回填低号。

只允许root逐案登记阶段补丁号；父版本已冻结，下一整数阶段尚未应用，且实际pinnedFlyway必须验证011 < 011.1 < 012和最终真实history顺序，才可在严格匹配的已知fixture应用。已应用更高版本的库不属于本次许可，停止并单独审阅前向路径，禁止启用outOfOrder/repair。V012依赖相应B06修正正式采用。候选补丁最终SQL/SHA仍须root完整读取、登记及独立ACK前不得第一次执行。此修正不新增设计范围，设计基线本就包含索引；B06结构验收仍OPEN。

### V011.1 首次正式应用前冻结

文件V011_1__transcript_material_index.sql，397bytes，SHA256 **9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713**。root已完整读取并验证唯一执行语句CREATE INDEX fk_transcription_input_3 ON transcription_input (material_snapshot_id)，没有IF NOT EXISTS、改列、改行或材料FK。原V011 a926c8b5…保持不变。

实际pinnedFlyway12.4.0 MigrationVersion与ResourceNameParser离线检查证明011<011.1<012，filename解析011.1且valid，未打开DB连接。root复核probe源SHA与实际JAR SHA c3f93df1…；完整来源/probe/JSON/候选字节保存在artifacts/phase-b/b06-index-patch-freeze。冻结记录时间：2026-10-22T04:17:13.930896+08:00。

本记录commit后root另发ACK，仅限原487a1cd59…:45591、known successful history至011且无更高版本的独有fixture。正式执行后验证actual history顺序/checksum、只增一索引、原rows/DDL/historyprefix不变、全suite与latest rerun0；失败证据不删除，未通过不验收。不授权任何其他小数版本、V012抢跑或生产调用。

## B00-1.3 设计增量采用（不代表正式迁移）

来源0d2079cd..7fb166b，fresh独立SPEC/QUALITY PASS、0findings，采用8e44080。93表709列实际元数据与字典/ER/DDL一致；原85表仅sensitive_exclusion PK增加category及两类CHECK，其他原表/字段/FK和V011八表子集不变。七新表V012，final_result_review随parentV013立即建physicalFK，不新增deferredFK、JSON附件owner kind或generation_input字段。详见[设计采用](../acceptance/b/database-generation-addendum-review.md)与[独立评审](../acceptance/b/database-generation-addendum-independent-review.md)。

V012/V013尚无正式共享/应用脚本；如实施时实际状态不同，停止并统一登记前向修正，不以设计采用改写历史。V011.1另行处理旧索引漏项，其SQL验收另审。首结果保存及证据核对交互仍pending，结构不推断业务许可。


## V011＋V011.1 纠正结构正式采用（2026-10-22）

完整0d2079cd..aef885d经fresh独立SPEC/QUALITY PASS0findings，归集022f68a/71c7c5c/1d710f9，51paths字节验证。原V011a926c8b5…以及中央新登记V011.1的9efc688e…均与首次应用前冻结副本一致，不修改已应用/共享迁移。11.1仅增加遗漏的材料FK支撑索引，FK仍V017；实际metadata8/63/39/13/22相等，纠正后suite11/0/0/0，原行/DDL/historyprefix保留，两次rerun各0。时间按原生UTC/epoch读取，原JDBC Asia/Shanghai显示原件保留。[实施采用](../acceptance/b/transcript-schema-implementation-report.md)、[独立评审](../acceptance/b/transcript-schema-independent-review.md)。

V012结构工作可按已采用1.3继续准备，其最终正式SQL仍必须中央完整阅读/SHA冻结提交/ACK后才首次应用；尤其保留延后FK的全部已评审支撑索引，不能再次删减oracle。完整B06/后续领域与B最终验收仍OPEN，未知/失败/高于11且缺11.1的库不在本路径许可内。


## V012 首次正式应用前冻结（2026-10-22）

文件V012__minutes_evidence.sql，21260bytes，SHA256 **10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7**。完整候选已在root阅读，十九CREATE与已采用B00-1.3完全同字节，所有CREATE后再建立44部署FK；只延后fk_generation_input_5(material_snapshot_id→material_snapshot.id)与fk_source_locator_3(material_revision_id→material_revision.id)至V017，并显式保留这两个索引。没有未来材料/最终membership表、schema新设计、seed、IFNOTEXISTS、旧SQL修改或重编号。

root独立从actual1.3metadata a6c758d4…过滤验证oracle d051aaa9…；目标19表123列92索引列21CHECK45FK列，仅过滤两个未部署FK。完整SQL/来源比较/原始RED和lower snapshots保存在artifacts/phase-b/b07-schema-freeze。冻结UTC：2026-10-21T20:53:07.558007+00:00。

只限新独有fixture ama-test-b01-feb4b5365dae，完整ID b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e，127.0.0.1:44915；原0表/MySQL8.4.6/nativeAIO0功能限定。lower001→002→005→006→007→008→009→010→011→011.1全部success、checksum及源SHA一致，已真实缺十九表assertionRED1/1/0/0，RED前后旧行/DDL/metadata/history不变（after仅多源SHA记录key）。JDBC原installed_on字符串按Asia/Shanghai显示，不标成serverUTC；原生UTC/epoch补证在最终交付保留。

本记录commit后root单独发送精确SHA ACK，拥有者才可按原候选字节复制到正式location、首次Flyway012并运行完整物理suite、五组actualmetadata/旧数据与historyprefix/重复迁移0验证。候选自共享起已冻结，任何问题保留原件并统一登记前向新版本；不改旧迁移/oracle、repair/reset/outOfOrder。未知/失败/高版本缺低号库不在许可范围。B07契约两项来源追溯修订不改变这十九表；复杂材料运行时仍受前置门限制，完整B07/B最终验收未获通过。
