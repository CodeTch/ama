import {readonly,shallowRef} from 'vue';import type {createSubtitleApi,SubtitleEvent} from './subtitle-api';import {ApiError} from '../../shared/api/client';import {recordingUuid} from './recording-api';
type Options={actor:()=>string|null;meeting:()=>string|null;sessionId:()=>string|null;eligible:()=>boolean};
/** Event cursor is accepted only together with an entire verified page. Server storage owns recovery. */
export function useLiveSubtitles(api:ReturnType<typeof createSubtitleApi>,options:Options){
 const events=shallowRef<SubtitleEvent[]>([]),cursor=shallowRef('0'),delayed=shallowRef(false),error=shallowRef<string|null>(null),loading=shallowRef(false),ready=shallowRef(false),state=shallowRef('NOT_STARTED');
 let epoch=0,disposed=false,denied=false,scope:{actor:string;meeting:string;session:string}|null=null,controller:AbortController|null=null,timer:ReturnType<typeof setTimeout>|null=null,flight:Promise<void>|null=null,segments=new Map<string,SubtitleEvent>();
 const active=(turn:number)=>!disposed&&turn===epoch&&!!scope&&scope.actor===options.actor()&&scope.meeting===options.meeting()&&scope.session===options.sessionId()&&options.eligible();
 function clear(){segments=new Map();events.value=[];cursor.value='0';ready.value=false;}
 function halt(){epoch++;if(timer!==null)clearTimeout(timer);timer=null;controller?.abort();controller=null;flight=null;scope=null;loading.value=false;clear();}
 function synchronize(){halt();if(disposed)return;denied=false;error.value=null;delayed.value=false;state.value='NOT_STARTED';const actor=options.actor(),meeting=options.meeting(),session=options.sessionId();if(!actor||!recordingUuid(meeting)||!recordingUuid(session)||!options.eligible())return;scope={actor,meeting,session};void poll();}
 function poll():Promise<void>{
  if(flight)return flight;const turn=epoch,selected=scope;if(!active(turn)||!selected||denied)return Promise.resolve();loading.value=true;controller=new AbortController();const request=controller;let more=false;
  const run=async()=>{try{
   const result=await api.read(selected.meeting,selected.session,cursor.value,request.signal);if(!active(turn)||request.signal.aborted)return;
   const next=new Map(segments);for(const event of result.events){const prior=next.get(event.segmentId);if(prior&&event.revision<=prior.revision)throw new ApiError('response',200,'实时字幕版本暂无法核实。');next.set(event.segmentId,event);}if(next.size>4608)throw new ApiError('response',200,'实时字幕超过当前显示限额，请使用会后转写。');
   segments=next;events.value=[...next.values()].sort((a,b)=>a.startMs-b.startMs||a.segmentId.localeCompare(b.segmentId));cursor.value=result.nextCursor;delayed.value=result.delayed;state.value=result.state;ready.value=true;error.value=null;more=result.hasMore;
  }catch(cause){if(active(turn)){clear();delayed.value=true;error.value=cause instanceof Error?cause.message:'实时字幕暂不可用，音频保存独立进行。';denied=cause instanceof ApiError&&[401,403].includes(cause.status??0);}}
  finally{if(active(turn)){loading.value=false;controller=null;flight=null;if(!denied)timer=setTimeout(()=>{timer=null;void poll();},more?0:500);}}};flight=run();return flight;
 }
 function refresh(){if(timer!==null)clearTimeout(timer);timer=null;denied=false;return poll();}
 function dispose(){halt();disposed=true;error.value=null;delayed.value=false;}
 return{events:readonly(events),cursor:readonly(cursor),delayed:readonly(delayed),error:readonly(error),loading:readonly(loading),ready:readonly(ready),state:readonly(state),synchronize,refresh,dispose};
}
