# 全页面按钮定义清单（2026-11-02）

从 Vue 模板 AST 生成，包含 60 个组件中的 521 处原生按钮，以及链接、展开控件、角色按钮和文件选择入口，共 585 处定义。循环生成的按钮以定义位置对账；行号相同的按钮通过列号区分。未注册到路由的遗留组件也保留在清单中。

此文件仅记录源码定义覆盖范围，不执行浏览器点击或业务操作；历史运行记录见 [按钮稳定性排查记录](button-stability-2026-11-02.md)。

重新生成：在项目根目录运行 `web/node_modules/.bin/tsx web/scripts/button-inventory.mts`。

## App.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [115:71](../../../web/src/App.vue#L115) | RouterLink | 智能会议助手 |  |
| [116:4](../../../web/src/App.vue#L116) | button | 我的语料反馈 |  |
| [117:34](../../../web/src/App.vue#L117) | summary | 当前用户 | :aria-busy="profilePending" :title="profilePending?undefined:profileName" |
| [117:495](../../../web/src/App.vue#L117) | RouterLink | 会议库 |  |
| [117:566](../../../web/src/App.vue#L117) | a | 退出登录 | v-if="session.current.value" |
| [117:651](../../../web/src/App.vue#L117) | RouterLink | 企业登录 |  |
| [121:5](../../../web/src/App.vue#L121) | button | 开始录音 |  |
| [122:5](../../../web/src/App.vue#L122) | button | 导入会议 |  |
| [126:5](../../../web/src/App.vue#L126) | button (tab) | 我负责的 | :tabindex="scope==='owned'?0:-1" :class="{active:scope==='owned'}" :aria-selected="scope==='owned'" |
| [127:5](../../../web/src/App.vue#L127) | button (tab) | 我参与的 | :tabindex="scope==='joined'?0:-1" :class="{active:scope==='joined'}" :aria-selected="scope==='joined'" |
| [128:60](../../../web/src/App.vue#L128) | button | 列表 | :class="{navy:!groupView}" :aria-pressed="!groupView" |
| [128:217](../../../web/src/App.vue#L128) | button | 专题分组 | :class="{navy:groupView}" :aria-pressed="groupView" |
| [129:482](../../../web/src/App.vue#L129) | summary | 会议状态 |  |
| [129:1636](../../../web/src/App.vue#L129) | summary | 会议分类 |  |
| [129:3272](../../../web/src/App.vue#L129) | button | 清空筛选 |  |
| [131:94](../../../web/src/App.vue#L131) | button | 重新读取专题 |  |
| [131:166](../../../web/src/App.vue#L131) | button | 恢复原专题请求 | v-if="topicCommands.pending.value" |
| [132:193](../../../web/src/App.vue#L132) | button | 批量调整专题 |  |
| [132:323](../../../web/src/App.vue#L132) | button | 取消选择 |  |
| [135:165](../../../web/src/App.vue#L135) | RouterLink | 企业 CAS 登录 |  |
| [137:90](../../../web/src/App.vue#L137) | button | 重试当前页 |  |
| [137:148](../../../web/src/App.vue#L137) | button | 返回第一页 |  |
| [138:325](../../../web/src/App.vue#L138) | button | {{group.topic.name}} |  |
| [138:555](../../../web/src/App.vue#L138) | button | 修改专题 |  |
| [138:701](../../../web/src/App.vue#L138) | button | 删除专题 |  |
| [139:304](../../../web/src/App.vue#L139) | button | 未归入专题 |  |
| [141:190](../../../web/src/App.vue#L141) | button | 上一页 | :disabled="loading&#124;&#124;!list.canPrevious.value" |
| [141:293](../../../web/src/App.vue#L141) | button | {{list.pageNumber.value}} | :disabled="loading" :aria-label="'第 '+list.pageNumber.value+' 页'" |
| [141:434](../../../web/src/App.vue#L141) | button | 下一页 | :disabled="loading&#124;&#124;!page.hasMore" |

## features/auth/LoginPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [111:13](../../../web/src/features/auth/LoginPage.vue#L111) | button | {{visible ? '隐藏' : '显示'}} | :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" |
| [120:11](../../../web/src/features/auth/LoginPage.vue#L120) | button | {{loading ? '登录中…' : '登录'}} | :disabled="loading" |

## features/import/ImportDialogView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [14:468](../../../web/src/features/import/ImportDialogView.vue#L14) | button | 关闭导入会议 |  |
| [15:138](../../../web/src/features/import/ImportDialogView.vue#L15) | button | 上传本地文件 |  |
| [15:223](../../../web/src/features/import/ImportDialogView.vue#L15) | button | 从知识库选择 |  |
| [15:437](../../../web/src/features/import/ImportDialogView.vue#L15) | div (button) | 可拖入背景材料，也可从知识库选择；无材料可直接开始处理。 |  |
| [15:741](../../../web/src/features/import/ImportDialogView.vue#L15) | input | 选择背景材料 |  |
| [16:178](../../../web/src/features/import/ImportDialogView.vue#L16) | button | {{saveLabel??'保存并返回列表'}} | :disabled="saveDisabled" :title="saveDisabled?'请先导入会议主文件':undefined" |
| [16:336](../../../web/src/features/import/ImportDialogView.vue#L16) | button | {{retrying?'重试原导入请求':acknowledged?'查看处理进度':'开始处理'}} | :disabled="startDisabled" |
| [16:882](../../../web/src/features/import/ImportDialogView.vue#L16) | button | 关闭保存提示 |  |
| [16:1143](../../../web/src/features/import/ImportDialogView.vue#L16) | button | 继续导入 | :disabled="promptBusy" |
| [16:1227](../../../web/src/features/import/ImportDialogView.vue#L16) | button | 放弃本次导入 | :disabled="discardDisabled&#124;&#124;promptBusy" |
| [16:1341](../../../web/src/features/import/ImportDialogView.vue#L16) | button | 保存并返回列表 | :disabled="saveDisabled" |

## features/import/KnowledgeMaterialPicker.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [11:339](../../../web/src/features/import/KnowledgeMaterialPicker.vue#L11) | button | 关闭知识库选择 |  |
| [11:769](../../../web/src/features/import/KnowledgeMaterialPicker.vue#L11) | button | {{item.name}} | :key="item.id" :class="{active:props.picker.library.value===item.id}" |
| [11:2440](../../../web/src/features/import/KnowledgeMaterialPicker.vue#L11) | button | 加载更多 | v-if="!props.picker.review.value&&props.picker.cursor.value" |
| [11:2749](../../../web/src/features/import/KnowledgeMaterialPicker.vue#L11) | button | 取消 | :disabled="props.busy" |
| [11:2832](../../../web/src/features/import/KnowledgeMaterialPicker.vue#L11) | button | 添加到会议 | :disabled="props.busy&#124;&#124;!props.picker.selected.value.length" |

## features/import/MaterialImportSection.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [9:137](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 上传本地文件 | :disabled="locked&#124;&#124;state.busy&#124;&#124;!material.ready.value" |
| [9:261](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 从知识库选择 | :disabled="locked&#124;&#124;state.busy&#124;&#124;!material.ready.value" |
| [9:947](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 重新选择原文件 | v-if="job.needsFile" :disabled="locked&#124;&#124;state.busy&#124;&#124;!material.ready.value" |
| [9:1107](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | {{job.command?'查回原请求':'重试'}} | v-if="job.error&&!job.needsFile" :disabled="locked&#124;&#124;state.busy&#124;&#124;!material.ready.value" |
| [9:1346](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | {{job.kind==='BATCH'?'重新选择文件':'移除'}} | v-if="job.canRemove" |
| [9:2070](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 重试解析 | v-if="item.state==='FAILED'" |
| [9:2173](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 移除 |  |
| [9:2270](../../../web/src/features/import/MaterialImportSection.vue#L9) | div (button) | 继续拖入背景材料，也可从知识库选择 可拖入背景材料，也可从知识库选择；无材料可直接开始处理。 | :class="{'has-files':material.items.value.length&#124;&#124;material.jobs.value.length}" |
| [9:2703](../../../web/src/features/import/MaterialImportSection.vue#L9) | input | 选择背景材料 | :multiple="originalFile===null" :disabled="locked&#124;&#124;state.busy&#124;&#124;!material.ready.value" |
| [9:3026](../../../web/src/features/import/MaterialImportSection.vue#L9) | button | 重新读取材料 | :disabled="state.busy&#124;&#124;locked" |

## features/import/MaterialOnlyDialog.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [16:350](../../../web/src/features/import/MaterialOnlyDialog.vue#L16) | button | 关闭背景材料 | :disabled="closeBlocked" |
| [16:737](../../../web/src/features/import/MaterialOnlyDialog.vue#L16) | button | 完成 | :disabled="closeBlocked" |

## features/import/SavedFilesPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [35:111](../../../web/src/features/import/SavedFilesPanel.vue#L35) | button | 继续导入 |  |
| [35:240](../../../web/src/features/import/SavedFilesPanel.vue#L35) | button | 重新读取 |  |
| [37:287](../../../web/src/features/import/SavedFilesPanel.vue#L37) | button | 添加背景资料 | v-if="!hasTask" |
| [38:79](../../../web/src/features/import/SavedFilesPanel.vue#L38) | button | 重新读取 | :disabled="processing" |
| [42:348](../../../web/src/features/import/SavedFilesPanel.vue#L42) | button | 调整背景材料 | v-if="hasTask" |
| [42:470](../../../web/src/features/import/SavedFilesPanel.vue#L42) | button | 上传本地文件 |  |
| [42:540](../../../web/src/features/import/SavedFilesPanel.vue#L42) | button | 从知识库选择 |  |
| [42:819](../../../web/src/features/import/SavedFilesPanel.vue#L42) | button | 重新读取背景材料 |  |
| [43:386](../../../web/src/features/import/SavedFilesPanel.vue#L43) | button | {{queue.importCommand.state.value==='UNCERTAIN'?'重试原处理请求':'开始处理'}} | :disabled="!canStart" |
| [45:272](../../../web/src/features/import/SavedFilesPanel.vue#L45) | button | 重试处理 | v-if="failed" :disabled="!canStart" |
| [45:364](../../../web/src/features/import/SavedFilesPanel.vue#L45) | button | 重试原处理请求 | v-else-if="queue.importCommand.state.value==='UNCERTAIN'" :disabled="processing" |
| [45:504](../../../web/src/features/import/SavedFilesPanel.vue#L45) | button | 进入转写校正 | :disabled="!ready" |

## features/import/UploadImportPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [24:1810](../../../web/src/features/import/UploadImportPanel.vue#L24) | button | 重新读取导入草稿 |  |

## features/import/UploadImportView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [18:754](../../../web/src/features/import/UploadImportView.vue#L18) | button | 图标 / 文件入口 | :aria-label="'上移'+item.name" :disabled="index===0&#124;&#124;uploading&#124;&#124;blocked" |
| [18:1088](../../../web/src/features/import/UploadImportView.vue#L18) | button | 图标 / 文件入口 | :aria-label="'下移'+item.name" :disabled="index===items.length-1&#124;&#124;uploading&#124;&#124;blocked" |
| [18:1444](../../../web/src/features/import/UploadImportView.vue#L18) | button | 移除 | :disabled="!mutable&#124;&#124;blocked&#124;&#124;!item.canRemove" :aria-label="'移除'+item.name" |
| [19:68](../../../web/src/features/import/UploadImportView.vue#L19) | summary | 恢复与文件顺序 |  |
| [19:121](../../../web/src/features/import/UploadImportView.vue#L19) | label | 重新选择原文件 |  |
| [19:150](../../../web/src/features/import/UploadImportView.vue#L19) | input | 图标 / 文件入口 | :disabled="!ready&#124;&#124;blocked&#124;&#124;['QUEUED','VERIFYING','INITIALIZING','ABORTING'].includes(item.status)&#124;&#124;importBusy&#124;&#124;!mutable" |
| [19:444](../../../web/src/features/import/UploadImportView.vue#L19) | button | 查询原上传 | :disabled="!ready&#124;&#124;blocked&#124;&#124;uploading" |
| [19:569](../../../web/src/features/import/UploadImportView.vue#L19) | button | 终止未完成上传 | v-if="item.canAbort" :disabled="!mutable&#124;&#124;uploading&#124;&#124;blocked" |
| [19:716](../../../web/src/features/import/UploadImportView.vue#L19) | button | 上移 | :disabled="!mutable&#124;&#124;uploading&#124;&#124;blocked" |
| [19:839](../../../web/src/features/import/UploadImportView.vue#L19) | button | 下移 | :disabled="!mutable&#124;&#124;uploading&#124;&#124;blocked" |
| [19:961](../../../web/src/features/import/UploadImportView.vue#L19) | button | 从本批移除 | :disabled="!mutable&#124;&#124;blocked&#124;&#124;!item.canRemove" |
| [21:2](../../../web/src/features/import/UploadImportView.vue#L21) | div (button) | 添加会议主文件 | v-if="mutable" :aria-disabled="!canAdd" |
| [22:2](../../../web/src/features/import/UploadImportView.vue#L22) | input | 选择会议文件 | :disabled="!canAdd" |
| [24:144](../../../web/src/features/import/UploadImportView.vue#L24) | summary | 上传记录与恢复 |  |
| [24:170](../../../web/src/features/import/UploadImportView.vue#L24) | button | 重新读取上传记录 | :disabled="uploading&#124;&#124;importBusy" |
| [24:280](../../../web/src/features/import/UploadImportView.vue#L24) | button | 重试原导入请求 | v-if="pending" :disabled="blocked&#124;&#124;importBusy" |
| [24:541](../../../web/src/features/import/UploadImportView.vue#L24) | button | 放弃已拒绝的导入请求 | v-if="importState==='CONFLICT'&#124;&#124;importState==='REJECTED'" :disabled="importBusy" |
| [25:125](../../../web/src/features/import/UploadImportView.vue#L25) | button | 开始新一批导入 | :disabled="importBusy&#124;&#124;blocked" |

## features/meetings/DeletionNotice.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [13:2](../../../web/src/features/meetings/DeletionNotice.vue#L13) | button | 查看删除进度 | v-if="flow.blockedMeeting.value&&!flow.visible.value" |
| [15:84](../../../web/src/features/meetings/DeletionNotice.vue#L15) | button | 关闭删除状态 |  |
| [18:222](../../../web/src/features/meetings/DeletionNotice.vue#L18) | button | 取消 |  |
| [18:274](../../../web/src/features/meetings/DeletionNotice.vue#L18) | button | 确认删除 | :disabled="flow.busy.value" |
| [20:295](../../../web/src/features/meetings/DeletionNotice.vue#L20) | button | 刷新清理状态 | :disabled="flow.busy.value" |
| [21:4](../../../web/src/features/meetings/DeletionNotice.vue#L21) | button | 查回原删除请求 | v-if="original&&['PREPARED','UNCERTAIN'].includes(flow.command.state.value)" :disabled="flow.busy.value" |
| [22:4](../../../web/src/features/meetings/DeletionNotice.vue#L22) | button | 关闭并重新读取会议 | v-if="original&&['CONFLICT','REJECTED'].includes(flow.command.state.value)" :disabled="flow.busy.value" |

## features/meetings/FeedbackPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [43:53](../../../web/src/features/meetings/FeedbackPage.vue#L43) | RouterLink | 我的会议 |  |
| [44:3](../../../web/src/features/meetings/FeedbackPage.vue#L44) | button | 我的语料反馈 |  |
| [44:115](../../../web/src/features/meetings/FeedbackPage.vue#L44) | button | 演示引导 |  |
| [44:249](../../../web/src/features/meetings/FeedbackPage.vue#L44) | button | 重置演示数据 |  |
| [45:48](../../../web/src/features/meetings/FeedbackPage.vue#L45) | summary | 当前用户 |  |
| [45:305](../../../web/src/features/meetings/FeedbackPage.vue#L45) | button | 提交反馈 |  |
| [45:367](../../../web/src/features/meetings/FeedbackPage.vue#L45) | button | 重新读取反馈 |  |
| [45:426](../../../web/src/features/meetings/FeedbackPage.vue#L45) | button | {{reviewing?'返回我的反馈':'反馈审核队列'}} | v-if="admin" |
| [45:530](../../../web/src/features/meetings/FeedbackPage.vue#L45) | a | 退出登录 |  |
| [49:67](../../../web/src/features/meetings/FeedbackPage.vue#L49) | button | 上一页反馈 | :disabled="loading&#124;&#124;cursors.length<2" |
| [49:145](../../../web/src/features/meetings/FeedbackPage.vue#L49) | button | 下一页反馈 | :disabled="loading&#124;&#124;!overview.nextCursor" |
| [50:3](../../../web/src/features/meetings/FeedbackPage.vue#L50) | button | 重新读取反馈 | v-if="error" |
| [53:182](../../../web/src/features/meetings/FeedbackPage.vue#L53) | button | 关闭反馈详情 | :disabled="locked" |
| [57:297](../../../web/src/features/meetings/FeedbackPage.vue#L57) | button | 提交补充 | :disabled="locked&#124;&#124;composing&#124;&#124;!supplement.trim()" |
| [58:661](../../../web/src/features/meetings/FeedbackPage.vue#L58) | button | 提交审核 | v-if="detail.feedback.state==='PENDING'" :disabled="locked&#124;&#124;composing&#124;&#124;!reviewReason.trim()" |
| [58:851](../../../web/src/features/meetings/FeedbackPage.vue#L58) | button | 撤销审核 | :disabled="locked&#124;&#124;composing" |
| [60:32](../../../web/src/features/meetings/FeedbackPage.vue#L60) | button | 放弃已拒绝的原请求 | v-if="actions.pending.value&&['CONFLICT','REJECTED'].includes(actions.command.state.value)" :disabled="actions.busy.value" |
| [60:210](../../../web/src/features/meetings/FeedbackPage.vue#L60) | button | 关闭 | :disabled="locked" |
| [60:281](../../../web/src/features/meetings/FeedbackPage.vue#L60) | button | {{actions.command.state.value==='ACKNOWLEDGED'?'重新核实已受理反馈':'重试原反馈处理'}} | v-if="actions.pending.value" :disabled="actions.busy.value&#124;&#124;!actions.available.value&#124;&#124;!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(actions.command.state.value)" |
| [61:183](../../../web/src/features/meetings/FeedbackPage.vue#L61) | button | 关闭提交反馈 | :disabled="submitBusy&#124;&#124;submissionLocked" |
| [61:1220](../../../web/src/features/meetings/FeedbackPage.vue#L61) | button | 取消 | :disabled="submitBusy&#124;&#124;submissionLocked" |
| [61:1313](../../../web/src/features/meetings/FeedbackPage.vue#L61) | button | 放弃已拒绝的原反馈 | v-if="['CONFLICT','REJECTED'].includes(submission.command.state.value)" |
| [61:1450](../../../web/src/features/meetings/FeedbackPage.vue#L61) | button | {{submissionLocked?'重试原反馈':'提交'}} | :disabled="submitBusy&#124;&#124;composing&#124;&#124;!submitComment.trim()" |
| [62:121](../../../web/src/features/meetings/FeedbackPage.vue#L62) | button | 知道了 |  |

## features/meetings/FeedbackTableView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [14:53](../../../web/src/features/meetings/FeedbackTableView.vue#L14) | button (tab) | {{label}} {{counts[state??'ALL']}} | :key="state??'ALL'" :class="{active:selected===state}" :aria-selected="selected===state" :disabled="loading" |
| [16:171](../../../web/src/features/meetings/FeedbackTableView.vue#L16) | button | {{item.comment}} |  |

## features/meetings/FuturePage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [14:62](../../../web/src/features/meetings/FuturePage.vue#L14) | RouterLink | 我的会议 |  |
| [14:435](../../../web/src/features/meetings/FuturePage.vue#L14) | RouterLink | 返回我的会议 |  |
| [14:701](../../../web/src/features/meetings/FuturePage.vue#L14) | button (tab) | {{name}} | :key="name" :class="{active:tab===name}" :aria-selected="tab===name" |
| [14:884](../../../web/src/features/meetings/FuturePage.vue#L14) | button | 提交反馈 |  |
| [14:1814](../../../web/src/features/meetings/FuturePage.vue#L14) | button | 下一页反馈 | v-if="feedbackNext" :disabled="feedbackLoading" |
| [14:1935](../../../web/src/features/meetings/FuturePage.vue#L14) | button | 重新读取反馈 | :disabled="feedbackLoading" |
| [14:2982](../../../web/src/features/meetings/FuturePage.vue#L14) | button | 取消 | :disabled="feedbackBusy&#124;&#124;feedbackSubmission.pending.value" |
| [14:3110](../../../web/src/features/meetings/FuturePage.vue#L14) | button | 放弃已拒绝的原反馈 | v-if="['CONFLICT','REJECTED'].includes(feedbackSubmission.command.state.value)" |
| [14:3288](../../../web/src/features/meetings/FuturePage.vue#L14) | button | {{feedbackSubmission.pending.value?'重试原反馈':'提交'}} | :disabled="feedbackBusy&#124;&#124;!feedbackComment.trim()" |

## features/meetings/FuturePanels.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [17:199](../../../web/src/features/meetings/FuturePanels.vue#L17) | button | 关闭面板 |  |
| [18:105](../../../web/src/features/meetings/FuturePanels.vue#L18) | button | 知识库 | :class="{active:tab==='library'}" |
| [18:194](../../../web/src/features/meetings/FuturePanels.vue#L18) | button | 已选文件（0） | :class="{active:tab==='selected'}" |
| [18:295](../../../web/src/features/meetings/FuturePanels.vue#L18) | button | 清空已选 |  |
| [18:468](../../../web/src/features/meetings/FuturePanels.vue#L18) | button | 选择本地文件 |  |
| [19:230](../../../web/src/features/meetings/FuturePanels.vue#L19) | button | ＋ 添加章节 | :disabled="sections.length>=8" |
| [19:480](../../../web/src/features/meetings/FuturePanels.vue#L19) | button | 移除 | :disabled="sections.length===1" |
| [21:59](../../../web/src/features/meetings/FuturePanels.vue#L21) | button | 说话人信息 |  |
| [21:100](../../../web/src/features/meetings/FuturePanels.vue#L21) | button | 合并声道 |  |
| [21:142](../../../web/src/features/meetings/FuturePanels.vue#L21) | button | 拆分片段 |  |
| [21:425](../../../web/src/features/meetings/FuturePanels.vue#L21) | button | 撤销上次操作 |  |
| [22:62](../../../web/src/features/meetings/FuturePanels.vue#L22) | button | 纠错建议 |  |
| [22:102](../../../web/src/features/meetings/FuturePanels.vue#L22) | button | 更多候选 |  |
| [22:307](../../../web/src/features/meetings/FuturePanels.vue#L22) | button | 接受建议 |  |
| [22:349](../../../web/src/features/meetings/FuturePanels.vue#L22) | button | 拒绝建议 |  |
| [22:391](../../../web/src/features/meetings/FuturePanels.vue#L22) | button | 恢复原文 |  |
| [22:433](../../../web/src/features/meetings/FuturePanels.vue#L22) | RouterLink | 我的语料反馈 |  |
| [26:104](../../../web/src/features/meetings/FuturePanels.vue#L26) | button | 取消 | :disabled="busy" |
| [26:177](../../../web/src/features/meetings/FuturePanels.vue#L26) | button | {{kind==='template'?'保存并使用':kind==='regenerate'?'重新生成':kind==='delete'?'确认删除':kind==='topic'?'创建专题':kind==='assign'?'归入专题':['materials','library','local'].includes(kind)?'添加到会议':'保存'}} | :disabled="busy&#124;&#124;kind==='delete'&&!deleteMeetingId&#124;&#124;kind==='assign'&&!selectedTopic&#124;&#124;kind==='topic'&&!title.trim()" :title="kind==='delete'?undefined:service" |

## features/meetings/MeetingCreatePanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [14:4](../../../web/src/features/meetings/MeetingCreatePanel.vue#L14) | button | {{label}} | :disabled="busy&#124;&#124;blocked&#124;&#124;terminal" |
| [15:4](../../../web/src/features/meetings/MeetingCreatePanel.vue#L15) | button | {{discovering?'正在查询…':'查询原请求'}} | v-if="state==='UNCERTAIN'" :disabled="busy&#124;&#124;discovering" |
| [16:4](../../../web/src/features/meetings/MeetingCreatePanel.vue#L16) | button | 重新读取恢复记录 | v-if="blocked" :disabled="busy" |
| [17:4](../../../web/src/features/meetings/MeetingCreatePanel.vue#L17) | button | 重新检查登录 | v-if="terminal" :disabled="busy" |
| [18:4](../../../web/src/features/meetings/MeetingCreatePanel.vue#L18) | button | 暂不创建 | v-if="state==='EMPTY'&&!blocked" :disabled="busy" |

## features/meetings/MeetingDateRange.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [23:80](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | summary | 会议日期 |  |
| [23:442](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | {{label}} | :key="key" :class="{active:draft.preset===key}" |
| [23:900](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | 图标 / 文件入口 | :aria-label="cal.label+'日历上个月'" |
| [23:1079](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | 图标 / 文件入口 | :aria-label="cal.label+'日历下个月'" |
| [23:1487](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | {{cell.n}} | :class="{selected:cell.selected,'in-range':cell.inRange}" :aria-label="cal.label+'日期 '+cell.date" :aria-pressed="cell.selected" :disabled="cell.blocked" |
| [23:1827](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | 清空 |  |
| [23:1896](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | 取消 |  |
| [23:1960](../../../web/src/features/meetings/MeetingDateRange.vue#L23) | button | 应用 |  |

## features/meetings/MeetingDetail.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [77:76](../../../web/src/features/meetings/MeetingDetail.vue#L77) | RouterLink | 返回列表 |  |
| [77:417](../../../web/src/features/meetings/MeetingDetail.vue#L77) | span (button) | 查看会议状态与处理记录 | :class="['tag meeting-status-control',navigation.value.value?.privateSections?.finalConfirmationId?'amber':'blue']" |
| [77:1476](../../../web/src/features/meetings/MeetingDetail.vue#L77) | button | 编辑会议信息 | v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished&&!['confirm','publish'].includes(activeStage)" |
| [77:1657](../../../web/src/features/meetings/MeetingDetail.vue#L77) | button | 保存 | v-if="navigation.value.value?.relation==='OWNER'&&!effectivePublished" :disabled="activeStage==='confirm'&&!minutesRef?.canSaveInformation&#124;&#124;activeStage==='publish'&&!minutesRef?.canSavePublicationConfiguration" |
| [78:126](../../../web/src/features/meetings/MeetingDetail.vue#L78) | button | 重新检查登录 |  |
| [79:149](../../../web/src/features/meetings/MeetingDetail.vue#L79) | RouterLink | 返回会议列表 |  |
| [80:109](../../../web/src/features/meetings/MeetingDetail.vue#L80) | button | 重新读取权限与内容 |  |
| [82:101](../../../web/src/features/meetings/MeetingDetail.vue#L82) | button | 重试 |  |
| [85:72](../../../web/src/features/meetings/MeetingDetail.vue#L85) | button | 重新读取导航 |  |
| [86:203](../../../web/src/features/meetings/MeetingDetail.vue#L86) | button | 重新检查恢复记录 |  |
| [92:327](../../../web/src/features/meetings/MeetingDetail.vue#L92) | button | 背景材料 |  |
| [95:256](../../../web/src/features/meetings/MeetingDetail.vue#L95) | button | 刷新 |  |
| [95:363](../../../web/src/features/meetings/MeetingDetail.vue#L95) | button | 刷新纪要 |  |
| [95:426](../../../web/src/features/meetings/MeetingDetail.vue#L95) | button | 候选与版本 | v-if="navigation.value.value?.relation==='OWNER'" |
| [95:674](../../../web/src/features/meetings/MeetingDetail.vue#L95) | button | 转写版本与语音审阅 | :disabled="!transcriptRef?.canReview" |

## features/meetings/MeetingInfoDrawer.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [24:88](../../../web/src/features/meetings/MeetingInfoDrawer.vue#L24) | button | 图标 / 文件入口 | :aria-label="closeLabel" :disabled="preventClose" |

## features/meetings/MeetingInfoEditor.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [70:186](../../../web/src/features/meetings/MeetingInfoEditor.vue#L70) | button | 图标 / 文件入口 | :aria-label="entry.field==='NAME'?'采纳名称':'采纳摘要'" :disabled="!suggestions.canDecide.value" |
| [70:441](../../../web/src/features/meetings/MeetingInfoEditor.vue#L70) | button | 图标 / 文件入口 | :aria-label="entry.field==='NAME'?'拒绝名称建议':'拒绝摘要建议'" :disabled="!suggestions.canDecide.value" |
| [73:36](../../../web/src/features/meetings/MeetingInfoEditor.vue#L73) | button | 重新读取建议 | :disabled="suggestions.loading.value&#124;&#124;suggestions.busy.value" |
| [76:68](../../../web/src/features/meetings/MeetingInfoEditor.vue#L76) | button | 重试原建议请求 | v-if="suggestionPending&#124;&#124;suggestions.command.state.value==='ACKNOWLEDGED'&&suggestionError" :disabled="suggestions.busy.value&#124;&#124;suggestions.blocked.value" |
| [77:4](../../../web/src/features/meetings/MeetingInfoEditor.vue#L77) | button | 重新核对当前建议 | v-if="['CONFLICT','REJECTED'].includes(suggestions.command.state.value)" |
| [81:84](../../../web/src/features/meetings/MeetingInfoEditor.vue#L81) | button | 重新加载编辑信息 |  |

## features/meetings/MeetingInfoForm.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [42:454](../../../web/src/features/meetings/MeetingInfoForm.vue#L42) | button | 更多会议分类 | v-if="categoryMore" :disabled="optionsBusy" |
| [44:148](../../../web/src/features/meetings/MeetingInfoForm.vue#L44) | button | 添加参会人 |  |
| [44:225](../../../web/src/features/meetings/MeetingInfoForm.vue#L44) | button | {{speakerBusy?'读取发言人…':'从发言人获取'}} | :disabled="speakerBusy" |
| [44:539](../../../web/src/features/meetings/MeetingInfoForm.vue#L44) | button | 图标 / 文件入口 | :aria-label="'移除 '+id" |
| [44:830](../../../web/src/features/meetings/MeetingInfoForm.vue#L44) | button | 图标 / 文件入口 | :aria-label="'移除外部人员 '+name" |
| [45:132](../../../web/src/features/meetings/MeetingInfoForm.vue#L45) | button | 确认已保存名单 | :disabled="busy&#124;&#124;blocked&#124;&#124;unresolved" |
| [46:340](../../../web/src/features/meetings/MeetingInfoForm.vue#L46) | button | 关闭人员选择 |  |
| [47:65](../../../web/src/features/meetings/MeetingInfoForm.vue#L47) | button (tab) | 公司内部人员 | :class="{primary:peopleTab==='internal'}" :aria-selected="peopleTab==='internal'" |
| [47:237](../../../web/src/features/meetings/MeetingInfoForm.vue#L47) | button (tab) | 外部人员 | :class="{primary:peopleTab==='external'}" :aria-selected="peopleTab==='external'" |
| [48:294](../../../web/src/features/meetings/MeetingInfoForm.vue#L48) | button | 全部部门 | :class="{active:!department}" |
| [48:389](../../../web/src/features/meetings/MeetingInfoForm.vue#L48) | button | {{dept.name}} | :key="dept.id" :class="{active:department===dept.id}" |
| [48:1105](../../../web/src/features/meetings/MeetingInfoForm.vue#L48) | button | 下一页人员 | v-if="peopleMore" :disabled="optionsBusy" |
| [48:1315](../../../web/src/features/meetings/MeetingInfoForm.vue#L48) | button | 重新读取目录 | :disabled="optionsBusy" |
| [49:396](../../../web/src/features/meetings/MeetingInfoForm.vue#L49) | button | 加入待选名单 |  |
| [49:597](../../../web/src/features/meetings/MeetingInfoForm.vue#L49) | button | 图标 / 文件入口 | :aria-label="'移除待选外部人员 '+name" |
| [50:132](../../../web/src/features/meetings/MeetingInfoForm.vue#L50) | button | 取消 |  |
| [50:198](../../../web/src/features/meetings/MeetingInfoForm.vue#L50) | button | 添加所选人员 | :disabled="optionsBusy" |
| [53:76](../../../web/src/features/meetings/MeetingInfoForm.vue#L53) | button | 取消 | :disabled="busy&#124;&#124;blocked&#124;&#124;unresolved" |
| [53:168](../../../web/src/features/meetings/MeetingInfoForm.vue#L53) | button | 重试原请求 | v-if="unresolved" :disabled="busy&#124;&#124;blocked" |
| [53:277](../../../web/src/features/meetings/MeetingInfoForm.vue#L53) | button | 保存 | :disabled="busy&#124;&#124;blocked&#124;&#124;conflict&#124;&#124;state==='CONFLICT'&#124;&#124;state==='REJECTED'" |
| [54:86](../../../web/src/features/meetings/MeetingInfoForm.vue#L54) | button | 重试原会议信息请求 | :disabled="busy&#124;&#124;blocked" |
| [55:80](../../../web/src/features/meetings/MeetingInfoForm.vue#L55) | button | 保留草稿并应用到最新版本 | v-if="conflict" :disabled="busy&#124;&#124;unresolved&#124;&#124;blocked" |
| [55:200](../../../web/src/features/meetings/MeetingInfoForm.vue#L55) | button | 放弃本地修改 | :disabled="busy&#124;&#124;unresolved&#124;&#124;blocked" |

## features/meetings/MeetingLibraryPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [19:90](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | RouterLink | 我的会议 |  |
| [19:247](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | button | 我的语料反馈 |  |
| [19:359](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | button | 演示引导 |  |
| [19:489](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | button | 重置演示数据 |  |
| [19:647](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | summary | 当前用户 |  |
| [19:903](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | button | 重新读取 |  |
| [19:962](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | RouterLink | 会议知识库 |  |
| [19:1035](../../../web/src/features/meetings/MeetingLibraryPage.vue#L19) | a | 退出登录 |  |
| [21:272](../../../web/src/features/meetings/MeetingLibraryPage.vue#L21) | RouterLink | {{group.context.libraryName}} | :to="'/meeting-library/'+encodeURIComponent(group.context.libraryId)" |
| [23:71](../../../web/src/features/meetings/MeetingLibraryPage.vue#L23) | button | {{library.error.value?'重试当前页':'继续读取会议成果'}} | :disabled="library.loading.value" |
| [25:97](../../../web/src/features/meetings/MeetingLibraryPage.vue#L25) | button | 知道了 |  |

## features/meetings/MeetingLibraryView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [6:144](../../../web/src/features/meetings/MeetingLibraryView.vue#L6) | button | 返回我的会议 |  |
| [8:104](../../../web/src/features/meetings/MeetingLibraryView.vue#L8) | button | 返回会议详情 |  |
| [9:268](../../../web/src/features/meetings/MeetingLibraryView.vue#L9) | button | {{row.meeting.name??'未命名会议'}} |  |

## features/meetings/MeetingList.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [15:185](../../../web/src/features/meetings/MeetingList.vue#L15) | button | {{meeting.name??'未命名会议'}} |  |
| [15:377](../../../web/src/features/meetings/MeetingList.vue#L15) | button | 图标 / 文件入口 | :aria-label="'复制编号 '+meeting.id" |
| [15:1213](../../../web/src/features/meetings/MeetingList.vue#L15) | button | {{topicNames[meeting.id]??'未归入专题'}} | :title="topicNames[meeting.id]??'未归入专题'" :aria-label="'调整专题：'+(topicNames[meeting.id]??'未归入专题')" |
| [15:1627](../../../web/src/features/meetings/MeetingList.vue#L15) | button | 删除 | :disabled="meeting.allowedActions?!meeting.allowedActions.includes('DELETE'):meeting.stage==='PUBLISHED'" :title="meeting.stage==='PUBLISHED'?'录音中、发布中或曾发布的会议不可删除':undefined" |
| [15:1932](../../../web/src/features/meetings/MeetingList.vue#L15) | button | {{stage(meeting)==='TRANSCRIPT_FAILED'?'重试转写':'重试纪要'}} | v-if="stage(meeting)==='TRANSCRIPT_FAILED'&#124;&#124;stage(meeting)==='MINUTES_FAILED'" |
| [15:2172](../../../web/src/features/meetings/MeetingList.vue#L15) | button | 重试发布 | v-if="stage(meeting)==='PUBLISH_FAILED'" |

## features/meetings/MeetingProgress.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [10:60](../../../web/src/features/meetings/MeetingProgress.vue#L10) | button | {{index+1}} {{label}} | :aria-label="label" :key="label" :class="{active:active===stages[index]&#124;&#124;active==='published'&&index===4,done:complete[index]}" :data-step="index+1" :data-complete="complete[index]" :aria-current="active===stages[index]&#124;&#124;active==='published'&&index===4?'step':undefined" :disabled="(publishedObserved&#124;&#124;value.stage==='PUBLISHED')&&index<4&#124;&#124;!enabled[index]" |

## features/meetings/MeetingPrototypeStage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [23:71](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L23) | RouterLink | 我的会议 |  |
| [26:39](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L26) | RouterLink | 返回列表 |  |
| [27:44](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L27) | button | 编辑会议信息 |  |
| [27:109](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L27) | button | 保存 |  |
| [27:174](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L27) | button | 更多操作 |  |
| [31:5](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L31) | button | 材料导入 |  |
| [32:5](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L32) | button | 转写校正 | :class="{done:stage==='minutes'&#124;&#124;stage==='confirm',active:stage==='transcript'}" :aria-current="stage==='transcript'?'step':undefined" :disabled="stage==='transcript'" |
| [33:5](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L33) | button | AI纪要 | :class="{done:stage==='confirm',active:stage==='minutes'}" :aria-current="stage==='minutes'?'step':undefined" :disabled="stage==='transcript'&#124;&#124;stage==='confirm'" |
| [34:5](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L34) | button | 会议确认 | :class="{active:stage==='confirm'}" :aria-current="stage==='confirm'?'step':undefined" :disabled="stage!=='confirm'" |
| [35:5](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L35) | button | 知识库发布 |  |
| [42:72](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L42) | button | 编辑 |  |
| [45:192](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L45) | button | 重新读取转写 |  |
| [46:42](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L46) | summary | 转写历史与原操作恢复 |  |
| [46:126](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L46) | button | 读取历史版本 |  |
| [53:65](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L53) | button | 核心要素 | :class="{active:minutesView==='elements'}" :aria-pressed="minutesView==='elements'" |
| [53:214](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L53) | button | 会议纪要 | :class="{active:minutesView==='body'}" :aria-pressed="minutesView==='body'" |
| [55:62](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L55) | button | {{group.name}} | :key="group.name" :class="{selected:selectedCategory===group.name}" :aria-current="selectedCategory===group.name?'location':undefined" |
| [56:246](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L56) | button | ♧ 局部重新生成 |  |
| [56:439](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L56) | button | ✎ 编辑 |  |
| [58:281](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L58) | button | 总结模板 |  |
| [58:353](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L58) | button | 重新生成 |  |
| [58:409](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L58) | button | ✎ 编辑 |  |
| [60:98](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L60) | button | 返回转写校正 |  |
| [60:165](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L60) | button | 进入会议确认 |  |
| [65:93](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L65) | button | 返回纪要编辑 |  |
| [67:103](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L67) | button | 返回纪要编辑 |  |
| [67:167](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L67) | button | 最终确认并继续发布 |  |
| [71:181](../../../web/src/features/meetings/MeetingPrototypeStage.vue#L71) | button | 关闭 |  |

## features/meetings/RecordingPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [36:62](../../../web/src/features/meetings/RecordingPage.vue#L36) | RouterLink | 我的会议 |  |
| [36:307](../../../web/src/features/meetings/RecordingPage.vue#L36) | button | 返回会议列表 |  |
| [37:513](../../../web/src/features/meetings/RecordingPage.vue#L37) | button | 图标 / 文件入口 | :aria-label="label" :title="label" :disabled="saving&#124;&#124;finished&#124;&#124;!session.current.value" |
| [37:757](../../../web/src/features/meetings/RecordingPage.vue#L37) | button | 中断本段 | v-if="active&#124;&#124;paused&#124;&#124;saving" :disabled="saving&#124;&#124;failed" |
| [37:901](../../../web/src/features/meetings/RecordingPage.vue#L37) | button | 结束录音 | :disabled="saving&#124;&#124;failed&#124;&#124;finished&#124;&#124;!parts.length" |
| [39:340](../../../web/src/features/meetings/RecordingPage.vue#L39) | RouterLink | 改为导入文件 |  |
| [39:550](../../../web/src/features/meetings/RecordingPage.vue#L39) | button | 恢复上传 | :disabled="busy" |
| [39:658](../../../web/src/features/meetings/RecordingPage.vue#L39) | button | 填写会议信息 | :disabled="!meeting" |
| [39:786](../../../web/src/features/meetings/RecordingPage.vue#L39) | button | 添加背景资料 | :disabled="!meeting" |
| [40:155](../../../web/src/features/meetings/RecordingPage.vue#L40) | button | 回到最新 |  |
| [40:852](../../../web/src/features/meetings/RecordingPage.vue#L40) | button | 重试读取字幕 | :disabled="subtitles.loading.value" |
| [41:1021](../../../web/src/features/meetings/RecordingPage.vue#L41) | button | 关闭背景材料 | :disabled="materialsState.closeBlocked" |
| [41:1436](../../../web/src/features/meetings/RecordingPage.vue#L41) | button | 完成 | :disabled="materialsState.closeBlocked" |
| [42:363](../../../web/src/features/meetings/RecordingPage.vue#L42) | button | 关闭中断录音确认 | :disabled="busy" |
| [42:693](../../../web/src/features/meetings/RecordingPage.vue#L42) | button | 取消 | :disabled="busy" |
| [42:781](../../../web/src/features/meetings/RecordingPage.vue#L42) | button | 保存本段 | :disabled="busy" |
| [43:331](../../../web/src/features/meetings/RecordingPage.vue#L43) | button | 关闭结束录音确认 | :disabled="busy" |
| [43:837](../../../web/src/features/meetings/RecordingPage.vue#L43) | button | 取消 | :disabled="busy" |
| [43:919](../../../web/src/features/meetings/RecordingPage.vue#L43) | button | 重试保存 | v-if="endError&&lastEnd" :disabled="busy" |
| [43:1027](../../../web/src/features/meetings/RecordingPage.vue#L43) | button | 保存 | :disabled="busy&#124;&#124;!!endError" |
| [43:1111](../../../web/src/features/meetings/RecordingPage.vue#L43) | button | 保存并开始转写 | :disabled="busy&#124;&#124;!!endError" |

## features/meetings/TopicDialogView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [13:58](../../../web/src/features/meetings/TopicDialogView.vue#L13) | button | 关闭 | :disabled="locked" |
| [23:70](../../../web/src/features/meetings/TopicDialogView.vue#L23) | button | 快速创建一级专题 | :disabled="locked" |
| [25:32](../../../web/src/features/meetings/TopicDialogView.vue#L25) | button | 知道了 | v-if="blocked" |
| [25:121](../../../web/src/features/meetings/TopicDialogView.vue#L25) | button | 放弃已拒绝的原请求 | v-if="pending&&['CONFLICT','REJECTED'].includes(state)" :disabled="busy" |
| [25:257](../../../web/src/features/meetings/TopicDialogView.vue#L25) | button | 取消 | :disabled="locked" |
| [25:324](../../../web/src/features/meetings/TopicDialogView.vue#L25) | button | {{state==='ACKNOWLEDGED'?'重新核实已受理专题':'重试原专题请求'}} | v-if="pending" :disabled="busy&#124;&#124;composing&#124;&#124;!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(state)" |
| [25:594](../../../web/src/features/meetings/TopicDialogView.vue#L25) | button | {{mode==='create'?'创建专题':mode==='delete'?'删除':batch?'应用':mode==='assign'?'确定':'保存'}} | v-else-if="mode!=='delete'&#124;&#124;deleteCount!==null" :class="mode==='delete'?'danger':'primary'" :disabled="busy&#124;&#124;composing" |

## features/meetings/WorkspaceTaskPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [11:116](../../../web/src/features/meetings/WorkspaceTaskPage.vue#L11) | button | 刷新任务 | :disabled="loading" |
| [14:81](../../../web/src/features/meetings/WorkspaceTaskPage.vue#L14) | button | 重试 |  |
| [14:137](../../../web/src/features/meetings/WorkspaceTaskPage.vue#L14) | button | 回到第一页 |  |
| [19:30](../../../web/src/features/meetings/WorkspaceTaskPage.vue#L19) | button | 第一页 |  |
| [19:85](../../../web/src/features/meetings/WorkspaceTaskPage.vue#L19) | button | 下一页 | :disabled="!page.hasMore" |

## features/minutes/ActionOwnerSelection.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [8:212](../../../web/src/features/minutes/ActionOwnerSelection.vue#L8) | button | 查找负责人 | :disabled="!enabled&#124;&#124;state.busy.value" |
| [8:492](../../../web/src/features/minutes/ActionOwnerSelection.vue#L8) | button | 选为负责人： {{person.displayName}} · {{person.department??person.stableId}} {{person.active?'':'（不可选择）'}} | :disabled="!enabled&#124;&#124;!person.active&#124;&#124;state.busy.value" |
| [8:714](../../../web/src/features/minutes/ActionOwnerSelection.vue#L8) | button | 下一页负责人 | v-if="state.nextCursor.value" :disabled="!enabled&#124;&#124;state.busy.value" |
| [8:927](../../../web/src/features/minutes/ActionOwnerSelection.vue#L8) | button | 保存所选负责人 | :disabled="!enabled&#124;&#124;!state.selected.value&#124;&#124;state.busy.value" |
| [8:1068](../../../web/src/features/minutes/ActionOwnerSelection.vue#L8) | button | 清除企业负责人身份 | v-if="hasIdentity" :disabled="!enabled&#124;&#124;state.busy.value" |

## features/minutes/GovernanceNotice.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [15:2](../../../web/src/features/minutes/GovernanceNotice.vue#L15) | button | 查看撤回 / 废止进度 | v-if="flow.blockedMeeting.value&&!flow.visible.value" |
| [17:62](../../../web/src/features/minutes/GovernanceNotice.vue#L17) | button | 关闭治理状态 |  |
| [22:562](../../../web/src/features/minutes/GovernanceNotice.vue#L22) | button | 暂不操作 |  |
| [22:630](../../../web/src/features/minutes/GovernanceNotice.vue#L22) | button | 确认 {{action==='WITHDRAW'?'撤回':'废止'}} | :disabled="!canSend" |
| [23:336](../../../web/src/features/minutes/GovernanceNotice.vue#L23) | button | 刷新处理状态 | :disabled="flow.busy.value" |
| [24:4](../../../web/src/features/minutes/GovernanceNotice.vue#L24) | button | 查回原请求 | v-if="original&&['PREPARED','UNCERTAIN'].includes(flow.command.state.value)" :disabled="flow.busy.value" |
| [25:26](../../../web/src/features/minutes/GovernanceNotice.vue#L25) | summary | 治理历史 |  |

## features/minutes/KnowledgePublishDemoPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [95:71](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L95) | RouterLink | 我的会议 |  |
| [98:39](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L98) | RouterLink | 返回列表 |  |
| [99:44](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L99) | button | 编辑会议信息 | :disabled="locked" |
| [99:128](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L99) | button | 保存 | :disabled="locked" |
| [99:212](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L99) | button | 更多操作 | :disabled="locked" |
| [103:5](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L103) | button | 材料导入 |  |
| [104:5](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L104) | button | 转写校正 |  |
| [105:5](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L105) | button | AI纪要 |  |
| [106:5](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L106) | button | 会议确认 |  |
| [107:5](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L107) | button | 知识库发布 |  |
| [114:201](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L114) | button | 清空搜索 | v-if="libraryQuery" :disabled="locked" |
| [117:8](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L117) | button (option) | ▱ {{library.name}} {{library.description}} › | :key="library.id" :class="{selected:library.id===selectedLibrary}" :disabled="locked" :aria-label="library.name" :aria-selected="library.id===selectedLibrary" |
| [120:159](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L120) | button | 返回根目录 | v-if="folderParent" :disabled="locked" |
| [120:666](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L120) | button | 进入 | v-if="folder.children?.length" :disabled="locked" :aria-label="&#96;进入 ${folder.name}&#96;" |
| [133:25](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L133) | button | 最终文档预览 |  |
| [136:246](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L136) | button | {{publishLabel}} | :disabled="publishDisabled" |
| [138:613](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L138) | a | 查看正式纪要 ↗ | :href="&#96;#knowledge-document-${formalDocument.id}&#96;" |
| [144:184](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L144) | button | 上一步 | :disabled="locked" |
| [144:279](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L144) | button | {{publishLabel}} | :disabled="publishDisabled" |
| [146:360](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L146) | button | 关闭预览 |  |
| [148:177](../../../web/src/features/minutes/KnowledgePublishDemoPage.vue#L148) | button | 关闭 |  |

## features/minutes/MinutesExportPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [16:1](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | {{review?'导出':'下载'}} | :class="{'ghost m19-published-download':iconOnly}" :aria-label="iconOnly?'下载纪要':undefined" :disabled="dirty&#124;&#124;!exports.available.value" :title="dirty?'请先保存修改':'导出当前已保存版本'" |
| [16:521](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | 关闭 |  |
| [16:1416](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | 查回原导出 | v-if="unknown&&['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(exports.command.state.value)" :disabled="exports.busy.value&#124;&#124;!exports.available.value" |
| [16:1645](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | 重新选择版本 | v-if="['CONFLICT','REJECTED'].includes(exports.command.state.value)" |
| [16:1798](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | 刷新进度 | v-if="exports.job.value" :disabled="exports.busy.value&#124;&#124;!exports.available.value" |
| [16:1946](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | a | 下载文件 | v-if="exports.download.value&&sameSource" :href="exports.download.value" |
| [16:2059](../../../web/src/features/minutes/MinutesExportPanel.vue#L16) | button | {{exports.job.value?'再次导出':'生成文件'}} | v-if="!exports.pending.value" :disabled="dirty&#124;&#124;exports.busy.value&#124;&#124;!exports.available.value" |

## features/minutes/MinutesGenerationPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [42:430](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L42) | button | 重新读取模板 | v-if="!state.templates.value" :disabled="state.loading.value" |
| [48:59](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L48) | button | 恢复原请求 | v-if="pending" :disabled="state.busy.value&#124;&#124;state.blocked.value" |
| [49:4](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L49) | button | 清除已拒绝请求并重新读取 | v-if="['CONFLICT','REJECTED'].includes(state.command.state.value)" :disabled="state.busy.value" |
| [51:4](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L51) | button | 刷新生成条件 | :disabled="state.loading.value&#124;&#124;state.busy.value" |
| [54:26](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L54) | button | 取消 |  |
| [54:94](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L54) | button | {{regionMode?'开始生成':'生成完整纪要'}} | :aria-label="regionMode?'开始生成区域候选':'生成完整纪要'" :disabled="generationDisabled" |
| [55:1328](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | button | 重新读取模板 | v-if="!state.templates.value&&!state.current.value.saved.templateRevisionId" :disabled="state.loading.value" |
| [55:1688](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | button | {{regionMode?'生成区域候选':'生成完整纪要'}} | :disabled="generationDisabled" |
| [55:2000](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | summary | 状态与恢复 |  |
| [55:2044](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | button | 恢复原请求 | v-if="pending" :disabled="state.busy.value&#124;&#124;state.blocked.value" |
| [55:2164](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | button | 清除已拒绝请求并重新读取 | v-if="['CONFLICT','REJECTED'].includes(state.command.state.value)" :disabled="state.busy.value" |
| [55:2318](../../../web/src/features/minutes/MinutesGenerationPanel.vue#L55) | button | 刷新生成条件 | :disabled="state.loading.value&#124;&#124;state.busy.value" |

## features/minutes/MinutesHistory.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [9:180](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 读取历史 | :disabled="busy&#124;&#124;!enabled" |
| [9:365](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 查看 |  |
| [9:432](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 下一页 | v-if="page?.hasMore" :disabled="busy" |
| [9:664](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 恢复此正文 | :disabled="locked" |
| [9:1012](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 上一页要素 | :disabled="elementPage===0" |
| [9:1097](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 下一页要素 | :disabled="!Object.values(detail.elements).some(x=>(elementPage+1)*20<x.length)" |
| [9:1235](../../../web/src/features/minutes/MinutesHistory.vue#L9) | button | 恢复此要素集合 | :disabled="locked" |

## features/minutes/MinutesSensitive.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [29:458](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 重新读取排除设置 | :disabled="loading&#124;&#124;saving" |
| [29:628](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 保留选择并应用到最新版本 | :disabled="locked&#124;&#124;saving&#124;&#124;loading" |
| [29:725](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 放弃本地选择 | :disabled="saving&#124;&#124;loading" |
| [29:1334](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 查看锁定来源原文 |  |
| [29:1664](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 上一页 | :disabled="page===0" |
| [29:1736](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 下一页 | :disabled="(page+1)*20>=choices.length" |
| [29:1957](../../../web/src/features/minutes/MinutesSensitive.vue#L29) | button | 保存排除选择 | :disabled="locked&#124;&#124;loading&#124;&#124;saving&#124;&#124;!settings?.exclusionRevision&#124;&#124;conflict" |

## features/minutes/MinutesSettings.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [10:25](../../../web/src/features/minutes/MinutesSettings.vue#L10) | button | 读取当前设置 | :disabled="locked" |
| [10:210](../../../web/src/features/minutes/MinutesSettings.vue#L10) | button | 开始修改设置 | :disabled="locked" |
| [10:526](../../../web/src/features/minutes/MinutesSettings.vue#L10) | button | 更多模板 | v-if="templates?.nextCursor" |
| [10:624](../../../web/src/features/minutes/MinutesSettings.vue#L10) | button | 保存模板选择 | :disabled="!editable&#124;&#124;locked&#124;&#124;!template" |
| [11:192](../../../web/src/features/minutes/MinutesSettings.vue#L11) | button | 移除 | :disabled="!editable&#124;&#124;locked" |
| [11:317](../../../web/src/features/minutes/MinutesSettings.vue#L11) | button | 上一页 | v-if="page>0" |
| [11:379](../../../web/src/features/minutes/MinutesSettings.vue#L11) | button | 下一页 | v-if="(page+1)*20<items.length" |
| [11:685](../../../web/src/features/minutes/MinutesSettings.vue#L11) | button | 加入排除集合 | :disabled="!editable&#124;&#124;locked&#124;&#124;!selected" |
| [11:774](../../../web/src/features/minutes/MinutesSettings.vue#L11) | button | 保存完整排除集合 | :disabled="!editable&#124;&#124;locked&#124;&#124;!settings.exclusionRevision" |

## features/minutes/MinutesSourcePreview.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [24:85](../../../web/src/features/minutes/MinutesSourcePreview.vue#L24) | a | {{title}} | :href="source.originalBytes.route" |
| [25:54](../../../web/src/features/minutes/MinutesSourcePreview.vue#L25) | button | {{playing?'暂停播放':'从 '+clock(state.startMs.value)+' 播放'}} | :disabled="!state.playable.value&#124;&#124;!ready" |
| [28:60](../../../web/src/features/minutes/MinutesSourcePreview.vue#L28) | button | 重试读取原件 |  |

## features/minutes/MinutesTemplateToolbar.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [9:618](../../../web/src/features/minutes/MinutesTemplateToolbar.vue#L9) | button | 新增模板 | :disabled="locked" |
| [9:712](../../../web/src/features/minutes/MinutesTemplateToolbar.vue#L9) | button | 重新生成 | :disabled="locked" |

## features/minutes/MinutesTextEditor.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [20:1141](../../../web/src/features/minutes/MinutesTextEditor.vue#L20) | button | 更多 | :aria-expanded="showRecovery" |
| [23:69](../../../web/src/features/minutes/MinutesTextEditor.vue#L23) | button | {{editor.intent.state.value==='UNCERTAIN'?'重试原编辑确认':'开始编辑'}} | v-if="!compact&#124;&#124;!editor.intent.canEdit.value" :disabled="editor.busy.value&#124;&#124;editor.loading.value&#124;&#124;editor.targetMissing?.value&#124;&#124;!!d.conflict.value" |
| [27:171](../../../web/src/features/minutes/MinutesTextEditor.vue#L27) | button | 关联所选文字 | :disabled="!editor.intent.canEdit.value&#124;&#124;!selection" |
| [28:49](../../../web/src/features/minutes/MinutesTextEditor.vue#L28) | button | 上一页来源 | :disabled="anchorPage===0" |
| [28:223](../../../web/src/features/minutes/MinutesTextEditor.vue#L28) | button | 下一页来源 | :disabled="anchorPage+1>=anchorPages" |
| [30:38](../../../web/src/features/minutes/MinutesTextEditor.vue#L30) | button | 将本地修改应用到最新版本 |  |
| [30:103](../../../web/src/features/minutes/MinutesTextEditor.vue#L30) | button | 放弃修改并读取最新版本 |  |
| [31:24](../../../web/src/features/minutes/MinutesTextEditor.vue#L31) | button | 保存 | :disabled="editor.busy.value&#124;&#124;!editor.intent.canAutosave.value&#124;&#124;!d.dirty.value&#124;&#124;!!d.conflict.value" |
| [31:175](../../../web/src/features/minutes/MinutesTextEditor.vue#L31) | button | 查回原保存结果 | v-if="['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(editor.command.state.value)&&editor.command.snapshot.value" :disabled="editor.busy.value" |
| [31:404](../../../web/src/features/minutes/MinutesTextEditor.vue#L31) | button | 放弃本地修改 | :disabled="editor.busy.value&#124;&#124;['PREPARED','SENDING','UNCERTAIN'].includes(editor.command.state.value)" |
| [31:566](../../../web/src/features/minutes/MinutesTextEditor.vue#L31) | button | 读取最新版本 |  |

## features/minutes/MinutesWorkspace.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [112:144](../../../web/src/features/minutes/MinutesWorkspace.vue#L112) | button | 恢复编辑 | :disabled="!canRecoverEditor" |
| [112:372](../../../web/src/features/minutes/MinutesWorkspace.vue#L112) | button | 核对纪要候选 |  |
| [112:729](../../../web/src/features/minutes/MinutesWorkspace.vue#L112) | button | 核心要素 | :class="{active:view==='elements'}" :aria-pressed="view==='elements'" |
| [112:864](../../../web/src/features/minutes/MinutesWorkspace.vue#L112) | button | {{stage==='published'?'AI纪要':'会议纪要'}} | :class="{active:view==='body'}" :aria-pressed="view==='body'" |
| [114:642](../../../web/src/features/minutes/MinutesWorkspace.vue#L114) | button | 更多 | :aria-expanded="generationPanel?.recoveryVisible??generationAdvanced" |
| [114:830](../../../web/src/features/minutes/MinutesWorkspace.vue#L114) | button | 关闭 |  |
| [115:322](../../../web/src/features/minutes/MinutesWorkspace.vue#L115) | button | 重新读取纪要 | :disabled="busy&#124;&#124;actions.busy.value" |
| [117:461](../../../web/src/features/minutes/MinutesWorkspace.vue#L117) | button | 复制全文 | :disabled="accessDenied" |
| [117:704](../../../web/src/features/minutes/MinutesWorkspace.vue#L117) | button | 编辑正文 | v-if="!bodyEditing" :disabled="!!locked" |
| [117:891](../../../web/src/features/minutes/MinutesWorkspace.vue#L117) | button | 取消 | :disabled="editor.busy.value&#124;&#124;['PREPARED','SENDING','UNCERTAIN'].includes(editor.command.state.value)" |
| [117:1052](../../../web/src/features/minutes/MinutesWorkspace.vue#L117) | button | {{editor.busy.value?'保存中…':'保存'}} | :disabled="editor.busy.value&#124;&#124;!editor.intent.canAutosave.value&#124;&#124;!!editor.draft.value?.conflict.value" |
| [120:534](../../../web/src/features/minutes/MinutesWorkspace.vue#L120) | button | 查看正文来源与审阅 | v-if="owner" :disabled="editor.busy.value&#124;&#124;editor.loading.value" |
| [123:188](../../../web/src/features/minutes/MinutesWorkspace.vue#L123) | button | {{categoryLabels[category]??category}} | :key="category" :data-category="category" :class="{selected:selectedCategory===category,active:selectedCategory===category}" :aria-current="selectedCategory===category?'true':undefined" |
| [123:534](../../../web/src/features/minutes/MinutesWorkspace.vue#L123) | button | 敏感信息 | :class="{selected:selectedCategory==='SENSITIVE',active:selectedCategory==='SENSITIVE'}" |
| [123:1028](../../../web/src/features/minutes/MinutesWorkspace.vue#L123) | p (button) | {{item.body}} 负责人： {{item.action.owner_display??'未明确'}} · 日期： {{item.action.due_on}} | :key="item.id" :role="owner?'button':undefined" :aria-disabled="owner?(editor.busy.value&#124;&#124;editor.loading.value&#124;&#124;actions.busy.value):undefined" :tabindex="owner?0:undefined" :title="owner?'查看来源与审阅':undefined" |
| [124:396](../../../web/src/features/minutes/MinutesWorkspace.vue#L124) | button | 局部重新生成 | v-if="owner&&stage==='minutes'" :disabled="locked&#124;&#124;workspaceDirty&#124;&#124;actions.busy.value" |
| [124:1147](../../../web/src/features/minutes/MinutesWorkspace.vue#L124) | button | {{entry.label??'来源与审阅'}} | :key="entry.locatorId" :class="entry.kind==='material'?'am-evidence-material':'am-evidence-recording'" :aria-label="'查看来源：'+(entry.label??'来源与审阅')" :title="entry.label??'读取真实来源标签后可核对；点击打开来源与审阅'" :disabled="editor.busy.value&#124;&#124;editor.loading.value" |
| [124:1671](../../../web/src/features/minutes/MinutesWorkspace.vue#L124) | button | 编辑要素 | v-if="owner&&stage!=='published'" :disabled="locked" |
| [124:1876](../../../web/src/features/minutes/MinutesWorkspace.vue#L124) | button | 上一页 | v-if="(pages[category]??0)>0" |
| [124:1986](../../../web/src/features/minutes/MinutesWorkspace.vue#L124) | button | 下一页 | v-if="((pages[category]??0)+1)*20<items.length" |
| [125:484](../../../web/src/features/minutes/MinutesWorkspace.vue#L125) | button | 查看背景来源 | :key="locatorId" :disabled="editor.busy.value&#124;&#124;editor.loading.value" |
| [126:341](../../../web/src/features/minutes/MinutesWorkspace.vue#L126) | button | {{inspectionView==='source'?'审阅':'返回来源'}} | v-if="selectedContent" |
| [126:563](../../../web/src/features/minutes/MinutesWorkspace.vue#L126) | button | 关闭 |  |
| [126:847](../../../web/src/features/minutes/MinutesWorkspace.vue#L126) | button | 重试读取来源 |  |
| [126:1746](../../../web/src/features/minutes/MinutesWorkspace.vue#L126) | button | 查看原始段落 |  |
| [126:1949](../../../web/src/features/minutes/MinutesWorkspace.vue#L126) | a | 读取原始文件 | :href="source.originalBytes.route" |
| [127:65](../../../web/src/features/minutes/MinutesWorkspace.vue#L127) | button | 读取审阅记录 |  |
| [127:357](../../../web/src/features/minutes/MinutesWorkspace.vue#L127) | button | 下一页审阅 | v-if="reviewHistory?.hasMore" |
| [128:244](../../../web/src/features/minutes/MinutesWorkspace.vue#L128) | button | 提交全文审阅 | :disabled="locked" |
| [128:321](../../../web/src/features/minutes/MinutesWorkspace.vue#L128) | button | 审阅所选文字 | :disabled="locked&#124;&#124;!reviewSelection" |
| [128:435](../../../web/src/features/minutes/MinutesWorkspace.vue#L128) | button | 读取审阅记录 |  |
| [128:727](../../../web/src/features/minutes/MinutesWorkspace.vue#L128) | button | 下一页审阅 | v-if="reviewHistory?.hasMore" |
| [128:851](../../../web/src/features/minutes/MinutesWorkspace.vue#L128) | button | 转换为待确认问题 | v-if="selected.kind==='ELEMENT'" :disabled="locked" |
| [129:389](../../../web/src/features/minutes/MinutesWorkspace.vue#L129) | button | 编辑负责人和截止日期 | :disabled="locked" |
| [129:494](../../../web/src/features/minutes/MinutesWorkspace.vue#L129) | button | 确认已保存的负责人和日期 | :disabled="locked" |
| [133:178](../../../web/src/features/minutes/MinutesWorkspace.vue#L133) | button | 关闭 |  |
| [134:101](../../../web/src/features/minutes/MinutesWorkspace.vue#L134) | button | 更多 | :aria-expanded="candidateAdvanced" |
| [134:237](../../../web/src/features/minutes/MinutesWorkspace.vue#L134) | button | 关闭候选对比 |  |
| [136:1028](../../../web/src/features/minutes/MinutesWorkspace.vue#L136) | button | 上一页候选要素 | v-if="candidatePage>0" |
| [136:1112](../../../web/src/features/minutes/MinutesWorkspace.vue#L136) | button | 下一页候选要素 | v-if="(candidatePage+1)*20<selectedCandidateElements.length" |
| [136:1352](../../../web/src/features/minutes/MinutesWorkspace.vue#L136) | button | 拒绝此候选 | :disabled="locked&#124;&#124;workspaceDirty&#124;&#124;actions.busy.value&#124;&#124;candidate.candidate.state!=='PENDING'" |
| [136:1551](../../../web/src/features/minutes/MinutesWorkspace.vue#L136) | button | 处置此候选 | :disabled="locked&#124;&#124;workspaceDirty&#124;&#124;actions.busy.value&#124;&#124;!['PENDING','STALE'].includes(candidate.candidate.state)" |
| [137:26](../../../web/src/features/minutes/MinutesWorkspace.vue#L137) | button | 保留当前稿 | :disabled="!candidate&#124;&#124;locked&#124;&#124;workspaceDirty&#124;&#124;actions.busy.value&#124;&#124;!['PENDING','STALE'].includes(candidate.candidate.state)" |
| [137:215](../../../web/src/features/minutes/MinutesWorkspace.vue#L137) | button | 采用候选 | :disabled="!candidate&#124;&#124;locked&#124;&#124;workspaceDirty&#124;&#124;actions.busy.value&#124;&#124;!candidate.eligibility.canAdopt" |
| [138:150](../../../web/src/features/minutes/MinutesWorkspace.vue#L138) | button | 关闭 |  |
| [138:547](../../../web/src/features/minutes/MinutesWorkspace.vue#L138) | button | 读取候选 | :disabled="busy" |
| [138:742](../../../web/src/features/minutes/MinutesWorkspace.vue#L138) | button | 检查候选 |  |
| [138:829](../../../web/src/features/minutes/MinutesWorkspace.vue#L138) | button | 下一页 | v-if="candidates?.nextCursor" |
| [142:129](../../../web/src/features/minutes/MinutesWorkspace.vue#L142) | button | 查回原决定结果 | v-if="['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(actions.command.state.value)&&actions.command.snapshot.value" :disabled="actions.busy.value" |
| [142:336](../../../web/src/features/minutes/MinutesWorkspace.vue#L142) | button | 重新读取后再决定 | v-if="['CONFLICT','REJECTED'].includes(actions.command.state.value)" |
| [143:799](../../../web/src/features/minutes/MinutesWorkspace.vue#L143) | button | 进入会议确认 | :disabled="!!locked&#124;&#124;actions.busy.value&#124;&#124;pendingCandidates.length>0" |
| [143:1464](../../../web/src/features/minutes/MinutesWorkspace.vue#L143) | button | 继续编辑 |  |
| [143:1526](../../../web/src/features/minutes/MinutesWorkspace.vue#L143) | button | 放弃修改 | :disabled="editor.busy.value" |
| [143:1621](../../../web/src/features/minutes/MinutesWorkspace.vue#L143) | button | 保存并继续 | :disabled="editor.busy.value&#124;&#124;actions.busy.value&#124;&#124;(editor.draft.value?.dirty.value&&!editor.intent.canAutosave.value)" |

## features/minutes/PersonalTemplateDialogView.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [16:60](../../../web/src/features/minutes/PersonalTemplateDialogView.vue#L16) | button | 关闭 | :disabled="blocked" |
| [28:5](../../../web/src/features/minutes/PersonalTemplateDialogView.vue#L28) | button | 放弃已拒绝的原请求 | v-if="pending&&['CONFLICT','REJECTED'].includes(state)" :disabled="busy" |
| [29:5](../../../web/src/features/minutes/PersonalTemplateDialogView.vue#L29) | button | 取消 | :disabled="blocked" |
| [30:5](../../../web/src/features/minutes/PersonalTemplateDialogView.vue#L30) | button | {{state==='ACKNOWLEDGED'?'重新核实已保存模板':'查回原模板保存结果'}} | v-if="pending" :disabled="busy&#124;&#124;composing&#124;&#124;concealed&#124;&#124;!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(state)" |
| [31:5](../../../web/src/features/minutes/PersonalTemplateDialogView.vue#L31) | button | {{mode==='delete'?'删除':accepted?'使用已保存模板':'保存并使用'}} | :class="mode==='delete'?'danger':'primary'" :disabled="busy&#124;&#124;composing&#124;&#124;concealed" |

## features/minutes/PersonalTemplateWorkspace.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [92:55](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L92) | button | 关闭模板管理 | :disabled="pending" |
| [98:24](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L98) | button | 编辑 | :disabled="pending&#124;&#124;locked" :aria-label="'编辑模板 '+item.name" |
| [98:150](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L98) | button | 删除 | :disabled="pending&#124;&#124;locked" :aria-label="'删除模板 '+item.name" |
| [98:283](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L98) | button | 使用 | :disabled="pending&#124;&#124;locked" :aria-label="'使用模板 '+item.name" |
| [102:28](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L102) | button | 返回首页 | :disabled="pending" |
| [102:105](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L102) | button | 下一页 | v-if="manager.page.value?.nextCursor" :disabled="pending" |
| [102:244](../../../web/src/features/minutes/PersonalTemplateWorkspace.vue#L102) | button | 关闭 | :disabled="pending" |

## features/minutes/PublicationPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [48:237](../../../web/src/features/minutes/PublicationPanel.vue#L48) | button | 查看检查明细 |  |
| [49:343](../../../web/src/features/minutes/PublicationPanel.vue#L49) | button | 关闭最终确认检查 |  |
| [49:602](../../../web/src/features/minutes/PublicationPanel.vue#L49) | button | {{showRequirements?'收起检查明细':'查看检查明细'}} | v-if="owner&&p.readiness.value" :aria-expanded="showRequirements" |
| [49:1708](../../../web/src/features/minutes/PublicationPanel.vue#L49) | button | 上一页条件 | v-if="requirementPage>0" |
| [49:1797](../../../web/src/features/minutes/PublicationPanel.vue#L49) | button | 下一页条件 | v-if="(requirementPage+1)*20<p.readiness.value.required.length" |
| [49:2003](../../../web/src/features/minutes/PublicationPanel.vue#L49) | summary | 选择一并冻结的可选审阅 |  |
| [49:2362](../../../web/src/features/minutes/PublicationPanel.vue#L49) | button | 完成 |  |
| [50:79](../../../web/src/features/minutes/PublicationPanel.vue#L50) | div (button) | 查看最终确认检查明细 |  |
| [50:409](../../../web/src/features/minutes/PublicationPanel.vue#L50) | button | 返回纪要编辑 |  |
| [50:480](../../../web/src/features/minutes/PublicationPanel.vue#L50) | button | 继续发布 › | v-if="p.final.value?.confirmation" |
| [50:594](../../../web/src/features/minutes/PublicationPanel.vue#L50) | button | 最终确认并继续发布 | :disabled="locked&#124;&#124;p.busy.value&#124;&#124;(!canPrepareFinal&#124;&#124;!embeddedInfo?.canPrepare)" |
| [52:230](../../../web/src/features/minutes/PublicationPanel.vue#L52) | button | 清空知识库搜索 | :hidden="!librarySearch" |
| [54:175](../../../web/src/features/minutes/PublicationPanel.vue#L54) | button | {{lib.name}} | :class="{sel:selectedDestination?.libraryId===lib.id&&!selectedDestination.path.length}" :aria-pressed="selectedDestination?.libraryId===lib.id&&!selectedDestination.path.length" :disabled="locked&#124;&#124;!!status&#124;&#124;p.busy.value" |
| [55:330](../../../web/src/features/minutes/PublicationPanel.vue#L55) | button | {{row.knownEmpty?'':'▸'}} | :class="{open:row.expanded}" :disabled="locked&#124;&#124;!!status&#124;&#124;row.knownEmpty" :aria-label="(row.expanded?'收起':'展开')+'文件夹 '+row.folder.name" :aria-expanded="row.expanded" |
| [55:627](../../../web/src/features/minutes/PublicationPanel.vue#L55) | button | {{row.folder.name}} | :disabled="locked&#124;&#124;!!status&#124;&#124;p.busy.value" :aria-pressed="selectedDestination?.folderId===row.folder.id" |
| [55:903](../../../web/src/features/minutes/PublicationPanel.vue#L55) | button | 更多子文件夹 | v-if="destinations.branch(lib,row.folder.id)?.expanded&&destinations.branch(lib,row.folder.id)?.page.hasMore" :disabled="destinations.busy.value&#124;&#124;locked&#124;&#124;!!status" |
| [55:1388](../../../web/src/features/minutes/PublicationPanel.vue#L55) | button | 更多文件夹 | v-if="destinations.branch(lib,lib.rootFolderId)?.page.hasMore" :disabled="destinations.busy.value&#124;&#124;locked&#124;&#124;!!status" |
| [57:2](../../../web/src/features/minutes/PublicationPanel.vue#L57) | button | 更多知识库 | v-if="destinations.libraries.value?.hasMore" :disabled="destinations.busy.value&#124;&#124;locked&#124;&#124;!!status" |
| [58:139](../../../web/src/features/minutes/PublicationPanel.vue#L58) | button | 重试读取目录 | :disabled="destinations.busy.value&#124;&#124;locked&#124;&#124;!!status" |
| [61:609](../../../web/src/features/minutes/PublicationPanel.vue#L61) | button | 最终文档预览 | :disabled="!p.final.value?.confirmation&#124;&#124;!p.final.value.body&#124;&#124;p.busy.value" |
| [62:358](../../../web/src/features/minutes/PublicationPanel.vue#L62) | button | 关闭最终文档预览 |  |
| [62:1264](../../../web/src/features/minutes/PublicationPanel.vue#L62) | button | 关闭预览 |  |
| [63:215](../../../web/src/features/minutes/PublicationPanel.vue#L63) | button | 发布到知识库 | :disabled="!destinations.canSubmit.value&#124;&#124;locked&#124;&#124;p.busy.value&#124;&#124;!!status&#124;&#124;!p.final.value?.confirmation" |
| [64:127](../../../web/src/features/minutes/PublicationPanel.vue#L64) | button | 刷新最终状态 | :disabled="p.busy.value" |
| [64:537](../../../web/src/features/minutes/PublicationPanel.vue#L64) | a | 查看正式文档 ↗ | v-if="status.officialDocumentUrl" :href="status.officialDocumentUrl" |
| [64:723](../../../web/src/features/minutes/PublicationPanel.vue#L64) | button | 撤回 / 废止与治理历史 | v-if="owner&&status.rawState==='SUCCEEDED'&&p.final.value?.observation&&app.governanceCommands" |
| [64:1001](../../../web/src/features/minutes/PublicationPanel.vue#L64) | summary | 发布尝试记录 |  |
| [64:1208](../../../web/src/features/minutes/PublicationPanel.vue#L64) | button | 下一页尝试 | v-if="attempts?.hasMore" |
| [65:188](../../../web/src/features/minutes/PublicationPanel.vue#L65) | button | {{p.command.snapshot.value.operation==='PUBLICATION_SUBMIT'?'查回原发布状态':'查回原最终确认'}} | v-if="p.command.snapshot.value&&['PREPARED','UNCERTAIN'].includes(p.command.state.value)" :disabled="p.busy.value" |
| [65:435](../../../web/src/features/minutes/PublicationPanel.vue#L65) | button | 重新读取后再决定 | v-if="['CONFLICT','REJECTED'].includes(p.command.state.value)" |
| [66:329](../../../web/src/features/minutes/PublicationPanel.vue#L66) | button | 关闭发布成功提示 |  |
| [66:1073](../../../web/src/features/minutes/PublicationPanel.vue#L66) | a | 查看正式文档 | :href="status.officialDocumentUrl" |
| [66:1217](../../../web/src/features/minutes/PublicationPanel.vue#L66) | button | 知道了 |  |

## features/minutes/PublishedLocationLink.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [7:70](../../../web/src/features/minutes/PublishedLocationLink.vue#L7) | RouterLink | {{location.value.value.libraryName}} | v-if="location.value.value" :to="{path:'/meeting-library/'+encodeURIComponent(location.value.value.libraryId),query:{meeting:meetingId}}" |
| [7:424](../../../web/src/features/minutes/PublishedLocationLink.vue#L7) | button | 重新读取知识库位置 |  |

## features/minutes/PublishedMaterialsDrawer.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [15:92](../../../web/src/features/minutes/PublishedMaterialsDrawer.vue#L15) | button | 重新读取材料 |  |

## features/recording/LegacyRecordingFiles.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [8:361](../../../web/src/features/recording/LegacyRecordingFiles.vue#L8) | button | 下载 | :disabled="busy" |

## features/recording/LiveTranscript.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [12:149](../../../web/src/features/recording/LiveTranscript.vue#L12) | button | 恢复断线字幕 |  |
| [12:1503](../../../web/src/features/recording/LiveTranscript.vue#L12) | button | 恢复断线字幕 | v-if="afterEventId" |

## features/recording/RecordingArchivePlayback.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [10:131](../../../web/src/features/recording/RecordingArchivePlayback.vue#L10) | button | 刷新归档状态 | :disabled="busy" |
| [10:590](../../../web/src/features/recording/RecordingArchivePlayback.vue#L10) | button | 重试录音归档 | v-if="retryable" :disabled="busy" |

## features/recording/RecordingIndicator.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [9:1](../../../web/src/features/recording/RecordingIndicator.vue#L9) | button | 返回在线录音 | v-if="visible" :class="{'is-paused':recorder.phase.value!=='RECORDING','is-saving':recorder.phase.value==='SAVING','has-error':recorder.phase.value==='ERROR'}" :title="'在线录音 · '+clock" |

## features/recording/RecordingTranscription.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [21:110](../../../web/src/features/recording/RecordingTranscription.vue#L21) | button | 刷新转写状态 | :disabled="c.busy.value" |
| [21:628](../../../web/src/features/recording/RecordingTranscription.vue#L21) | button | 恢复原转写请求 | v-if="['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(c.command.state.value)" :disabled="c.busy.value&#124;&#124;!c.available.value" |
| [21:818](../../../web/src/features/recording/RecordingTranscription.vue#L21) | button | 核对后重新准备 | v-if="['CONFLICT','REJECTED'].includes(c.command.state.value)" :disabled="c.busy.value" |
| [21:970](../../../web/src/features/recording/RecordingTranscription.vue#L21) | button | {{c.view.value?.state==='READY'?'开始转写':'准备并转写'}} | v-else-if="!c.view.value?.transcriptionTaskId&&!c.accepted.value" :disabled="c.busy.value&#124;&#124;!c.available.value&#124;&#124;!c.view.value?.archiveAvailable" |
| [21:1281](../../../web/src/features/recording/RecordingTranscription.vue#L21) | RouterLink | 查看转写进度 | v-if="c.view.value?.transcriptionTaskId&#124;&#124;c.accepted.value" :to="'/meeting/'+meetingId+'/processing'" |

## features/transcript/CorrectionPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [6:554](../../../web/src/features/transcript/CorrectionPanel.vue#L6) | button | 拒绝 | :disabled="busy&#124;&#124;!correction.canReject" |
| [6:691](../../../web/src/features/transcript/CorrectionPanel.vue#L6) | button | 接受 | :disabled="busy&#124;&#124;!correction.canAccept" |
| [6:842](../../../web/src/features/transcript/CorrectionPanel.vue#L6) | button | 撤销 | v-else-if="correction.status==='ACCEPTED'&&correction.canUndo" :disabled="busy" |

## features/transcript/SpeakerEditor.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [6:247](../../../web/src/features/transcript/SpeakerEditor.vue#L6) | button | {{speaker.label.slice(0,1)}} {{speaker.label}} {{speaker.anonymous&#124;&#124;!speaker.enterpriseId?'匿名':'已关联企业身份'}} | :disabled="readonly" |
| [6:524](../../../web/src/features/transcript/SpeakerEditor.vue#L6) | button | 编辑 | v-if="!readonly" |

## features/transcript/TranscriptCorrectionDemoPage.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [21:71](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L21) | RouterLink | 我的会议 |  |
| [24:39](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L24) | RouterLink | 返回列表 |  |
| [25:44](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L25) | button | 编辑会议信息 |  |
| [25:109](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L25) | button | 保存 |  |
| [25:174](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L25) | button | 更多操作 |  |
| [29:5](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L29) | button | 材料导入 |  |
| [30:5](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L30) | button | 转写校正 |  |
| [31:5](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L31) | button | AI纪要 |  |
| [32:5](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L32) | button | 会议确认 |  |
| [33:5](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L33) | button | 知识库发布 |  |
| [39:71](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L39) | button | 编辑 |  |
| [42:176](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L42) | button | 重新读取转写 |  |
| [43:55](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L43) | summary | 转写历史与原操作恢复 |  |
| [43:139](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L43) | button | 读取历史版本 |  |
| [48:171](../../../web/src/features/transcript/TranscriptCorrectionDemoPage.vue#L48) | button | 关闭 |  |

## features/transcript/TranscriptEditor.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [88:205](../../../web/src/features/transcript/TranscriptEditor.vue#L88) | button | 重新读取转写 | :disabled="editor.loading.value" |
| [89:229](../../../web/src/features/transcript/TranscriptEditor.vue#L89) | button | 关闭转写审阅 |  |
| [89:608](../../../web/src/features/transcript/TranscriptEditor.vue#L89) | button | 纠错建议与候选 |  |
| [89:685](../../../web/src/features/transcript/TranscriptEditor.vue#L89) | button | 恢复原操作结果 | :disabled="editor.busy.value&#124;&#124;editor.recoveryBlocked.value" |
| [89:820](../../../web/src/features/transcript/TranscriptEditor.vue#L89) | button | 放弃本地修改／已拒绝操作 | v-if="editor.draft.value?.dirty.value&#124;&#124;['CONFLICT','REJECTED'].includes(editor.command.state.value)" |
| [90:105](../../../web/src/features/transcript/TranscriptEditor.vue#L90) | summary | 转写历史与原操作恢复 |  |
| [90:189](../../../web/src/features/transcript/TranscriptEditor.vue#L90) | button | 读取历史版本 | :disabled="historyBusy" |
| [90:339](../../../web/src/features/transcript/TranscriptEditor.vue#L90) | button | {{item.kind==='RAW'?'原始转写 RAW':'人工工作版本 WORKING'}} · {{item.revisionToken}} · {{item.createdAt}} | :disabled="historyBusy" |
| [90:523](../../../web/src/features/transcript/TranscriptEditor.vue#L90) | button | 下一页历史 | v-if="history?.hasMore" :disabled="historyBusy" |
| [90:928](../../../web/src/features/transcript/TranscriptEditor.vue#L90) | button | 恢复为新的工作版本 | :disabled="bulkBusy&#124;&#124;tools.pending.value&#124;&#124;corrections.pending.value&#124;&#124;editor.busy.value&#124;&#124;editor.draft.value?.dirty.value&#124;&#124;!!editor.draft.value?.conflict.value&#124;&#124;!selected.capabilities.canRestore" |
| [91:286](../../../web/src/features/transcript/TranscriptEditor.vue#L91) | button | 读取纠错建议 | :disabled="bulkBusy&#124;&#124;corrections.busy.value" |
| [91:394](../../../web/src/features/transcript/TranscriptEditor.vue#L91) | button | 检查语音增强建议 | :disabled="bulkBusy&#124;&#124;!canUseTools()&#124;&#124;tools.pending.value&#124;&#124;corrections.pending.value&#124;&#124;corrections.busy.value&#124;&#124;!corrections.view.value?.canPropose" |
| [91:627](../../../web/src/features/transcript/TranscriptEditor.vue#L91) | button | 恢复原纠错操作 | v-if="corrections.pending.value" :disabled="bulkBusy&#124;&#124;corrections.busy.value" |
| [91:789](../../../web/src/features/transcript/TranscriptEditor.vue#L91) | button | 放弃已拒绝的纠错请求 | v-if="['CONFLICT','REJECTED'].includes(corrections.command.state.value)" |
| [94:226](../../../web/src/features/transcript/TranscriptEditor.vue#L94) | button | 继续核对 |  |
| [94:288](../../../web/src/features/transcript/TranscriptEditor.vue#L94) | button | 放弃修改并离开 | :disabled="editor.busy.value" |
| [94:386](../../../web/src/features/transcript/TranscriptEditor.vue#L94) | button | 保存并离开 | :disabled="editor.busy.value" |
| [95:291](../../../web/src/features/transcript/TranscriptEditor.vue#L95) | button | 返回校正 |  |
| [95:356](../../../web/src/features/transcript/TranscriptEditor.vue#L95) | button | 确认并生成 | :disabled="editor.busy.value" |

## features/transcript/TranscriptForm.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [45:28](../../../web/src/features/transcript/TranscriptForm.vue#L45) | button | {{clock(timing.get(segment.segmentId)!.startMs!)}} | v-if="timing.get(segment.segmentId)?.startMs!=null" :disabled="!playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)" :aria-label="'定位原录音 '+clock(timing.get(segment.segmentId)!.startMs!)" |
| [45:367](../../../web/src/features/transcript/TranscriptForm.vue#L45) | button | 此片段无对应原录音 |  |
| [50:497](../../../web/src/features/transcript/TranscriptForm.vue#L50) | button | 建立人工工作版本 | v-if="!editable&&showFooter" :disabled="busy&#124;&#124;blocked" |
| [50:982](../../../web/src/features/transcript/TranscriptForm.vue#L50) | button | 全部接受 | :disabled="!correctionsReady&#124;&#124;!correctionCount&#124;&#124;!editable&#124;&#124;busy&#124;&#124;blocked&#124;&#124;dirty&#124;&#124;pending" :title="correctionsReady?'逐条接受当前已核对的待处理建议':'纠错建议服务尚未接入'" |
| [50:1210](../../../web/src/features/transcript/TranscriptForm.vue#L50) | button | 全部拒绝 | :disabled="!correctionsReady&#124;&#124;!correctionCount&#124;&#124;!editable&#124;&#124;busy&#124;&#124;blocked&#124;&#124;dirty&#124;&#124;pending" :title="correctionsReady?'逐条拒绝当前已核对的待处理建议':'纠错建议服务尚未接入'" |
| [50:1467](../../../web/src/features/transcript/TranscriptForm.vue#L50) | button | 批量替换 | :disabled="!editable&#124;&#124;blocked&#124;&#124;busy&#124;&#124;pending" |
| [51:330](../../../web/src/features/transcript/TranscriptForm.vue#L51) | button | 恢复原操作结果 | :disabled="busy&#124;&#124;blocked" |
| [51:556](../../../web/src/features/transcript/TranscriptForm.vue#L51) | summary | 查看最新保存内容 |  |
| [51:664](../../../web/src/features/transcript/TranscriptForm.vue#L51) | button | 将我的修改应用到最新版本 | :disabled="busy&#124;&#124;pending&#124;&#124;blocked" |
| [51:765](../../../web/src/features/transcript/TranscriptForm.vue#L51) | button | 放弃本地修改，读取最新版本 | :disabled="busy&#124;&#124;pending&#124;&#124;blocked" |
| [52:177](../../../web/src/features/transcript/TranscriptForm.vue#L52) | button | {{timing.get(segment.segmentId)?.startMs!=null?clock(timing.get(segment.segmentId)!.startMs!):String(index+1).padStart(2,'0')}} | :aria-label="'定位原录音第 '+(index+1)+' 段'" :title="timing.get(segment.segmentId)?.startMs!=null&&playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)?'点击定位音频':'此片段无对应原录音'" :aria-disabled="timing.get(segment.segmentId)?.startMs==null&#124;&#124;!playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)" |
| [52:945](../../../web/src/features/transcript/TranscriptForm.vue#L52) | button | 图标 / 文件入口 | v-if="editable" :disabled="blocked&#124;&#124;busy&#124;&#124;pending" :aria-label="'编辑第 '+(index+1)+' 段'" |
| [52:1231](../../../web/src/features/transcript/TranscriptForm.vue#L52) | button | {{label}} | :key="key" :class="segment.marks.includes(key)?'on-'+({KEY:'key',CONCLUSION:'con',DISPUTE:'dis',GENERATION_FOCUS:'gen'}[key]):''" :disabled="!editable&#124;&#124;blocked" :aria-pressed="segment.marks.includes(key)" |
| [53:250](../../../web/src/features/transcript/TranscriptForm.vue#L53) | button | 确认转写并生成纪要 | :disabled="busy&#124;&#124;blocked&#124;&#124;!!conflict&#124;&#124;pending&#124;&#124;composing&#124;&#124;!canConfirm&&!confirmed&#124;&#124;!['EMPTY','ACKNOWLEDGED'].includes(state)" |
| [54:248](../../../web/src/features/transcript/TranscriptForm.vue#L54) | button | 关闭 | :disabled="modalBlocked" |
| [54:832](../../../web/src/features/transcript/TranscriptForm.vue#L54) | button | 取消 | :disabled="modalBlocked" |
| [54:916](../../../web/src/features/transcript/TranscriptForm.vue#L54) | button | 保存 | :disabled="modalBusy&#124;&#124;busy&#124;&#124;blocked&#124;&#124;composing&#124;&#124;!editable" |
| [55:236](../../../web/src/features/transcript/TranscriptForm.vue#L55) | button | 关闭 | :disabled="modalBlocked" |
| [55:1069](../../../web/src/features/transcript/TranscriptForm.vue#L55) | button | 取消 | :disabled="modalBlocked" |
| [55:1156](../../../web/src/features/transcript/TranscriptForm.vue#L55) | button | 替换 | :disabled="modalBusy&#124;&#124;busy&#124;&#124;blocked&#124;&#124;composing&#124;&#124;!editable" |

## features/transcript/TranscriptOperationsDialog.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [17:78](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L17) | button | 关闭 | :disabled="locked" |
| [20:310](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L20) | button | {{searching?'查找中…':'查找全部位置'}} | :disabled="locked&#124;&#124;searching&#124;&#124;!canMutate&#124;&#124;composing&#124;&#124;!query" |
| [22:93](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L22) | button | 选择全部位置 | :disabled="locked&#124;&#124;!canMutate" |
| [22:212](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L22) | button | 清空选择 | :disabled="locked" |
| [24:27](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L24) | button | 上一页 | :disabled="page===0" |
| [24:136](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L24) | button | 下一页 | :disabled="page+1>=pages" |
| [27:2](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L27) | button | 替换选中位置 | :disabled="locked&#124;&#124;!canMutate&#124;&#124;composing&#124;&#124;!selected.length" |
| [28:80](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L28) | button | 撤销当前工作版本 | :disabled="locked&#124;&#124;!canMutate&#124;&#124;composing" |
| [30:32](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L30) | button | {{state==='ACKNOWLEDGED'?'重新核实已受理修订':'重试原修订请求'}} | v-if="pending&&['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(state)" :disabled="busy&#124;&#124;composing" |
| [30:301](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L30) | button | 放弃已拒绝的修订请求 | v-if="pending&&['CONFLICT','REJECTED'].includes(state)" :disabled="busy" |
| [30:438](../../../web/src/features/transcript/TranscriptOperationsDialog.vue#L30) | button | 关闭 | :disabled="locked" |

## features/transcript/TranscriptPlayer.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [23:23](../../../web/src/features/transcript/TranscriptPlayer.vue#L23) | button | 播放或暂停录音 | :disabled="!state.url.value&#124;&#124;state.busy.value" |
| [23:631](../../../web/src/features/transcript/TranscriptPlayer.vue#L23) | button | {{speed}} x | :disabled="!state.url.value" |
| [23:1013](../../../web/src/features/transcript/TranscriptPlayer.vue#L23) | button | 重试 | v-if="state.error.value" |
| [24:39](../../../web/src/features/transcript/TranscriptPlayer.vue#L24) | button | {{String(index+1).padStart(2,'0')}} {{state.names.value[source.fileId]??'会议录音 '+(index+1)}} {{state.selected.value===source.fileId&&duration?clock(duration):'--'}} | :key="source.fileId" :class="{active:state.selected.value===source.fileId}" :disabled="state.busy.value" |

## features/transcript/TranscriptSpeakerPanel.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [13:381](../../../web/src/features/transcript/TranscriptSpeakerPanel.vue#L13) | button | 编辑说话人 {{speaker.label}} | :disabled="disabled" |
| [13:953](../../../web/src/features/transcript/TranscriptSpeakerPanel.vue#L13) | button | 保存说话人标注 | :disabled="!selected.length&#124;&#124;!label.trim()" |
| [13:1590](../../../web/src/features/transcript/TranscriptSpeakerPanel.vue#L13) | button | 合并说话人 | :disabled="!source&#124;&#124;!target&#124;&#124;source===target" |

## features/transcript/TranscriptSpeakerSummary.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [6:714](../../../web/src/features/transcript/TranscriptSpeakerSummary.vue#L6) | button | 改名 | :disabled="disabled" |
| [6:877](../../../web/src/features/transcript/TranscriptSpeakerSummary.vue#L6) | button | 合并说话人 | :disabled="disabled" |
| [6:1004](../../../web/src/features/transcript/TranscriptSpeakerSummary.vue#L6) | button | 拆分说话人 | :disabled="disabled" |
| [6:1131](../../../web/src/features/transcript/TranscriptSpeakerSummary.vue#L6) | button | 撤销 | :disabled="disabled" |

## shared/WorkspaceTopbar.vue

| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |
| --- | --- | --- | --- |
| [17:70](../../../web/src/shared/WorkspaceTopbar.vue#L17) | RouterLink | 我的会议 |  |
| [18:3](../../../web/src/shared/WorkspaceTopbar.vue#L18) | button | 我的语料反馈 |  |
| [19:3](../../../web/src/shared/WorkspaceTopbar.vue#L19) | button | 演示引导 |  |
| [20:3](../../../web/src/shared/WorkspaceTopbar.vue#L20) | button | 重置演示数据 |  |
| [21:33](../../../web/src/shared/WorkspaceTopbar.vue#L21) | summary | 当前用户 |  |
| [21:301](../../../web/src/shared/WorkspaceTopbar.vue#L21) | RouterLink | 会议库 |  |
| [21:372](../../../web/src/shared/WorkspaceTopbar.vue#L21) | a | 退出登录 | v-if="session.current.value" |
| [21:457](../../../web/src/shared/WorkspaceTopbar.vue#L21) | RouterLink | 企业登录 |  |
| [23:181](../../../web/src/shared/WorkspaceTopbar.vue#L23) | button | 知道了 |  |
