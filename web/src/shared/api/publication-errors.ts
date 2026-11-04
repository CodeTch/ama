/** Only validated responses on the matching final/publication command may settle an intent. */
export const publicationErrors:Readonly<Record<string,readonly [number,string]>>={
 FINAL_NOT_READY:[409,'确认条件已变化，请重新检查并处理待完成项。'],
 REVISION_CONFLICT:[409,'会议已被更新，请保留草稿并核对最新版本后重新确认。'],
 REVISION_EXHAUSTED:[409,'会议版本已达限额，请联系管理员。'],
 INVALID_OPTIONAL_REVIEW:[422,'所选审阅已失效，请重新检查并选择当前审阅。'],
 PUBLICATION_CONFIGURATION_CONFLICT:[409,'发布配置已被其他会话更新，请保留输入，重新读取后核对。'],
 PUBLICATION_CONFIGURATION_REQUIRED:[409,'请先保存并核对当前发布配置。'],
 PUBLICATION_CONFIGURATION_FROZEN:[409,'本次发布配置已冻结，请查询发布进度。'],
 PUBLICATION_DESTINATION_UNAVAILABLE:[409,'目标位置已失效或发布权限已变化，请重新选择。'],
 PUBLICATION_RETRY_INPUT_CHANGED:[409,'原确认内容已变化，请重新最终确认后提交。'],
 PUBLICATION_LOCAL_RECOVERY_UNAVAILABLE:[409,'任务尚不能恢复，请先查询原发布结果。'],
 EXECUTION_CONFLICT:[409,'发布执行已变化，请读取最新结果。'],
 PUBLICATION_NOT_READY:[409,'当前确认或发布条件已变化，请重新核对。'],
 IDEMPOTENCY_CONFLICT:[409,'原操作内容不一致，请重新读取当前结果后再操作。'],
 MEETING_NOT_WRITABLE:[409,'会议当前不可修改，请重新读取会议状态。'],
 CURRENT_REVISION_REQUIRED:[409,'请读取当前会议版本后重新操作。'],
};
