# 知识库发布实施验收矩阵

依据：需求模块四 P02–P06；原型全部脚本执行后的 DOM，哈希见 prototype-baseline.json。

| 编号 | 需求/原型区域 | 工程落点 | 验证 |
|---|---|---|---|
| KP01 | P02 联动目录/根目录/层级/名称与说明搜索 | PublicationDestinationPage、usePublicationDestinations | 目录契约、实际页面操作 |
| KP02 | P02 真实权限/可见范围/按会议保存标签 | JdbcPublicationConfigurations、PublicationPanel | 跨会话/并发/撤权/失效目录 |
| KP03 | P03 完整确认正文/来源/位置预览 | PublicationPreviewDialog、冻结文档模型 | 正文一致/长文本/失败恢复 |
| KP04 | P04 实际阶段/重复提交/刷新查回 | PublicationReadProjection、usePublication | 未发送/UNKNOWN/丢响应/成功 |
| KP05 | P04 明确失败重试与重选 | JdbcPublicationReplacement、恢复命令 | 原样/重选/过期/幂等 |
| KP06 | P04 成功结果持续可读/库路径 | MinutesWorkspace、PublicationPanel | 关弹窗/刷新/新会话 |
| KP07 | P05 正式文档信息/来源/历史 | PublicationDocumentView、文档读取服务 | 实际文档/快照不变 |
| KP08 | P05 独立库阅读与证据权限 | PublicationDocumentRead、provider | 四种权限组合/原始证据拒绝 |
| KP09 | P06 范围内记录、撤回和废止 | 既有治理页面/任务、库管理权限 | 取消/成功/失败/未知/直链封禁 |
| KP10 | 1440×900、1024×768 原型布局 | 发布两栏/底栏/预览/文档 | 截图、长标题/路径、无遮挡 |

规则来源：标签限制由后端/受控目录提供并配置，前端不定义产品限制；治理沿用已有记录页/弹窗，不扩建未定义的权限管理后台。外部联调统一经过兄弟 harness 固定 8792，模拟响应不构成真实公司接口权威。
