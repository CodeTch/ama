# B-R13 本地行动负责人选择

当前任务按 R2 既有义务复用本地目录和已采纳的纪要命令。真实公司目录/SSO 联调仍属 PLATFORM-B；该记录不证明公司人员来源。本地片段已采纳（2026-10-29）：首轮独立审查的重要姓名预算问题已修复并复核关闭，实际MySQL及完整浏览器通过；正式B不因此完成。

## 原因、实现与权限边界

真实日常会议 `70605bec-ada4-469a-bf9c-9f146909db66` 的正常目录返回 ACTIVE Person 后，携带当前 editor intent、版本及有效 CSRF 保存企业身份，原实现返回 422 ENTERPRISE_SELECTION_NOT_INSTALLED，已保存内容未变（bqual-action-red.log）。显示名/日期原功能与这一缺口分开保留。

当前服务端验证不信任客户端 Person。选定 ID 并入完整的历史身份集合，仍受 256 上限约束，在 S/F 两次以原有 identity→root 锁序预锁。`JdbcMeetingAccess.activePerson` 只消费本实例签发、同 DataSource/事务 holder、已经覆盖该 ID 的 opaque gate，以数据库中的精确比较校验已锁行的当前 ACTIVE/status 和显示名，只返回有界匹配结果，包含同事务自身更新后的状态；不在 root 后发现新身份或锁。随后仍经过完整原来源、版本、保存目标、最终确认/发布写保护和原子提交。历史参与者可停用；仅当前 actor 与新选定人员按各自规则验证，不要求所有历史身份在职。已知成功 exact replay 先返回原回执并重查当前权限，不重新挑选历史人员。

界面从实际 `app.options.people` 的当前响应选在职 Person；不根据任意 ID 构造 active Person。未保存选择在身份/会议/目标/已保存版本/权限变化时清除，旧异步响应被 epoch/abort 拒绝；同步 busy 观察者改变身份时不会发送旧查询。显式保存复用现有 action intent、原请求 journal、expected revision 与完整 tuple。企业身份的显示名只读；清除企业身份保留显示名/日期后才可手写名称。选择行动负责人不创建会议读取授权。

## 实际 MySQL/生产装配证据

`tools/dev/qualification-java/ama/api/LocalActionOwnerCheck.java` 在独立类路径运行，与正式类隔离。它使用当前日常 worker.properties、单个 owned MySQL、生产 ProcessingConfiguration / TranscriptConfiguration / MinutesConfiguration 工厂，实际调用 requireCombined；不写 SQL 会议、任务或结果夹具，不以测试 DTO/包装重试替代生产能力。

`bqual-action-genuine01.log` 通过六组断言：

- 真实锁下当前 Person 正向，伪造 gate、另一 issuer、已过期 holder、新 holder 重用旧 gate、未覆盖身份全部拒绝。
- 同 holder 中选定身份由 ACTIVE 改 DISABLED 后立即复查为 false；该身份变更事务强制回滚，真实状态恢复。
- 名称失配的命令拒绝，完整会议/搜索投影、历史集合/正文/要素/行动/审阅、回执、audit/outbox 计数相同。
- 透明 JDBC 屏障位于实际 S 捕获 COMMIT 之后；另一连接将所选 dev-viewer 暂时 DISABLED，F 422 拒绝，业务快照完全相同。finally 恢复该受控测试身份 ACTIVE。
- 执行真实 element_set INSERT 后注入 SQLException；事务回滚使同一业务快照相同，不留下部分版本/回执/审计/outbox。
- 正常 ACTIVE Person 保存成功；同原 key、版本与 payload 重放没有新版本、事件或业务变化。

身份状态 SQL 只用于已标识的本地测试身份生命周期/并发屏障，未构造成功业务结果。它不证明生产身份管理运维流程、公司目录、全部领域写后回滚或完整锁饱和。

## 浏览器与源码检查

bqual-action-browser-green02.log 已通过真实目录选择、保存/刷新、企业名只读/日期可编辑、被选旁观者仍 403、有效 CSRF 的 member 修改仍 MEETING_FORBIDDEN403。未知身份得到 400 INVALID_ARGUMENT，名称伪造得到 422 ACTIVE_PERSON_REQUIRED，已保存内容均未改变；不把两类拒绝写成同一错误。

Green03 扩展清除身份后立即重新进入编辑，尚未等待原操作的授权刷新结束，读到旧目标不可编辑并超时；该失败保留，不算通过。Green04 等待原操作刷新与检查区清除后，在新的当前目标上验证清除保留显示名/日期、恢复手写名称和再次选择，完整通过（bqual-action-browser-green04.log），未修改或放松产品门禁。

字段单元先 RED 后 GREEN 4 例；选择状态 3 例（包括同步重入 RED/GREEN）；当前 Web 全套 249/249，类型和构建通过；contracts/core/adapters/API/Worker 全套 **535/535，零失败/错误/跳过**。预算向量测试仅使用日常本地独立输出目录，不恢复任何旧运行窗口。首轮独立审查见 `bqual-action-owner-review-20261029.md`，姓名预算P2修复闭合见 `bqual-action-name-budget-review-20261029.md`；无其他已建立的P2+发现。


## 独立审查 P2：姓名正文必须在 JDBC 传输前有界

首轮独立审查发现 `enterprise_identity.display_name` 实为 LONGTEXT，原新方法直接 SELECT 后比较，不能以256身份数量上限替代字节预算。实际 MySQL 因果 RED（bqual-action-name-red.log）将精确本地测试身份的姓名临时设为126000字节中文/补充平面字符：虽拒绝短错误姓名，但仍执行一次原姓名正文查询；finally 恢复原身份与零业务变化。

修复保留所有 opaque gate 检查，SQL 在已预锁的同一身份行上以 ACTIVE、UTF-8字节长度和 BINARY 精确比较，仅 SELECT 常量1。FOR UPDATE 是对已持有行的 current read，不是在 root 后发现/锁新身份；不把 LONGTEXT 姓名传入驱动或 Java。GREEN 的同一超长姓名测试记录 rawNameQueries=0，另证大小写、Unicode和尾随空格精确匹配，身份恢复、业务无变化。

修复后真实生产工厂的六组边界全部重跑通过（bqual-action-genuine02.log），535条 Java 全回归再次通过（bqual-action-broad-java02.log）。独立修复复核已关闭P2；普通六服务按当前源码重启后，完整浏览器Green05通过。已发布会议的新企业tuple写入503/MINUTES_UNAVAILABLE且已保存JSON不变、正式文档字节SHA相同（bqual-action-published-boundary01.log）；不把泛化拒绝误记为特定409。首轮P2报告保留不覆盖。
