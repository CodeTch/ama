# E阶段完整执行计划

Spec: ../specs/2026-11-02-ama-governance-mail-design.md

全程/home/tanren/ama main、单agent、外部harness固定8792/2525；用户已授权自行裁定，不逐项询问。使用executing-plans/TDD/verification；不提交或切分支，不建worktree。

- [x] E01：复核既有反馈；新增源绑定/撤回/有效批准消费及实际MySQL负例。
- [x] E02：先写撤销/模型隔离/过期/授权用例；实现加密向量、真实embed、审核和候选匹配。
- [x] E03：先写失败分母/版本固定/白名单/回滚用例；实现数据集、持久评测、人工意见和受控发布。
- [x] E04：先写harness业务键去重/查询RED和mail未知重试用例；实现执行前授权、持久任务/查询对账。
- [x] 接入严格HTTP/OpenAPI/生成类型与桌面治理、声纹、评测/配置、邮件UI，包含原请求恢复和失权清理。
- [x] 正常部署后实际CAS及固定harness联调，两个桌面、成功/失败/越权/未知ACK/刷新恢复。
- [x] 完整适用回归、集中单agent自查、数据字典与阶段E验收摘要/证据/状态汇合。

完成范围：COMPLETE_LOCAL_CONTROLLED，E-01～04全部交付。最终Java913、Web606、相关MySQL执行54、harness132、启动器20，均0失败/错误/跳过；类型/API101/构建和部署后CAS10组通过。来源撤销原件保持BLOCKED及远端零发送，原请求接续4项、两桌面权限弹窗2项通过。源码/部署签名均c2e2de9006ed4d7a98f61735178539a2d1257cbd4fc3afc6e3be4dcb25ec5f72。见[验收摘要](../../acceptance/e/summary.md)与[当前接续](../../development/2026-11-02-phase-e-complete.md)。

Ruling: BCD已有正式本地受控完成证据，沿用其精确范围而不重跑；当前工作区11月2日离线模型/删除/导入改动全部保留，本轮全量回归包含它们。
Ruling: 原E计划迁移V016-019已被前期采用，使用新的V062起追加迁移，绝不覆盖已有迁移。
Ruling: 用户明确不问且禁止子agent，设计自审和集中代码自审由同一agent完成；不调用技能中的确认/分支/子代理步骤。
