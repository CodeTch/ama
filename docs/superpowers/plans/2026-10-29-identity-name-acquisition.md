# B-R12 名单身份姓名读取预算缺陷

当前真实 MySQL 验证发现既有 JdbcMeetingAccess.captureIdentities(names=true) 直接 SELECT enterprise_id,display_name,status。display_name 为 LONGTEXT；实际默认 WorkspaceReadBudget 为8MiB，临时8,400,000字节姓名仍在原身份预锁路径完整传输。RED日志 bqual-identity-name-red.log：rawQuery1/largeStringRead1/budgetRejected=false；finally已恢复测试身份，不构造业务结果。

当前优先修复，仅涉及原身份预锁读及三个现有命令调用点：

- 复用 WorkspaceReadBudget，无新部署配置和产品字符数限制。先对固定大小的身份/状态/长度元数据做有界 reservation，再以原排序 SELECT 元数据 FOR UPDATE；在读取姓名前累计 reserve Unicode/UTF8/UTF16与 owned copies，最后只读取相同已锁行、相同测量长度的姓名并验证实际长度。缺失/停用/当前actor判断保持原语义。
- 所有身份仍先于 subject/root。不要在root之后发现身份；selected activePerson 的 SELECT1精确比较保留。Status-only/historical/fact路径不扩大为姓名读取。
- create/save/confirm名单复用命令持有的同一 configured WorkspaceReadBudget，而非每个人重置；保留default overload兼容内部调用。当前事务、原key重放、CAS、名单草稿/确认、来源及发布写保护不变。
- 真实 DB 超默认预算的姓名先 RED后GREEN；补正常Unicode姓名、精确/少一累计边界、取消与恢复；实际当前应用名单保存/确认和代表冲突回归、535 Java及Web适用检查、同一reviewer审查。不得把编译/单元数当实际MySQL通过。

这是 B-QUAL 发现的重要缺陷，优先于后续共存/crash验证，不据此前Demo样本小就忽略。不恢复旧B-L5，不进入C/D/E/F或Git发布。
