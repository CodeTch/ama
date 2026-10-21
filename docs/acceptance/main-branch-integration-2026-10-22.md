# main分支归并记录

日期：2026-10-22。按用户“将所有分支改动合并到main分支，然后提交并推送到github”的指令执行。本记录只确认版本归并与本次检查结果，功能开发仍暂停，原有未通过的评审和验收条件继续有效。

## 归并范围

- 原main：`15b0a6bc4872cbed209c38474c3b1e425397e972`。
- 原origin/main：`af50e1ef570f9f7d78461ef8adbce4788370feb2`。
- 首先归并集成分支 `codex/phase-b-foundation`（`03790c274003959ddd4b738b0fbb936ad296a9e3`），再逐一归并其余分支，保留原提交历史。
- 归并代码检查点：`9f32596341c0282e0d977920ad84f756d270e32d`；本记录和更新后的接续入口作为后续文档提交。
- 共30个原功能分支，所有原分支tip均为main祖先，`git branch --no-merged main`为空。远端只存在main，也已纳入。
- 主工作区原未提交的总计划、B计划及接续入口已在 `65625e4` 保全；原作者工作树、分支和复现用临时评审包继续保留。

| 原功能分支 | 归并时tip |
| --- | --- |
| `codex/phase-a-contracts` | `684eafbb6647e3d62bc3595005315fe12c8fdcfd` |
| `codex/phase-a-ha` | `6ae6e8c5afcf1ada9e92bc2552d92cb46c1b609c` |
| `codex/phase-a-models` | `acf984bf910f5f5b350203137e3296861c88a177` |
| `codex/phase-a-web` | `96996addd8c34b819bb498a69ad442d60d6c8428` |
| `codex/phase-a-foundation` | `15b0a6bc4872cbed209c38474c3b1e425397e972` |
| `codex/phase-b-product-decisions` | `43766f3ce817e3a1cc9ecffb0e2d5af28e3e4792` |
| `codex/phase-b-storage` | `ba74def4cbe048d6b72a5306c66e3cd3b5a81e02` |
| `codex/phase-b-s3-preflight` | `9a6d4059f80e39853f958749998db07b3d88bc32` |
| `codex/phase-b-database-design` | `bd43ca31d6b4c263c5f81da83944998492ef943e` |
| `codex/phase-b-s3-rustfs-probe` | `a13e0cddd890b02e66f5189d6036bf7afed8b8e7` |
| `codex/phase-b-upload-design` | `40cd72717abf079d3373c072eddea19f881a8916` |
| `codex/phase-b-audit-delta` | `0e8f69842be1cb42a294cfbc4b5ffd1954f4a40f` |
| `codex/phase-b-identity` | `39b447d43a91cc0692a5d43ee109fcc82d37d6db` |
| `codex/phase-b-storage-selection` | `b2f933c6256f4c56597b2376823ba3fb205f436c` |
| `codex/phase-b-meeting-schema` | `ff22197f5f6231aeb689e035f4435ddbbcbcf114` |
| `codex/phase-b-file-schema` | `fd3c3d86e1c7998f95ded65704c3453579116d06` |
| `codex/phase-b-identity-history` | `3c425110f6f4c956427cd90b7327e5e7acf0d983` |
| `codex/phase-b-fixture-evidence` | `fdcdc92b11474dd6607cea0ee54695e451ded3b5` |
| `codex/phase-b-transcript-parser` | `9ae628058d53ddaff64e14adeeecb64ded653c1c` |
| `codex/phase-b-storage-s3` | `ff7b6efbd3c8d47ca018e57f2e7b8eb633894335` |
| `codex/phase-b-tasks` | `b126db27e592a563392292fef8551015251dbe50` |
| `codex/phase-b-generation-design` | `7fb166b01b6ff45213b973640ff7457c3cf36e56` |
| `codex/phase-b-transcript-schema` | `aef885da7a6f5c1245289943e5a5f0b83daaa835` |
| `codex/phase-b-meeting-domain` | `68aa0957a3fcb69630fee96075055edb3a3cde42` |
| `codex/phase-b-generation-schema` | `234e6e2be6cfa721d52c804e87e4b71a3b92e8b6` |
| `codex/phase-b-publication-schema` | `d9428eeb0c696b6d21c6303ffd1e8ac115ff56ce` |
| `codex/phase-b-minutes-pure` | `7e052a88fcd03d60523d013839701dd35a7f2b48` |
| `codex/phase-b-provenance-design` | `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d` |
| `codex/phase-b-file-registry` | `45dc8ed27449de191400c858b88413a518842f21` |
| `codex/phase-b-foundation` | `03790c274003959ddd4b738b0fbb936ad296a9e3` |

