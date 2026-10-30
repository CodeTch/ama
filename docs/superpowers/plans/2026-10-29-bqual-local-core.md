# R2 B-QUAL 当前本地核心验证批次

承接 R2 及唯一 B 摘要 B-R01～17；M1-Demo 的源码/浏览器成果保留，正式 B 仍未完成。只使用当前单个日常 MySQL、本地文件与现有六服务，不恢复封存资源，不新建 S3/第二数据库/双活配置。

1. **B-R13 必需字段与身份能力**：已完成目录/保存最小接线及实际生产工厂的 issuer/holder/coverage、S/F 身份撤回、同 holder 自修改、实际写后回滚、精确重放；浏览器选择/清除/权限已过，收口独立审查。
2. **B-R12 读取前预算、锁与 ambient transaction**：用当前正常导入/生成的真实图及生产工厂。透明 JDBC 观察必须在 executeQuery 之前记录正文传输（包括驱动缓存），不要仅数 getString。先验证 genuine 完整 gate 与 S/F 同一累计 ledger；在该 gate 的已锁当前行上分别验证 UTF-8、Unicode scalar、UTF-16、owned copies、response 的精确边界与超一拒绝、取消和累计重复读取。明确分开物理生产读取组件与完整应用查询的证据，不把子组件测试写成全端点资格。完整生产查询另验证实际 tiny 配置在正文 transfer 前拒绝、环境无业务修改、禁止 ambient transaction。现有 default limits 不变，不造 SQL 正文样本。
3. **B-R03/04/05/06/08 原子性与确定性竞态**：复用当前 genuine 组合，在各实际 S/A/F/写后位置设透明屏障或异常，使用正常命令创建输入；核对完整 graph/root/task/ref/receipt/audit/outbox，不只 HTTP。实际 identity→root→domain→task 的 holder/完整集合与不可倒置要求保留。B-R13 证据只按已测范围复用。
4. **B-R07/10/11 publication、共存与 crash**：先梳理现有 production failpoint/续租/UNKNOWN/reconciliation 边界，再在普通 Worker 可控停启且 finally 恢复的环境使用真正 WorkerLoop/session/handler，覆盖关键 crash、原业务键只查回和有界共存/饱和。不得把测试包装重试或 SQL 修复行当生产恢复成功。历史 56.85 秒领取延迟有独立来源/现象判定，未复现不写修复完成。
5. **B-R14/17 最终汇合**：对历史 PARTIAL/UNKNOWN/legacy origin 逐项给出适用于当前本地链的判断与证据，不能任意关闭或无期限重跑无关部署调查。保留 PLATFORM-B/F 未完成项；完成适用回归、B-00 差异和唯一全分支审查后才能宣告本地 B，通过后停止，不自动进入 C/D/E/F。

实施者仍是同一主代理；独立 reviewer 同一复用，保持源码单写者。每批只作必要实现/缺陷修复，工具保留真实来源与失败记录。用户已持续授权，无需重复确认。
