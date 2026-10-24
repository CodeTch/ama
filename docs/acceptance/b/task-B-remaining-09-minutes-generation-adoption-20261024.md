# Task9限定采用：minutes generation与受控provider

采用源码 `591545a4a1a4ce27edea54baa94e9d11666807b7`，初始作者提交 `0e22b812000f2efa00c2ef8ed66e06d8254beab5`，基线 `cff858e163ca7dea774dcf05c707ef7551956156`。独立初审0 Critical/4 Important/1 Minor；同作者fix1后，同一审查者认定F1–F4全部ADDRESSED，修复scope SPEC PASS、quality PASS，0新问题。原F5 Minor仍OPEN，由Task13最终全分支明确修复或规范裁定。本采用不宣称无条件全Task9规范合规，更不宣称整个B已完成。接续Task10→11→12→13后停止，不进入C/D/E/F。

## 采用行为与修复

真实有界分钟来源/原档案/profile/review/final图校验、初始化settings和accepted replay、认证durable controlled provider、真实Task7 Worker/marker/permit/private observer候选写入与完整terminal replay已实现。生成固定原业务revision，不推进会议、不清债；完整历史身份union先于root/task/execution锁，I/O解析hash在锁外；真实issuer/holder/fullspec/subject绑定保留，terminal replay无重复图写入。

fix1强制生成请求scope为FULL并对齐OpenAPI/语义幂等；原始档案root按完整type/version身份排序去重与当前精确成员校验，保留多版本union；已存候选从验证的原始body恢复不可变parent，不依赖最新current；provider在同owner锁内先校验logicalkey/capacity/journalbytes/file slots再写durable bytes，并保守计入crash残留。accepted journal仍必须对应完整可验证request/envelope/payload。边界响应预算按采用R1为413，旧503期待错误与原预算未生效产品缺陷分开记录。

## 独立验证与准确资格

- 原作者最终293 distinct unit是289 PASS加修正private Runtime反射测试后4 PASS的组合，非单批全绿。原正常迁移/受影响继承12 PASS，全部15×10原历史字段保持、migrate0；原bootstrap未显式证明UTC，最终SQL UTC表示比对不反向升级bootstrap资格。
- 原最终HTTP4、terminal5、disposition3、candidate pages1各有独立来源且相互重叠，不加总冒充新case。ADOPTED/REJECTED/STALE仅授权结构CAS且未改变current/root，不能代替Task10业务动作。
- fix1实际RED：F4三FAIL、F1两FAIL、F2/F3两FAIL；一次测试accessor编译0XML保留。修复后11聚焦unit PASS、8底层journal回归PASS；集成6项原窗5PASS/1FAIL，后fresh denied1PASS为组合资格；OpenAPI两检查exit0。五窗274main相同，仅denied child测试有限等待变化。原293不是postfix全套重跑。
- 原denied失败task fa172992-cbf0-4527-a638-ab6e4853b819已STALE，execution/operation SUCCEEDED、candidate0，但原permit120e2368-6b9b-46eb-9e5f-f53df037580c未结。后来精确产品settled调用一次true/目标0，无GET/POST；原失败保留。fresh denied用10秒deadline记录0/0/0/1恢复返回与count1→0，约1.3秒；原竞争原因仍未证实，未改产品settlement。
- F2是实际source-first双版本产物经过相同生产parser，加真实单版本active/denied持久链的组合证明；不是双版本DB marker恢复。F3是真实原候选图遇不同detached current观察的算法回归，未伪造DB current，不等于真实save/adopt链。
- 正式runtime在fix前真实启动一次，64MiB/2CPU、19档案、import+global reconciliation RUNNING/active0，精确18task/10operation前后不变，全部本次process关闭。newGenerationDispatch=false。该历史startup不冒称fix后重跑，也不证明完整generation业务链。

## 机械证据与资源

初始机械复用165历史窗/20557保护路径，最终增量10结果窗加formal runtime，fix1再核9窗/14XML。原作者96298路径、fix6342路径精确且index空；22fix own源码、5窗274main与原fact7文件hash吻合。raw旧树不反复全扫：最后机械只核全部raw path/size，对新增window/source/fact独立hash。作者扫描初始known6加实际provider2共8值，raw/staged及receipt内存零value/PEM命中；fix扫描仅新增6317raw/6341pre-receiptstage，最终commit多receipt1。限定范围，不宣称通用秘密审计。

所有原容器/schema/credential/provider档案与失败窗保留，无reset/drop/repair/bootstrap/生产发布/推送/main合并/外发。Task9仅专用schema，未新增DDL或模型。初始diff--check原空白问题仍作为历史证据，不虚称初始检查干净。

## 仍须闭合的后续义务

Task10真实first-save/adoption/settings、current PINNED与active exclusions后，须建立真实多版本durable marker并对同task/operation/execution/permit GET-only恢复，正确terminal与实际settlement，无额外POST；真实save/adopt/current-body改变后重放同terminal，不改旧candidate/graph/task、不resend。Task10全部version writer PENDING处置/完整拷贝，Task11 publication/freeze/readiness及formal combined启用，Task12真实LOCAL+S3完整链/浏览器仍OPEN。当前新正式generation/initial-settings保持缺贡献即failclosed，accepted replay/history不受此阻塞。

F5额外preparation F是实际R1 §3.2偏差，按SDD Minor不进此fix loop，已入pending register、closure map、Task10 brief和Task13强制审查输入；Task9采用不关闭它。最终全分支下界固定 `34cb226aefe99c67d3681af68d748b0aeea4e073`。

历史UNKNOWN、三排除窗、旧collector suite级限制与11 before-image缺口不升级；B01 IMM永久PARTIAL、B04 Q401 UNKNOWN、公司SSO/目录/模型/KB协议缺失、HA/容量/native物理IO未资格化边界保持。

## 协调镜像

精确review/ruling/runtime/closure镜像见 `artifacts/phase-b/b-remaining/controller-task09-adoption-20261024/manifest.json`，SHA `99d3b2c478989eef658797efbc1fb5dc330dacf859236ceca02dde4d00776baf`。复审报告SHA `bcb294ea3565931e5b675e9410ecbeb68112fffb1b380a6d59c5ab14f38908c1`；fix机械JSON SHA `9215231839e12bc47a8e4842fe2b303633dccfac4504ad1898c5ce18fe8a5c37`。W和全部raw原文保留。
