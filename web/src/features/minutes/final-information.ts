import type {components} from '../../shared/api/generated';import type {InfoFields} from '../../shared/api/meeting-info';
export function canPrepareFinalInformation(value:components['schemas']['FinalReadiness']|null){return !!value&&value.required.every(item=>item.satisfied)&&(value.ready?value.blockers.length===0:value.blockers.length>0&&value.blockers.every(code=>['MEETING_INFO_REQUIRED','ROSTER_CONFIRMATION_REQUIRED'].includes(code)));}
export type InformationValidation={field:'name'|'occurredOn'|'category';message:string};
export function validateFinalInformation(value:InfoFields):InformationValidation|null{
 if(!value.name?.trim())return {field:'name',message:'会议名称必填，请补充后确认'};
 // D-03: the actual meeting date is optional; never replace it with creation time.
 if(value.occurredOn){const date=new Date(value.occurredOn+'T00:00:00Z');if(!/^\d{4}-\d{2}-\d{2}$/.test(value.occurredOn)||!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==value.occurredOn)return {field:'occurredOn',message:'会议日期无效，请重新选择'};}
 if(!value.category)return {field:'category',message:'会议分类必填，请选择后确认'};
 return null;
}
export function finalInformationError(value:InfoFields):string|null{return validateFinalInformation(value)?.message??null;}
