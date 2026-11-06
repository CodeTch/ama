import type {WorkspaceNavigation} from '../../shared/api/workspace';

/** Entry is distinct from final readiness: missing information is completed on the confirmation page. */
export function workspaceStages(value:WorkspaceNavigation|null|undefined,publishedObserved=false){
 const saved=value?.saved,details=value?.privateSections;
 const published=publishedObserved||value?.stage==='PUBLISHED';
 const imported=value?.processingSource?.kind==='TRANSCRIPT_IMPORT_V1' ? value.processingSource : details?.tasks?.items.find(task=>task.kind==='TRANSCRIPT_IMPORT_V1');
 const recorded=value?.processingSource?.kind==='RECORDING_ARCHIVE_V1' || details?.tasks?.items.some(task=>task.kind==='RECORDING_ARCHIVE_V1');
 const transcript=published||!!saved?.transcriptRevisionId||(value?.processingMaterialStage??imported?.state)==='SUCCEEDED'&&!recorded;
 const minutes=published||!!saved?.transcriptConfirmationId;
 const savedMinutes=published||!!saved?.minutesRevisionId&&!!saved?.elementSetId;
 const candidates=!!details?.candidates?.items.length||!!details?.candidates?.hasMore;
 const generation=value?.activeMinutesGeneration??(details?.tasks?.hasMore||details?.tasks?.items.some(task=>task.kind==='MINUTES_GENERATE_V1'&&['READY','RUNNING','RECONCILING'].includes(task.state)));
 const final=published||!!details?.finalConfirmationId;
 if(value?.relation==='MEMBER'&&!published)return {available:{processing:false,transcript:true,minutes:true,confirm:false,publish:false,published:false},complete:[transcript,minutes,savedMinutes,final,published]};
 return {available:{processing:true,transcript,minutes,confirm:savedMinutes&&minutes&&!!details?.tasks&&!!details?.candidates&&!candidates&&!generation,publish:final,published},complete:[!published&&imported&&!recorded?(!value?.processingMaterialsChanged&&(value?.processingMaterialStage??imported.state)==='SUCCEEDED'):transcript,minutes,savedMinutes,final,published]};
}

export function resolveMeetingStage(requested:unknown,available:ReturnType<typeof workspaceStages>['available']){
 if(available.published)return 'published';
 if(typeof requested==='string'&&available[requested as keyof typeof available])return requested;
 if(!available.processing&&available.transcript)return 'transcript';
 return available.minutes?'minutes':available.transcript?'transcript':'processing';
}
