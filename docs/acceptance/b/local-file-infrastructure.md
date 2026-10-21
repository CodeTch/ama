# 本地文件基础设施检查点记录

日期：2026-10-21。状态：无数据库结构的基础设施实现与独立审查通过；**不代表 B-04 整体、B 阶段、平台联调或生产资格通过**。B-00 设计问题修正及独立复审仍在进行，未审表结构和阶段最终验收继续暂停。

## 代码与审查

- 开发工作树：`/home/tanren/ama/.worktrees/phase-a`，分支 `codex/phase-b-foundation`；集成提交 `1e0eeb5c45ef659e8a5a46e77d70b7a1cfd122e3`。
- 本地文件原实现 `982346b`，已审基础合入 `e5cdbed`，持久性修复 `781a75b`，范围读取修复 `ba74def4cbe048d6b72a5306c66e3cd3b5a81e02`。所有代码与历史提交保留。
- 独立初审 `/root/b04_local_review` 审查完整 `3445972..781a75b`：范围符合；发现 HTTP Range 前置全文件读取放大及文件进程数据库依赖。
- 原实现代理修复后，独立复审 `/root/b04_local_rereview` 审查 `781a75b..ba74def`：两项均 ADDRESSED；没有新增问题。详细报告与原始证据保留在 `artifacts/phase-b/b04-local/`，该目录的 `manifest.json` 登记 54 个文件的 SHA256。
- 集成没有冲突；集成后 `backend/` 与根 POM 同已审 `ba74def` 的内容完全一致。正式迁移目录未发生变化。

## 已有实现证据

|检查|实际结果|证据|
|---|---|---|
|LocalDurabilityIT|38，失败/错误/跳过均0|`artifacts/phase-b/b04-local/evidence-b04-local/fix1-LocalDurabilityIT.xml`|
|FileStorageContractIT local组|5，失败/错误/跳过均0；没有S3组|同目录 `fix1-FileStorageContractIT.xml`|
|范围/列表/取消/同inode修复|4个真实断言RED后全部GREEN|同目录 `fix1-red.xml`、`fix1-inode-red.xml`、`fix1-focused-green.xml`|
|组合回归 `python3 tools/checks/run.py all`|Python74、web8、Java19，全部通过且无跳过；OpenAPI2项通过|`artifacts/phase-b/local-integration-all.log`|
|独立文件可执行包|无JDBC/Flyway/MySQL/spring-tx库；独立进程测试使用实际exec JAR|同目录 `b04-fix1-runtime.log`及复审报告|

测试使用 WSL2/ext4、真实文件IO、独立小堆文件进程、测试PKI和loopback HTTPS。没有连接公司平台，没有修改/重置其他任务测试数据库。测试密钥及数据库凭证不进入该证据归档。

文件服务提供不可变对象与multipart恢复、文件/目录同步、descriptor-relative no-follow和不覆盖发布、挂载/active-role失效拒绝、mTLS服务身份与读写角色。HTTP范围大小和内容使用同一已验证打开描述符，小范围/无效/空读取不先扫描完整payload；listing仅对最终返回页计算payload哈希。显式stat仍执行完整校验。网络取消按真实响应IO错误检测，不声称响应头前即时获知断连或文件系统阻塞的固定时间上限。

## 后续门槛

- B-00：数据库设计、现有实现差异和新迁移登记仍须独立复审及修复回归，不能由本检查点解锁。
- B-04：文件登记/业务权限/orphan不可读、S3实际适配器及两个后端共同契约尚未完成。
- F-02：RHEL8.10/XFS/DRBD、断电、flush/barrier、fencing及真实接管没有资格证据；应用挂载/角色检查不等于集群隔离。
- 共享wire/TLS仍位于adapters JAR，含未使用的迁移资源；运行时数据库库已经排除。模块提取留后续整理，不引入数据库启动要求。
- 历史/临时数据清理没有新增自动策略；真实运维身份、受保护根目录和服务PKI继续作为部署前置条件。
