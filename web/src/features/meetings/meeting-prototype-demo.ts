export const DEMO_MEETING_ID='7b3707f9-ddf2-45cc-b116-7db26b87e4b5';

export type PrototypeStage='transcript'|'minutes'|'confirm';

export const prototypeGroups=[
 {name:'会议概述',paragraphs:['本次会议围绕阶段性进展、关键问题和下一步安排展开讨论。']},
 {name:'关键内容',paragraphs:['参会人员核对了当前进展与背景材料，并保留了需要继续确认的数据口径。']},
 {name:'会议结论',paragraphs:['会议同意先完成材料核对，再由责任团队反馈后续执行安排。']},
 {name:'行动项',paragraphs:['负责人：待确认；截止日期：待确认。请在后续沟通中补充明确。']},
 {name:'待解决问题',paragraphs:['部分事项仍需结合原始转写和相关材料继续核对。']},
 {name:'风险与阻碍',paragraphs:['当前暂无已确认的风险与阻碍，请结合会议来源继续审阅。']},
 {name:'敏感信息',paragraphs:['本次演示未标记敏感片段，不能据此认定不含敏感信息。']}
] as const;
