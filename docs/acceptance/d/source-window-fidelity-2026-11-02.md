> 当前仅验收1440×900、1024×768桌面；手机端全部计划和验收指标已移除，不转入F。原型和历史报告保持原样。

# 来源窗口、导航与原件播放

当前正式部署`0932989a416cce21ef1b7d32d4271eec26da3bbe830d29103d50d9dfbf96f4b5`。`source-window-qualified4-outcomes.json`完整Web581/0、type、API85字节一致、build、launcher20及正常部署均通过。后端没有在此窗口批次修改；生产891/0属于此前冻结信息投影的对应范围，不宣称重新执行。

源码包括`MinutesSourcePreview.vue`、`useMinutesSourceMedia.ts`及`MinutesWorkspace.vue`，复用原有`useSourceMedia`和真实转写二进制客户端。只有Source绑定的历史转写版本、精确段/文件、当前revision/accessRevision和sourceBytes资格都匹配，才读取精确历史原件。沿用受管长度/SHA及WAV格式验证与230400044有界录音上限，没有放大普通上传限额。无时间TXT只取真实名字，不创建播放URL/虚构00:00；材料仅在当前目录的文件、材料版本、root revision与Source匹配时显示真实文件名，匹配不到明确提示无法核实，不猜版本V1。

抽屉采用原型440px布局、原始背景色及共享drawer-h/drawer-b/card样式。默认显示真实原文件名、片段、可核实时间和说话人标签，原文件标题仍链接实际受保护文件；定位播放确实解码原字节。显式“审阅”入口保留D03/D05的全文/局部审阅、历史和行动负责人等实际功能，读取与关闭没有业务写入。关闭/切换审阅/actor/meeting/环节变化和403会清空私有来源、停播、撤销Blob；迟到回包不恢复旧内容，不丢弃不确定业务命令。

原版来源GET被后续审阅历史GET取消后，返回来源会错误显示“暂无关联来源”；真实`source-window-pending-review-red`0写复现，返回时重新读取精确目标后HMR02通过。原目录只有点击时更新高亮，1024真实原型source-pixel07产生12544差异；现按原型最大可见节更新、到顶首项，并仅在内容容器内滚动。

来源/候选两个真实隐藏modal缺陷分别有`remaining-window-route-source-02-diagnostic`和`candidate-window-late-close-red`。前者通过同一组件的浏览器历史返回转写遗留隐藏:modal，后者关闭父窗口后迟到候选GET再打开0×0隐藏modal。现在在原离开guard允许后仅关闭本会议拥有的窗口，候选父窗口/环节变化中止原读，并在接收及nextTick真正打开时再校验父窗口、环节和候选。全局治理/成功窗口、原请求及原草稿保护不被旁路。

## 已有正式业务证据与当前复验

`69cfd2e5...`正式批次：source-window-scope03五组PASS（实际AAC播放推进、审阅GET200、切审阅撤Blob、Escape/背景关闭、真实迟到metadata取消、另一标签logout204停播/清空、foreign CAS403），创建的4个实际Blob全部回收，0写/0pageerror；candidate-close03、candidate-stage01、source-route04均无隐藏modal/0写。template-pixel03桌面4图精确0；inventory03六组、10类窗口×两个桌面20截图/DOM，0写/0pageerror/0页面溢出。旧报告保持各自签名和范围。

`0932989a...`最终本窗口批次已完成：pending-review03、source-scope04五组、publication-success-visible-modal06、candidate-close04、candidate-stage02、source-route05、template-pixel04桌面4图0、inventory04六组/20图均PASS；publication-stage-activation02也PASS，初始minutes无隐藏发布/冻结/检查读取，进入真实确认后readiness200且ready=true、检查明细可读、干净顶部保存0命令、返回minutes不新增隐藏检查。原activation01只因DevTools丢失响应体而FAIL，保留并单列failure-assessment，改为真实in-page fetch读回后02通过，没有为此修改产品。serial batch2原exit1保留。原成功publication没有重提POST。

## 原型差异和读取竞争

不可变原型SHA-256仍是`ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。比较只投影真实API业务数据与已核实原件文件名，不改renderer/CSS、不遮罩、不放宽阈值。source-pixel03拍到加载态，无效；04头部几何和说话人投影问题、05/06/08保护读取失败全部保留。正式07的原始strict FAIL仍为1440各159、1024各12544；不能按进程退出0算像素通过。

HMR09已完成两个桌面×viewport/full四图，各159差异，包围盒分别为1440 `(1366,20)-(1389,32)`、1024 `(950,20)-(973,32)`，仅显式“审阅”入口文字；来源卡、抽屉几何、关闭按钮及其余背景精确一致。该额外入口是原型缺少而D03/D05要求的真实来源审阅入口，不能删除既有审阅业务来凑零。原strict报告仍为FAIL，不将159改记0。正式10已经在0932989a源码/部署对齐且harness前后稳定时确认四图各159；difference-attribution.json按实际DOM按钮矩形检查，四图矩形外差异全部0。原全图strict FAIL、差异图和原报告不变，归因不是mask或替换报告。

08的network-timings保留了readiness约2.33秒、settings约5.60秒、source约4.25秒、workspace约2.42秒及浏览器队列。隐藏发布面板在无原命令的minutes阶段仍发authorize/current/final/readiness，增加初始读取竞争；SSR用例实际RED为四次读取。现仅在进入确认/发布/回看、有已核实PUBLISHED观察或有经过校验的原命令时查询。`usePublication.restoreOriginal`只恢复本地原key/body，不赋予执行权限；execute仍需新的真实授权。新增四项行为用例和既有未知结果/已发布拒写/每个读取403清私有草稿等共13定向通过。真实已发布组件的SSR夹具已显式传入实际published环节，全部原拒绝断言保留；另补“观察已发布但环节尚未更新时仍查权威状态”的独立用例。完整Web首次579/580失败、修后581/0均保留，不以修改断言掩盖安全回归。

4500ms显示租约不变。新真实09在无原命令minutes阶段新增“不得请求readiness”断言并通过，不能将一次页面成功当独立性能基准。所有外部接口仍经兄弟harness固定8792/2525，其受控协议不是公司权威或真实ASR质量证明。

后续仍需完成其他编辑/候选/历史等窗口及逐控件总审查、最终桌面页面回归和BCD总账；本记录不是整个D阶段完成声明。
