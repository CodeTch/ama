import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {recordingUuid} from './recording-api';
export type RecordingProcessIntent={actor:string;meeting:string;session:string;requestId:string;expectedRevisionToken?:string;materialSnapshotId?:string|null};
export function createRecordingProcessIntent(context:TabJournalContext){
 return createScopedPrivateJournal(context,'recording-process-intent',(v:unknown,actor,meeting):v is RecordingProcessIntent=>{
  if(!v||typeof v!=='object'||Array.isArray(v))return false;
  const p=v as RecordingProcessIntent;
  return (Object.keys(p).sort().join(',')==='actor,meeting,requestId,session'||Object.keys(p).sort().join(',')==='actor,expectedRevisionToken,materialSnapshotId,meeting,requestId,session'&&typeof p.expectedRevisionToken==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(p.expectedRevisionToken)&&(p.materialSnapshotId===null||recordingUuid(p.materialSnapshotId)))&&p.actor===actor&&p.meeting===meeting&&recordingUuid(p.session)&&recordingUuid(p.requestId);
 });
}
