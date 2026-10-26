# 全分支汇入 main — 2026-10-26

按用户“将所有分支代码合并到 main 分支，提交并推送到 GitHub”的明确要求保存当前开发状态。目标仓库：`CodeTch/ama`，目标分支：`main`。本次是包含未完成 B5 实现的 WIP 集成，不代表 Task11 / Phase B 已完成验收。

## 集成范围

- 仓库共 38 个本地分支（main + 37 个开发分支）。此前 7 个分支未具有合并祖先关系，但其所有代码文件版本均已存在于 main 的历史对象中。
- 逐一补齐这 7 个分支的合并关系；每次合并后的树与合并前 main 完全一致。保留已经演进的实现、现有归档属性规则，消除自动合并产生的一段重复溯源文档。原分支与工作树均保留。
- 主要开发分支 `codex/phase-b-completion-20261023` 的未提交实现、测试和接续记录已保存为 `647a3a20`，290 个文件变化，并纳入 main。
- 主工作区的交接文档及项目评审文档和插图保存为 `c9cba2bd`。交接文件冲突保留双方历史内容，并增加最新集成说明。
- 合并后 backend / contracts / tools / web 源码先与产品快照逐项比对一致；随后根据现有 OpenAPI 重新生成 `web/src/shared/api/generated.ts`，消除接口类型漂移。
- 未将临时 PDF 校验图片、输出 PDF、空文件 `System.Xml.XmlDocument` 或被忽略的私有运行资源加入版本控制。

## 本次验证

| 检查 | 结果与范围 |
|---|---|
| Java test-compile | 全 8 个 Maven reactor 项目通过；离线增量编译，未声称 clean build |
| Java `*Test` | 692 项：686 通过，6 errors，0 assertion failures，0 skipped；未通过全量回归 |
| 通用 Python 检查 | tools/checks/tests、tools/dev/tests、tools/probes/tests、tools/providers、tools/providers/tests 共 115 项全部通过 |
| Python tools/tests | main 上 68 项中 36 通过、32 因缺少被忽略的历史证据文件报错；相同源码在原 phase-b-completion 工作树的 68 项全部通过 |
| 前端单元测试 | 8 项全部通过 |
| 前端类型与构建 | typecheck / build 通过 |
| OpenAPI | lint / bundle 通过 |
| API 生成一致性 | 重新生成后 check:api 通过 |

Java 的 6 项错误：

1. `WorkerShutdownTest`：4 项，反射仍查找旧的 Runtime 构造函数，抛出 `NoSuchMethodException`。
2. `Task09SessionAbortTest`：2 项，测试代理未支持 `releaseRecordedLookup`，抛出 `UnsupportedOperationException`。

上述测试和相关运行实现文件与集成前主线 `739193d` 一致；本次保存并记录已有问题，不将合并快照标记为验收通过。为完整收集各模块结果，Java 诊断命令使用 `maven.test.failure.ignore=true`；其进程退出 0 **不能解释为测试通过**，以上结论来自本次新生成的 JUnit XML。

Java 的归档夹具读取指向原开发工作树；Python 证据依赖也在原工作树验证。没有运行 `*IT` 数据库集成测试、没有重放旧业务窗口或触及保留的 UNKNOWN / 已消费授权。首次聚合入口扫描了 `.superpowers` 的历史源码，已停止；缺少环境配置的初始测试失败也不作为最终测试结果。

结构化分支清单、命令与结果见 [verification.json](integration-main-2026-10-26/verification.json)。开发接续以 [B5 暂停记录](development-pause-b5-partial-2026-10-26.md) 和原工作树的最新状态为准。
