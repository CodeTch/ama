<script setup lang="ts">
import {computed,watch,onUnmounted} from 'vue';
import Icon from '../../shared/Icon.vue';
import {useTranscriptWindow} from './useTranscriptWindow';
export type LiveTranscriptEvent={eventId:string;partId?:string;segmentId:string;revision:number;status:'temporary'|'stable';text:string;speakerTurns?:readonly {offset:number;startMs:number;label:string}[];startMs:number;endMs:number};
const props=defineProps<{events:readonly LiveTranscriptEvent[];afterEventId?:string|null;delayed?:boolean;state?:string;presentation?:'recording';parts?:readonly {partId:string;partNo:number;startMs:number}[];normalized?:boolean;scrollElement?:HTMLElement;retrying?:boolean;measurements?:{sizes:Map<string,number>;width:number}}>();
const delayedMessage=computed(()=>props.state==='DEFERRED'?'实时识别已暂缓，已识别内容保留，请使用会后转写。':props.state==='DONE'?'实时识别已结束，内容保留。':props.state==='CANCELLED'?'实时识别已取消，音频保存状态请单独核对。':'识别积压或连接暂不可用，已识别内容保留。');
const emit=defineEmits<{recover:[afterEventId:string|null]}>();
const visible=computed(()=>{if(props.normalized)return props.events;const bySegment=new Map<string,LiveTranscriptEvent>();for(const event of props.events){const current=bySegment.get(event.segmentId);if(!current||event.revision>current.revision)bySegment.set(event.segmentId,event);}return [...bySegment.values()].sort((a,b)=>a.startMs-b.startMs||a.segmentId.localeCompare(b.segmentId));});
function clock(ms:number){const value=Math.max(0,Math.floor(ms/1000));return `${String(Math.floor(value/60)).padStart(2,'0')}:${String(value%60).padStart(2,'0')}`;}
type TranscriptPart={partId:string;partNo:number;startMs:number};
type TranscriptRow={event:LiveTranscriptEvent;part:TranscriptPart|undefined;key:string;speaker:string;time:number;text:string};
const rowCache=new WeakMap<LiveTranscriptEvent,{part:TranscriptPart|undefined;rows:TranscriptRow[]}>();
const rows=computed(()=>{const parts=new Map(props.parts?.map(p=>[p.partId,p]));return visible.value.flatMap(event=>{
 const part=parts.get(event.partId??'');const prior=rowCache.get(event);if(prior&&prior.part===part)return prior.rows;
 const turns=event.speakerTurns?.length?event.speakerTurns:[{offset:0,startMs:event.startMs,label:'说话人待识别'}];
 const result=turns.map((turn,i)=>({event,part,key:event.segmentId+':'+turn.offset,speaker:turn.label,
  time:Math.max(0,turn.startMs-(part?.startMs??0)),text:event.text.slice(turn.offset,turns[i+1]?.offset)}));
 rowCache.set(event,{part,rows:result});return result;
});});
const windowed=useTranscriptWindow(rows,()=>props.scrollElement,props.measurements);
let selectedAll=false;
function keydown(event:KeyboardEvent){if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='a'){event.preventDefault();selectedAll=true;const selection=window.getSelection(),range=document.createRange();range.selectNodeContents(props.scrollElement!);selection?.removeAllRanges();selection?.addRange(range);}else if(!((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='c'))selectedAll=false;}
function resetSelection(){selectedAll=false;}
function copy(event:ClipboardEvent){if(!selectedAll||!event.clipboardData)return;event.preventDefault();event.clipboardData.setData('text/plain',rows.value.map(row=>`${row.speaker} ${partClock(row.time)}\n${row.text}`).join('\n\n'));}
function unbind(element?:HTMLElement){element?.removeEventListener('keydown',keydown);element?.removeEventListener('pointerdown',resetSelection);element?.removeEventListener('copy',copy);}
watch(()=>props.scrollElement,(element,previous)=>{unbind(previous);selectedAll=false;element?.addEventListener('keydown',keydown);element?.addEventListener('pointerdown',resetSelection);element?.addEventListener('copy',copy);},{immediate:true,flush:'post'});
onUnmounted(()=>unbind(props.scrollElement));
function partClock(ms:number){const seconds=Math.max(0,Math.floor(ms/1000));return [Math.floor(seconds/3600),Math.floor(seconds/60)%60,seconds%60].map(n=>String(n).padStart(2,'0')).join(':');}
</script>
<template><section v-if="presentation==='recording'" class="recording-live" role="list" aria-label="实时转写"><div v-if="delayed" class="banner warn" role="status">{{delayedMessage}}<button type="button" class="btn sm" :disabled="retrying" @click="emit('recover',afterEventId??null)">重试实时识别</button></div><div v-if="!rows.length" class="acr-live-empty"><Icon name="mic" :size="26"/><p>等待会议内容</p><span>全部录音段的实时转写将在此累计展示。</span></div><div v-if="windowed.before.value" aria-hidden="true" :style="{height:windowed.before.value+'px'}"></div><div v-for="({event,part,time,key,speaker,text},index) in windowed.visible.value" :key="key" :ref="el=>windowed.observe(el,key)" class="acr-virtual-row" role="listitem" :aria-posinset="windowed.start.value+index+1" :aria-setsize="rows.length"><div v-if="part&&(windowed.start.value+index===0||rows[windowed.start.value+index-1]?.part?.partId!==part.partId)" class="acr-part-divider">第{{part.partNo}}段录音</div><article class="acr-live-segment" :data-event-id="event.eventId" :aria-label="event.status==='stable'?'稳定字幕':'临时字幕'"><div class="acr-speaker-avatar"><Icon name="user" :size="15"/></div><div class="acr-live-copy"><div class="acr-live-meta"><b title="编号仅在当前识别窗口内有效；跨窗口同号不代表同一人">{{speaker}}</b><small v-if="part" class="muted">第{{part.partNo}}段</small><time :title="event.status==='stable'?'稳定字幕':'临时字幕'">{{partClock(time)}}</time><small v-if="event.status==='temporary'" class="acr-temporary" aria-label="临时字幕，仍可能修订">修订中</small></div><p>{{text}}</p></div></article></div><div v-if="windowed.after.value" aria-hidden="true" :style="{height:windowed.after.value+'px'}"></div></section><section v-else class="live-transcript" aria-label="实时转写"><header><strong>实时转写</strong><span v-if="delayed" role="status">{{delayedMessage}}</span></header><p v-if="!visible.length" class="muted">等待会议内容</p><ol v-else><li v-for="event in visible" :key="event.segmentId" :data-event-id="event.eventId"><time>{{clock(event.startMs)}}</time><span class="live-speaker">匿名说话人</span><span class="live-text">{{event.text}}</span><small>{{event.status==='stable'?'稳定':'临时'}}</small></li></ol><button v-if="afterEventId" type="button" class="btn sm" :disabled="retrying" @click="emit('recover',afterEventId??null)">重试实时识别</button></section></template>
<style scoped>.live-transcript{padding:16px;border:1px solid var(--border);border-radius:8px;background:#fff}.live-transcript header{display:flex;justify-content:space-between;gap:12px}.live-transcript header span,.live-transcript small,.live-transcript .muted{color:var(--muted);font-size:12px}.live-transcript ol{list-style:none;padding:0;margin:12px 0}.live-transcript li{display:grid;grid-template-columns:54px 88px 1fr auto;gap:8px;padding:9px 0;border-bottom:1px solid var(--border)}.live-speaker{color:var(--blue);font-size:12px}.live-text{white-space:pre-wrap;overflow-wrap:anywhere}.live-transcript time{font-variant-numeric:tabular-nums;color:var(--muted);font-size:12px}</style>

<style scoped>.recording-live{display:contents}</style>

<style scoped>.acr-virtual-row{display:flow-root;overflow-anchor:none}.acr-virtual-row .acr-live-segment{border-bottom:1px solid #F0F1F3}.acr-temporary{font-size:11px;color:var(--muted);font-weight:400}</style>
