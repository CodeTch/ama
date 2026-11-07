import {readonly,shallowRef,reactive,markRaw} from 'vue';
import type {createSubtitleApi,SubtitleEvent} from './subtitle-api';
import {ApiError} from '../../shared/api/client';
import {recordingUuid} from './recording-api';
type Options={actor:()=>string|null;meeting:()=>string|null;sessionId:()=>string|null;eligible:()=>boolean;recording?:()=>boolean};
/** Owned by the application: an accepted cursor, captions and reading position share one scope. */
export function useLiveSubtitles(api:ReturnType<typeof createSubtitleApi>,options:Options){
 const events=shallowRef<SubtitleEvent[]>([]),cursor=shallowRef('0'),delayed=shallowRef(false),error=shallowRef<string|null>(null),loading=shallowRef(false),retrying=shallowRef(false),ready=shallowRef(false),state=shallowRef('NOT_STARTED');
 const reading=reactive({following:true,scrollTop:0,measurements:{sizes:markRaw(new Map<string,number>()),width:0}});
 let epoch=0,disposed=false,denied=false,failures=0,scope:{actor:string;meeting:string;session:string}|null=null,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null,flight:Promise<void>|null=null,retryFlight:Promise<void>|null=null;
 let segments=new Map<string,SubtitleEvent>(),ordered:SubtitleEvent[]=[];
 const visible=()=>typeof document==='undefined'||document.visibilityState!=='hidden';
 const active=(turn:number)=>!disposed&&turn===epoch&&!!scope&&scope.actor===options.actor()&&scope.meeting===options.meeting()&&scope.session===options.sessionId()&&options.eligible();
 function clear(){segments=new Map();ordered=[];events.value=[];cursor.value='0';ready.value=false;reading.following=true;reading.scrollTop=0;reading.measurements.sizes.clear();reading.measurements.width=0;}
 function stopRequest(){if(timer!==null)clearTimeout(timer);timer=null;controller?.abort();controller=null;flight=null;loading.value=false;}
 function halt(){epoch++;stopRequest();scope=null;retryFlight=null;retrying.value=false;clear();}
 function synchronize(){
  const actor=options.actor(),meeting=options.meeting(),session=options.sessionId();
  if(!disposed&&options.eligible()&&scope?.actor===actor&&scope.meeting===meeting&&scope.session===session)return;
  halt();if(disposed)return;denied=false;failures=0;error.value=null;delayed.value=false;state.value='NOT_STARTED';
  if(!actor||!recordingUuid(meeting)||!recordingUuid(session)||!options.eligible())return;
  scope={actor,meeting,session};void poll();
 }
 const compare=(a:SubtitleEvent,b:SubtitleEvent)=>a.startMs-b.startMs||a.segmentId.localeCompare(b.segmentId);
 function poll():Promise<void>{
  if(flight)return flight;const turn=epoch,selected=scope;if(!active(turn)||!selected||denied||!visible())return Promise.resolve();
  loading.value=true;const request=new AbortController();controller=request;let more=false;
  const run=async()=>{try{
   const result=await api.read(selected.meeting,selected.session,cursor.value,request.signal);if(!active(turn)||request.signal.aborted)return;
   // Validate a complete page before mutating accepted history or its cursor.
   const revisions=new Map<string,SubtitleEvent>();
   for(const event of result.events){const prior=revisions.get(event.segmentId)??segments.get(event.segmentId);if(prior&&event.revision<=prior.revision)throw new ApiError('response',200,'实时字幕版本暂无法核实。');revisions.set(event.segmentId,event);}
   if(segments.size+[...revisions.keys()].filter(key=>!segments.has(key)).length>4608)throw new ApiError('response',200,'实时字幕超过当前显示限额，请使用会后转写。');
   if(revisions.size){
    for(const event of revisions.values()){
     const prior=segments.get(event.segmentId);
     if(prior){const index=ordered.indexOf(prior);ordered.splice(index,1);}
     let lo=0,hi=ordered.length;while(lo<hi){const mid=(lo+hi)>>>1;if(compare(ordered[mid]!,event)<=0)lo=mid+1;else hi=mid;}
     ordered.splice(lo,0,event);segments.set(event.segmentId,event);
    }
    events.value=ordered.slice();
   }
   cursor.value=result.nextCursor;delayed.value=result.delayed;state.value=result.state;ready.value=true;error.value=null;more=result.hasMore;failures=0;
  }catch(cause){if(active(turn)&&!request.signal.aborted){failures++;delayed.value=true;error.value=cause instanceof Error?cause.message:'实时字幕暂不可用，音频保存独立进行。';denied=cause instanceof ApiError&&[401,403].includes(cause.status??0);if(denied)clear();}}
  finally{if(active(turn)&&controller===request){loading.value=false;controller=null;flight=null;const terminal=['DONE','CANCELLED','DEFERRED'].includes(state.value)&&!more&&!error.value;if(!denied&&visible()&&!terminal){const wait=more?0:failures?Math.min(15000,500*2**Math.min(failures,5)):options.recording?.()===false?2000:500;timer=setTimeout(()=>{timer=null;void poll();},wait);}}}};
  flight=run();return flight;
 }
 function refresh(){if(timer!==null)clearTimeout(timer);timer=null;denied=false;return poll();}
 function retryRecognition():Promise<void>{
  if(retryFlight)return retryFlight;if(!scope)return Promise.resolve();const selected=scope;retrying.value=true;
  const flight=(async()=>{try{await api.retry(selected.session);if(scope===selected)await refresh();}catch(cause){if(scope===selected)error.value=cause instanceof Error?cause.message:'实时识别暂无法恢复';}})();
  retryFlight=flight;void flight.finally(()=>{if(retryFlight===flight){retryFlight=null;retrying.value=false;}});return flight;
 }
 function visibilityChanged(){if(!visible()){stopRequest();}else void refresh();}
 if(typeof document!=='undefined')document.addEventListener('visibilitychange',visibilityChanged);
 function dispose(){halt();disposed=true;error.value=null;delayed.value=false;if(typeof document!=='undefined')document.removeEventListener('visibilitychange',visibilityChanged);}
 return{events:readonly(events),cursor:readonly(cursor),delayed:readonly(delayed),error:readonly(error),loading:readonly(loading),retrying:readonly(retrying),ready:readonly(ready),state:readonly(state),reading,synchronize,refresh,retryRecognition,dispose};
}
