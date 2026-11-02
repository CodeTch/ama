> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# 已发布回看入口修复与三视口验证（2026-11-01）

在D08原型审查中发现三个可复现入口问题：发布后的核心/正文切换栏隐藏且默认仅正文；原音频播放器所在列被整体隐藏；正文直接子按钮的通用CSS把导出入口一并隐藏。

修复复用既有已发布快照和对象权限：显示核心/正文切换栏，默认核心；核心按原型连续只读展示；恢复来源媒体区域；移除误伤导出的按钮隐藏。编辑、局部生成和正文修改的既有发布后门禁保持。

## 验证

| 范围 | 结果 | 项目根目录下证据 |
| --- | --- | --- |
| 实际Vue组件切换栏/默认核心/编辑入口缺席 | RED→GREEN | `.tools/local-app/continue-d08-published-tabs-red.log`、`continue-d08-published-green.log`（合并9项通过） |
| 已发布来源媒体可见 | 实际浏览器RED→GREEN | `d08-page-matrix/published-media-red/report.json`；下两行GREEN报告 |
| 已发布导出入口可见 | 实际浏览器RED→GREEN | `d08-page-matrix/published-export-red/report.json`；下两行GREEN报告 |
| 负责人，1440/1024/390 | 3视口PASS，0业务写请求，正文保持 | `.tools/local-app/d08-page-matrix/published-access-green-01/report.json` |
| 有会议读取权限的参会人，1440/1024/390 | 3视口PASS，0业务写请求，正文保持 | `.tools/local-app/d08-page-matrix/published-member-green-01/report.json` |
| 完整前端 | 486通过，0失败/跳过 | `.tools/local-app/continue-d08-web-full.log` |
| 类型/build/API一致 | 通过 | `.tools/local-app/continue-d08-typecheck.log`、`continue-d08-build.log`、`continue-d08-api-check.log` |

实际会议 `4fb39e89-3d56-4750-ba02-48d0d45af038` 已通过真实TXT/确认/受控生成/逐项显式审阅/最终确认/真正本地harness发布。源码/部署 `b498b746ee0c8a15ec3f4c6942e4ce4153c943c2f7e9fbebd6a28fbace6a80c4` 一致，standard六角色运行，固定HTTP8792/SMTP2525。

可复用只读检查：`node tools/dev/published_readback_integration.mjs .tools/local-app/d08-page-matrix/NEW_DIRECTORY MEETING_UUID`。脚本断言core/body切换、来源媒体区域可见、三格式导出窗口、没有编辑入口、无横向页面溢出，并比对前后正文和0业务写请求。参会人案例用同脚本仅把登录名改为dev-member，完整执行脚本保存在案例目录。

导出检查只打开窗口并核对Markdown/PDF/Word选项，不新建导出任务；三格式文件生成/下载仍引用既有D06证据。来源媒体案例使用真实TXT，正确显示“无原录音”，不冒充本次完成音视频播放或C语音容量验证。

## 自查与仍开放项

按用户约束单agent自查，没有独立子agent审查。核对了4500ms续查期限/旧scope隔离、published样式作用域、默认与手动切换、版本固定、owner/member只读、原件读取授权和导出窗口无隐式写入；本次修改没有未解决的必修复发现。

D08整体仍开放：逐个可达按钮的成功/失败/禁用证据尚未全部绑定，其他页面/弹窗三视口与原型差异尚未全部收口。已发布页面仍有原型没有的进度条和说话人状态卡，列入视觉差异待收口；本报告没有声称全页面像素相等。原型原件SHA保持ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867。
