> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 会议信息、真实目录与名单草稿验收（2026-11-02）

本批正常部署与正式证据源码均为 `8e50225c79dbb49f64f2d47937e5571500a1fc157d4e1e494ac26da6f1f8b2cd`。主项目 main/单agent，兄弟 master；外部目录、CAS、知识库和模型均使用固定 `http://127.0.0.1:8792`，SMTP2525不变。兄弟是受控被测实现，不是公司目录权威。

## 修复与边界

本地开发 DirectoryPort 原先只查五个固定SQL身份并统一返回“本地开发”，没有经过兄弟接口。现改为显式启用的 ControlledDirectoryAdapter；严格固定origin、禁重定向、总时限≤3秒、1MiB响应上限、严格JSON/重复字段/分页核验，服务失败不伪造空目录。正常生产未配置的CompanyDirectoryAdapter仍明确不可用。兄弟实际请求暴露稳定ID不能搜索、用户输入%/_被当通配符、active输出整数三处缺口；最小修正保留原实现其他并行修改。admin API只创建缺失的五个明确dev CAS目录夹具及AMA受控联调部门，不覆盖已有记录，不从模拟目录自动创建AMA身份/授权。

“从发言人获取”只读取当前已保存转写的人工speaker快照；显式enterpriseId需目录精确匹配且ACTIVE，禁止按姓名或部门推断。无enterpriseId人工标签仅作外部显示文本。读取后复核当前转写ID和会议版本；读取期间人工草稿变化、actor/meeting/关闭作用域变化、读失败均不部分应用。关闭抽屉使eligible失效，迟到结果不能复活旧草稿。

部门按钮仅筛选实际读取的当前人员页，tooltip明确范围；搜索和分页仍真实请求目录。人员窗口有独立待选状态，取消不修改抽屉草稿；外部姓名与机构必须明确填写，以现有externalRosterNames的显示文本保存，不冒充独立已核验机构字段。保存名单草稿不授予权限，明确确认才授权或撤权。AI建议保留真实来源、版本、dirty保护和原请求恢复，以字段内联显示；没有建议不造候选。

## 正式证据

- `.tools/local-app/d08-page-matrix/workspace-info-pixel-08-aligned/report.json`：1440×900、1024×768、390×844，各视口/全页共6项精确零差异。真实当前会议 `3d40a666-d08e-43b8-bc2e-681e68a0da46` 的信息/名单/目录/建议只投影到原型数据；原型renderer/CSS和SHA不改，无mask/容差。sourceAligned、qualifying、harnessStable全部true。
- `.tools/local-app/d08-page-matrix/info-roster-cas-02-aligned/report.json`：真实AAC导入/RAW/WORKING、人工speaker原键回放、关闭时hold读取不复活、企业身份草稿取消、真实部门筛选/外部机构验证与取消、显式保存与确认授权、重新确认撤权、外部人工标签不猜身份，共8组通过。新会议 `6b97b7ed-b24c-44c6-bab2-ed1d393c90e5`，原请求保存在该输出目录的受限private-originals.json；没有业务SQL写入。
- `.tools/local-app/d08-page-matrix/workspace-pixel-08-aligned/report.json`：前一核心/正文工作台三视口/全页12项精确零差异，正式签名与兄弟前后一致。
- Java生产模块885/0；Web541/0；launcher20/0；兄弟67/0；type/API83/build/deploy均退出0。日志前缀 `.tools/local-app/bcd-completion/info-roster-`，逐步结果 `info-roster-qualified-outcomes.json`。Java缺失自猜Maven目录的第一次命令退出127保留，实际mvnw完整结果为production-java2.log，不将127写成产品测试失败或通过。

## 失败保留及剩余范围

最初信息抽屉6项非零，后续迭代和Vue动态slot编译/旧文案断言失败均保留，没有重标通过。第一次正式并行截图 `workspace-info-pixel-07-aligned` 在初始内容等待期间触发权限显示lease超时，页面如实关闭内容且提供重新读取，未拍到内容；其失败报告不改。随后串行正式08同一部署通过。该事实不等于并行编辑性能专项通过，也没有延长4500ms显示期限。

新AST审计06为491个button节点/676个interactive节点，其中正常路由549个interactive节点；只是映射，不等于成功点击数。人员窗口的真实搜索按钮和显示范围与原型静态全目录数据语义不同，不能拿信息抽屉6项零差异冒充人员窗口所有状态零差异。B/D仍须完成剩余处理/转写/录音/确认/发布等页面及控件统一验收；BCD总状态继续未签收。


后续部门窗口已由[完整目录与窗口验收](directory-window-2026-11-02.md)替代上述“仅当前页部门”实现：真实全目录/精确远程过滤、关闭迟到与跨页待选4组，加原名单8组全部PASS；窗口原始非零与逐像素归因见新文档，抽屉/工作台当前fd41d301回归保持零。原记录不重写为当时已支持全目录。
