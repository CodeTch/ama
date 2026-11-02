# AMA 离线模型接口适配设计

以 `offline_pipeline_v4_20261021_update.zip` 中的 `offline_client.py` online 模式和 `llm_correct.py` 为接口依据，以相邻 `ama-integration-harness` 的实现进行本地联调。用户已确认：LLM 结果进入待确认纠错，ASR 原文保留，采纳后才写入人工转写版本。

## 约定

- 六类 POST：`/vad`、`/diarize`、`/embed`、`/inference/asr-nano/modelserving/asr`、`/inference/asr-punc/modelserving/punc`、`/v1/chat/completions`。
- 音频为完整 16 kHz 单声道 PCM16 WAV；前两类 JSON/Base64，ASR 与声纹为二进制。服务时间为相对当前音频的整数毫秒，AMA 内部为带原始偏移的采样点。保留 ASR 缺省 tokens，不虚构 confidence 或企业身份。
- 说话人参数 `speaker_count`、`min_speakers`、`max_speakers`、`pval` 可选；默认不发送猜测的人数。
- LLM 请求 `model`、`messages: [{role: user, content: prompt}]`、`temperature: 0`；读取 `choices[0].message.content` 中的 `[{seg,old,new,why}]`。一窗口对应一个编号 0；支持 fenced JSON；拒绝错误编号、不存在的原文、冲突/重叠替换、截断或非 JSON 响应。替换只生成候选，绝不直接覆盖原文。
- harness 为 `http://127.0.0.1:8792`，声纹维度 256，模型 ID 默认 `deepseek-v4-flash`。不访问截图中的内网模型地址。
- ASR 空文本是合法结果；空窗口不生成虚假转写段，整份无可识别文字返回现有 `NO_TEXT` 错误。

## 实现选择

沿用现有 HTTP 客户端、执行记录和语音检查点，扩展独立 CORRECTION 阶段。对比直接把 LLM 混入标点阶段或新增独立服务，这种方式可以保存已完成阶段、失败后恢复，并复用现有待确认/采纳/拒绝/撤销流程。旧 ENHANCEMENT 检查点继续表示标点结果；新增 CORRECTION 明确保存模型 ID、源文本和纠错项，旧任务仍可仅从 ASR/ENHANCEMENT 生成候选。现有五阶段引擎归档不改写，新增阶段检查其输入和模型配置一致性。

实现者决定：模型配置和说话人参数作为适配器显式配置；不把模型字段直接暴露到浏览器业务 API。用户提供了单 agent、main、根目录约束，所有工作原地执行，保留既有未提交修改。

## 验证

通过真实本地 HTTP 服务器检验请求路径、headers、WAV、可选参数、毫秒转换、空识别、LLM JSON 解析和异常边界；通过流水线测试检验检查点恢复和原文不变；通过 MySQL/harness 集成测试检验六阶段、候选采纳/拒绝/撤销及旧检查点兼容。保留可重复执行的联调命令和结果证据。
