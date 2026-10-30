# B-R13 本地行动负责人选择最小接线

此任务属于既已授权的 R2 DEMO-03/B-QUAL 义务。当前显示名/日期编辑已可用，企业身份保存因 ENTERPRISE_SELECTION_NOT_INSTALLED 被拒绝；保留该历史事实。本次不接入公司接口、不新增数据源或通用目录架构。

## 设计与边界

复用当前真实人员 options API 和 enterprise_identity。用户只能从 API 返回的 active Person 选择；显示名取返回的 Person，不能根据任意输入 ID 构造 active Person。选定尚不等于保存，显式“保存所选负责人”通过已有 MINUTES_ACTION_FIELDS / editor intent / revision / journal 命令写入完整 tuple。手写显示名保留现有功能，企业选择提供单独清除身份入口。切换身份/会议/目标/权限或当前版本变化清空搜索和未保存选择；未决原命令沿用既有恢复路径。

服务端不信任浏览器 Person。新增身份先并入已有 discovery 完整 identity 集合，在 S/F 两次同 holder 的 identity→root 锁序下验证当前 ACTIVE 身份与匹配 display_name，且仍校验 owner/edit、现有图、sourceSet、element、版本、来源和发布写保护。历史身份无需全部 ACTIVE；不能以新增选择为由拒绝无关已停用历史身份。已知成功 exact replay 继续返回原结果并重查当前权限，无需把历史选择重做。未安装时的构造期 blanket 拒绝移到实际命令验证，以保证保存后的身份可恢复；不存在/停用/名称失配的选择被拒绝且无业务/receipt/outbox 修改。

身份验证仅能消费当前 JdbcMeetingAccess 发行、同 DataSource/transaction holder、已覆盖选定 ID 的 opaque gate；不接受字符串充当能力，不在 root 后发现/新锁身份。读取名称/状态只读取已锁行，S/F 都要验证，包含同事务自身修改后的重新检查。新选择仍计入既有 256 身份上限。

## 执行和验证

1. 在新的日常未发布会议上以有效 CSRF 和当前编辑确认发送真实企业负责人 tuple，记录当前 422；不复用封存批次。
2. 实现最小 JDBC gate 验证及 action fields 命令接线；先写相应负向与 holder/覆盖测试再实现。真实 MySQL 验证 active 保存/刷新、未知和伪造名字拒绝、停用/并发变更拒绝、零部分写和 exact replay。身份变更用既有受权本地身份管理路径，若无路径则使用同一 owned DB 的明确测试身份生命周期，不伪造业务结果。
3. 复用现有 options 人员读 API 实现 owner-only 选择区；保留现有日期与显示名编辑、IME/草稿/原请求保护；当前阶段无企业真实能力宣称。
4. 运行受影响 Java/Web 单元、类型/契约/构建及真实浏览器选择/保存/刷新/拒绝；同一独立 reviewer 审查；更新唯一 B-R13 行与证据。

必要进展不等待重复授权。此任务完成不会单独完成 B；精确预算、锁、回滚、共存饱和及关键 crash 等义务仍在 B-QUAL。
