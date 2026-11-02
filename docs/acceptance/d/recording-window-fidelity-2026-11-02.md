> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 录音工作台、确认窗口与身份失效（2026-11-02）

本批正式部署源码 `bbb45d8df63bfacdf67e5b7335482c412b968e38deb92c35fd191e38932a7b51`；main、单agent。兄弟HTTP8792/SMTP2525固定，代码配置摘要 `03b73a43899a19d53f502a1df3099f7abf2685ba931668e9036a5f4438de8f8a`，只是受控联调提供方。真实模型语义质量不在这份截图与协议证据中签收。

新LiveTranscript录音呈现使用实际partId/顺序/样本范围：累计时间转换段内时间、按真实录音段分组、匿名标签、最新revision去重；稳定/临时状态保留在可访问名称及时间提示中，积压时仍显示恢复入口。默认通用组件保留。原“回到最新”同时读取当前游标后的真实字幕，持续恢复不依赖伪造文字。录音页恢复原型的左侧返回、面包屑和DEV声明；保存范围、未确认缓存与服务端保存说明使用真实事实，不照抄原型“未接入服务端保存”。

中断/结束窗口复用原生dialog负责模态与Escape、内层原型mask/modal的布局。取消不写finish；真实写入未确认时关闭、Escape和离开保护；身份/会议变化清理私有窗口，迟到finish不能导航到另一身份。成功finish确认后允许自身正常跳转，开始录音的新会议路由不被保存中的导航守卫误拦。

## 正式业务证据

`.tools/local-app/d08-page-matrix/recording-window-pixel-06-aligned/report.json` 的businessPassed/qualifying均true：真实CAS、Chromium公开JFK假设备、AudioWorklet/WS/原始PCM，无用户麦克风；5个分片、284212字节PCM。暂停、两个窗口取消、三视口/全页共18组拍摄；所有取消阶段0个finish。保存时真正的服务端finish200被延迟交付，关闭X禁用、Escape仍保留窗口；释放原响应后仅1个finish、真实归档可读，WAV载荷SHA与实际发送PCM逐字节一致。会议 `fc6c3d1b-dfbb-4604-80ba-278f680a1be9`，session `b97d7d4f-f7f5-4544-9ed6-cb343562ae6d`，目前保留完整原件。

`.tools/local-app/bcd-completion/recording-scope-loss-02-aligned/report.json` 正式PASS、sourceAligned=true：另一标签页实际撤销当前AMA会话，在服务端已提交finish而浏览器尚未收响应时，原窗口和字幕清空；迟到成功不导航、不重发。重新CAS登录后原manifest.finished=true、part清单不变，195790字节PCM与原WS分片SHA一致。会议 `ffe4fa46-fc1e-4180-a8a3-d94a284f36f9`，session `c1b15b5f-50ad-4a3a-a8d5-55a5eeca78df`。这是实际会话撤销，非stub401或模拟空状态。

完整Web561/0、type/API83/build/launcher20及正常部署全部退出0，日志前缀recording-window-qualified；后端产品代码未在本UI批次修改，沿用同一Java源码的目录批次888/0，不声称重跑888。新录音分组呈现有真实RED→GREEN；scope组件用例显式执行真实生命周期清理回调并验证迟到不导航（SSR结束后不能假设watch仍存活），真正自动身份清理由上述浏览器跨标签会话撤销证明。初次SSR自动watch断言失效的日志保留，不把测试夹具限制称为产品故障。

## 原型与失败记录

正式18项完整原图/实现/diff均保留；严格整图pass=false，**没有宣称录音18项零差异**。主要剩余包括真实服务端保存/缓存与原型本地模拟说明、字幕来源状态的文案，中断提示如实保留未确认分片而不声称已生成独立文件。页面及全页高度已对齐；手机底栏/文字自然换行已修，不通过裁剪或掩码消差。原型SHA不改、精确阈值0、maskedRegions=[]。

- 01误用原型路由 `/recording/{id}`，原件5块已ACK；正确原型路径为`/meeting/{id}/record`。该例失败保持。原协议在浏览器退出后接管writer并finish成功；工具HTTP客户端读取Secure本机会话Cookie得到401，改由真实Chrome会话读取。`recording-window-pixel-01-readonly-finish` 只读通过，独立只读SQL元数据+5个物理对象逐SHA验证，285698字节原PCM与归档一致。
- 02已拍18张真实RED图，但保存后未等待finish ACK即查询archive404；旧失败保持。只读后续确认原finish已完成，不重新提交；`recording-window-pixel-02-readonly-finish` 独立5块物理SHA及292384字节归档完整。脚本现先等原finish200，再读归档，二进制通过真实浏览器会话读取。
- 03/04/05迭代均真实录音、归档字节验证通过；页面、弹窗、移动排版差异逐步修复，原非零diff均保留。

此批不重复已通过的C10/50/1800容量；C正式30分钟原件及D07已删除native06的各自状态保持。后续仍须确认/发布、剩余扩展窗口与全控件矩阵收口，不能据本批宣布全部BCD通过。


## 当前源码完整原生链复验

9f1e28b5cef97f9bd373870b6e71ebf26e296755bd92c8901f0febd6579c9719正常部署和工作源码一致；`.tools/local-app/recording-native-cas/bcd-final-native-08-aligned/report.json` **16组PASS**，API实际SIGKILL/自有环境恢复6420.961944ms。meeting e8a2e600-8cb3-4325-bc86-f6b39dce80a7，session de4a99be-fbd2-4f79-903f-c0f437b3b1ef，archive task89bff1d0-2f9e-439e-a3a5-230d9cfccd42，447057样本/894158字节WAV，SHA145c53685c5504fef47b19181bfe5218a4bc153b39825335b1c1c3fe22594d1a。每原生Worklet帧的长度/顺序/SHA与归档一致；原字节缓存补传、刷新不自动打开设备、暂停/后台站内路由/新分段、finish等待、独立受管copy→真实RAW、丢intake ACK刷新同key/body单任务、完整字幕封尾刷新和三视口、第二真实CAS403全部通过。Chrome模拟麦克风，不声称物理设备或真实模型语义准确度。

原native07因探针还有旧稳定字幕CSS谓词而FAIL，实际UI已有字幕/11获ACK分片。修的只有.tools探针选择器，未改产品判定或伪造字幕。其自有meeting44888400-8d29-4381-8a18-9be385f1ce1f/sessionf8127ce5-0816-421b-9e30-b3714aadb02d已一次真实接管/finish并读取704044字节WAV，全部11原帧SHA一致；独立只读MySQL元数据及11个受管物理对象704000 PCM完全一致（native07-owned-finish/physical-original-proof.json）。不把07重标通过、不再重新finish这个已结束夹具。
