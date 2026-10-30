# 多格式文档导入进展与证据（2026-10-29）

当前原型全功能验收仍为 **NOT ACCEPTED**。本报告只证明文档主文件导入及DOCX后续处理，不代表背景材料、音视频、录音或其他原型功能已经完成。

## 实际实现

- 主文件支持TXT、DOC、DOCX、PDF、PPTX，保留原始上传字节、文件名、SHA-256、批次顺序与来源ID。
- 旧 `ama.strict-utf8-lines.v1` 与对应处理档保持不变；新增 `ama.office-documents.v1` / `ama.import-documents.v1` 不可变配置，manifest只接受与新解析器匹配的 `IMPORT_DOCUMENT`，禁止借旧TXT配置偷换解析算法。
- PDFBox3.0.8解析PDF；POI6.0.0读取Word97二进制DOC；DOCX/PPTX以JDK受限XML处理，拒绝外部实体，按文档段落/幻灯片顺序返回真实文字。没有音频的文档不生成说话人或时间戳。
- 解析与任务继续经过现有文件授权、受保护来源读取、原请求、共享聚合预算及原子RAW写入；失败不返回部分结果。
- 前端文件选择、拖拽、上传请求验证、原文件重选和重试均使用同一格式集合；提交时再次核对服务真实声明的格式。不是只修改accept属性。

## 验证

|证据|结果与范围|
|---|---|
|`.tools/local-app/full-prototype-office-red-v2.log`|旧TXT实现对真实DOC报INVALID_UTF8，对破损PDF可能错误接受文本；作为新格式RED保留|
|`.tools/local-app/full-prototype-config-green-v2.log`|20项解析/配置/原TXT兼容回归通过；包含新manifest绑定及错误旧步骤拒绝|
|`.tools/local-app/full-prototype-upload-begin-{red,green}.log`|真实上传facade仍只接受TXT的问题重现并修复，所有5格式原请求/原key保持|
|`.tools/local-app/prototype-parity/20261029-documents-native-v3/report.json`|7项真实链通过：5格式分别从正常UI上传→真实Worker→RAW→转写页面；混合5文件来源顺序；破损PDF任务FAILED且无RAW|
|`.tools/local-app/prototype-parity/20261029-document-minutes-v3/report.json`|真实DOCX→WORKING→确认→模板/生成任务→核心/正文→原文来源；恰好1个成功生成任务|
|`.tools/local-app/full-prototype-java-selected-v1.log`|104 classes /557 tests /0 failure/error/skip，BUILD SUCCESS；是原适用B集合加本次解析/配置，不声称所有历史环境绑定套件|
|`.tools/local-app/full-prototype-test-unit-v2.log`|该轮前端267/0；后补上传facade有定向GREEN，最终全前端重跑待绑定|
|`.tools/local-app/full-prototype-ports-green-v3.log`|12项本地运行器归属/恢复/端口隔离回归通过|

原始native-v1/v2及document-minutes-v1/v2失败记录全部保留。native-v2在首个DOC已成功后遇到Chromium响应体观察错误，后续以产品已显示的真实taskId及授权GET观察，不伪造业务响应。分钟链前两次测试错误等待了不存在的文案/未等待人员搜索完成，修正脚本后v3通过，未把失败文件改写为PASS。

## 本地入口与界限

当前隔离worktree服务：`http://127.0.0.1:5174/meetings`（API8182，management8183）。5173/8082/8083属于主目录的另一套服务，本次没有停止或替换。运行器支持并记住显式端口，登录仍检查精确配置的loopback Origin与CSRF。

DOCX分钟链使用本地受控合成模型，验证流程和来源关联，**不证明模型语义质量**。图像型PDF/OCR、加密文件专项、复杂版式与大文件资源边界仍需补专项验证；现有负例包括损坏文档、外部实体、哈希、字节/文字/段落预算及取消。

后续仍按原型全功能清单推进：背景材料及知识库选择、真实录音/音视频、说话人/纠错/反馈、专题/归组/删除、个人模板/局部生成及会议知识库。未完成项不能按“未来页面”验收通过。
