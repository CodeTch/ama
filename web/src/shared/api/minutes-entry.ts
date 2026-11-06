import type {useSession} from '../composables/useSession';
import {createWorkspaceApi} from './workspace';
import {createMeetingInfoApi} from './meeting-info';
import {createMinutesSavedApi} from './minutes-saved';

export function createMinutesEntry(session:ReturnType<typeof useSession>){
 const actor=()=>session.current.value?.actor.enterpriseId??null;
 const workspace=createWorkspaceApi(session.client,()=>session.current.value);
 const minutesSaved=createMinutesSavedApi(session.client,actor);
 const info=createMeetingInfoApi(session.client,actor);
 // One direct URL entry may overlap the route chunk download with its independently authorized read.
 // Claims are single use, tied to the session object and a short startup window.
 function entryReader<T>(read:(meeting:string,signal:AbortSignal)=>Promise<T>){
  let entry:{meeting:string;identity:typeof session.current.value;controller:AbortController;started:number;result:Promise<T>}|null=null;
  function clear(){entry?.controller.abort();entry=null;}
  function start(meeting:string){
   const identity=session.current.value;
   if(entry?.meeting===meeting&&entry.identity===identity&&performance.now()-entry.started<=4000)return;
   clear();
   if(!identity)return;
   const controller=new AbortController(),result=read(meeting,controller.signal);
   void result.catch(()=>{}); // A redirect can supersede this request before the component claims it.
   entry={meeting,identity,controller,started:performance.now(),result};
  }
  function claim(meeting:string,signal:AbortSignal){
   const request=entry;entry=null;
   if(!request)return;
   if(request.meeting!==meeting||request.identity!==session.current.value||performance.now()-request.started>4000||signal.aborted){request.controller.abort();return;}
   const cancel=()=>request.controller.abort();
   signal.addEventListener('abort',cancel,{once:true});
   return request.result.finally(()=>signal.removeEventListener('abort',cancel));
  }
  return {start,clear,claim};
 }
 const content=entryReader(minutesSaved.current),navigation=entryReader(workspace.navigation),information=entryReader(info.saved);
 function clearMinutesEntry(){content.clear();navigation.clear();information.clear();}
 function preloadMinutesEntry(meeting:string){content.start(meeting);navigation.start(meeting);information.start(meeting);}
 return {info,workspace,minutesSaved,preloadMinutesEntry,clearMinutesEntry,claimMinutesEntry:content.claim,claimNavigation:navigation.claim,claimInformation:information.claim};
}
