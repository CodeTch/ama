# B00-1.2 上传元数据定向补充记录

2026-10-22。**独立增量评审已通过，协调者采用为 B00-1.2-review 当前设计基线。** 1.1 是此前已经采用的基线；其 R1—R6 全部关闭、旧评审报告、完整 metadata 及 fixture 历史保持原样，本记录不替代或重签旧独立结论。

## 精确范围与依据

- 工作树 `/home/tanren/ama/.worktrees/phase-b-upload-design`；分支 `codex/phase-b-upload-design`；BASE `a13e0cd`。
- 正式 brief：phase-a `.superpowers/sdd/2026-10-21-ama-phase-b/task-B-00-upload-addendum-brief.md`；已读取其完整文本、实际 B-04 brief/storage preflight/database task contracts、架构相关约束、需求 CAP-01…06/TX-02/TX-09/CROSS-06、产品补充和决定台账，以及当前完整 `StorageContracts`、`FileStoragePort`、`LocalUploadJournal` 与 B00 upload/file/reference 定义。
- 既定 API 初始化含 `{purpose,name,size,sha256}`，完成仅 `{parts:[{part,sha256}],sha256}`；StorageContracts.UploadSpec 及 local journal 的 spec 无业务 name/purpose。AVAILABLE file 又必须在 blob durable 之后登记，所以重启前后缺少可恢复的业务元数据来源。
- 唯一结构变化：`upload_session` 末尾追加 `display_name LONGTEXT NOT NULL`、`purpose VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`。名称原值是数据，不是路径，不加产品名称上限；用途复用既有 CODE/引用类型，只接受服务端启用且有权的安全码，不发明产品用途表、公司字典或新角色。
- 直接同步声明源/数据字典/审阅 DDL/完整 ER/设计/迁移 V009 登记。未修改正式 Flyway、应用 Java、HTTP 契约文件或其他领域表；未分配新迁移号。

## 恢复链与范围边界

begin 成功响应前原子提交业务元数据、固定位置与存储内部 token；重启按该行恢复。`provider_upload_token` 保存内部 UploadToken.sessionId，结合 backend_id 重建 token，不把 public uploadId 猜成存储会话 ID。

不可变已接纳 upload_part 集合与 expected_size/hash 足以恢复完成决定：按统一锁序锁 session，完整匹配客户端 part/hash 与服务端 receipt，OPEN→FINALIZING 时固定完整 hash；冻结后禁止改片或回 OPEN。未知结果保持 FINALIZING，对固定目标重放/对账且重新确保 durable 后才登记；迟到写入不得更改冻结字节或提交回执。无需新增 JSON manifest、hash 指纹或 revision 列。abort 与完成单向决定、删除代际、同结果幂等及当前权限复验在设计补充中明确。

完成登记事务复制 display_name 到 file_object，并保留 purpose 到 session 与 `file_reference`。B-04 注册器用技术 owner `UPLOAD_SESSION` 保留尚未被后续领域消费的已返回 fileId；领域接收在同事务建立自己的合法引用再解除上传引用。会话结果/清单不因普通 TTL 清空，业务列＋账本共同保护删除/重放，未使用新产品用途或新表。

未发现必须另加列的结构不可能。本次不新增 creator_id（每次 MeetingAccess，操作者已有审计/幂等）、part_size（建议值与实际逻辑 receipt 分离）或通用 JSON 元数据。S3 任意逻辑分片到合法 transport parts 的受保护暂存/合并及其恢复清单、迟到 I/O/complete/abort 竞争仍由 B-04 独立实现和共同契约测试。旧 preflight 的传输 partSize 建议不转化为本轮业务 schema 新字段；如要改共同协商约束，须另作契约评审。

## 新空库完整验证

本任务仅新建并操作 fixture `ama-test-b01-865006926142`，loopback `127.0.0.1:46067`、schema `ama_b01_fixture`。helper 所在工作树为本次设计树，镜像固定 `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`，实际版本 **8.4.6**。未连接、执行 SQL、重置或清理旧 1.1 / 其他任务 fixture；本库保留供协调者处理，凭证仅在忽略的本地 `.tools`。

实际命令（WSL，从 Windows-local cwd 用 wsl.exe --cd/--exec 启动）：

