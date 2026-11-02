# 转写展示及 harness 联调阶段验收

全程 /home/tanren/ama、main、单 agent。保留原工作区修改；未提交 Git。

## 已实现

- 处理任务成功、转写步骤激活后重新读取正文；保护未保存草稿及未知请求。
- RAW 已保存但 WORKING 尚未建立时展示原始内容。
- 文本姓名前缀作为只读文件标签展示，不写企业身份，不改原文与来源。无时间戳显示 --:--。
- 媒体来源未加载、读取错误、未产生转写和纯文本分别显示。
- 历史 503 不再递归重试：故障注入原先 3 秒 13 次，修复后首次 1 次；显式重试恢复。
- 音频模拟结果明确标识，避免当作真实识别。
- 全量测试暴露专题重试按钮缺少无障碍名称，已补充 aria-label，保留外观及原请求重试行为。

## 新鲜验证

- Web 601/601 通过、0 跳过：.tools/local-app/transcript-dialogue-units-final.log。
- typecheck 成功；build 成功（现有大 bundle 警告保留）；85 个 API schema 对照通过。
- 正常启动器重启 API/Worker/文件服务/前端及本地辅助服务，保留业务数据及 sibling harness，见 .tools/local-app/dialogue-restart-verified.log。
- 重启后真实 CAS/上传/持久化/浏览器矩阵 22 项通过：
  - dialogue-text-final/report.json：8 项，会议 901223b4-30e7-451c-9e36-9aa432f8ba58。
  - dialogue-audio-final/report.json：7 项，会议 9d51094d-18e2-4dc6-8304-1b36e519a0bc，simulatedAsr=true。
  - dialogue-recovery-final/report.json：3 项。
  - dialogue-invalid-02/report.json：4 项，会议 840756f2-bfe5-4139-8cf3-67f0d4894186。
- 上述目录均在 .tools/local-app；文本/音频各保留 1440、1024 截图。成功报告 pageerror 列表为空。
- 原失败报告保留：测试脚本未等待文件入口就绪、旧窗口名称、服务重启期间连接拒绝；没有把它们修改为通过。

## harness 配置

继续连接 /home/tanren/ama-integration-harness 的 127.0.0.1:8792。使用 admin API 新建场景 3559/3560，只匹配指定 AAC 解码后两个 WAV 窗口的 binary_sha256。所有其他输入继续走既有场景。所有台词为显式模拟，时间窗口不是识别出的发言轮次。
场景安装工具 tools/dev/harness_dialogue_scenario.py，归属收据 .tools/local-app/dialogue-harness-scenarios.json。

## 未通过全范围验收的原因

本批没有实现通用真实 ASR 接入、按词句时间与 diarization 对齐、新版本自动候选持久化及跨窗口声纹一致性。音频仍按原窗口保存段落。前端姓名标签投影不是后端结构化说话人模型。
因此仅上述阶段场景为 0 失败，不宣称用户原完整方案已经达到 0 异常。
