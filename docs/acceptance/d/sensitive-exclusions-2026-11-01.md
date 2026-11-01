# D-03 敏感排除、材料来源与目录验收

状态：本批次源码与真实 CAS/API 验证完成，浏览器接续仍未通过；已按用户要求暂停，阶段 D 未完成。所有外部请求通过兄弟项目固定 HTTP 8792 / SMTP 2525；受控检测和模型输出只验证协议、版本、过滤及页面行为，不代表公司服务权威或实际模型检测质量。

## 已有实际证据

`.tools/local-app/sensitive-exclusion/cas-01/report.json` 的六组检查通过。通过真实 CAS 导入三段 TXT，实际 Worker 产出 RAW，恢复并确认工作版本，生成两个敏感类别，保存人工正文。单项排除与全部排除均保存真实版本；同键回放、同键异体 409、未检测源 422 已验证。新的模型请求不含所选排除片段原文，新的候选证据、敏感与背景目录不引用该物理片段。原始转写、原要素和人工正文保留。全选案例中的两个类别共享同一个物理片段，不将其计为两个独立来源。

`.tools/local-app/sensitive-exclusion/material-resume-02/report.json` **整例失败，前四组通过**。会议 `d3b0423d-1a61-4995-adf7-6bddee8ca7f7` 实际引用知识库材料并导入 TXT，首次完整生成后保存人工正文。知识库敏感来源没有正文/要素证据，仍能通过当前要素集的敏感/背景目录预览；错误要素集 404、外人 403。真实页面的材料原文、受管原始文件、独立背景目录、排除复选框、幂等保存、冲突与任意新来源拒绝都已验证。错误下载测试客户端导致的最初 401，以及无稿读取错误未显示导致的空白页，分别保留在 `material-cas-01`、`material-resume-01`，均未改成通过。

## 材料排除发现的实际缺陷

上述材料重生成任务 `7d5307e4-f4d6-4d39-83b2-62655bfb2cf2` 成功，但验收脚本随后误用 `background` 字段而停止；正确字段为 `backgroundMaterials`。独立核对该任务的真实提供方请求，确认还有产品缺陷：选择整份知识库来源 `s4` 排除后，同一版本的十个文本段落 `s5`—`s14` 仍进入模型输入。证据保留在 `material-resume-02/physical-exclusion-red-proof.json`，对应请求文件为 `minutes/provider-store/requests/f8afd578-7f84-4fd7-81e4-25a7db8c3c96.json`。该任务不能算排除成功，其候选 `c80eacad-4ffe-4e41-b836-a2339bfe898c` 不应采用。

修复以材料修订、解析文档、解析器、解析文本 SHA 和受管原文件共同识别同一物理版本。整份知识库排除会覆盖其全部页段别名；排除某段也会移除包含该段的整份文本副本，但不会通过整份副本继续扩大到其他段。其他材料修订不受影响。该规则同时用于原文输入、已有要素/背景摘要、受控提供方来源选择和新输出引用检查；没有正文证据的敏感/背景引用同样不能绕过检查。

## 归档与保存边界

新行为通过 `ama.controlled-minutes-http.v4` 和 `ama.minutes-model-request.v4` 装配，输入指纹仍使用 v3，输出结构仍使用 v2。旧 v1—v3 的配置、请求和回执保持各自版本，不改写历史提供方输出。新标准与敏感场景均追加独立 release，并共享包含全部旧 release 的最终索引。

保存排除项仍需完整存储来源证明。新选择必须来自当前敏感目录；旧项只允许保留当前排除修订中已经保存的准确来源/类别组合，并找到曾实际应用该排除的来源证明。反复保存相同选择仍合法；转写确认、材料快照、文档或解析器不再当前时不能凭旧项授权。显式移除后，未被当前检测列出的来源不能重新加入。

当前 SET 来源入口只允许当前要素集，且 locator 必须是其真实证据或目录成员；不会获得要素集审阅、编辑或额外文件权限。历史来源继续使用独立的负责人历史路由。

## 本次源码验证

`continue-source-exclusions-production-java2.log`：生产 Java 854 项通过，零失败/错误/跳过。`continue-material-overlap-green2.log`：材料物理重叠、协议 v4、旧归档字节保留、个人模板渲染等定向回归通过。`continue-source-exclusions-runtime1.log`：本地启动与场景工具 30 项通过。安装器独立编译 `continue-source-exclusions-installer-compile2.log` 通过。兄弟项目透明桥接 `continue-source-exclusions-harness2.log` 五项通过，固定端口设计未变。

v4 实际 CAS/API 接续 `.tools/local-app/sensitive-exclusion/material-v4-api-01/report.json` **六组通过**，源码/部署签名均为 `377765521a0de8e7e2acdca2abac3ee02c44b7779b1bc2b03cecd7e523910161`。真实任务 `b41376dc-f502-48e6-9964-1fe8a42ea67b` / 输入 `210697ac-80c4-4107-917f-bae8c393092b` 成功，候选 `12398057-88c3-49f5-85ee-aaa621902c18` 已显式采用。提供方真实请求使用 v4，背景原文数组为空，候选有效证据/敏感/背景来源均属于剩余转写；当前原稿只在显式采用后改变，人工标题保留。采用后旧 SET 来源路径 404；旧排除项连续两次新修订保存成功；明确移除后重新加入未检测旧项为 422。原 v3 初次生成请求原键重放仍返回旧任务 `234a478d-ecf3-4be5-a95b-3a6306e90fad`，原知识库受管文件仍可读取。`filtered-proof.json`、`retained-twice.json`、`before.json`、`final-current.json` 与原始请求记录均保留。

该六组报告是 **CAS/API/真实 Worker 验证**，不能替代完整浏览器通过。`material-v4-01` 在新生成前失败于临时重读按钮竞态；已补授权等待期的加载状态，15项定向测试通过。`material-v4-02` 仍因材料原文预览等待超时、元素消失而失败，没有新生成任务。该问题尚待定位；不能把脚本主动测试的 404 当作 UI 请求失败的根因，也不能延长权限期限来规避。接续应同时采集来源请求的 targetKind/targetId、请求失败/取消事件、组件 epoch 与当前权限/恢复状态。

当前会议已发生上述显式采用和移除：revisionToken=13，当前正文 `e72d3b8f-bb18-4c31-a157-ff9e4ff9e568`、要素集 `2d436d9b-2a7d-48d8-86ee-1fb538f5cfe0`、排除修订 `940501bf-2315-467b-92f0-1dc8e1cc0281`（空集合），needsGeneration=true。不得把原候选当 PENDING 或把旧 SET 当当前版本继续点击。

完整浏览器接续脚本已准备为 `.tools/local-app/continue-sensitive-material-v4-ui-cas.mjs`，仅语法检查，尚未执行。该脚本使用全新真实 TXT/KB 会议，先生成含知识库检测的新稿，再验证目录预览、排除、v4 过滤、显式采用、旧项保留与 UI 移除；需要显式 sensitive 场景。用户要求本批结束后停止，后续只在收到“继续任务”后启动。当前已恢复 standard 场景，见项目接续文档。
