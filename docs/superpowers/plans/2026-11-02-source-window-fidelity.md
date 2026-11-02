> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 来源窗口与审阅入口对齐

原10类窗口/三视口30图的只读真实CAS基线remaining-windows-inventory-02-aligned通过，0写入；fresh fixture97af141a-3b9e-436c-a136-b2dfad3d1188仍未冻结/未发布。原canonical3d40a666仅用于只读原型比较。

已真实修的两个前置：从transcript经真实进度条进入minutes、开来源窗、浏览器history back到transcript后原native modal宽高0/inert仍阻挡；source-route-02-diagnostic实际RED，MeetingDetail在所有离开guard允许后关闭仅本会议DOM内dialog[open]，HMR03 GREEN/0写入。不能关闭全局治理窗口，也不能绕过既有待保存/未知请求guard。新建总结模板三视口原型对比桌面4图0、手机33114差异是native外罩24pxpadding挤窄了原型92vw，模板罩padding0后HMR02六图严格0。两修尚待后续最终正常部署。

当前source_window_reference_compare.mjs只读验证真实source字节长度/SHA与Content-Disposition名字后投影原型文件名；原SHA固定ab21d0...867，不改renderer/CSS。01保留两张1440真实RED且关闭选择器旧错；02已按旧口径完成对照（原型关闭正确[data-close]），后续仅保留桌面两视口要求。原源drawer宽440/max92vw，当前实际560/fullvw；原框颜色.35与实际.33也不同，不能只改文案而不处理几何。原框有真实定位播放入口，当前只给原文件下载，需实际RED后补复用真实历史media读取。

选择方案：原生全屏drawer-mask为外罩、内层原型drawer/drawer-h/drawer-b，默认来源卡使用真实文件名/片段/时间/说话人和可验证播放。头部明确“审阅”入口切换真实审阅/行动负责人面板；保留后端要求的全文/局部审阅、历史、企业负责人、日期和转换等功能，不能删除来凑像素。新增审阅入口若造成原型没有的少量像素，须明确原strict FAIL与具体必要差异，不能伪装0。原件标题保留真实原件入口，不造语义100%。

媒体应复用useSourceMedia/createTranscriptsApi.source的长度/SHA/编码/actor/revision校验，先读取Source.sourceVersion的真实历史TranscriptDetail及精确file/segment，只有有真实时间且真实可播放来源才显示定位播放，不为纯TXT造00:00。关闭/源切换/actor/meeting/stage/403必须立即停播并撤销Blob，迟到读回不得复活；采用原current READ/文件权限，不将身份映射当授权。材料无文件名时保留真实未知，不猜版本V1或文件名。具体实现前先实际缺播放RED和scope/字节行为测试。

Source父read也需在关闭/换target时同步取消并清source/review选择；原read全局epoch不能让关闭后的旧回包进入另一个target。原生close事件要避免旧close事件关闭新reopen。原不确定业务命令仍独立保护，不因关闭纯读取窗口丢key/body。来源查询失败可重试但不是空/默认支持。再真实AAC源定位播放/纯TXT/撤权/关闭迟到/两个桌面视口和相应正常部署后的对照；更多模板/候选/生成/敏感/材料/编辑窗口按总窗口计划继续。

2026-11-02实施进展：新增MinutesSourcePreview和useMinutesSourceMedia，8个作用域/历史关联/材料名称行为测试通过，完整Web577/0。真实AAC播放02和source-scope02五组已HMR通过；原件完整校验、切换审阅停播/撤销Blob、三种关闭、迟到读取、实际跨标签logout204和foreign CAS403均0业务写。来源独立读取控制器与epoch不再干扰候选或业务命令。pixel03读取中无效、04/05发现header/数据投影问题；05已完成1440两张原始差异244像素仅头部审阅/关闭，第二视口权限租约核验超时导致整例FAIL。06初始标签读取期间同样发生权限清空，保留。后续正式部署/浏览器串行并保存network timings，不延长4500ms租约，不重标这些失败。

扩展检查：候选检查GET返回后会nextTick打开嵌套native弹窗，需实际验证父“候选与版本”已关闭或会议环节已切换时迟到读回不得重新打开隐藏modal；已备真实route.fetch持有响应探针，先取得RED再修复，不能仅靠关闭当前dialog[open]声称覆盖所有迟到读取。
