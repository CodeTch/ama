> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 发布目录、冻结预览与原任务恢复

本报告记录D08当前发布范围，BCD总体仍未签收。产品外部请求均经兄弟项目固定HTTP8792/SMTP2525；本地受控签名知识库、测试模型和CAS不是公司真实接口权威。兄弟代码/配置摘要保持03b73a43899a19d53f502a1df3099f7abf2685ba931668e9036a5f4438de8f8a，HEAD8d5eeeda04d833ee986b59ea2d3ba1ab8eacd5fa。

## 已正常部署的目录/预览范围

源码与部署5dd1aff56c43d0e9edb0ea9e48eaa0a33753d7372b49c0ca7158c662a046b6ce。其前端源文件全量568/0、生产Java891/0、type/API85字节一致/build/launcher20/正常deploy通过。构建生成dist入口导致source_signature从df69acabb75f42692b812ab1ef878b16ab2fa17d3ef5e33fdef761a1ea0ce250变为上述部署值；未在两者间改业务源文件。最后成功提示修复另在验证/部署，不能把此范围冒充已包含该修复。

- 将库和其文件夹合并为原型的联动位置区；真实稳定ID、层级完整路径、逐分支分页/展开/收起/选择。切库立即清除旧文件夹；旧响应不能写入新目录。
- 搜索只筛选当前真实已加载的库；未读全时明确显示“已加载”，不宣称全目录搜索。加载失败可重试，不能把错误当空目录或提交未经核实的位置。
- 选择草稿按actor/meeting/tab私有命名空间保存，刷新先重新读取真实库和每层文件夹页再接受，不能直接信任缓存名称/ID。顶部保存明确显示“发布位置已保存在此标签页”，不伪称服务端配置已保存，不修改会议信息、名单、最终确认或权限。身份/会议切换与退出清理私有位置。
- 预览正文只取真实最终确认；发布页标题只读。新增FinalDetail.information，从已保护和受预算约束的原图按最终确认的info_revision_id、roster_revision_id选择精确历史信息/名单，不取当前根的文本补齐。客户端重验两ID与final一致；冻结信息/名单缺失时不打开空预览。3个新增Java用例覆盖历史与当前不同、缺失/重复拒绝、未确认名单拒绝；该类合计5/0。
- 预览展示确切冻结标题/正文、会议日期/摘要/名单、确认编号和所选目标路径。关闭/X/Escape仅影响显示，不生成命令；源/权限失效清空内容。

正式真实CAS：`.tools/local-app/d08-page-matrix/publication-directory-preview-03-aligned/report.json` **8组PASS**。明确自有模拟器夹具publication-directory-owned-fixture.json增加一个库、25个子文件夹和一个嵌套文件夹及显式owner/member grants；只设置模拟器数据，未写AMA业务表。产品验证均通过8792透明桥和签名provider接口。证明真正20+5分页、子目录选择、所有关闭控件/三视口、刷新跨页再核验、真实200回包丢失后失败/禁发/重试、持有旧回包后切库不复活、搜索清除不暗改选择、实际member/foreign目录403。0业务写入。

旧03-development真实暴露恢复资格晚于final时不触发目录加载，修复watch资格与锁状态后正式通过；旧目录preview01在并行重测试时4500ms访问续查到期，页面安全清空，原失败保留；单独02和正常部署03均通过。没有放宽4500ms期限。

## 严格原型比较

不可变原型SHA ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867，renderer/CSS未修改。publication-pixel-02-aligned为真实基线，6图差异447265/661825/185758/506392/27640/410076。05迭代后卡片、目录和固定底栏几何一致。

正式`publication-pixel-07-aligned-attribution/report.json` source/deploy/harness稳定，1440 viewport/full各9260，1024各9393，390 viewport0/full8267；**严格整图仍FAIL**。独立difference-attribution.json证明所有非零像素都在真实权限说明banner和证据完整性第二行，区域外全部0。诊断归因不改变原图、失败报告或门槛，不是mask通过。

