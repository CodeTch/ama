# AMA Offline Model Client Implementation Plan

> 使用 executing-plans，在 `/home/tanren/ama` 的 main 分支单 agent 执行；不创建 worktree，不提交其他正在进行的修改。

**Goal:** 按离线 online 协议调用六类接口，通过 harness 完成本地参数与结果联调，将 LLM 输出接入待确认纠错。

**Architecture:** 扩展现有 HttpSpeechModelPort；独立 CORRECTION 检查点与现有 ASR/ENHANCEMENT 并存；通过现有 SpeechReviewRepository 生成可采纳候选。

**Tech Stack:** Java 21、Jackson、AWS Apache HTTP transport、MySQL/Flyway、JUnit、Python 开发工具。

**Spec:** `docs/superpowers/specs/2026-11-02-offline-model-client-design.md`

## Global Constraints

- AMA 根目录、main、单 agent；不请求真实模型。用户后续已授权发现联调问题时直接修复 AMA 和 harness，两侧均保留其他已有改动。
- 原文不自动改写；HTTP 错误不能当作空纠错结果。
- 保留历史归档和检查点语义，不覆盖用户已有变更。

## Review Focus

- LLM 空数组与非法响应不同；错误 seg、原文缺失、重叠项不能变成候选。
- 静音窗口、尾窗口和时间偏移，缺省 tokens 不补造。
- 纠错请求失败后复用已成功的五阶段，模型与原文绑定。
- 旧任务只有 ASR/ENHANCEMENT 仍可阅读和采纳已有候选。
- 请求超时后执行证据必须保持可恢复，不能错误释放共享容量。

### Task 1: HTTP 契约与纠错值

- [x] 补充 HttpSpeechModelPortTest：可选说话人参数、空识别、整数编号、LLM 请求/响应、非法响应。
- [x] 运行定向测试观察缺失行为。
- [x] 实现 SpeechContracts 纠错值及有界替换、HTTP CORRECTION、可选人数配置与空识别兼容。
- [x] 定向单元测试通过。

### Task 2: 检查点与待确认纠错

- [x] 流水线测试覆盖失败恢复、原文不变、空窗口、配置不匹配。
- [x] 增加 CORRECTION codec、Flyway 约束扩展和执行路径；接入导入/worker。
- [x] SpeechReviewRepository 验证 CORRECTION 与标点源文本一致并生成候选；保留旧检查点兼容。
- [x] 更新受影响集成断言并验证采纳、拒绝、撤销。

### Task 3: 本地联调与交付

- [x] 核对 harness 最新实现/运行实例，通过实际 HTTP 调用六类接口。
- [x] 后端单元测试及语音/纠错数据库集成通过。
- [x] 写入接口映射、运行命令及结果证据；自审 diff 并检查 main 和已有工作区改动。
- [x] 按用户后续要求读取 harness 对话最新进展，并运行真实浏览器/API/worker/harness 端到端采纳和撤销。

## Progress

- 基线：HttpSpeechModelPortTest、CompanySpeechAdapterTest、SpeechPipelineTest、SpeechCheckpointCodecTest、ControlledSpeechConfigTest 通过。
- 用户确认采用待确认纠错方式。依赖顺序：Task 1 的 CorrectionResult 被 Task 2 检查点及候选读取消费。

- 最终定向单元：23 / 23；实际 MySQL + harness 集成：31 / 31，0 跳过；浏览器全链路：6 / 6。
- 原版 offline_client.py / llm_correct.py 直连当前 8792 通过，额外 11 个 HTTP 契约检查通过。
- 首轮发现 200 ms 超时测试的服务端到达竞态；改为先确认 STARTED，再验证 UNKNOWN 与终态。诊断另确认 MySQL 时间回拨 153 ms，测试领取采用 worker 式有界轮询。最终回归均通过。
- 自审补充“纠错不得删除整段正文”约束，并先观察失败测试再修复验证。
- V061 已应用到本地日常库，API/worker 已加载新版本。harness 对话最终状态为完成，当前 8792 版本已满足协议，本轮未发现需要继续改写 harness 源码的问题。
- 全仓 `mvn test` 并非全绿：生产模块 898 项通过，integration-tests 内历史辅助单元有 59 项失败/异常。完整边界、复现与证据见 `docs/acceptance/b/offline-model-client-2026-11-02.md`。
