import type {WorkspaceNavigation} from '../../shared/api/workspace';

/** Entry is distinct from final readiness: missing information is completed on the confirmation page. */
export function workspaceStages(value:WorkspaceNavigation|null|undefined,publishedObserved=false){
 const saved=value?.saved,details=value?.privateSections;
 const published=publishedObserved||value?.stage==='PUBLISHED';
 const imported=details?.tasks?.items.some(task=>task.kind==='TRANSCRIPT_IMPORT_V1'&&task.state==='SUCCEEDED');
 const recorded=details?.tasks?.items.some(task=>task.kind==='RECORDING_ARCHIVE_V1');
 const transcript=published||!!saved?.transcriptRevisionId||!!imported&&!recorded;
 const minutes=published||!!saved?.transcriptConfirmationId;
 const savedMinutes=published||!!saved?.minutesRevisionId&&!!saved?.elementSetId;
 const candidates=!!details?.candidates?.items.length||!!details?.candidates?.hasMore;
 const generation=details?.tasks?.items.some(task=>task.kind.startsWith('MINUTES_')&&!['SUCCEEDED','FAILED','CANCELLED'].includes(task.state));
 const final=published||!!details?.finalConfirmationId;
 return {available:{processing:true,transcript,minutes,confirm:savedMinutes&&(value?.relation==='MEMBER'||!!details?.tasks&&!!details?.candidates&&!details.tasks.hasMore&&!candidates&&!generation),publish:final,published},complete:[transcript,minutes,savedMinutes,final,published]};
}

export function resolveMeetingStage(requested:unknown,available:ReturnType<typeof workspaceStages>['available']){
 if(available.published)return 'published';
 if(typeof requested==='string'&&available[requested as keyof typeof available])return requested;
 return available.minutes?'minutes':available.transcript?'transcript':'processing';
}
