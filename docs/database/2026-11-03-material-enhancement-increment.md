# V064 背景材料增强候选增量

新增独立的私有候选表 `material_enhancement_candidate`，不修改冻结的语音 IMPORT/EMPTY profile 或既有 speech checkpoint。迁移 SHA256：`4fe6ae851ba1d8ce107bb381ad329d313ad2473b816c2f6f340d5cf0e204bccb`。已在本地主库及本轮独占集成库按序应用；不重置、repair 或改写旧 SQL。

| 字段／约束 | 含义与边界 |
| --- | --- |
| id、meeting_id | 独立候选 UUID；会议 FK；会议删除先清除此表 |
| actor_id、request_key | 原发起者与原请求键；`(meeting_id, actor_id, request_key)` 唯一；actor 使用二进制排序规则 |
| expected_revision、source_revision_id | 原会议修订、不可变人工工作稿；采用时通过原 SAVE 命令再核对 |
| material_snapshot_id | 当前已解析材料的固定快照，可为明确空集合 |
| profile_sha256 | 独立 `ama-material-enhancement.v1` 输入／结果协议版本摘要 |
| input_body、source_body | 外调前持久化的精确模型请求与原人工稿；各最多 4 MiB |
| output_body | 绑定原请求 SHA、段标识顺序、材料修订的增强结果；最多 4 MiB |
| decision_key、receipt_body | 显式采用前固定决策键；复用原 SAVE 回执，结果丢失按原键恢复；回执读取上限 8 MiB |
| created_at | 服务端创建时间，UTC |

每会议至多 64 个候选，每次至多 16 份材料；源文本至多 4096 段／1048576 UTF-16 单元，获准材料总计至多 262144 UTF-16 单元。每份文本切为至多 65536 UTF-16 单元的连续单元，避免拆断代理对。数据库列为 MEDIUMTEXT，读取 SQL 先按 OCTET_LENGTH 设限，应用再验证结构；不将数据库宽类型当作无限输入许可。

生成前后均准备当前材料权限并在会议锁内检查修订、材料快照及解析状态；真实 MATERIAL_TEXT 从受管文件读取并核对长度与 SHA。候选过期只返回状态元数据。显式采用创建新的 WORKING 版本，保留 RAW、原人工稿、标记和历史；版本变化或撤权拒绝采用。该表是在线私有业务数据，不是语料训练集。

模型调用只走兄弟项目固定 `http://127.0.0.1:8792`，HTTP/1.1、不跟随跳转、有界请求／响应／超时；兄弟持久化精确请求 SHA 和结果。受控“术语对照”规则用于证明材料实际进入请求、产生可审阅差异，不等于企业模型质量验收。

验证索引见 [在线录音修复验收](../acceptance/d/online-recording-repair-2026-11-03.md)。
