# 本地模型协议探针样例

仅使用 Python 3 标准库。仓库不提供线上默认地址、真实音频、凭证或成功容量报告。当前授权范围只运行本机 loopback 假 HTTP 服务；以下样例使用 2 秒合成静音，不具备识别质量评估价值。

在仓库根目录运行，可完整执行 CLI 并在终端查看脱敏指标（临时 WAV、配置和报告随目录删除）：

```bash
python3 - <<'PY'
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import wave
from tools.probes.tests.test_model_probe import fake_service

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    wav = root / 'synthetic.wav'
    with wave.open(str(wav), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        audio.writeframes(b'\x00\x00' * 32000)
    with fake_service({'text': ''}) as (endpoint, _, _):
        config = root / 'local.json'
        config.write_text(json.dumps({
            'protocol': 'asr', 'endpoint': endpoint,
            'authorization_id': 'local-synthetic-only',
            'max_concurrency': 1, 'timeout_seconds': 3,
            'repeats': 1, 'max_run_seconds': 10,
            'arrival_interval_ms': 0
        }))
        output = root / 'metrics.json'
        subprocess.run([sys.executable, 'tools/probes/model_probe.py',
                        '--config', str(config), '--output', str(output), str(wav)], check=True)
        print(output.read_text())
PY
```

运行测试：

```bash
python3 -m unittest discover -s tools/probes/tests -p test_model_probe.py
python3 -m unittest discover -s tools/probes/tests -v
```

## 配置与协议

`probe(config: dict, wav_paths: list[str]) -> dict`，CLI 为 `python3 tools/probes/model_probe.py --config /绝对路径/配置.json --output /绝对路径/指标.json [WAV路径 ...]`。成功全部通过退出 0；有请求错误或未发送退出 1；配置/输入/写文件失败退出 2。API 的配置或 WAV 校验失败抛出固定描述的 `ValueError`，不发请求。

必须显式提供 `protocol`、`endpoint`、`authorization_id` 和 `max_concurrency`。`authorization_id` 是本次授权测试的引用，由操作者确认有效授权；它不会发到服务端或写进指标，不能替代平台鉴权。可用 `authorization` 显式设置完整 HTTP Authorization 值；仅在本地受控配置中提供，禁止提交生产凭证。不从环境变量、其他服务商、netrc 或系统代理继承任何凭证或路由。

| protocol | 请求 | 必需的额外配置 | 校验响应 |
| --- | --- | --- | --- |
| vad | JSON `audio_b64`，完整 WAV 字节的 Base64 | 无 | `segments`：毫秒区间数组，允许静音为空 |
| spk | JSON `audio_b64`、`vad_segments` | 同一完整 WAV 对应的 `vad_segments` | `timeline`：开始毫秒、结束毫秒、非负说话人编号 |
| asr | 完整 WAV 二进制，octet-stream | 无 | `text` 字符串，允许静音为空；可选 `tokens` 的存在与格式分别校验 |
| embed | 完整 WAV 二进制，octet-stream | 无 | 非空有限数值数组 `embedding`，不假定向量维度 |
| punc | JSON `text` | `text` 非空字符串 | `text` 字符串；缺失不能用原文回退冒充成功 |
| llm | JSON `model`、单条 user `messages`、`temperature:0` | `text` 完整业务提示词、`model` | `choices[0].message.content` 字符串 |

音频只接受 16kHz、单声道、16bit PCM WAV，发送包含 RIFF/WAVE 头的完整文件，不转码、不裁剪、不发送路径、不用裸 PCM 或 multipart。SPK 必须显式传入该音频真实 VAD 的原始毫秒数组，探针不自动串联六个服务。未验证的 `speaker_count/min_speakers/pval` 不发送，未知配置直接拒绝。

非 LLM 默认使用文档调用约定的 `Host: inference.dev.zsfund.com`，可显式用 `host` 覆盖；LLM 默认使用 URL 的 Host，仅在显式 `host` 时覆盖。必须用平台实际确认的值联调。传输采用直连 HTTP(S)，HTTPS 使用系统信任验证证书；无代理继承、无重定向、无自动重试。需代理或 mTLS 的平台尚须另行实现受控适配，不能用环境代理绕过测试范围。

