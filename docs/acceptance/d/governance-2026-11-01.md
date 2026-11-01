# D-05 撤回治理联调（2026-11-01）

范围：当前兄弟项目固定 8792 接口上的受控协议验证；不是公司真实权威或真实模型质量验收。主库/文件服务为真实本地实例。所有失败证据保留。

通过的实际部署签名：`4f46a028c51c55ae6b5b6b2f2fc4d47b7abfd0856dabebd89efccbf324c7dc5c`。

证据：`.tools/local-app/governance/cas-05/report.json`，8 组 PASS，页面 JS errors 为空。额外 `.tools/local-app/governance/resume-cas-03/report.json` 5 组 PASS，沿用 cas-04 原已提交撤回任务。

- 真实 CAS 登录；dev-admin 具有平台管理员角色，仍不能普通读取无授权会议/材料/文件。
- 新消费者 `203094a4-12de-40a6-bb0e-1def7d6f2d29` 通过真实批量接口引用已发布来源，上传并解析 25,168,708 字节 PDF。
- 撤回来源 `e8824826-5e77-4029-b94d-28fdaa42e4c8`，原治理任务 `e339c175-e627-4150-b8b2-2f98655e555f`。外部暂时 503 时本地和引用方已拒绝内容；页面遮蔽耗时 412 ms（cas-04 为 2283 ms）。
- 在真实 HTTP 下载被消费者背压暂停时撤回，恢复读取后仅收到 3,276,783 字节并 aborted，未完成原文件；没有宣称能收回已下载数据。
- 浏览器丢失 202 后刷新原键/原正文恢复；同键修改原因 409。兄弟接口 `bridge_forward_drop` 确实转发原命令至受控 provider、丢失成功响应，后台用原命令键查回 SUCCEEDED。
- 同一真实签名回执重复投递均 204；签名篡改 400；旧 ACTIVE 观察不能当终态回调复活对象。
- 先前 MD/PDF/DOCX 的导出下载入口与各 fileId 均 403。
- 外部直接废止 `db0dbb33-962f-469a-bb86-074f9f28bd35` 且不发送回调，由定期对账使普通读取拒绝。
- 治理弹窗保留 1440/1024/390 截图。截图本身不构成全产品像素一致结论。

联调修复：导出任务 MINUTES_EXPORT_V1 原先导致已发布工作区 503，已接入任务来源关系和严格站内状态路径；治理控制器原先漏掉异常映射，真实冲突落到 /error 被误报 403，现复用 PublicationErrors 返回原 409/400。相应 RED/GREEN 日志均在 `.tools/local-app/continue-export-workspace-*` 与 `continue-governance-error-wire-*`。

cas-01 在导出任务工作区加载失败，未提交撤回；cas-02/03 在测试脚本过滤 Secure cookie 的长流登录失败，未提交撤回；cas-04 已提交并验证失效/中断，但冲突状态码失败，后续 resume-cas-03 接续完成。不得将失败报告覆盖为 PASS。
