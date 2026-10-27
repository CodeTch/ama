# 所有开发分支与当前代码快照汇入 main — 2026-10-28

用户明确授权合并所有分支代码到 main、提交并推送 GitHub。仓库：`CodeTch/ama`。这次保存当前开发成果，Phase B 仍为 **INCOMPLETE**。

## 合并范围

- 刷新 origin 后，37 个开发分支的既有提交已全部是 main 的祖先，远端只有 main。
- 将 `codex/phase-b-completion-20261023` 中尚未提交的 127 个文件保存为 `4b68213dc6b59f9cc850b6acce1a70ed4102235a`，包含会议信息/名单、上传导入、转写与生成、纪要/审阅/发布界面、workspace 查询、本地开发启动工具、验证工具和接续状态。
- main 原有流程文档先保存为 `c71efef8`，再合并上述开发快照。唯一合并冲突位于 `DEVELOPMENT_HANDOFF.md`；保留 main 历史集成记录，并新增本次最新入口。产品代码无冲突。
- 合并后的 `backend/`、`contracts/`、`tools/`、`web/` 与已验证开发快照逐项 Git diff 为空。
- 所有原分支和工作树保留。main 的 `output/`、`tmp/`、空文件 `System.Xml.XmlDocument`，以及另一个工作树中的两份未跟踪 review diff 保持原状，不作为产品源码提交。被忽略的私有运行数据和凭据不纳入本次快照。

## 本次验证

| 检查 | 实际结果 |
| --- | --- |
| Java 离线通用回归 | 886 项，0 failures/errors/skipped，162 份新 JUnit 报告 |
| Python 当前项目六个测试目录 | 236 项，0 failed/skipped |
| Web 单元测试 | 241 项，0 failures/skipped；开发树和合并后的 main 均通过 |
| TypeScript、前端生产构建 | 开发树和合并后的 main 均通过 |
| OpenAPI lint/bundle、API 生成一致性 | 开发树和合并后的 main 均通过 |
| Git 内容与空白检查 | 产品源码与快照一致，暂存内容 diff --check 通过 |

Java 选择器为 `*Test,!Task11Fix02WireVariantsTest`，保留既有历史私有夹具排除。实际使用新建的 `AMA_TASK11_EVIDENCE_WINDOW` 和 `AMA_TASK11_REPOSITORY_ROOT`，以及与 `tools/bl5_launch.py` 一致的 `-Djdk.httpclient.allowRestrictedHeaders=host`。最后一次完整所选回归为上述 886 项全通过。

首次 Java 检查缺证据输出配置，随后一次缺 Host 开关，原始失败日志均保留。Python 通用发现入口还会遍历 `.superpowers` 中的历史源码封存，已中止该次不适用的发现，改为明确选择当前六个测试目录。未通过修改产品行为或扩大测试排除来处理这些配置问题。

验证期间源码保持稳定；唯一整理是移除 `tools/tests/test_bl5_resources.py` 末尾的多余空行，未改变可执行内容。该目录整理后再次执行 101 项 Python 测试通过。

Java/Python 在原开发树执行，部分离线测试依赖该树既有公开证据或计划文件，不能据此宣称独立干净克隆的整套测试已通过。详细命令、计数、排除、分支指针和日志哈希见 [verification.json](integration-main-2026-10-28/verification.json)。完整本次日志保留在开发树及 main 的 `artifacts/integration-main-2026-10-28/`，不覆盖历史运行窗口。

## 阶段边界与接续

本次没有重新启动数据库 IT、真实浏览器验收或已消费的 B-L5 运行批次。已有独立整合审查结论为未发现新的 Critical/Important 源码缺陷，限定 API 证据按原报告范围采纳；不等于 Phase B 完成。剩余浏览器、精确预算/锁序/事务故障、关键 crash/restart、成员正向文档权限等义务继续保留，详见 [B 阶段摘要](../acceptance/b/summary.md)。

当前最新接续入口见根 [DEVELOPMENT_HANDOFF.md](../../DEVELOPMENT_HANDOFF.md)；仍从原开发树动态状态继续，不从旧 B-L1 暂停入口重复开始，不恢复已关闭资源窗口，不启动 C/D/E/F。