| 可选配置 | 默认 / 范围 | 含义 |
| --- | --- | --- |
| timeout_seconds | 3 / (0,30] | socket I/O 等待上限，非服务端处理 SLA，非总墙钟硬截止 |
| repeats | 1 / 1..1000 | 每份输入重复次数；一批总计最多 1000 请求 |
| max_run_seconds | 10 / (0,60] | 新请求准入预算；到期停止准入，已发请求继续收尾 |
| arrival_interval_ms | 0 / 非负 | 0 为有界突发批次；20 为计划每 20ms 一次，即 offered 50/s；计划到达跨度必须小于准入预算 |
| source_offset_ms | 0 / 0..86400000 | 服务毫秒区间映射回来源时统一加此偏移；多来源偏移应分批传入 |
| window_seal_ms | 0 / 0..86400000 | 调用者实际量得的窗口封口等额外等待，用于端到端统计；不自动捏造采集延迟 |
| host / authorization | 未设置 | 显式覆盖头 / 完整鉴权值；拒绝控制字符 |

最大并发显式限制为 1..100；每 WAV 最多 4MiB，每响应最多 2MiB，文本输入最多 64KiB；每个并发 worker 仅持有本次有界 WAV/请求。长录音应先与平台约定有效输入策略；此探针不会未经确认截断 VAD/SPK 音频。所有 WAV 都在首次 HTTP 请求前校验。运行期间不要修改输入文件。

## 指标口径

`requestCount` 为计划请求总数，`sentCount` 为开始传输的尝试数，`validCount` 为响应满足协议数，`notSentCount` 为未发尝试数。`audioSeconds` 为计划音频秒数（重复/重叠输入重复计入），`submittedAudioSeconds` 为实际尝试提交的音频秒数。punc/LLM 为 0。两者均不等于去重后的真实会议时长。

`queueMs/serviceMs/endToEndMs` 含 count、nearest-rank p50/p95/p99/max 和 censoredCount。原始逐请求指标在 `samples`，`rawSamplesPath` 为 API 的 `#/samples` JSON Pointer，CLI 写入后为 `绝对报告路径#/samples`。样本 ID 为内部序号，不写音频文件名、响应原文、token 内容、声纹向量、凭证、端点或完整业务提示词。

队列时间从该请求计划到达算起，不把尚未到达的等待算成排队；有界并发不足时仍按原到达时刻计算积压，不通过推迟时钟隐藏队列。`serviceMs` 包含读取/编码、连接、网络、读取、校验的客户端观察时间，不能单独解释为服务器推理时间。端到端时间为队列＋上述服务观察时间＋显式 window_seal_ms，不能直接代替真实浏览器采集到稳定字幕的指标。

HTTP 429、其他非 200、无效/缺字段响应、网络错误、超时、预算停止均保留在 errors、原始样本和对应有观测的时延中。未发送项 serviceMs=null；TIMEOUT、NETWORK_ERROR、未发项为删失观测，其端到端等待只代表已观测下限。含删失值的分位数不能作成功请求延迟或 SLA 达标证明。

超时或不确定网络失败停止本批新准入，等待已有调用收尾；`inflight.unknownServerAtEnd/reservedAtEnd` 保留旧服务端工作可能仍执行的额度，`clientAtEnd=0` 不代表服务端已取消。不得循环重跑补足吞吐：平台确认完成/取消或提供安全截止、全局额度后才能释放未知额度。探针没有跨进程全局租约，不适合多进程叠加运行。

`offeredRequestsPerSecond` 是配置到达速率；突发批次为 null。`observedRequestsPerSecond` 仅为尝试数/本批客户端墙钟观察时间，不能当作稳定可用吞吐。未发、超时、响应无效和质量失败必须共同评估，HTTP 200 数量不能证明容量。

真实环境授权尚未获得，不在此运行 1/10/50/100 路公司服务测试。后续按 [容量验收记录](../../../docs/acceptance/a/model-capacity.md) 中的阶梯和停止条件推进。
