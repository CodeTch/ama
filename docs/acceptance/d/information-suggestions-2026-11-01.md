# D02 会议信息建议验收（2026-11-01）

通过固定端口兄弟 harness（HTTP 8792 / SMTP 2525）进行真实 CAS、浏览器及应用队列联调；受控模型输出用于协议验证，不作为公司模型质量依据。单 agent；AMA main；没有切分支、worktree 或提交。

源码/运行签名：`a9364664a8a3abafda79a778871aa52089e7bfb1482cc90dc6217f318ab86067`。主库迁移 V053。

- `.tools/local-app/info-suggestions/cas-04/report.json` 7 组 PASS：实际上传 TXT → RAW → 确认 → 模型异步任务 → 来源绑定 NAME / SUMMARY；无自动写信息；名单草稿不授予访问；拒绝原键重放；旧版本 409；未保存人工值保留；采纳丢响应后刷新并原键/原正文重试，单字段改变且人工摘要/日期/名单/权限不变。
- cas-04 截图取于原请求刷新完成之前，390 宽仍显示待恢复状态；增加等待回执后清理恢复记录与保存按钮恢复的断言，cas-05 用于最终稳定截图，结果另行补充。
- cas-03 真实捕获服务端已采纳但刷新后入口消失。根因是应用初始化身份时把尚未确定的会议范围 null 当作离开会议，清除了记录。新增应用级测试 `continue-info-suggestion-hydration-red1.log` 真实 RED；按身份恢复与显式路由切换分开同步后，`continue-info-suggestion-hydration-green1.log` 10/10 PASS，typecheck5 PASS。显式离开仍清理，账号切换/注销仍隔离。
- `.tools/local-app/info-suggestions/recover-cas03-01/report.json` 原 cas-03 原请求精确重放 PASS，原 revision 8；前后信息逐字段相同。失败证据及私有原请求保留，未另建采纳键。
- `continue-info-suggestion-full-java1.log` 804/804 PASS；`continue-info-suggestion-fullweb1.log` 471/471 PASS（早于两处前端时序修复）；目标建议测试5、应用测试5通过，最终全量回归仍待后续阶段收口。

实现边界：分类保持人工选择；不使用完整正文充当摘要；只从经完整历史来源验证的真实候选读取名称和 OVERVIEW。当前确认/材料/模板/排除发生变化时旧建议不再可用。确认名单为独立操作，采纳不授予任何访问权。D03/D04 区域生成及 D08 全页面矩阵仍未完成，本页不是阶段 D 完成声明。

最终稳定浏览器补验：`.tools/local-app/info-suggestions/cas-05/report.json` 7/7 PASS，会议 `8d529588-0af2-4153-8888-4df679a88b33`。新增断言确认原请求恢复记录已清理、保存按钮重新可用后，才捕获1440/1024/390截图；case04原过渡态截图保留。
