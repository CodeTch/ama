import type {FinalReadiness} from '../../shared/api/publications';

export type FinalCheckTarget=
 |{kind:'information';field?:'name'|'category'|'occurredOn'}
 |{kind:'review';targetId:string;reviewKind:'SOURCE_SUPPORT'|'ACTION_FIELDS'}
 |{kind:'outcome';targetId:string}
 |{kind:'background';locatorId?:string}
 |{kind:'candidate';candidateId:string}
 |{kind:'transcript';corrections?:boolean}
 |{kind:'minutes'|'settings'|'sensitive'|'tasks'|'retry'};
export type FinalCheck={id:string;label:string;status:'passed'|'failed'|'unknown';reason:string;actions:{label:string;target:FinalCheckTarget}[]};
export function confirmationCheckPage(requested:number|undefined,count:number):number{return Math.max(0,Math.min(requested??0,Math.ceil(count/20)-1));}
const rules:{id:string;label:string;codes:string[];reason:string;target:FinalCheckTarget}[]=[
 {id:'information',label:'基础信息和参会名单',codes:['MEETING_INFO_REQUIRED','ROSTER_CONFIRMATION_REQUIRED'],reason:'补全名称、分类并确认参会名单；日期、摘要和名单可空。',target:{kind:'information'}},
 {id:'transcript',label:'使用当前已确认转写',codes:['TRANSCRIPT_CONFIRMATION_REQUIRED'],reason:'请确认当前保存的会议转写。',target:{kind:'transcript'}},
 {id:'corrections',label:'转写纠错已处理',codes:['CORRECTIONS_PENDING','CORRECTION_UNAVAILABLE'],reason:'请处理待确认的纠错建议；读取失败时重新核对。',target:{kind:'transcript',corrections:true}},
 {id:'saved',label:'纪要正文和要素已保存',codes:['BODY_REQUIRED','ELEMENT_SET_REQUIRED','GENERATION_REQUIRED','SETTINGS_REQUIRED'],reason:'请保存正文和核心要素，完成生成所需设置。',target:{kind:'minutes'}},
 {id:'versions',label:'转写、材料和生成版本一致',codes:['SAVED_SOURCE_OR_SETTINGS_STALE'],reason:'来源或设置已变化，请重新生成并采用当前版本。',target:{kind:'settings'}},
 {id:'conclusions',label:'重要结论与行动有会议依据',codes:[],reason:'请打开对应条目核对会议来源，并提交全文审阅。',target:{kind:'minutes'}},
 {id:'actions',label:'行动负责人和截止信息已核对',codes:[],reason:'请核对已保存的行动字段；没有截止日期时可以保持为空。',target:{kind:'minutes'}},
 {id:'background',label:'背景引用未代替会议结论',codes:[],reason:'请核对背景材料及其与会议结论的区别。',target:{kind:'background'}},
 {id:'sensitive',label:'敏感设置已应用且无冲突',codes:['SENSITIVITY_UNRESOLVED','EXCLUDED_SOURCE_USED','EXCLUDED_TEXT_REINTRODUCED'],reason:'请处理敏感排除选择与当前正文的冲突。',target:{kind:'sensitive'}},
 {id:'pending',label:'无待生成任务和待采用候选',codes:['PENDING_CANDIDATE','OUTSTANDING_GENERATION'],reason:'请处理当前生成任务或候选，再重新检查。',target:{kind:'tasks'}},
 {id:'sources',label:'来源关系完整且可读取',codes:['MATERIALS_UNAVAILABLE','TASK11_NOT_INSTALLED','PUBLICATION_HISTORY_UNAVAILABLE'],reason:'来源或确认依赖暂不可读取，请重试；仍失败时保留当前内容。',target:{kind:'retry'}},
 {id:'permission',label:'当前用户具备最终确认权限',codes:['PUBLISHED','PUBLICATION_BUSY'],reason:'会议已经发布或发布任务尚未结束，请核对当前结果。',target:{kind:'retry'}},
];

/** A projection of the server's complete readiness check. It never writes or grants review credit. */
export function finalConfirmationChecks(readiness:FinalReadiness|null,owner:boolean,labels:Record<string,string>):FinalCheck[]{
 const covered=new Set([...rules.flatMap(r=>r.codes),'REVIEW_REQUIRED']);
 const checks=rules.map(rule=>{
  const failed=!!readiness&&rule.codes.some(code=>readiness.blockers.includes(code));
  const item:FinalCheck={id:rule.id,label:rule.label,status:readiness?failed?'failed':'passed':'unknown',reason:readiness?failed?rule.reason:'已通过':'尚未读取确认结果',actions:failed?[{label:'去处理',target:rule.target}]:[]};
  if(rule.id==='conclusions'||rule.id==='actions'){
   const reviewKind=rule.id==='actions'?'ACTION_FIELDS':'SOURCE_SUPPORT';
   const pending=readiness?.required.filter(r=>r.reviewKind===reviewKind&&!r.satisfied)??[];
   if(pending.length){item.status='failed';item.reason=rule.reason;item.actions=pending.map(r=>({label:labels[r.targetId]??(reviewKind==='ACTION_FIELDS'?'行动项':'待核对条目'),target:{kind:'review',targetId:r.targetId,reviewKind}}));}
  }
  if(rule.id==='pending'&&readiness){
   item.actions=[...readiness.pendingCandidateIds.map(candidateId=>({label:'核对待采用候选',target:{kind:'candidate' as const,candidateId}})),...(readiness.outstandingTaskIds.length?[{label:'查看处理任务',target:{kind:'tasks' as const}}]:[])];
   if(item.status==='failed'&&!item.actions.length)item.actions=[{label:'查看处理状态',target:{kind:'tasks'}}];
  }
  if(rule.id==='permission'&&!owner){item.status='failed';item.reason='仅会议负责人可执行最终确认。';item.actions=[];}
  return item;
 });
 if(readiness?.blockers.some(code=>!covered.has(code))||readiness?.blockers.includes('REVIEW_REQUIRED')&&!readiness.required.some(r=>!r.satisfied))checks.push({id:'other',label:'其他确认条件',status:'unknown',reason:'服务器检查尚未完成，请重新读取当前结果。',actions:[{label:'重新检查',target:{kind:'retry'}}]});
 return checks;
}
