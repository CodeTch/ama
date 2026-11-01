# 2026-11-01 D03 / D08 接续记录

用户收到导入修复验收后要求“继续任务”。已确认 `/home/tanren/ama`、`main`，全程单 agent，不建分支/worktree，不自动提交，保留全部已有改动和失败证据。

知识库导入修复已完成，证据见 `docs/acceptance/harness-material-import-2026-11-01.md`；旧阶段暂停入口保留为历史。本次从 D03 材料 v4 完整浏览器失败接续，其后处理 D08 新鲜发布专题与页面矩阵。B/C/D 资格不会因恢复执行自动变为通过。

执行顺序：

- [x] D03：记录来源请求和导航授权续查的耗时/取消，复现并定位预览内容消失；需要产品修复时先写失败回归。
- [x] D03：新鲜 TXT + KB 会议完整 UI 来源、排除、v4 请求过滤、采用、已保存旧项保留/明确移除验证。
- [x] 恢复 standard，通过启动器部署并核对源码签名；保留固定外部 HTTP8792 / SMTP2525。
- [x] D08：新鲜真实发布会议，专题正向及422/403/409/重放与三视口。
- [ ] D08：按真实可达页面核对现有证据、原型和按钮矩阵，明确剩余缺口。
- [x] 对实际修改运行对应回归并自查，更新阶段摘要及机器接续状态。

自查由同一 agent 完成，遵守用户禁止子 agent 的要求。实际 CAS 验证期间冻结产品源码及 Maven，原请求未知时保持原 key/body，不覆盖旧失败报告。

## D03 授权续查时序

`fresh-material-v4-ui-01` 已真实完成预览/背景来源/原件、三视口勾选保存、v4物理过滤、显式采用和两次保留旧项（前7组），最后 saved-only UI 被授权续查到期清空，整例FAIL保留。会议8b7b4903-ebbf-4cb7-8536-6750d3316769，候选6efe275f-c82e-4112-a8da-abe33a26d9e3已ADOPTED，旧排除项仍保存，不得重当PENDING。

发现独立可重复时序缺陷：期限以请求开始计，旧续查却在响应完成后再等interval，慢而成功的响应会累计挤占期限。确定时钟回归 `slow successful renewals keep their start cadence without extending authority lifetime` RED（3项中1失败）→GREEN3；仅按开始时间调度下一次续查，4500ms上限与失权清空保持。完整前端485PASS。首次typecheck发现新测试数组下标的严格空值类型错误，已补充确定存在的断言，目标3项/typecheck/build/API一致再次通过；旧失败日志保留。下一步实际同夹具回读与新鲜完整UI。


`lease-cadence-ui-01` 对原失败会议持续15秒显示已保存旧排除项PASS；新鲜完整 `fresh-material-v4-ui-02/report.json` **8组PASS**，源码/部署c7d15b5一致，无pageerror。会议ff9336ad-f937-4aa9-bd76-97eadbbfeb62；真实TXT/KB、current SET/外人门禁、来源/原件/背景目录、三视口勾选保存、v4模型实际payload物理过滤、显式采用/人工标题保护、旧项两次保留、saved-only UI取消保存、移除后再加入422完整通过。旧ui01失败与旧v4-01/02失败全部保留。


D08 `d08-page-matrix/topic-cas-01/report.json` **8组PASS**；会议4fb39e89-3d56-4750-ba02-48d0d45af038真正发布，验证未发布/混批422原子性、已发布本人正向/原键、非空删除及陈旧409、参会人读/改个人专题403、旧原键不重应用、三视口编辑取消/删除阻断。standard六角色正常，源码/部署c7d15b5一致。

同时记录home/minutes-core/minutes-body/confirm/publish/published各3视口共18个页面截图及可见控件几何，无文档横向溢出。但已发布的首次3张拍到真实加载态，**不作为完成内容的视觉通过**，另做published-readback-01等待实际转写/纪要后采集。

重新使用Vue DOM解析器扫描web/src全部57个Vue组件得到464个按钮（402个生产候选、62个演示/未来页面待路线审查），补齐旧437清单漏掉App.vue的25处等。机器结果 `d08-page-matrix/controls-audit-02`；仅为清单，所有未绑证据项继续REVIEW_REQUIRED，不能据总数宣称业务成功。初次使用底层baseParse未使用HTML void规则而失败，改用compiler-dom.parse后正常；这属于审计工具错误，不是产品模板错误。


已发布加载完成后的原型对照又发现：核心/正文tabs被隐藏，默认仅正文；published-transcript播放器列被CSS整体隐藏；正文直接子按钮被CSS隐藏，连导出也消失。新增actual组件回归真实RED（header display:none）；实际已发布会议的播放器和导出可见性分别RED，见published-media-red、published-export-red。修复为默认核心/可切正文、恢复来源媒体列、移除误伤导出的通用隐藏，并按原型把发布核心目录作为连续只读内容展示。现有后端授权、最终快照与写禁用规则未改。组件/撤权/续查目标9PASS，完整Web486PASS、type/build/API一致PASS。

增加可复用只读验证脚本 `tools/dev/published_readback_integration.mjs`，对既有真实已发布会议在三视口检查tabs、来源媒体、核心/正文、三格式导出窗口及写入口缺席，并核对无业务写请求和正文未变；只打开导出窗口，不生成新任务。部署后实际验证中。


## 本批最终回读与准确后续

`published-access-green-01`负责人及`published-member-green-01`参会人各3视口PASS，均0业务写请求、正文固定，切换栏/媒体区域/三格式导出窗口可用；当前源码/部署b498b746一致，standard六角色存活。机器证据 `.tools/local-app/continue-d03-d08-final-verification.json`。本次仅前端/开发验证工具变化，Java/IT没有在本批重跑，历史结果保留其原范围。

单agent集中自查完成，核对续查开始时刻/过期/旧scope、发布只读与固定版本、CSS作用域、owner/member、导出无隐式写入和失败证据保留；本次修复无未解决必修项。

仍需继续D08完整矩阵：使用新controls-audit-02的464行，先按生产路由剔除演示/未来/不可达分支，再逐项绑定成功/失败/禁用原因和实际报告；继续其他页面/弹窗三视口与原型差异。已发布页面额外进度条和说话人状态卡列入待收口视觉差异。不得把现有8+8+3+3和静态总数当作全D通过。D保持NOT_ACCEPTED，B/C失败保留。

可以复用新鲜有效发布会议4fb39e89-3d56-4750-ba02-48d0d45af038做只读/专题接续；其topic归集当前存在。材料ff9336ad会议已采用且已明确移除排除项，needsGeneration=true，不能当原PENDING/未移除夹具。8b7b4903旧失败会议候选也已采用但旧排除仍保留。所有未知请求仍先查原key/body，无新生成资格时不另造任务。运行中的Maven/CAS脚本均已结束，保留服务运行。
