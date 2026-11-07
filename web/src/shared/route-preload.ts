// Code only: hover/focus never creates meetings, fetches private content or opens devices.
export const workspaceRoutes={
 home:()=>import('../App.vue'),
 meeting:()=>import('../features/meetings/MeetingDetail.vue'),
 recording:()=>import('../features/meetings/RecordingPage.vue'),
 feedback:()=>import('../features/meetings/FeedbackPage.vue'),
 library:()=>import('../features/meetings/MeetingLibraryPage.vue'),
 voices:()=>import('../features/governance/GovernanceVoices.vue'),
};
export function preloadWorkspaceRoute(path:string){
 const load=path.startsWith('/meeting/')?workspaceRoutes.meeting:path.startsWith('/recording')?workspaceRoutes.recording:path==='/feedback'?workspaceRoutes.feedback:path.startsWith('/meeting-library')?workspaceRoutes.library:path==='/my/voiceprints'?workspaceRoutes.voices:path==='/meetings'?workspaceRoutes.home:null;
 if(load)void load().catch(()=>{});
}
export function installRoutePreloading(){
 const loaded=new Set<string>();
 function intent(event:Event){
  const target=event.target;if(!(target instanceof Element))return;
  const element=target.closest<HTMLElement>('[data-prefetch],a[href]');if(!element)return;
  const path=element.dataset.prefetch??element.getAttribute('href');if(!path||!path.startsWith('/')||loaded.has(path))return;
  if((navigator as Navigator&{connection?:{saveData?:boolean}}).connection?.saveData)return;
  if(loaded.size>=64)loaded.clear();loaded.add(path);preloadWorkspaceRoute(path);
 }
 document.addEventListener('pointerover',intent,{passive:true});document.addEventListener('focusin',intent);
 return ()=>{document.removeEventListener('pointerover',intent);document.removeEventListener('focusin',intent);};
}
