# Task7采用：prepared external运行时与旧回执有界恢复

采用源码 `bdf9762458a9d2ea046b0a8a5bb5e2b07c6df2bc`，原Task7基线 `de1611a67ba483afe21f3b29bb69c2d32028efef`；初始实现84b4b0b、fix01 6cce4794及fix02完整保留。最新独立限定审查SPEC COMPLIANT、quality APPROVED，0 Critical/Important/Minor，唯一残余T7IR-01已关闭。Task7框架及本地受控证据采用；全部B阶段尚未完成，接续9→10→11→12→13，之后停止，不进入C/D/E/F。

## 行为与边界

框架通过真实私有issuer/DataSource/holder/fullspec/执行关系绑定prepared观察和DB-only输出；输出、task/attempt、operation/execution、audit/outbox原子提交，精确许可在已验证终态提交后独立结算。Worker的普通租约转外部执行所有权、丢ACK/心跳/关闭后的同一事实保留、generation-only窄STALE保留、矛盾隔离与完整有界对账仍受既有协议约束。

fix02只在JdbcExternalOperations添加27行recovery-only旧零效果确认。真实FAILED_ZERO_EFFECT已提交但ACK丢失、授权显式retry创建后继后，验证旧执行完整不可变proof、原完整spec、旧存储result及其精确已结束attempt，再由原注册策略验证，可有限确认并释放同实例本地槽位。普通ACTIVE旧token仍严格拒绝；不重发、不重复writer/attempt/event、不授予写或发送权限、不改变后继epoch/owner/state/output/attempt，不结算后继许可；verified-success矛盾优先级保留。

## 独立审查与证据

- fix02完整限定审查W/task-07-fix02-independent-review-20261024.md，SHA `22818db8d10088d0485213cd52e673750abbba0892fab2553643dbe8a9e6bc27`，协调者完整阅读。T7IR-01 ADDRESSED；IR02/IR03/CV01–03此前关闭且无本修复回归。
- 增量机械核验W/task-07-fix02-independent-custody-20261024.json，SHA `2d41fbf06a13f2bf256575779719aa8a44ce30ea1622cc09b78dc62bc37df41a`，PASS。1505作者路径、10638累计清单条目、5 backend变更，磁盘/提交/封印一致；四项可变公开元数据与同W报告有精确前像。
- 9777采用保护及18836旧公开artifact逐项不变，重叠9717，不能相加当独立数；13份前像与fix01详细报告保留。六个fix02执行窗口的命令、exit、XML、源码/helper原始字节、468项backend Java/SQL哈希均保留。
- 实际正常迁移重跑0、15条完整历史与原bootstrap一致，无新DDL/模型/重置。既有Task7独占schema及单独ACK继续有效。控制者实际复核8输入封印、3精确CID完整选定字段/mounts及6个记录PID消失；1510项增量文件/27156936字节的known6+PEM扫描零匹配。仅是明确范围检查，不称全局无进程或通用秘密审计。

## 测试的准确限定

最终采用407个去重通过身份：153集成为完整批次的151 PASS加随后2项精确诊断替换；254单元一次通过。完整153集成命令本身exit1，含1 FAIL和1 ERROR，绝非单批全绿。所有旧395身份在本次确有执行，新增12身份；此前窗口不叠加计数。

新旧回执场景实际RED12/3断言失败/0错误；修复后第一次证据序列化/测试token断言失败原样保留；随后12 PASS且最终批次新12全部通过。真实容量一Worker和prepared Reconciliation在同实例恢复后推进无关工作，无重启/改semaphore/第二实例替代；真实running后继占许可时前后完整快照不变，旧恢复28 SQL/26行且零写，只能结算旧终态许可。九项proof/result/attempt/UNKNOWN/foreign负面控制及既有live-owner/contradiction控制通过。

原完整批次的RecoveryBounds消费位置断言失败与B06初始claim Optional为空的设置ERROR，根因均UNKNOWN。后续诊断保持生产与原断言不变并通过，只证明后一次运行，不解释或修复原失败。source-dependency-proof核对14生产文件与后续两处仅诊断变更、其余方法及ProbeSource字节闭包。独立审查Q-01/Q-02认为这两项是证据/重复性限制，非本fix02分支新缺陷，不阻断本限定采用；最终全分支审查必须收到，不得称已修复flake或环境已证原因。

## 未完成的消费者资格与下一步

T7CV-04仍由实际9/10/11消费者闭环：Task9 durable provider及跨重启request/payload/result身份、原始十五profile/producer输入闭包与完整候选图/三种终态replay；Task10真实first-save/adoption/完整拷贝与所有版本writer的PENDING处置；Task11真实readiness/冻结/发布投影。Task7受控fixture通过不等于这些生产策略已安装。Task9新generation命令/handler正式组合启用须等实际Task10与Task11共享条件完成，Task11/12补完整链证据，保持默认fail-closed。

原B01 IMM永久PARTIAL、B04 Q401 UNKNOWN、缺公司SSO/目录/模型/KB真实协议、HA/容量/native物理I/O未资格化等界限不变。所有历史失败/警告及最终待处理登记保留。无main合并/推送/生产发布/邮件。最终独立全分支审查下界固定34cb226aefe99c67d3681af68d748b0aeea4e073。

## 协调证据

[精确镜像清单](../../../artifacts/phase-b/b-remaining/controller-task07-adoption-20261024/manifest.json)，SHA `b1587153fb4f8e0a67e779fc37393ed907b5fac964614dbe83dc366bc9f168ed`。原W、全部原始raw及前像继续保留，用户接续要求优先于技能清理建议。
