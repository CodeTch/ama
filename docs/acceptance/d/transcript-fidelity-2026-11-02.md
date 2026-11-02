> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 转写工作台、语音审阅与原请求恢复（2026-11-02）

正常部署签名 `a9f9350e80fbaa5e6e69e1a41c615d0d4c59e50003836008fa6848c4b1c639fd`；main、单agent，外部均经兄弟固定8792/2525。当前兄弟受控能力不作为公司目录或模型质量权威。

## 修复

转写页原有高级标注/纠错整张表单常驻正文后方，另有重复历史入口。现在通过说话人侧卡及“查看会议状态与处理记录”中的“转写版本与语音审阅”打开原生窗口。所有原接口、版本历史、原始稿读取、原请求恢复、标注/合并/拆分/撤销仍可操作；仅打开只读窗口不写入。未知转写/纠错请求阻止关闭；actor/scope失效关闭窗口。恢复记录先于转写读取完成时，须同时等读取权限和当前内容就绪后自动打开，避免丢ACK后窗口隐藏。

侧卡只显示实际人工标签和片段计数，不造企业身份、部门或声纹匹配；新标签只用于后续版本/生成，tooltip明确既有确认与纪要保留。冷刷新时，标注和审阅入口分别等待真正的读取能力就绪，防止先点击后无反应。普通转写未标注名称显示“未知”，发布历史仍用原来的匿名标签。纠错状态来自实际当前pending数，未读取时不显示已核对。移动端原时间入口不再被旧样式隐藏；段落标记、图标和容器渲染按不可变原型对齐。

全量回归曾触发HTTP body取消失败。新增确定性用例证明：底层body在调用者取消后晚到成功，原transport可将其返回为成功。现在在fetch和完整body/204返回前复核signal。新16项transport回归及最终完整543项均通过；未通过的旧542报告保留，没有当全绿。

## 正式当前证据

- `.tools/local-app/bcd-completion/transcript-review-cas-02-aligned/report.json`：13组真实CAS/UI/API通过，新会议 `efa54b89-2f7c-463f-ae44-132f671b3c19`，双真实WAV→RAW/增强→人工工作版本→实际提案。包括待处理确认阻断、提交已落库后丢ACK/刷新恢复原key/body、窗口不能关闭、明确接受/立即撤销、旧接受回放不重新挂载、原提案回放、批量拒绝、标注/合并/改名/部分片段拆分/侧栏撤销、历史与RAW不可变、readiness来源图、三视口、foreign读取/写入403。
- `.tools/local-app/d08-page-matrix/transcript-pixel-08-aligned/report.json`：当前真实AAC会议 `3d40a666-d08e-43b8-bc2e-681e68a0da46`，1440×900、1024×768、390×844，各viewport/fullPage共6项精确零差异；sourceAligned/qualifying/harnessStable均true。原型renderer/CSS/SHA保持，参考仅接收真实保存内容/标记/文件字节且独立核对SHA；无mask/容差。
- 同一部署回归：`workspace-info-pixel-09-aligned` 抽屉6项0，`workspace-pixel-09-aligned`核心/正文12项0。`current-audio-pixel-03-aligned`两桌面核心/正文8项0；390原型自身溢出仍报告非零/整体pass=false，保持产品390可用，不重标通过。
- `.tools/local-app/bcd-completion/c30-large-source-browser-transcript-current/report.json`：30分钟57,600,044字节精确原件/15窗口RAW、真实metadata、processing无重复受理、完整下载SHA/解码/播放/跳转/变速、三视口、foreign拒绝6组通过，0业务写；不是重做1800秒容量。
- `transcript-fidelity-qualified-outcomes.json`：完整Web543/type/API83/build/launcher20/deploy全部退出0。Java生产885在本批之前通过，转写本批未改Java/SQL/契约，不宣称重新跑了885。最终只读浏览器批次五个命令退出0，详 `transcript-final-browser-outcomes.json`。

## 原失败及原请求接续

第一参考投影漏真实marks造成原型渲染异常，随后只补数据映射；所有图和失败保留。首真实新窗口测试暴露恢复窗口打开竞态；会议 `649e3f1f-5ef9-4008-b13b-61211c5003ef` 的接受实际已提交，后续使用原key/body查回，没有创建替代决定。`original-resume-01` 先回放/撤销保持历史，随后并发处理期间current GET503；其后JFR只读current/history均200，未改后端门禁或延长期限。实际原批量已在浏览器继续完成，`resume-02`错误续接等待已禁用按钮失败；`resume-03`先回读零pending再继续，无重复决定，其8个效果检查通过，但整例末尾冷入口过早点击失败仍保留。入口就绪修复后 `transcript-review-original-readback-01` 五组通过、原RAW和当前版本不变、独立foreign拒绝；最终新正式整例13组完整通过。

本批完成转写工作台收口，不代表所有B/D页面都已验收。下一批继续processing、录音、最终确认/发布及人员/模板/来源等剩余窗口的统一原型和控件核对。


2026-11-02同阶段作用域收口：bdaef9f5正常部署上新鲜转写正例 `transcript-review-cas-04-aligned/report.json` 13组PASS，fixture3555978c-2504-4891-a047-577c948f8588；正文/标记保护负例 `speaker-undo-body-marks-01-aligned/report.json` PASS且两次均0新增写。正例03已接受实际第二段，但脚本错误断言第一段必须变化；原FAIL保留，04只改断言为真实一段接受/另一段不变，不改产品顺序或后端结果。当前人员窗口批次另补已恢复成功后清除过期“结果尚未确认”的关闭提示，组件实际RED→GREEN；尚待新部署。
