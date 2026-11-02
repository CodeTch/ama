> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# C06 持久字幕生产接线执行细化

父计划为 `2026-11-01-bcd-completion.md` Task3/5。用户授权单agent/main，全部BCD/D完成前继续，不询问；外部统一兄弟固定8792/2525，不能当公司权威。以下是下一实现选择，尚无实现/验收声明。

## 设计选择

Ruling: 每个录音一个持久技术job及有界窗口，不为每片建durable_task；否则3600片突破当前256任务完整图上限。API进程拥有有界并发调度器，job租约在MySQL跨进程协调；字幕失败不影响PCM写入、ACK、归档。只对新录音或显式重新进入录音操作安装job，不自动扫描所有历史录音启动模型。

- 拟V059：recording_subtitle_job/session唯一、owner/meeting/cancel epoch、当前part及样本/seq游标、当前segment/window/revision、event计数、lease owner/attempt/expiry、next_run/error/deferred；recording_subtitle_event/session+event_no主键、part/segment/revision/status/text/time/source seq/created_at。不改V056/57/58。
- 调度器只读有界due选择，实际claim按身份→meeting/fence→录音admission/session→job顺序锁；权限每次重新验证，不能用候选行或上次快照当授权。撤权/删除/治理后不发送新模型请求、不提交晚到结果。
- 输入从连续已ACK块读取，每窗口最多3块（≤6秒）。准备在当前根锁内捕获精确元数据，事务外读真实PCM到EOF验证SHA；beforeSend再次查当前授权/同一job租约；F重验同一原块清单，不因新块加入而无谓作废旧连续范围。任何缺口、旧块修改或读失败均不产生字幕。
- 同segment第1/2块为temporary修订，第3块为stable封口。暂停、换part、finish时，非满窗口使用最后已识别文本另写更高revision stable，并移动window起点；恢复同part不得把新块追加到已经封口的旧window。稳定仅指本应用封口，不虚构provider稳定语义或跨窗聚类。
- 不按字符串去重；相同segment高revision替换，重复词/数字保留。segment ID绑定session/part/window。eventId为会话内单调十进制token，每次event和cursor更新同事务；重启不复活旧revision，cursor分页每次≤64，bounded text预算，超预算明确deferred，不影响录音保存。
- 使用现有HttpSpeechModelPort ASR和V056 speech_capacity/execution，lane REALTIME至多64、offline保留2、total66；beforeSend先持久准入。timeout/disconnect保留UNKNOWN，完整EOF或harness可核验证据才结算。扩展现有ownerCurrent恢复逻辑识别REALTIME技术job；不能因不在durable_task就立即把活job fence。资源表无内容，meeting删除不得假结算；字幕job/event内容实际清理。
- GET `/api/v1/recordings/{sessionId}/subtitles?afterEventId=...` 是授权只读分页，不隐藏创建/模型发送。READ成员需要当前会议READ，历史游标必须属于会话有效范围；返回hasMore/nextCursor/delayed及事件真实source seq。生产RecordingWorkspace挂LiveTranscript并轮询/断线游标恢复；身份、会议或录音改变立即清空晚到数据。
- 全链时延由真实采集/负载输入的片段结束时间到可见event观测计，不用落库时间掩盖上传积压；100/300/7200和temporary≤3s/stable≤5s分别按实际证据判定。harness仅协议/业务测试。

## 顺序与验证

1. 核对C03当前CAS结果、源码/部署签名；原失败/原键/新旧migration SHA保留。
2. 写真实MySQL+受管mTLSPCM+固定harness的新C06 IT：缺生产接线RED；3块revision/重复词/数字、乱序缺口、partial tail稳定、换part/重启/after游标、模型错误而ACK完整、晚到/删除/撤权、全局资源UNKNOWN及reserved64。只在独占schema迁移新V059，迁移后不能再改脚本。
3. 实现私有调度/资源observer/稳定event与read，接API生命周期。每次HTTP在DB事务外；外部结算和业务结果写入分离；所有writer必须消费真实job租约和当前根。
4. 严格OpenAPI/SDK/运行schema；前端原事件游标、有界内存、延迟/失败明确、实际RecordingWorkspace接线，先客户端/界面RED后实现。
5. 适用生产Java/Web/类型/契约/独占MySQL回归→正常部署→实际CAS录音/WS ACK/字幕/断线/重启/第二身份/三视口；新的短负载和时延如实记录。
6. 继续两小时归档独立ASR输入；普通上传32MiB保持。倾向实际queue能力下生成受管内部转写输入副本（相同原SHA、持久原归档关系），使用独立长音频profile和有界流，而非放大所有client上传限额或伪造upload_session。该部分须另行精化实际FileUploads/authority能力，不能仅按本草案实现旁路。
7. 所有BCD剩余矩阵、冻结真实容量和最终集中自查仍按父计划执行，不在C06局部通过后停止。