```sh
cd /home/tanren/ama/.worktrees/phase-b-upload-design
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
python3 tools/checks/database_design.py generate
python3 -m py_compile tools/checks/database_design.py
python3 tools/dev/mysql_fixture.py start
python3 tools/checks/database_design.py verify --container ama-test-b01-865006926142
```

完整 CREATE＋所有 FK＋实际全部 information_schema 读取/比较 **exit 0**：**85 表、674 列、270 索引定义/404 索引列、99 ENFORCED CHECK、179 FK/186 FK列**。每列类型、顺序、NULL/default/extra/排序；索引列序/唯一性/前缀；CHECK AST/ENFORCED；FK目标/RESTRICT全部一致。字典/DDL/ER逐字节同步。既有非法 storage enum 与未知 identity FK 负向分别3819/1452，AST保留分组/字面值测试通过。本轮没有失败 DDL/metadata 运行。

本版 DDL SHA256：`402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362`。完整实际证据：[database-upload-addendum-metadata-evidence.json](database-upload-addendum-metadata-evidence.json)。生成器仅把当前版验证输出改到此独立路径，保留旧 [database-metadata-evidence.json](database-metadata-evidence.json) 原字节。

全量新旧 metadata 逐项程序比较 **exit 0**，见 [database-upload-addendum-delta-evidence.json](database-upload-addendum-delta-evidence.json)：所有旧元数据行均保持一致，仅新增 upload_session 第14列 display_name 与第15列 purpose；tables/indexes/checks/foreign_keys数组完全相同，列无移除/改序。基线 1.1 DDL SHA256 仍为 `16d69102d5b775130efac720a31794fcedf0ff27f4f0002ef3fb9756a605d183`；旧完整 metadata 文件 SHA256 `3ada3636bdfcecdf5502dca48378b11f26b88e827f8fe6ab05efec9fee679d54`。同时逐字节比较 BASE 的旧三个评审记录、旧 metadata 和 V001/V002/V005，7项全等；具体 SHA 保存在 delta evidence。

## 自审与独立门

自审核对：增量只有两列；已有 R1—R6 结构/约束和记录未动；完整 schema 与生成物同步；新旧证据分离；初始化和完成恢复均有字段来源；名称不作路径；purpose 不构成新授权；已返回上传文件有引用保护；单向冻结及最终登记仍遵循短事务/先存储 durable 后 DB；不把数据结构成功冒称应用行为通过。

尚待 B-04 的行为证据：真实 API＋MySQL 跨进程 begin→restart→complete 的 name/purpose round trip，长名称/中文/引号/反斜杠/换行无损，未知/停用/无权用途，冻结前后重启与迟到分片，同请求双完成/异请求冲突，存储成功 DB 提交失败的不可读孤儿与单次登记，complete/abort/delete竞争，以及两个后端共同契约和 S3 transport 适配。没有运行 Maven/应用恢复/并发/性能/HA/公司集成测试。本轮证明结构可建且一致、设计链可追踪，不能关闭这些实施义务。

独立评审由协调者对精确 BASE..HEAD 安排；在该结论之前本版保持 **pending independent review**。V009 仅在通过该门后消费，若已有缺列 V009 则按 registry 从 V028+ 前向迁移，不改旧脚本或猜造历史值。

## 协调者采用记录

- 作者 `40cd72717abf079d3373c072eddea19f881a8916`，范围 `a13e0cd..40cd727`；独立 `/root/b00_upload_review` 判定 spec compliant、quality Approved，Critical/Important/Minor 均0，原始报告见 [独立评审](database-upload-addendum-independent-review.md)。
- 原字节集成 `33dd83f`；除中央迁移登记保留协调者 V006 时间线记录外，七个对应路径与作者 HEAD 完全一致。采用只更新状态/保存原报告，不改已验证生成 DDL/数据字典/ER，DDL SHA256 仍为 `402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362`。
- 1.1 历史 metadata、原始三份评审和全部冻结旧 SQL 保留；V006 偏差由独立记录处理，不由本次评审覆盖或免除。
- 允许后续 B-04/V009 消费两列及恢复协议；真实应用恢复、并发、权限、文件登记和两个后端共同契约仍未验收。
