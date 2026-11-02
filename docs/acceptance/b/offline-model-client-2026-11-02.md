# 离线六接口与纠错采纳联调记录

日期：2026-11-02，Asia/Shanghai。AMA 在 `/home/tanren/ama` 的 `main` 分支单 agent 完成；保留已有工作区修改，没有建立分支或 worktree，没有提交或推送。

接口依据为 `/mnt/d/Desktop/offline_pipeline_v4_20261021_update/offline_pipeline_v4_20261021_update` 中的 `offline_client.py` online 模式与 `llm_correct.py`。已读取并复查 harness 对话 `01a19baa-9207-74d2-8903-1fee3f9d52d4` 的最终进展，其最新实现已运行于 `http://127.0.0.1:8792`。本轮直接对运行中的双项目验证；harness 目前满足约定，发现的问题在 AMA 适配和验证代码中完成修复。

## 交付行为

- `/vad`、`/diarize` 发送完整 WAV 的 JSON/Base64；`/embed`、ASR 发送完整 WAV 二进制；标点发送 `text`；纠错发送标准 Chat Completions 结构。声纹 256 维，默认纠错模型 `deepseek-v4-flash`。
- 可显式传递说话人数、聚类阈值，以及已确认的纠错实体词典；缺省时省略参数。时间戳换算包含原始文件偏移，缺省 tokens/confidence 不补造。
- `CORRECTION` 独立保存模型 ID、源文本、`seg/old/new/why` 修改项。与 ASR、标点检查点分离，失败后可复用前五阶段。旧五阶段任务仍能读取已有标点建议。
- LLM 结果只生成待确认候选。接受创建新的人工版本；拒绝保留原文；撤销恢复原版本；RAW 历史不变。
- 空 ASR 是合法静音结果；空窗口不调用文本模型、不创建虚假文字，整份静音使用 `MEDIA_NO_TEXT`。
- 非 JSON、截断、错误编号、原文不存在、重叠替换和删除整段文字均拒绝。空建议 `[]` 与接口失败保持不同语义。
- V061 仅扩展检查点阶段约束，已应用至 `ama_local_dev`；历史迁移和引擎归档未修改。

## 最终验证

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| HTTP 适配、值对象、流水线恢复、codec、worker 定向单元 | 23 通过，0 失败 | `.tools/offline-speech/targeted-unit.log` |
| 实际 MySQL + 当前 harness 的集成测试 | 31 通过，0 失败，0 跳过 | `.tools/offline-speech/report.json`、`verify.log` |
| 真实浏览器 → API → worker → harness → 人工采纳 | 6 项核验通过，浏览器错误 0 | 下方端到端报告 |
| 固定 8792 直接 HTTP 契约检查 | 11 通过 | `.tools/offline-speech/harness-contract.json` |
| 参考项目原版 `Services`、`call_llm`、`parse_json_array` 直连 | 通过 | 同上，`originalReferenceClient` 字段 |
| 本地应用状态 | frontend/backend/worker/files/model/knowledge 全部存活 | `.tools/offline-speech/local-start.log` 和最终 `local_app.py status` |
| 静态检查 | `git diff --check`、Python 编译、Node 语法检查通过 | 本轮工具输出 |

31 项集成测试分布：`SpeechPipelineIT` 2、`SpeechExecutionObserverIT` 5、`ControlledSpeechImportIT` 5、`SpeechReviewIT` 15、`OfflineSpeechReviewIT` 2、`RecordingTranscriptSourceIT` 单窗口与双窗口 2。包括六条执行摘要、ASR/LLM 超时 UNKNOWN、失败恢复、静音、旧检查点、候选接受/拒绝/撤销、权限，以及双录音窗口的 12 个检查点。

测试专用数据库 `ama_c_recording_361c5beecf68` 已在脚本 finally 中删除；日常应用数据库保留。

## 浏览器端到端证据

最终报告：[report.json](/home/tanren/ama/.tools/offline-speech/e2e-6182a9cc-92a2-4045-becb-1f1446a28fd1/report.json)。截图：[待处理](/home/tanren/ama/.tools/offline-speech/e2e-6182a9cc-92a2-4045-becb-1f1446a28fd1/pending.png)、[接受并刷新](/home/tanren/ama/.tools/offline-speech/e2e-6182a9cc-92a2-4045-becb-1f1446a28fd1/accepted.png)、[撤销](/home/tanren/ama/.tools/offline-speech/e2e-6182a9cc-92a2-4045-becb-1f1446a28fd1/undone.png)。前一轮完整成功报告同样保留于 `.tools/offline-speech/e2e-6c5e1fec-34cc-4635-9393-fc3ebebd3a04/report.json`。

