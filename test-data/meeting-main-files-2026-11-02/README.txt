会议主文件上传测试包（2026-11-02）

所有人物、发言、任务和日期均为虚构测试数据。音频由本机离线中文TTS合成，
不是实际会议录音。所有人物标签共用一个合成声音，不用于验证真实多说话人分离。

本包共有 32 个上传样本，标准格式11种：
音频：MP3 / WAV / M4A / AAC / FLAC / OGG / WEBM
文本：TXT / DOCX / SRT / VTT
标准音频时长约 136.4 秒，同一来源；标准文本均包含同一场会议的10段发言。
SRT/VTT时间轴根据合成音频分段实际长度生成，供时间戳校验。

目录用途：
  files/01_audio       7种有效音频容器/编码
  files/02_text        4种有效文本/文档/字幕
  files/03_variants    6个编码、换行、Unicode、文件名和音频参数变体
  files/04_multi_file  2段WAV和2段TXT，供添加、调整顺序、移除、恢复测试
  files/05_negative    11个异常或边界样本；预期结果见samples.csv和manifest.json

建议操作：
1. 先上传02_text/meeting.zh-CN.txt，核对10段发言、中文、人名、任务及日期。
2. 对其余10种标准格式分别新建会议或新导入批次，比较同一会议内容。
   多个格式来自同一会议；同时导入这些格式可能让会议内容重复。
3. 多文件顺序测试只选择04_multi_file里的两段WAV，或只选择两段TXT。
   先按01、02导入；另一次反向选择或移动顺序，核对最终段落顺序。
4. 逐一测试03_variants，再单独测试05_negative，观察错误提示和重试/移除行为。
   静音文件不应产生虚构会议内容；具体ASR表现需在实际服务上验证。
5. 当前代码限制每批最多16个文件、每个不超过32MiB。
   over_32MiB_by_1byte.txt大小精确为33,554,433字节，用于验证超限拦截。
   验证数量上限时，可从01_audio、02_text、03_variants合计选择17个正常样本。

内容核对要点（示例预期，不要求ASR逐字一致）：
  陈宇：2026-11-04 18:00 前提交格式兼容性报告。
  王敏：2026-11-05 18:00 前完成回归测试。
  周宁：2026-11-06 12:00 前整理操作说明。
  下次验收会议：2026-11-06 15:00。以上时间均按Asia/Shanghai解释。

配套文件：
  samples.csv       Excel可打开的清单，含场景、预期、字节数及SHA-256
  manifest.json     完整清单、标准字幕时间轴、模拟任务预期
  SHA256SUMS        32个上传样本的原始字节校验值
  validation.json   运行verify.py后产生的文件级校验结果
  generate.py       离线生成器（Python 3.11+、python-docx、ffmpeg及Windows中文TTS）
  verify.py         校验器（从项目根目录执行；依赖python-docx、ffmpeg、ffprobe）

项目根目录下重新校验：
  python3 test-data/meeting-main-files-2026-11-02/verify.py

重新生成请指定新目录，生成器拒绝覆盖既有样本：
  python3 test-data/meeting-main-files-2026-11-02/generate.py --output test-data/meeting-main-files-regenerated

验证范围：文件哈希、真实音频容器/编码和可解码性、字幕解析、DOCX/TXT内容及异常样本。
本包未自动上传到应用，也不声称已验证完整UI、后端转写或纪要生成链路。
