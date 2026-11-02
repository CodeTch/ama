> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 处理进度、冻结文件与独立背景材料（2026-11-02）

当前批次正常部署源码 `bdaef9f565fc83fa6f0cd79ac4234a9c296432de04e4ba19fdfb827014f56db4`；main、单agent。兄弟harness固定8792/2525，仅作受控联调。完整Web552、type/API83/build/launcher20与正常部署通过，日志前缀 `.tools/local-app/bcd-completion/processing-speaker-qualified-`；完整Web日志 `processing-and-speaker-scope-web1.log`。这不是整个BCD完成声明。

实际CAS报告 `.tools/local-app/d08-page-matrix/processing-frozen-materials-cas-02-aligned/report.json` 七组全部通过：TXT/DOCX上传；服务端已受理202后丢响应、刷新保持同一key/body和关闭守卫；冻结任务与RAW回读；追加第三个文件后旧任务仍仅使用原两文件；独立背景材料上传/关闭/刷新，旧输入与RAW不变；真实held GET关闭/Escape守卫；三视口无横向溢出；另一真实CAS身份读取三个私有接口403且无材料窗口/原文。

该例会议 `10b4a344-8d75-4156-b8ef-f038bd507046`，任务 `9816a1b5-5d90-4354-9cee-0ea868ca6798`，输入 `021a5a7b-ca4c-44f2-9e1c-47727681e5d0`，RAW `7cbeb20c-bd8a-46bc-8822-89a1032b41a4`。没有业务SQL、重复受理或改写旧源。背景材料采用当前支持的DOCX；没有为测试新增TXT背景支持。

真实入口RED保留 `processing-material-entry-red`：旧按钮打开包含主会议文件的完整导入器。现新增MaterialOnlyDialog仅挂载真实MaterialImportSection，主导入器材料组件也只在窗口打开期间挂载，避免隐形控制器共享恢复记录。关闭守卫使用MaterialState.closeBlocked；读取失败的blocked不能困住用户，真实未决写继续保护。后台读取就绪前文件input与可见上传按钮一致禁用。窗口guard和idle failed read已有单元RED→GREEN。

原型精确对照 `.tools/local-app/d08-page-matrix/processing-pixel-03-aligned/report.json` 源码/部署一致、harness前后稳定，原型SHA不变，**严格像素结果仍FAIL**：1440/1024各视口与全页3726差异；390视口789、全页2926。保存完整原图/实现/diff，无掩码/容差。差异包括真实完成语义与原型固定“增强”文字、真实冻结任务提示与原型没有事实依据的材料V计数/本地模拟说明；实际InputDetail没有材料版本，空目录snapshot为null，不能投影常数V1或声称纯文本跑过ASR。此前pixel01错误参考V1与pixel02迭代原件均保留。本页业务通过，视觉语义取舍需在最终B/D差异台账明确裁定，不能把3726改写成0。

说话人侧栏保护同批部署：真实正文误撤销RED保留并已通过restorations恢复人工版本；新的current/parent精确比较只允许speaker变化，正文/标记/来源变化或版本不确定均不写。正式负例 `.tools/local-app/bcd-completion/speaker-undo-body-marks-01-aligned/report.json` PASS：两次明确人工编辑后侧栏均拒绝，段落及current ID保持，0新增业务写。首次body-marks-real-green因访问租约超时未进入编辑，0写，仍保留FAIL。新说话人正例及全部剩余页面继续执行。
