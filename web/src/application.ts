import {inject,type InjectionKey} from 'vue';
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
export function createApplication(origin:string,journals:TabJournalContext={tab:crypto.randomUUID(),storage:null,recovery:'NEW_NAMESPACE'}){
 const session=useSession(origin),meetings=createMeetingsApi(session.client),list=useMeetingList(meetings.list,()=>session.current.value?.actor.enterpriseId??null);
 const workspace=createWorkspaceApi(session.client),tasks=createWorkspaceTasksApi(session.client);
 const stop=session.onInvalidate(list.invalidate);
 const info=createMeetingInfoApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const create=useMeetingCreate(info.send,()=>session.current.value?.actor.enterpriseId??null,journals.storage,journals.tab,journals.recovery);
 const discovery=createCommandDiscoveryApi(session.client,()=>session.current.value?.actor.enterpriseId??null);
 const createDiscovery=useCommandDiscovery(discovery.lookup,()=>session.current.value?.actor.enterpriseId??null,()=>create.command.snapshot.value);
 const stopLogout=session.onLogout(create.logout);
 return {session,meetings,info,create,discovery,createDiscovery,list,workspace,tasks,dispose(){createDiscovery.dispose();stop();stopLogout();list.dispose();session.invalidate();create.dispose();}};
}
export const applicationKey:InjectionKey<ReturnType<typeof createApplication>>=Symbol('ama-application');
export function useApplication(){const app=inject(applicationKey);if(!app)throw new Error('Application context missing');return app;}
