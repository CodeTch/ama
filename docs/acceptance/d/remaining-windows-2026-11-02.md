> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 剩余窗口基线与实际缺陷

窗口基线source/deploy为9f1e28b5...9719。新的自有meeting97af141a-3b9e-436c-a136-b2dfad3d1188通过真实CAS/AAC→RAW/人工→真实受控生成/显式审阅，尚未最终确认/发布。factory ready01 PASS，task679ca716-5560-4cb3-9a84-bd7396a79843，input2281798e-0cde-4577-9c25-324b2a6b2d46。不要编辑固定canonical3d40a666。

`.tools/local-app/d08-page-matrix/remaining-windows-inventory-02-aligned/report.json`六组PASS、30张窗口截图/DOM（source-review/action-owner/template-create/template-library/template-edit/template-delete/generation-full/generation-region/versions-tools/candidate-compare，每类三视口）。4个真实个人模板、1个已保存历史和1个已采用候选；目录人员待保存只是本地选择，取消模板后重开空草稿；原型之外功能还需对照裁定。body/rootrevision保持，0业务写入，0pageerror，0页面横向溢出。第一例错误取了全页首个来源而不是ACTION稳定ID导致选择器失败，保持FAIL；02改用实际目标element ID并等真实审阅HTTP200。

真实SPA跨环节RED：remaining-window-route-source-02-diagnostic通过产品进度条从转写进纪要、开来源、浏览器返回转写，遗留:modal=true/hiddenAncestor=true/宽高0，0写入。MeetingDetail新增本会议DOM ref，在所有原离开guard允许后关闭自身dialog[open]，不触全局或绕过guard；HMR03已实际GREEN。尚未新正常部署，不能按9f签名冒充已包含修复。

原型新增总结模板：template-window-pixel-01-iteration桌面4图0、手机2图33114；actual modal342px vs原358.796875，是native罩padding24造成。仅模板外罩padding0后template-window-pixel-02-iteration三视口viewport/full6图严格0，无mask/threshold/原型renderer改变。仍是HMR，需最终正常部署新目录证据。

来源原型完整三视口对比正在进行。默认原型440px/max92vw，而actual560px/fullvw，实际额外审阅/负责人需要保留；源播放、精确名字与原件回读不能虚构。后续方案见docs/superpowers/plans/2026-11-02-source-window-fidelity.md。所有窗口/按钮总体仍OPEN，不把静态700控件或30张截图当原型通过。

2026-11-02当前来源/导航批次：正常部署69cfd2e5154035aef4ddc041de4135e09287a37b6b011de0c417d1fef95c1c8e，完整Web577/0、type/API85/build/launcher20/deploy均0（source-window-qualified2-*）。source-window-scope-03-aligned已正式五组PASS，0业务写/0pageerror，四个实际创建Blob在切换/关闭/真实退出后全部撤销；包括实际审阅GET200、Escape/背景关闭、关闭后的真实迟到metadata、跨标签logout204、新foreign CAS来源403。原01失败与HMR02不重标。

新增真实候选关闭RED：candidate-window-late-close-red实际原候选GET200响应由route.fetch持有，关闭父“候选与版本”后才放行；旧实现出现:modal=true、hiddenAncestor=true、宽高0（0写入）。现为父关闭/环节变化中止并作废读取，read accept和nextTick打开时都核对当前父窗口/环节/候选；HMR02已同探针GREEN/0写。正常部署后的候选关闭/SPA返回、来源SPA返回、两个桌面原型图及窗口基线正在串行复验，不把完整单元测试当窗口验收。

正常部署69cfd2全部窗口批次结果：candidate-close03、candidate-stage01、source-route04均PASS/0写/无隐藏modal；template-window-pixel03两个桌面4图严格0；remaining-windows-inventory03六组、10类×两个桌面20截图/DOM全PASS，0写/0pageerror/0溢出。source-window-pixel07为正式qualified原始strict FAIL：1440两图各159，仅新增审阅入口；1024两图各12544，另有原型随滚动更新目录而实现不更新的真实差异，仍待修，不能仅按正常退出码0签收。

后续已取得另一实际RED：source-window-pending-review-red在原来源GET真实200响应被持有时，用户先切审阅并GET200读取历史，旧来源请求被后一个纯读取取消；返回来源时旧实现错误显示“暂无关联来源”（0写/0pageerror）。返回来源时若未持有已核实来源则重新读取精确当前目标，HMR02已PASS。新增桌面目录滚动同步按不可变原型amSyncDirectory：最大可见节高亮、到顶首项、滚动容器内定位和目录焦点可见；正式12544差异是其RED。上述两个修复尚待下一次正常部署，不能混入69cfd2资格。