## 冲突处理与原件保护

- 大多数作者提交此前已被挑选至集成分支。对这些重复提交的冲突保留集成分支的后续修订；每次此类处理均确认暂存树与该次合并前内容一致，没有以旧文件覆盖后续修复。
- identity、tasks、upload-design的非等价补丁属于集成适配，保留现有身份/会议/API修订、AWS BOM与较新的迁移登记记录。
- B07纯S1和D01来源设计新增交付全部保留：272份minutes非属性载荷和64份provenance非属性载荷与作者tip逐字节/文件模式一致；根 `.gitattributes` 保留两分支规则的并集。
- 相对foundation，338条路径发生变化：337条属于上述两项交付的并集，另一条是主工作区接续入口；没有其他来源代码回退。
- 12份正式Flyway迁移与foundation逐字节一致；没有重写、重编号或应用迁移。
- 总计划SHA256：`a532dec6e94c53f0e81ae92f14b4f84b6a56cf9d68c37bd6e27d7903f911ffa9`。
- B计划SHA256：`bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862`。两份计划在Git与磁盘上均保持原字节。
- 已完成只读独立归并审查，未发现归并引入的问题；审查范围不包括替代原功能验收。

## 本次新鲜验证

检查对象为上述归并代码检查点；工具使用现有锁定Java21和Node22.23.4，不加载私有夹具环境。

| 命令 | 结果 |
| --- | --- |
| `python3 tools/checks/run.py all` | PASS：Python93项、前端单测8项、OpenAPI两项检查、Java单测164项；测试均无失败或跳过。 |
| `npm --prefix web run typecheck` | PASS。 |
| `npm --prefix web run check:api` | PASS，生成类型与已提交文件一致。 |
| `npm --prefix web run build` | PASS，28模块。 |
| `python3 tools/checks/run.py e2e` | PASS：8项，无失败、错误或跳过。 |
| `python3 tools/checks/run.py visual` | PASS：2项，无失败、错误或跳过。 |
| `./mvnw -B -ntp -DskipITs verify` | BUILD SUCCESS，所有模块打包验证通过。依环境集成测试明确未执行。 |

本地本次原始日志及摘要保存在 `artifacts/integration-main-2026-10-22/`。未操作共享MySQL/S3夹具，也未重跑需要独立空库、破坏性迁移探测或真实平台的集成测试；历史通过证据原件保持不变。

## 保留的待处理事项

- B07纯S1：F1（P1，递归历史确认哈希漏验）、F2（P2，共享DAG缓存绕过深度预算）、F3（P2，原始字节在边界检查前复制）仍待原作者修复及独立复审。
- D01 / B00-1.4-proposed：R1（P2，完整上下文闭包遗漏保留的basis context）仍待设计修订及复审。B00-1.3历史基线保持原件。
- B04-Q401、B01-IMM-01、B06队列锁序假设以及公司接口、HA/fencing、真实容量和生产资格仍按[原暂停接续说明](development-handoff-2026-10-22.md)处理。
- 合入main不等于B07/D01采用通过或阶段B最终验收通过；本次没有恢复功能开发、发送真实消息或操作生产环境。