V059首次新schema2d23fd307995失败于第一条CREATE，MySQL3780明确session FK字符集/排序不兼容（实际parent utf8mb4_0900_ai_ci，child原ascii_bin），无字幕表创建成功，失败原脚本保存在.tools/local-app/bcd-completion/c06-v059-failed-first-create.sql。未修复/清理旧失败库，修正尚未成功应用的V059列后另建独占库；主库058不动。


当前C06实现/验证进展：V059新独占bf39889988a8迁移成功，SHA见字典；JdbcLiveSubtitles真实生产服务、全局REALTIME资源ledger、API只读分页及显式录音创建接入、API有界生命周期、严格SDK/82运行schema、RecordingPage实际LiveTranscript与scope晚到防护已实现但尚未正常部署。Web全512/type/build/API byte check通过。DurableSubtitleIT最初5项已全PASS（c06-http-production-green3.log），含实际mTLS PCM、harness、进程重建、游标、HTTP403和含录音物理删除。首次late deletion夹具漏finish被业务正确409拒绝后修夹具；随后暴露实际MySQL1442，旧V055触发器与DELETE子查询冲突，改为当前所属session绑定分页删除后完整5PASS。新增换part/finish及disabled owner两项PASS，第三项stable tail源SHA改变真实RED（旧实现仍封stable）后补完整当前指纹/时间/执行证明，正在完整8项重跑。所有失败保留。

harness新负例真实RED无效JSON500，修admin_create_scenario为400 invalid_json_object，独立新测试库全65PASS；不更改固定8792/2525。JDK默认h2c导致body丢失的测试fixture使用显式HTTP1.1，与实际Apache客户端一致。正常固定端口重启后继续真实联调；不将harness当公司权威。

正常部署完成：主库059/六角色健康，源码与部署签名均99a84776696d0ffa044ccfd9df3e2a95daf5eceffcaa03c66d341c15de14b2ff。联合实际IT49PASS（23追加+14纠错+2资源+10字幕），生产Java873PASS、Web512/type/build/API82PASS。固定harness重启PID2265462/原端口，资源三项实际证明及五个无效JSON400通过。正在c06-native-subtitles-cas-01：原生Worklet→WS真实ACK→临时/稳定UI、实际SIGKILLAPI后IndexedDB原PCM留存、原字节恢复上传已局部PASS；整例仍运行，不能提前记整例通过。

待集中自查：当前held(job)将租约过期与权限改变都用AccessDenied；应补实际模型执行期间强制lease过期的负例，确认仅丢弃过期结果并等待重试，不误将仍有权限的job永久CANCELLED。先RED再最小修复；完成当前实际CAS后再改源码，避免对齐范围混淆。


真实CAS最终c06-native-subtitles-cas-01/report.json 13组PASS，原生473856样本、947756字节WAV、每帧SHA核验，session e94e749d-d21d-4c47-a98f-f1105d8c4fff / meeting509b88f4-ff9a-45c2-b502-ea8bf25c08f4。含实际API SIGKILL、原IndexedDB音频/原字节重传、临时稳定字幕/刷新/DONE尾段封口、三视口与第二CAS403；源码/部署签名99a847...相同。只证明兄弟受控协议/业务链，不证明公司模型或100/300/7200时延。

集中自查租约负例真实RED：c06-expired-lease-red.log在真实1000ms模型中使job租约过期，预期DELAYED实际CANCELLED。held(job)现在用私有LeaseChanged处理过期/其他attempt占用，仍经原attempt/owner条件更新，不能取消新租约；权限/epoch拒绝仍CANCELLED。完整11项真实字幕正在c06-expired-lease-full-green.log验证；该微修复尚未部署，旧完整CAS范围固定不改写。
