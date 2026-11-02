> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 最终确认页面与原请求恢复

当前正常部署源码为 `36919384593b250090dabad82bdca08b604e8d03e7c69b4995730461276c6fc0`。仅本节范围完成，BCD总体验收仍未结束。外部目录和模型通过固定8792/2525兄弟项目，非公司真实权威，生成内容及人工审阅为明确的受控测试数据。

正式页面证据 `.tools/local-app/d08-page-matrix/confirmation-pixel-06-aligned/report.json`：1440/1024/390三视口及完整长页共6图，原型SHA `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，全部严格0像素；无遮罩、阈值或renderer修改，source/deploy/harness前后稳定。原型只投影真实保存的信息、名单、版本、正文和目录。明细窗口保留真正required/blocker、分页及可选审阅；顶部保存仅存信息，不自动确认名单/扩大权限/冻结。

`confirmation-header-save-02-aligned/report.json` 三组真实CAS通过：干净保存0请求；检查明细实际行只读；修改名称后仅1次PUT信息，名单、access revision、正文、要素和未冻结状态不变，刷新再保存0新命令。`confirmation-info-unknown-live-repair/green-report.json` 在同一原浏览器对真实已提交但丢ACK的信息保存进行恢复，2次PUT相同key/body，未提前冻结。此前缺恢复按钮及空“编辑会议信息”入口RED保留。

`confirmation-final-freeze-01-aligned/report.json` 保持FAIL：三必填字段均在请求前阻断；真实最终确认201提交丢ACK、刷新后原key/body200恢复均已完成，但其随后断言“未发布修改信息应409”错误。MIN-14和需求模块四§3/5.4明确：发布前允许实质修改，清空当前final指针并保留历史。后端PUT200正确，不为测试改变产品规则。

`confirmation-refinal-history-01-aligned/report.json` 同样保留FAIL：已核验上述失效及原快照可读，已通过真实顶部保存恢复测试名称，产生新的最终确认并完成原key/body丢ACK刷新恢复；末尾错误地对整个历史响应deepEqual，把每次读取的当前root observation当作冻结内容，revision18→20导致断言失败。冻结内容本身不变。

`confirmation-refinal-readonly-close-01-aligned/report.json` 是上述已提交操作的只读接续，PASS，无新最终确认或发布命令。核验原请求2次相同key/body、已恢复名称、两份不同最终确认历史；正文、要素、审阅成员、原profiles一致且provenanceVerified=true。实际CAS dev-member读200且正文逐字段一致；dev-viewer读和确认403，页面无会议名称泄漏。未发布，目录读取仅真实GET。

当前自有会议 `c2096a1c-a1d0-487a-90ac-3392eff423d7`，有效最终确认 `ebad7ddd-011c-42d4-9024-19cd2f7a6d66`；旧确认 `66da26b7-89bd-4e15-8cf4-1e383ffc8d0c` 保留。**不可重跑原冻结脚本创建新key，不可把此未发布夹具当发布后只读证据。** 下一步先发布页/预览只读RED与修复，再真实发布和发布后写保护。

当前前端全量561/0、type/API83字节一致/build/launcher20/正常deploy全部通过，日志 `confirmation-qualified-*`。本批没有修改Java，不把之前生产888/0冒充本批重跑。
