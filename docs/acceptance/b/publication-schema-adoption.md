# V013 发布结构前置采用记录

2026-10-22。完整来源 `7f061e8a..d9428eeb`（准备 `1c8d3ef` 与最终 `d9428eeb`）经新的独立审查，SPEC PASS、技术质量 APPROVED，P0—P3 均为零。协调者已完整阅读作者报告和[独立审查](publication-schema-independent-review.md)，接入为 `ee0bcd2`、`9bb6fe2`；追加本记录前，95 个交付路径均与作者最终 Git blob 逐字节一致。这里仅采用五表结构前置，完整 B08 和阶段 B 仍未验收。

唯一新迁移 V013__publications.sql 为 7661 bytes，SHA256 `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c`，Flyway checksum `-847352333`，自首次共享起保持不变。五个 CREATE、十八条外键及唯一延后的材料外键支撑索引与 B00-1.3 一致；final_result_review 本次即具备两条物理外键。实际五组元数据为 5 表/42 列/30 索引列/4 CHECK/18 外键列，与原实际完整库独立过滤的 oracle 完全相等。

真实缺表 RED 为 1/1/0/0；首次完整 PublicationSchemaIT 为 10/0/0/0，正常 runner 同时执行的 62 项单测通过。重复执行的单测和重叠 RED 不累加成更多独立用例。旧五十八个对象的行、DDL、五组元数据及十一项成功 history 前缀保留；新五张表探针全部回滚，private013 与 production-latest 重跑各零操作，四份完整快照同 SHA `866f7bf59731563f5aeb0d52d9b464b0cd451cb1e91df27873a02cd10d65c841`。机械接入没有再次运行已通过且未改变的测试，也没有数据库操作。

中央冻结提交 `0759dc9` 的实际时间为 2026-10-21 21:48:00 UTC（epoch1792619280），早于独立 ACK 接收 21:48:36.027422 和同一测试库首次应用 21:49:39（epoch1792619379）。21:47:04.731880 是静态核对采集时间，未混称 Git 提交时间。原 JDBC Asia/Shanghai 显示与原生 UTC/epoch 分别保留。

独立测试库为 ama-test-b01-ee4d94105bb9，完整 ID `45e74f283bd96e71a876a303ffede564306d77da91b54fc7e73b2ec0a1bef36d`，127.0.0.1:46539，初始零表、MySQL8.4.6、native AIO OFF、2CPU/1GiB/无额外 swap/256PIDs。它只提供功能证据，不是原生 I/O、公司平台、RHEL、容量或 HA 资格。未 reset、repair、outOfOrder、重启或删除旧测试库。

[完整实现报告](publication-schema-implementation-report.md)保留作者原始状态；本协调记录决定当前结构采用范围。所有准备/失败/原始 SQL/日志/XML/快照和初始清单保留在 artifacts/phase-b/b08-schema-author；完整作者与审查原件、路径图和接入校验另封存于 b08-schema-coordinator，均有 SHA 和 -text。历史准备脚本属于十一项低版本 checkpoint，不为适配当前 V013 而放宽其保护。

同会议/身份/权限、输入和来源哈希、语义支持、最终版本冻结、候选阻断、CAS、发布唯一性与 UNKNOWN 查回仍须由 B08 服务及真实契约另行验证。原 SQL 接受的边界样本不作为这些行为的通过证据；材料非空仍等 V017；两个待定交互、真实公司接口及运维条件继续保留。后续发现结构问题只使用统一登记的前向版本，不修改已应用或共享 SQL。
