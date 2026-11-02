> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# C 当前并发与30分钟长文件验证

本项协议/延迟/完整性验收通过。外部ASR等全部经兄弟项目固定HTTP8792/SMTP2525；这是受控模拟器联调，不代表公司接口权威或真实模型语义质量。C其他义务仍以阶段汇总为准，本报告不自动提升B/D状态。

用户最终标准为10场同时录音、50名真实CAS在线（10录音+40其他）、连续1800秒；当前长文件30分钟。F保留50/200与两小时测试，未声称已执行。

| 项目 | 实际结果 |
| --- | --- |
| 正式目录 | `.tools/local-app/recording-load/c-10-50-1800s-qualification-01/` |
| 报告/进程/冻结一致性 | report.passed=true、reasons=[]；owned runner loadExitCode=0、harness unchanged=true |
| 录音/在线/最短实际持续时间 | 10 / 50 / 1800.004685531秒 |
| 原始分片 | 9000发送/9000核验，10份归档完整逐帧SHA与字节回读 |
| 缺ACK/业务/瞬态/后台错误 | 0 / 0 / 0 / 0 |
| ACK p95 | 529.81074795毫秒 |
| temporary字幕 | 6000事件，实际客户端收到时间减来源结束时间，p95=2012.78002785毫秒（门槛3000） |
| stable字幕 | 3000事件，相同口径p95=2405.82079605毫秒（门槛5000） |
| 会后独立RAW | 完整30分钟归档→独立TRANSCRIPT_INPUT受管副本→真实intake/worker→RAW；15个120秒窗口/75checkpoint/1800000毫秒 |
| 资源观测 | 412样本，所拥有AMA进程与load客户端峰值RSS1975537664字节、CPU296.87%；不含MySQL容器与内核缓存 |
| AMA冻结签名 | d9afb285d652d0ccc4d0c91a0282feacf15f735aa614e1081159e089d3c023bd（开始/结束一致） |
| harness HEAD | 8d5eeeda04d833ee986b59ea2d3ba1ab8eacd5fa |
| harness代码/配置摘要 | 2e444177033f1b2abed6f50f56d17979f2df4853eaf498d689dffb41347e6c20（开始/结束一致） |

正式命令/场景为 `load realtime-10-30m`，场景 `tools/load/scenarios/realtime-10-30m.json`；实际owned runner脚本 `.tools/local-app/bcd-completion/owned_final_c_runner.py` 和退出证据 `c-10-50-1800s-qualification-01-exit.json` 保留。原始observations/events/资源采样、逐用户分片与归档证明均在正式目录，未删样本或修改结果。

此前30秒resume01实际FAIL：temporary p95=3671毫秒，全部原帧/归档/后续RAW完整；启动时worker probe碰到文件服务重启导致退出。修复真实listener就绪/自有进程存活门禁与资源采样错误传播后，resume02实际PASS（temporary1918、stable2869毫秒），再开始正式1800秒资格。冷启动失败保留，不合并成正式成功。

容量后前端消费者修复以新签名4017d93cb12cc05ce71450a3c2634c68f83273cb79cb448858c6398b76ff9f7e正常部署。`.tools/local-app/bcd-completion/c30-large-source-browser-current/report.json`六组PASS：实际输入元数据、15窗/任务来源/SHA、完整57600044字节WAV浏览器解码1800秒与播放/定位/倍速、1440/1024/390视口、外人拒绝且0业务写。仅只读元数据和规范受管录音来源读取采用既有有界230400044上限，普通公开上传仍32MiB。

原生独立恢复 `.tools/local-app/bcd-completion/recording-archive-transcription-native-cas-06/report.json`16组PASS：真实Worklet/WS、API SIGKILL后6573毫秒原PCM恢复、刷新新增part、暂停/跨路由、归档及独立copy→RAW、intake已受理丢响应后刷新同key/body重放202、整份WAV逐帧SHA、字幕DONE/刷新/三视口与外人403。恢复测试不替代正式连续录音；原native05探针跨导航采集失败及只读补证仍按原报告保留。
