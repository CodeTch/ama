# B00-1.3 生成结果关系持久化增量设计记录

2026-10-22。状态：**根任务已 ACK 具体方案并授权详细设计/独有设计库验证；本交付待 fresh independent design/spec + quality review，不是协调者最终采用。** 本记录不修改已采用1.1/1.2评审，不代表正式Flyway、B07应用、平台、性能、HA或上线验收。

## 基线、授权与历史

- 初始proposal BASE `0812b6da0b143544de4075929b5dd01568547d14`；89行原提案SHA `6abe12335303c176331089814e56aee32aae7b2c01a77ecc3cd9741350b044ff`，原报告SHA `41033057615824b640bf21d2858c6e3628d802509d0f435525333c6bb616d167`。根任务已按原字节另存 `*-initial-preserved.md`；原记录不被最终结论覆盖。
- 根读/ACK后，clean owned worktree `/home/tanren/ama/.worktrees/phase-b-generation-design`、分支 `codex/phase-b-generation-design` fast-forward到精确评审 BASE **`0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40`**。这是最终变更比较基线，包含已采用冻结的V010；未消费其他并行未评审代码。
- ACK裁定：七张目录/建议/复核表归V012；final_result_review归V013，与父确认表同时创建并立即具有两个physical FK，不新增deferred FK。现有support_state保留原版本classification；精确result_review为人类断言，final membership固定历史选择。显式内容级adopt+review可原子建立新目标和新actor/time/hash断言，绝不复制旧review信用。
- 首次guarded自动有效保存还是explicit采用、逐项复核还是明确内容级合并确认，**仍待用户答复**；本设计没有自动启用任一选项，没有把通用采用/确认按钮变成自动证实，也没有强制额外点击。
- 先保全1.2三设计文件与checker原字节于 [design-history/B00-1.2/manifest.json](../../database/design-history/B00-1.2/manifest.json)，含SHA及原source路径；原文件均与BASE Git字节相同。现行设计原“上传补充待独立评审”过期标题改为已采用，并明确前后历史，不重签1.2结论。

## 最终设计范围

新增 sensitive_detection / sensitive_detection_source / background_note / background_note_source / action_suggestion / result_review / result_review_evidence，以及V013 final_result_review。前三类生成实体逐条保存原始input_id，混合partial或复制版本不把旧建议改称新生成。未选择检测、同一locator的两个标签、背景正文/来源、原始人物提议和未核实日期均有类型明确的落点。
原85表唯一physical delta：sensitive_exclusion PK从(revision_id,locator_id)变为(revision_id,locator_id,category)，增加仅两标签CHECK；无列/FK修改。其余84个CREATE块、全部原FK语句保持字节相同。生成input无manifest/profile/epoch增列；不用附件或raw model JSON绕过关系设计；不扩七类主体要素；不新增产品长度/历史上限。
人工接受字段继续在action_item；模型owner/date仅在action_suggestion。SOURCE_SUPPORT与ACTION_FIELDS分别表达来源支持判断和字段确认，二者不能互相替代；reviewed_by/time来自服务器认证上下文/UTC，历史身份不设FK是事件时身份保留策略，写时必须验证ACTIVE身份/权限。
支持声明精确绑定目标ID/版本/全文/anchor或行动元组、完整证据集合和固定源版本/hash，最新相同目标范围的PARTIAL/UNSUPPORTED不能被旧SUPPORTED覆盖。新目标/内容/来源失效旧信用；历史review保留。最终确认将精确review IDs和完整投影纳入source_manifest_hash并冻结membership，后续复核不改旧确认。
完整设计补充覆盖requirement→entity、序号/集合去重/partial selection、候选→采用、人工保存/恢复、读ACL、源独立权限、失效、删除顺序、全局锁序、任务token/lease/version/epoch fence、CAS与原子提交、目录/review查询索引和未来容量验证。CHECK/FK只证明声明范围，不宣称语义真值、身份或跨会议完整性。

## 独有MySQL8.4设计fixture

仅新建并访问 `ama-test-b01-991f0386f26d`，完整ID `a5bd8e25f6047c542e94af6aac5bec68e60532c183d460714586480b2e22add3`，`127.0.0.1:44649`，schema `ama_b01_fixture`。
镜像引用及实际image ID均为 `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9` / `sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`；实际版本8.4.6。label `ama.test-fixture=b01`；独有匿名volume `6b52ad2c92b76275d8bedc1f18eed0bbbb947a79e897df36125f444b30873c06` 挂载 `/var/lib/mysql`，AutoRemove=false。
执行前source `/home/tanren/ama/.worktrees/phase-a/.tools/env.sh`，SHA `6f25be38756c782d7345aacf3b4f5a525c0de6d3db246487ed560b6d55983dbc`。process-local窄wrapper只对本次新helper run追加 `--innodb-use-native-aio=OFF`，并拒绝失败后删除；共享helper/default不改，`/proc/sys/fs/aio-max-nr`前后65536不变。凭证只在忽略的.tools，本报告/证据不含密码。
实际initial proof记录**0业务schema表**及 `@@innodb_use_native_aio=0`，完整ID/image/label/mount/127binding见 [fixture-initial.json](../../../artifacts/phase-b/b00-generation-design/fixture-initial.json)。最终再次核对同ID/mount及@@0，见 [fixture-final.json](../../../artifacts/phase-b/b00-generation-design/fixture-final.json)。没有操作既有/失败/其他owner fixture，没有reset/repair/sysctl变更或公司调用；本独有库保留待协调者评审处置。