1. 在真实页面登录，上传唯一的 8 秒 WAV，经正常处理入口读取 ASR 原文。
2. 从这场新测试会议的执行账本读取六条 COMPLETED 记录，逐条核对 harness 的路径、请求 SHA-256、响应 SHA-256 和终态。
3. 精确场景让 LLM 返回“因该 → 应该”，页面展示非空 PENDING 候选，此时当前正文和 RAW 仍是原文。
4. 点击“接受”，确认新的工作版本包含纠错文字，RAW 原文及段落保持一致。
5. 刷新浏览器后，接受结果保持不变。
6. 点击“撤销”，当前正文恢复原文，候选变为 UNDONE，RAW 历史仍不变。

本轮浏览器创建的三个精确匹配场景已全部删除；测试会议和报告保留供复查。没有使用 Playwright 路由替身拦截产品请求，也没有直接向业务表插入候选或转写结果。

## 调试中修正的问题

- 200 ms 的旧超时用例可能在请求到达 harness 之前取消，却立即要求服务端存在执行记录。改为先观察真实 STARTED，再断言客户端 UNKNOWN 和服务端最终完成；ASR 与 LLM 均通过。
- MySQL 时间在入队与领取间出现回拨；诊断值 `available_at=10:27:50.225446`、`database_now=10:27:50.072016`。测试改为按真实 worker 的方式进行最多 3 秒的领取轮询，不改业务状态或可领取时间。
- 自审发现整段删除会生成业务不支持的空候选；新增失败用例后在纠错值对象校验阶段拒绝，HTTP 侧返回无效响应错误。
- 浏览器脚本按实际页面的自动进入/“进入转写校正”两种就绪路径继续，不将正常的按钮卸载竞态视为模型或业务故障；最终两次完整采纳流程成功。

## 全仓单元检查的限制

额外执行了 `AMA_TASK11_REPOSITORY_ROOT=/home/tanren/ama ./mvnw -B -ntp test`。生产模块共 898 项通过：contracts 11、core 107、adapters 580、api 169、realtime 8、worker 23。随后 integration-tests 模块中的 418 项辅助单元有 11 个断言失败、48 个异常，共 59 项未通过。不能将本次交付表述为全仓测试全部通过。

主要限制包括历史 `.superpowers/sdd/...` 验收 ACK、`accepted-rows-after.json`、`business-o4-closed.json` 等证据文件缺失，历史装配/启动测试依赖未满足。此外 `Bl5MeterForwardTest` 显式要求 `jdk.httpclient.allowRestrictedHeaders=host`，该全仓命令没有设置；`Task11Fix02WireVariantsTest` 报空指针。逐用例摘要保存在 [unit-summary.json](/home/tanren/ama/.tools/offline-speech/unit-summary.json)，完整日志位于 `.tools/offline-speech/all-unit.log`。

涉及测试类：Task11B3LifecycleInputsTest、Task11B3BudgetAssemblyTest、Task11B3InputsTest、Task11B3LifecycleAssemblyTest、Bl5MeterForwardTest、Task11B3ContinuationInputsTest、Task11B3ContinuationStartupTest、Task11B3LocalOracleTest、Task11B2ReadbackPromotionTest、Task11B3LifecycleReviewRegressionTest、Task11Fix02WireVariantsTest、Task11B3BudgetStartupTest、Task11B3BudgetFinalizationTest、Task11B3BudgetInputsTest。

本次定向检查与实际接口、数据库和浏览器联调独立执行，全部通过；没有伪造历史验收证据来消除上述失败。

## 复现

```sh
cd /home/tanren/ama
. .tools/env.sh
python3 tools/dev/offline_speech_check.py
python3 tools/dev/local_app.py start
node tools/dev/offline_speech_e2e.mjs
```

详细参数见 [接口映射与运行指南](/home/tanren/ama/docs/runbooks/offline-model-harness.md)。当前页面 `http://127.0.0.1:5174/meetings`，API 8182，harness 8792。

本地 harness 的音频识别、分离与声纹输出为模拟数据；本记录证明协议、调用链、检查点及人工决策流程成立，不评价真实模型质量或生产延迟。
