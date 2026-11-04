# 在线录音补充修复验收（2026-11-03）

本轮在线录音补充修复及其 B/C/D 本地受控验证已完成。主项目始终为 `/home/tanren/ama`、`main`，单 agent，无子 agent、工作树、分支切换、提交或推送。原“先不修改代码”的分析阶段结束后，用户明确授权继续实施并完成 B/C/D；附件及旧文档中的指令没有被当作执行授权。

本记录是 [原 B/C/D 完成基线](../bcd-completion-2026-11-02.md) 的补充。历史容量和已受理任务不重新提交，不把其他并行会话的改动归为本轮独占成果。原差异分析与逐项方案在 [修复计划](../../superpowers/plans/2026-11-03-online-recording-gap-analysis-and-repair-plan.md)，实施取舍在该计划第7节。

## 实际修复与验收映射

| 任务／问题 | 修复后的行为 | 直接证据 |
| --- | --- | --- |
| T1 / R01 多段清单 | 持久化重建后按采集顺序返回，各段序号/样本及末段一致；保留客户端范围校验 | RecordingServiceTest 2/3/16段、RecordingProtocolIT、原两段实际HTTP/WAV |
| T2 / R02–R03 初始化、设备失败 | 无raw可重试初始化；先查回原会话；重复授权拒绝保留一个0样本空段；设备可用后才开有效新段；原中文恢复提示不被页面覆盖 | usePcmRecorder 18项；device-05三次拒绝及解除后真实采集 |
| T3 / R04–R06 保存事实 | 采集样本、服务端连续确认、待上传和尾段完整性分别记录；确认元数据与PCM删除同事务；旧缓存不编造确认与时间；未知尾段封存后仍未知 | recording-journal-facts 12项；device-05刷新、保存、重进 |
| T4 / R07、R09 字幕恢复 | 暂时503/超时/无效响应保留上次验证的字幕和游标；失权清除；暂缓不显示为正在补处理 | 字幕控制器/视图测试；真实字幕API与页面读回 |
| T5 / R08、R16 时钟、滚动 | 计时取真实采样数，包括不足2秒的数据；暂停不计时；阅读历史不抢位置，回到最新等待刷新及DOM更新 | 控制器短采样；两个桌面实际采集/暂停；滚动交互两项 |
| T6 / R10、R15 封存、处理、信息 | 结束前持久化保存并处理意图；原归档与来源复制、原导入请求恢复；canSubmit/businessReady/intake/execution齐全；仅保存不提交转写；结束后信息抽屉可读 | 原native两段→archive→唯一TRANSCRIPT_IMPORT；device-05仅保存任务数0；两桌面信息读回 |
| T7 / R11 材料增强 | 真实已解析材料进入独立版本化请求；候选绑定原稿、会议修订、材料快照与profile；审阅后明确采用新WORKING；恢复使用原键；RAW和原人工稿不变 | DOCX上传/解析、MaterialEnhancementIT4、MaterialAuthorityGateIT5、verified-08与adoption-recovered-02接续 |
| T8 / R12–R14 来源与窗口 | 原PCM派生独立WAV及Range；授权/撤权/损坏源受控拒绝；440px右侧材料抽屉、数量、关闭保护；结束后信息入口；共享品牌顶栏 | RecordingPartWave2、RecordingAccessIT7、readback-final-02、browser-final3 |
| T9 / 全部 | 补回归、实际浏览器/HTTP/MySQL/worker、原字节与历史对照、严格视觉差异和阶段记录 | 下述独立结果，不将测试总数当作功能完成度 |

## 联调过程中另外定位的跨层缺陷