保留这些文字差异有明确产品依据：正式文档继承目标库权限，原录音/转写/引用材料仍分别鉴权；原型权限说明容易混淆源访问，不复制该承诺。原型静态检查因测试行动无负责人/截止日期显示“存在待处理项”，实际无截止信息不强制补日期；已完成冻结源/人工审阅核验时显示“冻结版本与来源已核验”，不伪称“重要结论100%有证据”。所用审阅是明确受控测试断言，不证明模型语义质量。

## 已实际提交的唯一发布，严禁再提交

会议c2096a1c-a1d0-487a-90ac-3392eff423d7；最终确认ebad7ddd-011c-42d4-9024-19cd2f7a6d66；publication d93471e8-e1f5-4757-9a54-2d56b6644701；原task 064cf079-5062-4a21-97a9-c9b15d57d28b。当前**SUCCEEDED/SUCCESS/APPLIED**，不再是未发布夹具。

`publication-submit-unknown-01-aligned/`保留实际202回执、原key/body、刷新前私有journal、SUCCEEDED投影、正式文档HTML与截图、原wire及stderr。实际4组已经完成：202提交后丢浏览器ACK；刷新只查原任务至成功，只有1次发布POST；成功后当前revision的info/roster写均409且冻结内容/信息不变；负责人/参会人正式文档字节一致、foreign读取与发布403。

整例**未通过**：末尾回看UI受不可见成功modal阻挡；探针catch还误对JSON.stringify结果调用.catch，未写出原最终异常report。failure-assessment.json如实登记两层失败及已完成事实，不补造PASS。新只读`publication-success-hidden-modal-red/report.json`重现0写入的真实缺陷：native :modal=true，隐藏Publication祖先，宽高0，仍阻挡已发布页。后续只恢复原journal读取，不重提发布。

修复将成功modal Teleport到body，沿用原型480px成功窗口；scope清理关闭modal并重置submitted/successShown，恢复后未知位置显示真实发布编号而不是编造名称。组件RED→GREEN；`publication-success-visible-modal-green-01-iteration`实际原journal恢复后窗口可见、可关闭并切换正文，0业务写入。此迭代尚非正常部署资格；完整569及新正常部署/跨标签退出清理/三视口正式接续正在执行。

## 成功窗口正式接续（已通过）

成功窗口正常部署源码9f1e28b5cef97f9bd373870b6e71ebf26e296755bd92c8901f0febd6579c9719。完整Web569/0、type/API85/build/launcher20/正常deploy均0；Java未在Teleport修复中改动，沿用上述实际891/0的后端范围，不伪称新跑Java。

`publication-success-visible-modal-05-aligned/report.json` PASS/sourceAligned=true，0业务写。使用原202提交前保存的私有journal在真实浏览器reload，只读取已经成功的原publication，恢复后成功窗口native modal确实位于body且宽高非0；“知道了”可关闭，AI纪要正文可操作，1440/1024/390均无页面横向溢出。实际同cookie另一个标签POST logout204后，第一标签弹窗/正文均被清空；匿名时原不确定命令仍原样保留以供同身份恢复；另一标签实际CAS改登录dev-viewer后，原标签通过“重新检查登录”刷新身份，在相同tab命名空间清理旧actor journal，外人看不到原正文。

02-aligned只因Playwright convenience newPage拥有的上下文不能再newPage而FAIL；03错误要求匿名时删除不确定命令，与原请求恢复协议冲突；04通过整页导航产生新tab命名空间后错误检查全storage清理。这些探针失败均保留，没有为断言改变产品协议。05使用真正原标签、真实另一标签CAS身份变化、产品“重新检查登录”入口验证了适用隔离边界。成功modal已经部署修复，原发布整例/错误序列化没有被重标为PASS，业务成果只读接续保持，不再发布。