## 实际验证与证据

全部命令在owned worktree、root pinned env下运行：

```sh
python3 tools/checks/database_design.py generate
python3 -m py_compile tools/checks/database_design.py
python3 artifacts/phase-b/b00-generation-design/start_fixture.py
python3 tools/checks/database_design.py verify --container ama-test-b01-991f0386f26d
python3 artifacts/phase-b/b00-generation-design/constraint_probes.py
python3 artifacts/phase-b/b00-generation-design/verify_delta.py
python3 artifacts/phase-b/b00-generation-design/final_checks.py
```

完整review DDL首次执行成功，所有information_schema字段与声明一致：**93表、709列、290索引定义/441索引列、109 ENFORCED CHECK、196 FK/203 FK列**。逐列类型/顺序/nullable/default/extra/collation、逐索引唯一性/列顺序/方向/前缀、CHECK AST/ENFORCED、FK目标列序/RESTRICT全部核对。REGEXP_LIKE AST比较器保留函数名/完整参数及大小写匹配模式，实际MySQL lowercase-hex检查生效。
DDL SHA256 **`ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`**，见 [完整实际metadata](database-generation-addendum-metadata-evidence.json)。1.2 metadata SHA仍 `3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad`，旧1.1与1.2证据未改；当前字典/DDL/ER与声明逐字同步。
23项独立实际事务探针结果见 [constraint-probes.json](../../../artifacts/phase-b/b00-generation-design/constraint-probes.json)，synthetic seed与具体语句另存 `.sql.txt`，**不是正式SQL迁移**。包括双标签同locator两行、一标签取消另一仍在且检测不删、背景与raw owner多行/emoji字节无损、建议日期与accepted NULL分离、重复标签/序号/源集合1062、非法标签/零ordinal/审核outcome/空anchor/目标XOR/大写非hex摘要3819、未知来源/最终父FK1452、被复核证据与被最终引用review删除1451。
其中4项是明确的**数据库能力边界见证**：缺失审核身份、跨会议review、不同target的review evidence、合法格式但内容错误的hash在raw SQL层可插入，关联核对返回0；它们不被伪称“数据库已拒绝”。后续B07/D03/B08必须实现并测试owner/active identity/source ACL、same-meeting/target、完整source set及精确hash一致，失败整体回滚。此处没有写应用实现或用测试常量替代权限/语义判断。
探针每笔事务ROLLBACK，失败连接关闭也回滚；最终实查93表，除DDL固定task_outbox_sequence一行外全部0业务行。无隐藏试验数据残留；没有关闭FOREIGN_KEY_CHECKS或删除/重建任何库。
[增量证据](database-generation-addendum-delta-evidence.json)逐项证明原85表全部列/table/FK metadata不变；仅既有索引新增sensitive_exclusion PRIMARY第3列category及单个CHECK。84个原CREATE块及85表原FK文本字节相同。V011八表每块CREATE+FK SHA、各metadata数组相等、incoming FK也未增加均有独立条目。新八表新增35列；93/709现为实际观察，不再只是算术预测。
保全manifest逐一核对V001/V002/V005/V006/V007/V008/V009/**V010** SQL、migration-register、shared helper及旧metadata SHA不变。V010 SHA `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db`。未写正式SQL、注册表、应用Java/API/worker或用户总计划。
一次Windows→WSL嵌套引号命令在shell解析阶段失败，未触发SQL；改用已捕获的具体container名后首次DDL/metadata运行exit0。没有失败DDL、重置、重建或“修复旧库”隐藏路径。

## 尚未关闭的实施门

- 真实应用级权限/身份/跨会议/target/source hash与集合完整性、用户意图绑定、CAS/stale lease/epoch/token、候选/task/children原子完成、重启/幂等/并发与最终membership冻结，均待各owner实施和独立验证；本轮数据库探针不等于这些功能通过。
- B07 anchor/hash/profile/Unicode/source-alias与支持coverage契约仍单独评审；immutable配置/解析版本必须可恢复。未知外部负责人、人类源支持判断、两项待答交互问题、公司SSO/模型/材料权限和真实平台能力不能由schema推定。
- V012/V013真正编写前复核未共享/未应用；若已共享或应用，报告实际元数据，由根分配前向增量，不能改写旧迁移或自取V028。V011继续消费完全不变的1.2八表子集。
- 根安排fresh independent design/spec+quality review，对精确 `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40..提交HEAD` 审查；本作者不自签独立结论，不修改原评审/总计划。

## 协调采用补记

2026-10-22：完整owned0d2079cd..7fb166b的独立SPEC/QUALITY均PASS，Critical/Important/Minor均0。root完整读取两份报告后采用为B00-1.3-review，集成8e44080，34路径在本补记前与作者finalHEAD逐字节相同。93表/709列/290索引定义441索引列/109CHECK/196FK203FK列为独有MySQL8.4.6实测；23关系探针中4项刻意展示应用边界，不冒称应用拒绝验证。

[完整独立评审](database-generation-addendum-independent-review.md)。审阅DDL SHA ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93；实际元数据SHA a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5。旧1.2四文件/所有原迁移不变；原始报告另存coordinator-reports及SHA清单。此次机械采用未操作DB或重复验证。

本次只采用设计。七张新表归V012，最终复核membership归V013；两项产品交互选择仍pending，不由结构默认启用。正式迁移、codec/coverage和业务权限/事务/语义证据待各自任务。V011漏索引另由中央V011.1前向修正，本评审不代签该正式迁移。
