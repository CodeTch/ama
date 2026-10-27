import type {components} from './generated';
export function revisionToken(value:unknown):value is string {return typeof value==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(value)&&(value.length<19||value<='9223372036854775806');}
function shape(value:unknown,keys:string):value is Record<string,unknown>{return typeof value==='object'&&value!==null&&!Array.isArray(value)&&Object.keys(value).sort().join(',')===keys;}
const uuid=(v:unknown)=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function observation(value:unknown):boolean {
 return shape(value,'accessRevision,accessRevisionToken,currentConfirmationId,currentMinutesRevisionId,revision,revisionToken')
  &&typeof value.revision==='number'&&Number.isInteger(value.revision)&&value.revision>=0&&typeof value.accessRevision==='number'&&Number.isInteger(value.accessRevision)&&value.accessRevision>=0
  &&revisionToken(value.revisionToken)&&revisionToken(value.accessRevisionToken)
  &&(value.currentConfirmationId===null||uuid(value.currentConfirmationId))&&(value.currentMinutesRevisionId===null||uuid(value.currentMinutesRevisionId));
}
export function intentProjection(value:unknown,meetingId:string):value is components['schemas']['SavedIntentProjection'] {
 return shape(value,'intentEpochToken,meetingId,observation,state')&&uuid(value.meetingId)&&value.meetingId===meetingId&&observation(value.observation)
  &&(value.state==='KNOWN'?revisionToken(value.intentEpochToken):value.state==='UNKNOWN'&&value.intentEpochToken===null);
}
export function intentAck(value:unknown,meetingId:string):value is components['schemas']['SavedIntentAck'] {
 return shape(value,'current,replayed,result')&&typeof value.replayed==='boolean'&&shape(value.result,'kind,meetingId')&&value.result.kind==='EDIT_INTENT'&&value.result.meetingId===meetingId
  &&intentProjection(value.current,meetingId)&&value.current.state==='KNOWN'&&value.current.intentEpochToken!=='0';
}

export function intentConflict(value:unknown,meetingId:string):value is components['schemas']['SavedIntentConflict'] {
 return shape(value,'current,error')&&shape(value.error,'code')&&value.error.code==='REVISION_CONFLICT'&&intentProjection(value.current,meetingId);
}
