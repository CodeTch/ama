# 音视频导入与回听的局部验证记录（2026-10-29）

整体原型全功能验收仍为 **NOT ACCEPTED**。本记录只覆盖已真实执行的音视频导入、原文件回听和转写后处理，不替代录音、实时识别、说话人、材料、专题、反馈、模板等剩余验收。

本地工作树运行于 http://127.0.0.1:5174。处理版本 `ama.import-media-documents.v1`、解析版本 `ama.media-documents.v1` 与旧 TXT/Office 版本并存；通过确定性追加发布安装，旧不可变发布未覆盖。ASR 为本机 CPU faster-whisper 1.2.1，模型 Systran/faster-whisper-small 固定 revision `536b0662742c02347bc0e980a01041f333bce120`，模型清单 SHA256 `97b01ed2c124f97d7f30969d563b66fcba2a42e2511c50eb91dcf4409b11b303`。运行时逐文件验证模型字节，不在推理中下载或向外发送媒体。

## 已通过的真实路径

- `.tools/local-app/prototype-parity/20261029-media-native-v2/failure.json` 保留前六种格式成功证据：WAV、MP3、M4A、MP4、MOV、MKV。每种均使用正常浏览器上传、真实文件服务与 Worker、真实识别，读取 RAW 核对原文件 SHA256/字节数、识别正文与时间戳。该次整体脚本最终失败，原因是第七个场景在文件输入尚未启用时注入文件；不能把该报告整体称作 PASS。
- `.tools/local-app/prototype-parity/20261029-media-native-v3/report.json` 通过 WEBM、损坏 WAV、无音轨 MP4。修正测试为等待实际启用的文件选择控件。后两项均真实 FAILED 且没有 RAW；未替换业务响应。
- `.tools/local-app/prototype-parity/20261029-player-native-v1/report.json` 通过 WAV/MP4 实际解码时长11秒、播放/暂停、1.25倍速、键盘进度定位、段落时间戳定位。Blob URL 只在完整原字节哈希验证后建立，账号/版本变更及新读取释放旧 URL；迟到响应不恢复旧来源。
- `.tools/local-app/prototype-parity/20261029-media-minutes-v1/report.json` 的 WAV 会议 `f685ccea-813c-4b20-8b9b-0eadc19f8d48` 通过 RAW→人工稿→确认→模板→一次真实生成及来源回读。脚本历史步骤标签仍含 `docx`，实际输入由 fixture 的 `format=wav` 选取；该标签不是 DOCX 证据。纪要 Provider 仍为受控合成模型，因此不接受其内容语义质量。

测试媒体来自 OpenAI Whisper 的公开 JFK fixture，经本机 FFmpeg 转封装；不涉及用户麦克风或用户上传内容。结果只证明该公开英语短语音的真实识别，尚不证明中文长会议、多人分离、实时识别或所有浏览器编码的质量。

## 已修复并保留复现的边界

1. 文件名为 TXT、正文以 ID3 开头时不再误走音频；旧 MOV 不要求现代 ftyp 才进入解码器。使用文件注册表已授权原文件名；扩展名不代替真实解码校验。
2. RAW 持久化保留真实时间戳；保存转写读取与纪要事实读取共同要求已验证的媒体 profile，旧 TXT/Office 不接受伪造时间戳。工作稿仍保持原来源元数据。
3. 模型证明、原字节哈希、UTF16/段落/时长限额、取消、进程非零退出及输出完整性都经实际边界测试。
4. 二进制读取仍受同源、当前会话、严格大小、完整原哈希和撤权处理约束；源文件名复用既有有界 Content-Disposition 编码。

新版本的长时推理/内存/并发与完整预算资格尚需重新绑定验证；旧阶段的资格报告不自动覆盖新媒体版本。不做整页像素比对。