1. **二进制流失败后响应悬挂**：分段源读取在权限期限到达或PCM损坏时，已取得OutputStream后错误处理又尝试JSON writer。分段与整场归档现在在未提交响应时reset后返回业务错误；已提交时终止截断流，绝不向WAV追加JSON。移除每块重复的全量DB权限读取，保留初始及RevocableInputStream周期/期限校验。最终RecordingAccessIT真实HTTP腐坏PCM断言为503，7项全部通过。
2. **材料差异跨Jackson版本序列化错误**：Spring Boot 4的Jackson 3把Jackson 2 ArrayNode当作普通bean，真实HTTP的changes变成对象。控制器改用既有有界TranscriptWire字节JSON输出；实际MockMvc测试先失败后通过；原候选原请求的HTTP重放验证数组恢复，不创建替代候选掩盖失败。
3. **采用成功被误判为待恢复**：采用复用SAVE回执，前端误按带operationId的TranscriptOperationReceipt校验。改为Receipt并强制SAVED_REVISION与同一meetingId；同步OpenAPI、生成类型和运行时schema。回归接受真实保存回执，拒绝外会议及确认回执。
4. **恢复后的事实被后续动作覆盖**：冷重试不再把已封存会话改回中断；封存不把未知尾段强写完整；下一次授权失败不把上一人工完整段改成系统中断。分别有失败到通过的回归证据。

## 实际录音与材料链路

本轮原两段录音：

- meeting：`36f0a4f3-5755-4509-863d-229b606e5629`；session：`cfd871b4-749f-4ffe-b671-83fec981a015`。
- 原native探针在finish已受理后受Vite HMR中断，原报告保留FAIL。后续查回同一会话、归档和任务；没有重新录音、重发新finish意图或另建转写任务。
- 9个独立采集PCM帧的SHA逐帧匹配整场WAV；两段独立WAV内容分别匹配对应连续区间，Range `bytes=42-65`核对一致；原生audio成功读取实际时长。
- 整场WAV SHA256：`d7b962717a59d7a4adee08360501450be093b46da7cbba018b660d28148c7a04`。最终只读读回再次一致。
- 会后转写只有一个TRANSCRIPT_IMPORT_V1任务，实际状态SUCCEEDED；原始RAW为 `6c3c633a-7fc2-480b-bbc0-acef036cbbcc`。
- 录音材料抽屉实际上传一份 `背景术语.docx`，解析READY；材料快照 `44eae2c8-b768-43bc-8766-c359db45f83f`。重试过程中复用原文件，没有重复上传。

材料增强使用受控术语“我们下次会议→我们下次会议（材料校核）”。原WORKING为 `868f96ba-d165-4fd8-834b-d5c14cda2114`；候选 `58869b02-d8f2-49a7-b1bb-ab18de59e704`；采用后的WORKING为 `068c1b21-b256-455c-8cc7-6ac53dc41e42`。

`verified-08/report.json`保留生成和采用各自“已提交、丢200响应、页面刷新、原请求重试”的四条精确命令；每对key/body/path完全一致。该次因前端回执校验错误未能结束，原FAIL未改写。

`adoption-recovered-02`继续恢复原采用键 `12252cb6-1485-4f44-a81a-c173274dc561`，request body仍为 `{}`。原探针浏览器已关闭，因此明确通过生产scoped journal恢复捕获的原意图再执行真实UI重试；不是声称浏览器存储跨销毁上下文自然保留。此次pending清除，当前WORKING、会议修订、历史列表、RAW及原WORKING前后全等，没有再产生新版本。其末尾截图曾遇到可重试的权限读取失败，不能用作稳定成功页面截图；最终 `readback-final-02/adopted-transcript.png`另以全新浏览器只读核对正文及“生成增强候选”可用入口。

最终readback阻断登录后的所有非GET/HEAD业务请求，记录0业务写入，覆盖两个桌面结束状态、信息抽屉、材料抽屉、分段audio以及采用后的转写。两份已成功记录均不应再用生成/采用脚本重建“成功”。

## 验证结果及时间边界

日志根目录（下文L）：`.superpowers/sdd/2026-11-03-online-recording-gap-analysis-and-repair-plan/`。
实际业务证据根目录（下文R）：`.tools/local-app/recording-repair-20261103/`。

