import type {components} from '../../shared/api/generated';
export type Capability=components['schemas']['OperationsCapabilities'];
export type Catalog=components['schemas']['OperationsCatalog'];
export type Candidate=components['schemas']['OperationsCandidate'];
export type CandidateDetail=components['schemas']['OperationsCandidateDetail'];
export type Report=components['schemas']['OperationsReport'];
export type Rules=components['schemas']['OperationsQualificationRules'];
export type DatasetDetail=components['schemas']['OperationsDatasetDetail'];
export type DatasetPage=components['schemas']['OperationsDatasetsPage'];
export type CandidatePage=components['schemas']['OperationsCandidatesPage'];
export type EvaluationPage=components['schemas']['OperationsEvaluationsPage'];
export type CorpusPage=components['schemas']['OperationsCorpusPage'];
export type VoicePage=components['schemas']['OperationsVoiceprintsPage'];
export type VoiceDetail=components['schemas']['OperationsVoiceDetail'];
export type WorkbenchSample=components['schemas']['OperationsWorkbenchSample'];
export type SpeakerCandidates=components['schemas']['OperationsSpeakerCandidates'];
export const defaultRules=():Rules=>({maxCer:.15,maxDer:.2,minEntityF1:.85,minEvidenceCoverage:.9,minEvidenceCorrectness:.95,minMinutesRubric:.8,maxFar:.05,maxFrr:.1,minimumPerKind:1});
export const kindLabels:Record<string,string>={ASR:'语音识别',DIARIZATION:'说话人分离',MINUTES:'会议纪要',VOICE_SAME:'声纹 · 同人',VOICE_DIFFERENT:'声纹 · 异人',ENTITY:'实体抽取',EVIDENCE:'证据链接'};
export const feedbackLabels:Record<string,string>={PERSON:'人名纠错',DEPARTMENT:'部门纠错',PRODUCT:'产品纠错',FINANCIAL_TERM:'金融术语纠错',PRODUCT_ALIAS:'产品别名',TERM_PRONUNCIATION:'术语读音'};
export const stateLabels:Record<string,string>={PENDING:'待审核',RUNNING:'运行中',COMPLETED:'已完成',CANCELLED:'已取消',APPROVED:'已批准',ACCEPTED:'已通过',REJECTED:'已驳回',NEEDS_MORE:'需要补充',REVOKED:'已撤销',DRAFT:'待评测',PUBLISHED:'已发布',SAVED:'已保存'};
export const usageLabels:Record<string,string>={EFFECTIVE:'已生效',NOT_APPLIED:'尚未采用',PENDING_ADOPTION:'等待同步',WITHDRAWN:'已撤回',REVOKED:'授权已撤销',EXPIRED:'授权已过期',DISABLED:'人员已停用',INVALID_SOURCE:'来源已变化',LEGACY:'历史未结构化',INCOMPATIBLE:'模型不兼容',MATCHING_DISABLED:'候选匹配未启用'};
export function localTime(value:string|null|undefined){if(!value)return '—';const date=new Date(value);return Number.isNaN(+date)?'—':new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(date);}
export function percent(value:number|undefined){return value===undefined?'—':`${(value*100).toFixed(1)}%`;}
