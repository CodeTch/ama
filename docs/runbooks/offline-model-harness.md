# 离线模型接口与 harness 联调

AMA 按 `offline_pipeline_v4_20261021_update` 的 online 客户端约定发请求；开发环境使用 `http://127.0.0.1:8792` 的 `ama-integration-harness`。所有音频为完整的 16 kHz、单声道、PCM16 WAV。请求使用 `Host: inference.dev.zsfund.com`，未配置鉴权时不发送空 Authorization。

| 阶段 | POST 路径 | 请求 | 读取的结果 |
| --- | --- | --- | --- |
| VAD | `/vad` | JSON：`audio_b64` | `segments: [[start_ms,end_ms]]` |
| 说话人分离 | `/diarize` | JSON：`audio_b64`、`vad_segments`；可选 `speaker_count`、`min_speakers`、`max_speakers`、`pval` | `timeline: [[start_ms,end_ms,speaker_int]]` |
| ASR | `/inference/asr-nano/modelserving/asr` | `application/octet-stream`：完整 WAV | `text`，可选 `tokens: [[文字,start_ms,end_ms]]` |
| 标点 | `/inference/asr-punc/modelserving/punc` | JSON：`text` | `text` |
| 声纹 | `/embed` | `application/octet-stream`：完整 WAV | `embedding: number[]`，当前安装配置为 256 维 |
| 纠错 | `/v1/chat/completions` | JSON：`model`、`messages`、`temperature: 0` | `choices[0].message.content` 内的 JSON 纠错数组 |

时间为当前 WAV 内的整数毫秒；AMA 将其转换为带文件偏移的采样位置。说话人编号只表示模型分组，不等于员工身份。ASR 缺省 `tokens` 时保持缺省，未返回的置信度不补造。

`HttpSpeechModelPort.diarize(clip, spans, options)` 可显式传递人数和聚类参数；默认请求省略这些可选字段。`correct(text, entities)` 可传本次已确认的专名或 `Agent（转写里常写成：A准）` 这类明确映射。导入任务默认不猜测实体词典；harness 因而可以合法返回 `[]`，这与错误响应不同。

## 纠错与结果输出

每个音频窗口对应 LLM 输入的编号 0，提示词中的转写格式为 `[0] ?：原文`。默认模型与参考客户端一致，为 `deepseek-v4-flash`。返回示例：

```json
{"choices":[{"message":{"role":"assistant","content":"[{\"seg\":0,\"old\":\"因该\",\"new\":\"应该\",\"why\":\"同音错字\"}]"},"finish_reason":"stop"}]}
```

客户端支持纯 JSON 和 JSON 代码围栏；错误句子编号、原文不存在、重复或重叠替换、截断、非 JSON 和超限响应均判为失败。多处相同原文在同一窗口内一起生成替换；不同修改基于原始输入定位，避免串联替换再次修改新文本。

检查点分别保存 `ASR` 原文、`ENHANCEMENT` 标点文本和 `CORRECTION` 的模型 ID、输入及修改项。导入结果仍是 ASR 原文。用户在现有“待确认纠错”中采纳后才创建新转写版本；拒绝保持原文，撤销恢复原版本。历史任务只有前五阶段时，仍读取已有标点候选。

静音 ASR `text: ""` 和 `tokens: []` 是合法结果。空窗口不调用文本模型、不生成转写段；整份音频无识别文字使用现有 `MEDIA_NO_TEXT` 处理错误。音频末尾空窗口不会抹掉前面已识别内容。

新增数据库迁移 `V061__speech_correction_checkpoints.sql` 扩展检查点阶段约束。历史迁移和引擎归档保持原有内容。配置检查仍使用 `.tools/local-models/speech-runtime-v1.json`；LLM 的独立检查点校验当前纠错模型与标点输入。六类 HTTP 请求都使用 harness 的执行证据协议；超时保持 UNKNOWN，直到服务端完成或提供 fence 终态证据。

## 验证命令

先启动更新后的 harness（8792），并启动 AMA 本地开发环境。以下脚本只连接本地 harness，创建独立测试数据库，结束后删除该数据库；不重启 harness，也不改写 AMA 的日常开发数据。

```sh
cd /home/tanren/ama
. .tools/env.sh
python3 tools/dev/offline_speech_check.py
```

脚本会打包当前源码、应用迁移、执行 `SpeechPipelineIT`、`SpeechExecutionObserverIT`、`ControlledSpeechImportIT`、`SpeechReviewIT`、`OfflineSpeechReviewIT`，以及 `RecordingTranscriptSourceIT` 的单窗口和双窗口转写用例。证据保存在 `.tools/offline-speech/report.json`、`verify.log` 和 `migration.log`。如果预检提示 LLM 不返回 JSON 数组，应先更新运行中的 harness；磁盘代码已更新并不代表旧进程已加载。

定向单元测试：

```sh
./mvnw -B -ntp -pl backend/worker -am \
  -Dtest=HttpSpeechModelPortTest,CompanySpeechAdapterTest,SpeechContractsTest,SpeechPipelineTest,SpeechCheckpointCodecTest,TranscriptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

日常开发服务需加载新 JAR 和数据库迁移时，执行 `python3 tools/dev/local_app.py start`。启动器检测源码变更后重载本项目服务，保留已有端口配置和本地业务数据。

浏览器端到端联调（先运行上面的集成脚本，准备当前构建和依赖）：

```sh
python3 tools/dev/local_app.py start
node tools/dev/offline_speech_e2e.mjs
```

浏览器脚本使用已有本地测试账号 `1 / 1`，创建一场新的测试会议并上传脚本生成的唯一 WAV。仅对该音频及其唯一文本配置 harness 精确场景。随后通过正常页面生成待处理建议、接受、刷新、撤销，读取原始转写历史，并将 AMA 的六条执行记录与 harness 的请求/响应摘要逐条对照。结束时删除本次创建的场景，保留测试会议和 `.tools/offline-speech/e2e-*/report.json`、截图供复核。

本地验证覆盖协议和流程；harness 返回模拟识别数据，不能据此评价真实模型的识别准确率、声纹质量或生产延迟。参考项目的音近召回、实体库构建、消歧与真实声纹匹配算法不在本次接口适配范围内。