| 范围 | 结果 | 原件／限定 |
| --- | --- | --- |
| Web完整单元测试 | **642/642**，退出0 | L/web-full-final.log；包含并行工作区当前测试，不全是本轮新增 |
| 录音控制器 | **18/18** | L/prior-part-green.log；含冷重试、上一完整段保护；不与Web全量重复累加为覆盖量 |
| 最终两个桌面浏览器回归 | **16/16**，退出0 | L/browser-final3.log、xml；12存储事实+4滚动/材料窗口 |
| 早期录音恢复浏览器 | **28/28** | L/browser-regression-01.log；较早批次，最终12项存储事实已重跑，非总计44个唯一用例 |
| Java生产单元批次 | **898/898**，0失败/错误 | L/backend-unit-02.json、log；在后续HTTP wire与流修复之前执行，不冒称最终全量重跑 |
| 后续Java定向 | **17/17** | L/backend-targeted-final.json、独立XML；RecordingService12、PartWave2、模型HTTP2、wire1 |
| 真实MySQL+HTTP+固定harness | **23/23** | L/final-integration-01.json、四份-final01.xml；访问7、协议7、增强4、材料权限5 |
| 流修复后最终集成 | **7/7** | L/final-stream-it.json、RecordingAccessIT-final-stream.xml；包括腐坏PCM503；独占库迁移54项至V064，测试后删除 |
| 兄弟harness完整测试 | **137/137** | L/harness-full-02.log；1条Starlette弃用提示，不是测试失败 |
| 类型／接口／构建 | 全部退出0 | L/type-final.log、api-final.log、build-final.log；OpenAPI生成类型及**104**运行时schema一致；构建保留现有大chunk提示 |
| 真实设备恢复与保存 | PASS | R/device-05/report.json；三次拒绝、实际采集/暂停、刷新、尾段未知、仅保存零转写 |
| 原采用恢复 | PASS | R/adoption-recovered-02/report.json；精确接续原请求，不新增候选/决策键 |
| 原件与UI最终只读 | PASS | R/readback-final-02/report.json；0业务写入、原SHA、两桌面窗口、采用正文 |
| 原型整图 | 对照完成，**pixelPass=false** | R/visual-final-02/report.json；见下一节，不能写成视觉全零 |
| 工作区／运行 | main，六角色存活；正常start退出0 | L/local-start-final.log；双仓git diff --check通过；无提交 |

最终证据索引为 `R/final-evidence-index.json`，包含报告哈希及本轮关键文件的源码哈希。共享工作区可能继续被用户其他会话修改，因此哈希只代表记录时点。

## 两个桌面视口与原型差异

原型文件未修改，SHA256为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。将实际暂停的采样时间、manifest、字幕和用户事实投影进原型，同为暂停且继续按钮悬停，完整截图严格比较，无裁剪、缩放、遮罩或阈值放宽。

| 视口 | 原型整图 | 实际整图 | 差异像素 | 严格结果 |
| --- | --- | --- | --- | --- |
| 1440×900 | 1440×993 | 1440×1068 | 344287 | FAIL，22.39% |
| 1024×768 | 1024×932 | 1024×1007 | 270362 | FAIL，26.22% |

非零差异的具体保留依据：

- 顶栏采用用户后来确认的共享“智会助手 / 在线录音”及当前账户入口，替代原型“我的会议 / 会议详情”。不是恢复旧图中导航的像素结论。
- 保存说明展示真实采集/确认范围；原型的“未接入服务端保存”不适用于本系统。
- 增加真实分段回听区，控制卡增高、字幕卡下移；这部分承担原型逐段独立音频语义，不能用演示Blob代替。
- 实时字幕显示真实临时/稳定状态；本地受控模型说明与原型通篇演示声明不同。
- 材料入口数量为0时也显示数字；真实材料抽屉复用原上传/知识库组件，其准备、解析失败和原请求恢复仍由真实状态驱动。

