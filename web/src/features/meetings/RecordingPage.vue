<script setup lang="ts">
import {recordingProcessingApi} from '../recording/processing-api';
import {useProcessingMaterials} from '../import/useProcessingMaterials';
import {useTranscriptFollow} from '../recording/useTranscriptFollow';
import {createRecordingProcessIntent} from '../recording/recording-process-intent';
import RecordingPartFiles from '../recording/RecordingPartFiles.vue';
import RecordingMaterialsDrawer from '../recording/RecordingMaterialsDrawer.vue';
import MaterialImportSection from '../import/MaterialImportSection.vue';
import type {MaterialState} from '../import/useMaterials';
import LiveTranscript from '../recording/LiveTranscript.vue';import {useLiveSubtitles} from '../recording/useLiveSubtitles';
import {computed,ref,watch,onMounted,onUnmounted,nextTick} from 'vue';import {useRoute,useRouter,onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router';import {useApplication} from '../../application';import {useMeetingCreateFlow} from './useMeetingCreateFlow';import MeetingInfoDrawer from './MeetingInfoDrawer.vue';import MeetingInfoEditor from './MeetingInfoEditor.vue';import Icon from '../../shared/Icon.vue';
const app=useApplication(),{session,pcmRecorder:native,create}=app,route=useRoute(),router=useRouter();
const infoEditorRef=ref<InstanceType<typeof MeetingInfoEditor>>();
async function closeInfo(){if(await infoEditorRef.value?.beforeLeave()!==false)infoOpen.value=false;}
async function canLeave(){
 const editor=infoEditorRef.value;
 if(editor&&(editor.pendingRecovery||editor.dirty||editor.conflict)){
  infoOpen.value=true;
  if(!await editor.beforeLeave())return false;
 }
 if(materialsState.value.closeBlocked&&(!finished.value||materialsDismissBlocked.value)){await openMaterials();return false;}
 if(authorizing.value){native.cancelAuthorization();return true;}
 return !busy.value;
}
onBeforeRouteUpdate((to,from)=>!from.params.id&&to.params.id===meeting.value||canLeave());
onBeforeRouteLeave(canLeave);
const localDevelopment=import.meta.env.DEV&&import.meta.env.VITE_LOCAL_DEV_LOGIN==='1';
const materialsOpened=ref(false),materialsOpen=ref(false),emptyMaterials=():MaterialState=>({busy:false,blocked:false,failed:false,hasFiles:false,closeBlocked:false}),materialsState=ref(emptyMaterials());
const materialsDismissBlocked=computed(()=>materialsState.value.dismissBlocked??materialsState.value.closeBlocked);
async function openMaterials(){if(!meeting.value||!actor())return;materialsOpened.value=true;materialsOpen.value=true;}
function closeMaterials(){if(materialsDismissBlocked.value)return false;materialsOpen.value=false;void materialSummary.reload();return true;}
const infoOpened=ref(false),infoOpen=ref(false),interruptDialog=ref<HTMLDialogElement>(),endDialog=ref<HTMLDialogElement>(),busy=ref(false),localError=ref<string|null>(null),endError=ref<string|null>(null),lastEnd=ref<'save'|'process'|null>(null),live=ref<HTMLElement>();
const actor=()=>session.current.value?.actor.enterpriseId??null,meeting=computed(()=>native.meetingId.value),active=computed(()=>native.phase.value==='RECORDING'),paused=computed(()=>native.phase.value==='PAUSED'),authorizing=computed(()=>native.phase.value==='AUTHORIZING'),saving=computed(()=>native.phase.value==='SAVING'),controlsBusy=computed(()=>busy.value||saving.value||authorizing.value||!!native.operation?.value&&!(native.operation.value==='reconnect'&&(active.value||paused.value))),failed=computed(()=>native.phase.value==='ERROR'),finished=computed(()=>native.record.value?.finished??false);
const parts=computed(()=>{
 const record=native.record.value;
 return record?.parts.map((part,index)=>{
  const last=index===record.parts.length-1;
  const closed=!last||record.finished||!active.value&&!paused.value&&!saving.value&&(native.phase.value==='INTERRUPTED'||['MANUAL','SYSTEM','FINISHED'].includes(part.endReason??''));
  const confirmed=part.sampleCount>0&&part.confirmedSequence===part.finalSequence&&part.confirmedSamples===part.sampleCount;
  return {id:part.partId,index:index+1,bytes:part.sampleCount*2,chunkCount:part.finalSequence,
   elapsedMs:part.sampleCount/16,savedElapsedMs:(part.confirmedSamples??0)/16,
   saved:closed&&confirmed,reason:part.endReason,incomplete:part.tailIntegrity!=='COMPLETE'};
 })??[];
});
const capacityReached=computed(()=>parts.value.length>=16&&native.phase.value==='INTERRUPTED'||parts.value.reduce((n,p)=>n+p.chunkCount,0)>=4096||parts.value.reduce((n,p)=>n+p.elapsedMs,0)>=7200000);
const capacityNotice=computed(()=>parts.value.length>=16&&native.phase.value==='INTERRUPTED'?'已达到16段录音上限，请结束保存':capacityReached.value?'已达到录音时长或分片上限，请结束保存':parts.value.length>=15?'录音接近16段上限，请及时结束保存':parts.value.reduce((n,p)=>n+p.chunkCount,0)>=3900?'录音接近分片上限，请及时结束保存':r.elapsed.value>=6900000?'录音接近两小时上限，请及时结束保存':null);
const savedParts=computed(()=>parts.value.filter(p=>p.saved));
const recordedCount=computed(()=>parts.value.filter(p=>p.bytes>0).length);
type SubtitlePart={partId:string;partNo:number;startMs:number};
const subtitleParts=computed<SubtitlePart[]>((previous)=>{
 let samples=0;
 const result=(native.record.value?.parts??[]).map((part,index)=>{
  const item={partId:part.partId,partNo:index+1,startMs:Math.floor(samples/16)};
  samples+=part.sampleCount;
  return item;
 });
 return previous?.length===result.length&&result.every((p,i)=>p.partId===previous[i]?.partId&&p.startMs===previous[i]?.startMs)?previous:result;
});
const pageMounted=ref(false),unavailableSession=ref<string|null>(null);
const materialSummary=useProcessingMaterials((id,signal)=>app.materials.list(id,signal),actor,()=>meeting.value??'',()=>pageMounted.value&&!!meeting.value);
const materialCount=computed(()=>materialsState.value.count??materialSummary.value.value?.items.length);
const r={...native},interrupted=computed(()=>native.phase.value==='INTERRUPTED'&&!busy.value&&parts.value.some(p=>p.bytes>0)),state=computed(()=>authorizing.value?'等待麦克风授权':saving.value?'保存中':busy.value?'正在恢复录音':unavailableSession.value?'上次录音不可用':failed.value?(native.failure.value?.recoveryAction==='authorize'?'麦克风不可用':native.failure.value?.recoveryAction==='initialize'?'初始化失败':'保存失败'):active.value?'录音中':paused.value?'已暂停':finished.value?'已结束采集':interrupted.value?'本段已中断':'等待开始录音');const label=computed(()=>failed.value?(native.failure?.value?.recoveryAction==='authorize'?'重新授权':native.failure?.value?.recoveryAction==='initialize'?'重试初始化':'重试保存'):active.value?'暂停录音':paused.value?'继续录音':recordedCount.value?'开始下一段':'重新授权并开始'),transcriptStatus=computed(()=>subtitles.state.value==='DEFERRED'?'实时识别已暂缓，可在会后转写':subtitles.state.value==='DONE'?'实时识别已结束':subtitles.state.value==='CANCELLED'?'实时识别已取消':subtitles.error.value?'字幕暂不可用，内容保留':paused.value?'已暂停，内容保留':interrupted.value?'已中断，内容保留':active.value?'识别中，内容可能调整':'当前累计内容'),saveStatus=computed(()=>native.pendingCount.value?`${native.pendingCount.value} 个分片待服务端确认`:finished.value?'录音已保存':native.record.value?.parts.some(p=>p.confirmedSamples===undefined)?'保存范围待核对':parts.value.some(p=>p.bytes>0)?'分片已获服务端保存确认':'等待录音数据');
const subtitles=app.liveSubtitles??useLiveSubtitles(app.subtitleApi,{actor,meeting:()=>meeting.value,sessionId:()=>native.record.value?.session.sessionId??null,eligible:()=>pageMounted.value&&!!actor()&&!!native.record.value&&route.params.id===meeting.value});
const follow=useTranscriptFollow(live,()=>subtitles.events.value,subtitles.reading);
const processIntent=createRecordingProcessIntent(app.journals);
const stopSubtitles=watch(()=>[pageMounted.value,actor(),route.params.id,meeting.value,native.record.value?.session.sessionId],()=>subtitles.synchronize(),{immediate:true,flush:'sync'});
function clock(ms:number){const t=Math.floor(Math.max(0,ms)/1000);return `${String(Math.floor(t/3600)).padStart(2,'0')}:${String(Math.floor(t/60)%60).padStart(2,'0')}:${String(t%60).padStart(2,'0')}`;}
const flow=useMeetingCreateFlow(create,actor,async id=>{await native.attach(id);await router.replace('/recording/'+id);app.setMeetingScope(id);return router.currentRoute.value.path==='/recording/'+id;});
async function initialize(){
 const who=actor();if(!who)return;
 const id=typeof route.params.id==='string'?route.params.id:null;
 if(id){
  await native.attach(id);
  if(pageMounted.value&&who===actor()&&route.params.id===id)app.setMeetingScope(id);
  return;
 }
 if(unavailableSession.value)return;
 if(native.record.value?.finished)await native.invalidate();
 const old=await native.restore();
 if(!pageMounted.value||who!==actor()||route.params.id)return;
 if(old&&'unavailableSessionId' in old){unavailableSession.value=old.unavailableSessionId;return;}
 if(old&&!old.finished){
  await router.replace('/recording/'+old.meetingId);
  if(!pageMounted.value||who!==actor())return;
  if(router.currentRoute.value.params.id!==old.meetingId)throw Error('无法打开原录音，请重试');
  app.setMeetingScope(old.meetingId);
 }
}
let initializing:Promise<void>|null=null;
function initializeOnce(){
 if(initializing)return initializing;
 initializing=initialize().finally(()=>{initializing=null;});
 return initializing;
}
async function start(fresh=false,automatic=false){
 const who=actor();if(busy.value||!who)return;
 busy.value=true;localError.value=null;
 try{
  if(fresh&&unavailableSession.value){
   await native.skipAutomaticRestore(unavailableSession.value);
   if(pageMounted.value&&who===actor()&&meeting.value)await native.invalidate();
  }
  else{await initializeOnce();if(unavailableSession.value)return;}
  if(automatic&&native.record.value)return;
  if(!pageMounted.value||who!==actor()||finished.value||active.value||paused.value||failed.value)return;
  if(!meeting.value){await flow.open();if(flow.error.value)throw flow.error.value;}
  if(!pageMounted.value||who!==actor())return;
  if(!meeting.value)throw Error('会议创建尚未确认，请重试原请求');
  await native.start();
 }catch(cause){if(cause instanceof DOMException&&cause.name==='AbortError')return;if(pageMounted.value&&(who===actor()||!actor()))localError.value=native.error.value??(cause instanceof Error?cause.message:'无法开始录音');}
 finally{busy.value=false;}
}
async function chooseRecovery(id:string,sid:string){if(busy.value)return;busy.value=true;localError.value=null;try{await native.attach(id,sid);await router.replace('/recording/'+id);app.setMeetingScope(id);}catch(cause){localError.value=cause instanceof Error?cause.message:'原录音暂不可恢复';}finally{busy.value=false;}}
async function primary(){if(controlsBusy.value)return;localError.value=null;try{if(failed.value){const authorize=native.failure?.value?.recoveryAction==='authorize';await native.retry();if(authorize)await start();return;}if(active.value){await native.pause();return;}if(paused.value){await native.resume();return;}await start();}catch(cause){localError.value=native.error.value??(cause instanceof Error?cause.message:'录音操作尚未完成');}}
async function importFiles(){
 if(controlsBusy.value)return;
 localError.value=null;
 try{
  if(parts.value.some(p=>p.bytes>0)||r.elapsed.value>0){localError.value='请先结束并保存已有录音，再导入文件。';return;}
  const id=meeting.value;
  if(native.record.value){await native.retry();await app.recordingApi.discardEmpty(native.record.value!.session);await native.skipAutomaticRestore(native.record.value!.session.sessionId);}
  await native.invalidate();
  await router.push(id?{path:'/meetings',query:{import:id}}:'/meetings');
 }catch(cause){localError.value=cause instanceof Error?cause.message:'导入入口暂不可打开。';}
}
const reconnecting=ref(false);
async function reconnect(){if(reconnecting.value)return;reconnecting.value=true;localError.value=null;try{await native.reconnect();}catch(cause){localError.value=cause instanceof Error?cause.message:'恢复上传尚未完成';}finally{reconnecting.value=false;}}
async function interrupt(){const who=actor(),id=meeting.value;if(!who||busy.value)return;busy.value=true;localError.value=null;try{await native.interrupt();if(who===actor()&&id===meeting.value)interruptDialog.value?.close();}catch(cause){if(who===actor()&&id===meeting.value)localError.value=cause instanceof Error?cause.message:'本段保存未完成';}finally{busy.value=false;}}
async function end(){const who=actor(),id=meeting.value;if(!who||busy.value)return;try{if(active.value)await native.pause();if(who!==actor()||id!==meeting.value)return;lastEnd.value=null;endError.value=null;endDialog.value?.showModal();}catch(cause){if(who===actor()&&id===meeting.value)localError.value=cause instanceof Error?cause.message:'暂停未确认，请保留当前页面';}}
const processing=recordingProcessingApi(session.client);
async function finish(action:'save'|'process'){
 const who=actor(),id=meeting.value,sid=native.record.value?.session.sessionId;
 if(!who||!id||!sid||busy.value)return;
 busy.value=true;lastEnd.value=action;endError.value=null;
 try{
  if(action==='process'){
   const old=processIntent.load(who,id);
   if(old.kind==='INVALID'||old.kind==='UNAVAILABLE')throw Error('处理意图暂无法读取，请保留当前页面');
   if(old.kind==='RESTORED'&&old.value.session!==sid)throw Error('原处理请求属于另一份录音，请先核对。');
   const intent=old.kind==='RESTORED'?old.value:{actor:who,meeting:id,session:sid,requestId:crypto.randomUUID()};
   if(processIntent.save(who,id,intent)!=='SAVED')throw Error('无法保存处理意图，请保留当前页面');
  }else if(!processIntent.clear())throw Error('无法取消尚未提交的处理意图，请保留当前页面');
  if(failed.value)await native.retry();
  if(!finished.value)await native.finish();
  if(who!==actor()||id!==meeting.value)return;
  if(materialsDismissBlocked.value)throw Error('录音已保存，请等待材料上传完成或恢复原请求后继续。');
  busy.value=false;
  const stopped=await router.push(action==='process'?'/meeting/'+id+'/processing':'/meetings');
  if(stopped)throw Error('录音已保存，请完成当前操作后继续。');
  endDialog.value?.close();
 }catch(cause){if(who===actor()&&id===meeting.value)endError.value=cause instanceof Error?cause.message:'服务端保存尚未确认，请恢复后重试';}
 finally{busy.value=false;}
}

async function closeDecision(dialog:HTMLDialogElement|undefined){
 if(busy.value)return false;
 if(dialog===endDialog.value){
  if(finished.value&&lastEnd.value==='process'){
   const who=actor(),id=meeting.value,sid=native.record.value?.session.sessionId;
   if(!who||!id||!sid)return false;
   busy.value=true;
   try{
    const submitted=await processing.read(sid);if(who!==actor()||id!==meeting.value)return false;
    if(submitted.meetingId!==id)throw Error('录音归属暂无法核对');
    if(submitted.state!=='NOT_REQUESTED'){
     if(!processIntent.clear())throw Error('处理已受理，原请求记录暂无法清理。');
     busy.value=false;dialog?.close();await router.push('/meeting/'+id+'/processing');return true;
    }
   }catch(cause){if(who===actor()&&id===meeting.value)endError.value='须先核对原处理请求再取消：'+(cause instanceof Error?cause.message:'状态暂不可读取');return false;}
   finally{busy.value=false;}
  }
  if(!processIntent.clear()){endError.value='处理意图暂无法取消，请保留页面重试。';return false;}
  lastEnd.value=null;endError.value=null;
 }
 dialog?.close();return true;
}
watch(infoOpen,value=>{if(value)infoOpened.value=true;},{flush:'sync'});
function resetPrivateDialogs(){interruptDialog.value?.close();endDialog.value?.close();materialsOpen.value=false;materialsOpened.value=false;materialsState.value=emptyMaterials();infoOpen.value=false;infoOpened.value=false;localError.value=null;endError.value=null;lastEnd.value=null;}
watch(()=>[actor(),meeting.value],resetPrivateDialogs,{flush:'sync',immediate:true});
watch(()=>[actor(),route.params.id],()=>{unavailableSession.value=null;},{flush:'sync'});
async function load(){
 const who=actor();
 try{
  await initializeOnce();
  if(pageMounted.value&&who===actor()&&!busy.value&&finished.value&&meeting.value===route.params.id)await router.replace('/meeting/'+meeting.value+'/processing');
 }
 catch(cause){if(pageMounted.value&&(who===actor()||!actor()))localError.value=cause instanceof Error?cause.message:'录音记录暂不可用';}
}
const stop=watch(()=>[actor(),route.params.id],()=>{if(pageMounted.value&&actor()&&!busy.value)void load();});
let unmounted=false;
onMounted(async()=>{
 const autoStart=route.query.start==='1';
 if(!session.current.value)try{await session.refresh();}catch{/* Existing session feedback handles identity loss. */}
 if(unmounted)return;
 pageMounted.value=true;
 if(autoStart){await router.replace({path:route.path,query:{}});await start(false,true);}
 else await load();
 if(autoStart&&pageMounted.value&&finished.value&&meeting.value===route.params.id)await router.replace('/meeting/'+meeting.value+'/processing');
});
onUnmounted(()=>{unmounted=true;pageMounted.value=false;stopSubtitles();if(!app.liveSubtitles)subtitles.dispose();stop();flow.dispose();});
</script>
<template><div class="layout"><div class="main"><main class="content"><div class="ac-page acr-page"><header class="page-head meeting-back-head"><button class="btn ac-workspace-back meeting-list-back" type="button" aria-label="返回会议列表" title="返回会议列表" @click="router.push('/meetings')"><Icon name="arrowL" :size="18"/></button><div><h1 class="page-title">在线录音</h1><div class="page-desc">在线录音并实时转写，站内切换不影响录音，可继续处理其他工作。请保持当前标签页打开，刷新、关闭标签页或退出浏览器将中断录音。</div></div></header>
 <section class="card acr-record-card" aria-label="会议录音"><div class="acr-record-top"><div class="acr-record-symbol" :class="active?'is-recording':'is-paused'"><Icon name="mic" :size="30"/><span></span></div><div class="acr-record-time"><strong id="ac-record-clock">{{clock(r.partElapsed.value)}}</strong><span id="ac-record-status">第{{parts.at(-1)?.index??1}}段 · {{state}}</span><small class="muted">累计录音 <time id="ac-record-total">{{clock(r.elapsed.value)}}</time></small></div><div class="acr-record-controls"><button class="acr-icon-button" :aria-label="label" :title="label" :disabled="controlsBusy||finished||capacityReached&&!failed||interrupted&&native.pendingCount.value>0||!!unavailableSession||!session.current.value" @click="primary"><Icon :name="failed?'refresh':active?'pause':'play'" :size="20"/></button><span class="acr-interrupt-slot"><button v-if="active||paused||saving" class="btn sm ghost" :disabled="controlsBusy||failed" @click="interruptDialog?.showModal()">中断本段</button></span><button class="acr-icon-button acr-stop" aria-label="结束录音" title="结束录音" :disabled="controlsBusy||failed||finished||!!unavailableSession||(!parts.some(p=>p.bytes>0)&&r.elapsed.value===0)" @click="end"><svg width="19" height="19" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="5" y="5" width="14" height="14" rx="2"/></svg></button></div></div>
 <div class="acr-record-status-area"><div class="acr-record-save small muted">已记录音频 {{clock(parts.reduce((n,p)=>n+p.elapsedMs,0))}} · 服务端已确认 {{clock(parts.reduce((n,p)=>n+p.savedElapsedMs,0))}} · {{native.syncing?.value?'正在同步保存状态':saveStatus}}<details class="acr-save-details"><summary>保存详情</summary><span>{{parts.reduce((n,p)=>n+p.chunkCount,0)}} 个音频分片 · {{native.pendingCount.value}} 个待确认 · 本地待确认缓存 {{Math.ceil(native.pendingBytes.value/1024)}} KB</span></details></div><div class="acr-record-feedback" aria-live="polite"><div v-if="authorizing" class="acr-interruption-note small muted">正在请求麦克风权限 <button class="btn sm ghost" @click="native.cancelAuthorization()">取消授权</button></div><div v-else-if="native.notice?.value||capacityNotice" class="acr-interruption-note small muted" role="status">{{native.notice?.value??capacityNotice}}</div><div v-if="unavailableSession" class="small acr-interruption-note"><span role="status">上次录音已无法访问，可以开始新录音。</span> <button class="btn sm primary" :disabled="busy" @click="start(true)">开始新录音</button></div><div v-if="localError||(!unavailableSession&&r.error.value)" class="ac-error" role="alert">{{localError??r.error.value}} <RouterLink v-if="!session.current.value" to="/login" class="btn sm ghost">重新登录</RouterLink></div><div v-else-if="!unavailableSession&&interrupted" class="small muted acr-interruption-note">{{native.pendingCount.value?'已有音频暂存本机，请先恢复上传。':'已保留可恢复的音频范围，可开始下一段。'}}</div><div v-if="(!recordedCount||native.failure?.value?.recoveryAction==='authorize')&&!unavailableSession&&!authorizing" class="small muted acr-interruption-note">请允许麦克风权限并检查设备。<button class="btn sm ghost" :disabled="controlsBusy" @click="importFiles">改为导入文件</button></div><div v-if="!unavailableSession&&native.pendingCount.value&&(failed||r.error.value||!native.connected.value)" class="acr-reconnect"><span class="small muted">未确认音频 {{native.pendingCount.value}} 个分片</span><button class="btn sm" :disabled="reconnecting||!!native.operation?.value" @click="reconnect">恢复上传</button></div></div></div><RecordingPartFiles v-if="native.record.value&&savedParts.length" :rows="savedParts"/>
 <div v-if="native.recoveryCandidates?.value.length" class="card card-p" aria-label="选择原录音"><p>请选择要恢复的旧录音。没有时间信息的记录不会自动排序。</p><button v-for="(choice,index) in native.recoveryCandidates.value" :key="choice.sessionId" class="btn" :disabled="busy" @click="chooseRecovery(choice.meetingId,choice.sessionId)">旧录音 {{index+1}} · {{choice.parts}} 段 · {{clock(choice.samples/16)}}</button></div><div class="acr-subtle-tools"><button class="acr-tool" :disabled="!meeting||!!unavailableSession" @click="infoOpen=true"><Icon name="edit" :size="14"/> 填写会议信息</button><span></span><button class="acr-tool" :disabled="!meeting||!!unavailableSession" @click="openMaterials"><Icon name="file" :size="14"/> 添加背景资料 <small v-if="materialCount">{{materialCount}}</small></button></div></section>
 <section class="card acr-transcript-card"><div class="card-h"><div class="card-t">实时转写</div><span class="small muted">全部录音段 · {{transcriptStatus}}</span><button class="acr-tool ml-a" title="读取最新字幕并滚动到底部" @click="follow.returnToLatest(subtitles.refresh)"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M12 4v14m-5-5 5 5 5-5M5 21h14"/></svg> 回到最新</button></div><div ref="live" class="ac-live-text acr-live-text" tabindex="0" aria-label="实时字幕内容" aria-description="使用方向键阅读；Ctrl+A或Command+A选择全部字幕，Ctrl+C或Command+C复制" @scroll="follow.onScroll"><LiveTranscript presentation="recording" normalized :measurements="subtitles.reading?.measurements" :scroll-element="live" :retrying="subtitles.retrying?.value" :parts="subtitleParts" :events="subtitles.events.value" :after-event-id="subtitles.ready.value?subtitles.cursor.value:null" :delayed="subtitles.delayed.value" :state="subtitles.state.value" @recover="subtitles.retryRecognition()"/><div v-if="subtitles.error.value" class="ac-error" role="alert">{{subtitles.error.value}}<button class="btn sm" :disabled="subtitles.loading.value" @click="subtitles.refresh()">重试读取字幕</button></div></div><div class="acr-simulation-note">音频分片保存到服务端；未确认音频暂存本机，最多约十分钟，重连后补传。刷新、关闭或休眠会中断设备，恢复时开启新段；字幕延迟不影响音频保存。</div></section>
 </div><div v-if="localDevelopment" class="demo-note">当前为本地联调环境，录音和分片保存使用本地服务。<br>识别与材料增强使用受控模型接口，页面展示本地测试数据。</div></main></div></div><MeetingInfoDrawer :open="infoOpen" :prevent-close="infoEditorRef?.closeBlocked??false" @close="closeInfo"><MeetingInfoEditor protect-dirty-close ref="infoEditorRef" v-if="infoOpened&&meeting&&app.infoScopeReady.value" :meeting-id="meeting" :eligible="!!session.current.value" @close="infoOpen=false"/></MeetingInfoDrawer><RecordingMaterialsDrawer :open="materialsOpen" :prevent-close="materialsDismissBlocked" @close="closeMaterials"><MaterialImportSection recording v-if="materialsOpened&&meeting" :meeting-id="meeting" @state="value=>materialsState={...value,count:value.count??0}"/><p v-if="materialsState.closeBlocked&&!materialsState.busy" class="small ac-error mt12" role="status">请先核对原材料请求，确认结果后再离开。</p></RecordingMaterialsDrawer>
 <dialog ref="interruptDialog" class="recording-decision-mask mask" role="presentation" @cancel.self.prevent="closeDecision(interruptDialog)" @click.self="closeDecision(interruptDialog)"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="record-interrupt-title"><header class="modal-h"><h3 id="record-interrupt-title" class="m-t">中断本段录音</h3><button type="button" class="m-x" aria-label="关闭中断录音确认" :disabled="busy" @click="closeDecision(interruptDialog)"><Icon name="x" :size="18"/></button></header><div class="modal-b"><p>本段已确认音频将保留，未确认分片仍暂存本机；继续录音将开启新段。</p><p v-if="localError||r.error.value" role="alert">{{localError??r.error.value}}</p></div><footer class="modal-f"><button class="btn" :disabled="busy" @click="closeDecision(interruptDialog)">取消</button><button class="btn primary" :disabled="busy" @click="interrupt">保存本段</button></footer></section></dialog>
 <dialog ref="endDialog" class="recording-decision-mask mask" role="presentation" @cancel.self.prevent="closeDecision(endDialog)" @click.self="closeDecision(endDialog)"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="record-end-title"><header class="modal-h"><h3 id="record-end-title" class="m-t">结束录音</h3><button type="button" class="m-x" aria-label="关闭结束录音确认" :disabled="busy" @click="closeDecision(endDialog)"><Icon name="x" :size="18"/></button></header><div class="modal-b"><p>本次会议共{{recordedCount}}段录音，累计{{clock(r.elapsed.value)}}。保存后可稍后继续处理，也可立即开始处理。</p><div class="small muted">所有录音段按顺序归属同一会议，无需重新上传。取消将返回当前暂停或中断状态，不自动恢复录音。</div><p v-if="busy" class="small muted mt12" role="status">正在确认原分片并提交归档；归档完成后才可回听。</p><div class="ac-error small mt12" role="alert">{{endError??''}}</div></div><footer class="modal-f"><button class="btn" :disabled="busy" @click="closeDecision(endDialog)">取消</button><button v-if="endError&&lastEnd" class="btn primary" :disabled="busy" @click="finish(lastEnd)">重试保存</button><button class="btn" :disabled="busy||!!endError" @click="finish('save')">保存</button><button class="btn primary" aria-label="保存并开始转写" :disabled="busy||!!endError" @click="finish('process')">开始处理</button></footer></section></dialog>
</template>



<style scoped>.recording-decision-mask{inset:0;width:auto;height:auto;max-width:none;max-height:none;margin:0;padding:0;border:0;color:var(--text);overflow:visible}.recording-decision-mask:not([open]){display:none}.recording-decision-mask::backdrop{background:transparent}.recording-decision-mask>.modal{margin:0;padding:0}.recording-decision-mask .modal-b{min-height:0}.recording-decision-mask .modal-b [role=alert]{color:var(--red)}</style>

<style scoped>.acr-record-card{position:relative}.acr-record-save{margin-bottom:8px;min-height:40px}.acr-save-details{display:inline-block;margin-left:12px;position:relative}.acr-save-details summary{cursor:pointer}.acr-save-details[open]>span{position:absolute;z-index:2;right:0;top:100%;width:max-content;max-width:75vw;padding:10px;background:#fff;border:1px solid var(--border);border-radius:6px;box-shadow:0 3px 12px #0001}.acr-record-feedback{height:48px;overflow:auto;display:flex;align-items:center;justify-content:center;gap:12px;scrollbar-gutter:stable}.acr-record-feedback .ac-error{margin:0}.acr-record-feedback .acr-interruption-note{margin:0}.acr-reconnect{display:flex;align-items:center;gap:10px}.acr-record-time{width:190px}.acr-record-time time{font-variant-numeric:tabular-nums}.acr-transcript-card .card-h{min-height:50px}.acr-live-text{overflow-anchor:none;scrollbar-gutter:stable}@media(max-width:600px){.acr-record-feedback{height:72px;flex-wrap:wrap;gap:4px}.acr-record-time{width:auto}.acr-transcript-card .card-h{min-height:72px}}</style>

<style scoped>.acr-record-status-area{display:grid;height:calc(1.8em + 14px);font-size:11px}.acr-record-status-area .acr-record-save{grid-area:1/1;min-height:0;margin:0!important;align-self:center}.acr-record-status-area .acr-record-feedback{grid-area:1/1;height:100%;min-height:0;background:#fff;z-index:1}.acr-record-status-area:has(.acr-record-feedback>*) .acr-record-save{visibility:hidden}.acr-record-status-area .acr-record-feedback:empty{display:none}@media(max-width:600px){.acr-record-status-area{height:72px}}</style>
