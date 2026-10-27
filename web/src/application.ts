import {inject,shallowRef,shallowReadonly,watch,type InjectionKey} from 'vue';
import {useSession} from './shared/composables/useSession';
import {createMeetingsApi} from './shared/api/meetings';
import {createMeetingInfoApi} from './shared/api/meeting-info';
import {createCommandDiscoveryApi} from './shared/api/command-discovery';
import {useCommandDiscovery} from './shared/composables/useCommandDiscovery';
import {useMeetingCreate} from './features/meetings/useMeetingCreate';
import type {TabJournalContext} from './shared/composables/tab-journal';
import {createWorkspaceApi} from './shared/api/workspace';
import {createWorkspaceTasksApi} from './shared/api/workspace-tasks';
import {useMeetingList} from './features/meetings/useMeetingList';
import {createMeetingOptionsApi} from './shared/api/meeting-options';
import {createInfoJournal} from './features/meetings/info-journal';
import {createUploadsApi} from './shared/api/uploads';
import {createTranscriptImportApi} from './shared/api/transcript-import';
import {useUploadQueue} from './features/import/useUploadQueue';
import {createTranscriptsApi} from './shared/api/transcripts';
import {createTranscriptJournal} from './features/transcript/transcript-journal';
import {createMinutesGenerationApi,validGenerationCommand} from './shared/api/minutes-generation';
import {createScopedPrivateJournal} from './shared/composables/scoped-private-journal';
import {createMinutesSavedApi,validMinutesCommand} from './shared/api/minutes-saved';
import {createMinutesInspectionApi} from './shared/api/minutes-inspection';
import {createMinutesEditorJournal} from './features/minutes/minutes-editor-journal';
import {createPublicationsApi,validPublicationCommand} from './shared/api/publications';
import {ApiError} from './shared/api/client';
export function createApplication(origin:string,journals:TabJournalContext={tab:crypto.randomUUID(),storage:null,recovery:'NEW_NAMESPACE'}){
 const session=useSession(origin),meetings=createMeetingsApi(session.client),list=useMeetingList(meetings.list,()=>session.current.value?.actor.enterpriseId??null);
 const workspace=createWorkspaceApi(session.client),tasks=createWorkspaceTasksApi(session.client);
 const publications=createPublicationsApi(session.client,()=>session.current.value?.actor.enterpriseId??null),publicationJournal=createScopedPrivateJournal(journals,'minutes-publication',validPublicationCommand);
 const minutesSaved=createMinutesSavedApi(session.client,()=>session.current.value?.actor.enterpriseId??null),minutesInspection=createMinutesInspectionApi(session.client);
 const minutesEditorJournal=createMinutesEditorJournal(journals),minutesActionsJournal=createScopedPrivateJournal(journals,'minutes-actions',validMinutesCommand),minutesScopeReady=shallowRef(true);
 function syncMinutes(actor:string|null,id?:string|null){const editor=minutesEditorJournal.synchronize(actor,id),actions=minutesActionsJournal.synchronize(actor,id);const publication=publicationJournal.synchronize(actor,id);minutesScopeReady.value=editor&&actions&&publication;}
 const stopMinutesIdentity=watch(()=>session.current.value?.actor.enterpriseId??null,actor=>{if(actor!==null)syncMinutes(actor);},{flush:'sync'});
 const stop=session.onInvalidate(list.invalidate);
 const info=createMeetingInfoApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const create=useMeetingCreate(info.send,()=>session.current.value?.actor.enterpriseId??null,journals.storage,journals.tab,journals.recovery);
 const discovery=createCommandDiscoveryApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const createDiscovery=useCommandDiscovery(discovery.lookup,()=>session.current.value?.actor.enterpriseId??null,()=>create.command.snapshot.value);
 const options=createMeetingOptionsApi(session.client),infoJournal=createInfoJournal(journals);
 const generationJournal=createScopedPrivateJournal(journals,'minutes-generation',validGenerationCommand),generationScopeReady=shallowRef(true),minutesGeneration=createMinutesGenerationApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const stopGenerationIdentity=watch(()=>session.current.value?.actor.enterpriseId??null,actor=>{if(actor!==null)generationScopeReady.value=generationJournal.synchronize(actor);},{flush:'sync'});
 const transcriptJournal=createTranscriptJournal(journals),transcriptScopeReady=shallowRef(true),transcripts=createTranscriptsApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const stopTranscriptIdentity=watch(()=>session.current.value?.actor.enterpriseId??null,actor=>{if(actor!==null)transcriptScopeReady.value=transcriptJournal.synchronize(actor);},{flush:'sync'});
 const infoScopeReady=shallowRef(true),meetingScope=shallowRef<string|null>(null);
 const stopInfoIdentity=watch(()=>session.current.value?.actor.enterpriseId??null,actor=>{if(actor!==null)infoScopeReady.value=infoJournal.synchronize(actor);},{flush:'sync'});
 function setMeetingScope(id:string|null){syncMinutes(session.current.value?.actor.enterpriseId??null,id);meetingScope.value=id;generationScopeReady.value=generationJournal.synchronize(session.current.value?.actor.enterpriseId??null,id);transcriptScopeReady.value=transcriptJournal.synchronize(session.current.value?.actor.enterpriseId??null,id);infoScopeReady.value=infoJournal.synchronize(session.current.value?.actor.enterpriseId??null,id);}
 const actor=()=>session.current.value?.actor.enterpriseId??null,uploads=createUploadsApi(session.client,actor),intake=createTranscriptImportApi(session.client,actor);
 const uploadQueue=useUploadQueue(uploads,async(id,signal)=>{const nav=await workspace.navigation(id,signal);if(nav.relation!=='OWNER'||nav.navigationState!=='CHECK_PRODUCER')throw new ApiError('http',403,'当前身份或会议状态不允许上传。');return nav;},actor,()=>meetingScope.value??'',()=>meetingScope.value!==null,journals,intake);
 const stopLogout=session.onLogout(()=>{const a=minutesEditorJournal.clear(),b=minutesActionsJournal.clear();const c=publicationJournal.clear();minutesScopeReady.value=a&&b&&c;create.logout();infoScopeReady.value=infoJournal.clear();uploadQueue.logout();generationScopeReady.value=generationJournal.clear();transcriptScopeReady.value=transcriptJournal.clear();});
 return {publications,minutesSaved,minutesInspection,minutesScopeReady:shallowReadonly(minutesScopeReady),minutesGeneration,generationScopeReady:shallowReadonly(generationScopeReady),transcripts,transcriptScopeReady:shallowReadonly(transcriptScopeReady),session,meetings,info,create,discovery,createDiscovery,list,workspace,tasks,options,journals,uploads,intake,uploadQueue,setMeetingScope,meetingScope:shallowReadonly(meetingScope),infoScopeReady:shallowReadonly(infoScopeReady),dispose(){stopMinutesIdentity();stopGenerationIdentity();stopTranscriptIdentity();uploadQueue.dispose();createDiscovery.dispose();stop();stopLogout();stopInfoIdentity();list.dispose();session.invalidate();create.dispose();}};
}
export const applicationKey:InjectionKey<ReturnType<typeof createApplication>>=Symbol('ama-application');
export function useApplication(){const app=inject(applicationKey);if(!app)throw new Error('Application context missing');return app;}