最终材料抽屉固定440px、右对齐、底部完成区，标题只显示一次，关闭按钮在右侧；实际截图为R/readback-final-02/materials-{1440,1024}.png。信息、录音中/暂停、授权失败、中断、封存未知尾段等截图见同根readback/device目录。本轮精确整图数值覆盖同暂停状态，其他状态通过行为断言与截图检查；没有“全部状态自动像素门禁全零”的结论。

## 主项目与兄弟项目的契约边界

主项目新增独立 `ama-material-enhancement.v1`、V064私有候选表和生成/查询/采用API，详 [数据库增量](../../database/2026-11-03-material-enhancement-increment.md) 与 `contracts/openapi/material-enhancements.yaml`。

正式材料增强采取**保存人工工作稿→根据当前已解析材料生成候选→审阅→采用新版本**。其快照在生成增强请求时锁定；不改变冻结IMPORT/EMPTY语音导入profile，也不把“开始处理”描述成已经自动执行了材料增强。完整RAW与材料增强是独立步骤，这一实施取舍在修复计划中明确记录。材料、人工稿或权限变化时拒绝旧候选，不静默覆盖人工修订、确认稿或历史纪要。

兄弟 `/home/tanren/ama-integration-harness` 保持原 `master` 分支，HTTP **8792**、SMTP **2525** 固定设计不变。改动包括 `backend/app/material_enhancement.py`、SQLite持久回执表、路由、请求日志摘要、测试和README；按精确请求SHA幂等，同键不同输入409，有界输入/输出413/422。主项目使用HTTP/1.1、有界超时/字节、不跟随重定向，并独立验证来源、材料修订、顺序、结果SHA及输出大小。

术语规则仅是受控测试夹具，不作为公司ASR/LLM接口或质量权威。真实获准MATERIAL_TEXT由主项目文件注册体系读取并核对字节与SHA，生成前后重新检查材料权威；不能以harness说“允许”代替主项目鉴权。

## 复跑和失败保留

所有命令从主项目根目录执行，先 `. .tools/env.sh`。前端检查使用 `npm --prefix web run test:unit/typecheck/check:api/build` 对应独立命令。集成脚本 `L/run_recording_it.py`创建独占库，root仅用于测试库迁移，测试使用普通账号，finally删除测试库；需要当前本地speech/asr runtime环境，勿指向其他业务库。

最终只读核验可运行：

```bash
flock -s .tools/local-app/lifecycle.lock node tools/dev/recording_repair_readback.mjs \
  .tools/local-app/recording-repair-20261103/adoption-recovered-02 \
  .tools/local-app/recording-repair-20261103/readback-new-owned-output
```

使用新的自有输出目录，不覆盖原件。`recording_repair_integration.mjs`含生成/采用操作，不能对已完成夹具盲目重跑；恢复必须使用报告中的原key/body。设备恢复探针会创建其自有会议，明确使用模拟输入设备和故障注入。

早期失败包括：实际产品缺陷（清单顺序、失败恢复、IDB确认/尾段、字幕丢历史、二进制流、wire对象、回执种类），以及环境/探针问题（模块加载超时、APIRequestContext没有发送loopback Secure会话cookie、共享服务重启/HMR、测试端口占用、未同时配置大小写no_proxy）。所有原日志和FAIL保留；未通过删除PCM、放宽鉴权、改原型或覆盖报告换取PASS。

设备拒绝使用显式NotAllowedError注入，解除后使用真实Chromium模拟音源和AudioWorklet；该环境fake-UI绕过实际权限拒绝，不使用fake-UI时出现NotSupportedError。因此不宣称物理麦克风或真实用户权限切换已验证。原C10录音并发/50在线/30分钟结果属于其历史冻结源；本轮未重跑容量，也不把该结果扩展到新增增强端点。手机端及生产平台/模型/HA等资格仍按既有边界处理。
