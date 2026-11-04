<script setup lang="ts">
import {correctionRange} from './correction-range';
import {speakerColor} from './speaker-color';
import Icon from '../../shared/Icon.vue';
import {useFixedDockHeight} from '../../shared/composables/useFixedDockHeight';
import {computed,ref,watch,nextTick} from 'vue';
import type {TranscriptEdits} from './transcript-journal';
import type {SpeechCorrections} from '../../shared/api/transcript-corrections';
import type {DeepReadonly} from 'vue';
import type {TranscriptSegment} from '../../shared/api/transcripts';
const props=withDefaults(defineProps<{value:TranscriptEdits;canReview?:boolean;corrections?:DeepReadonly<SpeechCorrections['items']>;canDecide?:boolean;playback?:{fileId:string;ms:number;playing:boolean}|null;editable:boolean;busy:boolean;dirty:boolean;conflict:TranscriptEdits|null;conflictToken:string|null;state:string;blocked:boolean;blockedReason?:string;storageStatus:string;error:string|null;confirmed:boolean;canConfirm:boolean;pendingSave?:boolean;revisionToken?:string;commit?:(value:TranscriptEdits,expected:string)=>Promise<boolean>;showFooter?:boolean;published?:boolean;provenance?:Array<Pick<TranscriptSegment,'segmentId'|'sourceFileId'|'startMs'|'endMs'|'speakerId'>>;playableFiles?:string[];correctionsReady?:boolean;correctionCount?:number;pendingCorrectionSegments?:readonly string[];simulatedSpeech?:boolean;fileLabels?:Readonly<Record<string,string>>;speakerColorKeys?:Readonly<Record<string,string>>;speakerLabels?:Readonly<Record<string,string>>}>(),{showFooter:true});
const emit=defineEmits<{edit:[value:TranscriptEdits];save:[];confirm:[];retry:[];reload:[];discard:[];reapply:[];begin:[];tools:[];review:[];acceptAll:[];rejectAll:[];decide:[id:string,decision:'ACCEPT'|'REJECT'];undoCorrection:[id:string];feedback:[id:string];seek:[fileId:string,startMs:number]}>();
const query=ref(''),pendingOnly=ref(false),composing=ref(false),editDialog=ref<HTMLDialogElement>(),editText=ref(''),modalError=ref<string|null>(null),modalBusy=ref(false);
let editing:string|null=null,modalValue='',modalRevision='',attempted=false,submittedValue:string|null=null;
const suggestions=computed(()=>{
 const grouped=new Map<string,DeepReadonly<SpeechCorrections['items'][number]>[]>();
 for(const item of props.corrections??[]){
  if(!grouped.has(item.segmentId))grouped.set(item.segmentId,[]);
  grouped.get(item.segmentId)!.push(item);
 }
 return grouped;
});
const inlineSuggestions=computed(()=>{
 const items=new Map<string,{id:string;change:ReturnType<typeof correctionRange>}>();
 const bodies=new Map(props.value.map(segment=>[segment.segmentId,segment.body]));
 for(const [segmentId,group] of suggestions.value){
  const item=group.find(candidate=>candidate.status==='PENDING'&&bodies.get(segmentId)===candidate.source);
  if(item)items.set(segmentId,{id:item.id,change:correctionRange(item.source,item.replacement)});
 }
 return items;
});
const expandedSuggestion=ref<string|null>(null);
function trackSuggestion(id:string,event:Event){
 if((event.currentTarget as HTMLDetailsElement).open)expandedSuggestion.value=id;
 else if(expandedSuggestion.value===id)expandedSuggestion.value=null;
}
function toggleSuggestion(id:string){
 expandedSuggestion.value=expandedSuggestion.value===id?null:id;
}
const filtered=computed(()=>{
 const term=query.value.trim().toLocaleLowerCase();
 return props.value.map((segment,index)=>({segment,index})).filter(({segment})=>{
  const matches=!term||segment.body.toLocaleLowerCase().includes(term)||speakerLabel(segment.segmentId).toLocaleLowerCase().includes(term)||suggestions.value.get(segment.segmentId)?.some(item=>item.replacement.toLocaleLowerCase().includes(term));
  return matches&&(!pendingOnly.value||props.pendingCorrectionSegments?.includes(segment.segmentId));
 });
});
const visible=filtered;
const closeBlocked=computed(()=>modalBusy.value||props.busy||
 (pending.value||props.pendingSave)&&(props.blocked||['INVALID','UNAVAILABLE'].includes(props.storageStatus??'')));
const modalBlocked=computed(()=>closeBlocked.value||pending.value||props.pendingSave);
watch(()=>props.editable,allowed=>{if(!allowed){editDialog.value?.close();editText.value='';modalError.value=null;composing.value=false;}});
const pending=computed(()=>!['EMPTY','ACKNOWLEDGED','CONFLICT','REJECTED'].includes(props.state));
const labels:Record<TranscriptSegment['marks'][number],string>={KEY:'关键',CONCLUSION:'结论',DISPUTE:'争议',GENERATION_FOCUS:'重点生成'};
const timing=computed(()=>new Map((props.provenance??[]).map(s=>[s.segmentId,s])));
function speakerLabel(id:string){const source=timing.value.get(id);return source?.speakerId&&props.speakerLabels?.[source.speakerId]||props.fileLabels?.[id]||((source?.startMs!=null||source?.speakerId)?(props.published?'匿名说话人':'未知'):'文本段落');}
const following=ref(true);
const playingSegment=computed(()=>{
 const playback=props.playback;
 if(!playback?.playing)return undefined;
 return props.provenance?.find(s=>s.sourceFileId===playback.fileId&&s.startMs!==null&&s.endMs!==null&&s.startMs<=playback.ms&&s.endMs>playback.ms)?.segmentId;
});
async function followPlayback(){
 if(!following.value||!playingSegment.value||props.published||editDialog.value?.open)return;
 await nextTick();
 if(following.value&&playingSegment.value)document.getElementById('seg-'+playingSegment.value)?.scrollIntoView({block:'nearest'});
}
watch(playingSegment,()=>void followPlayback());
function pauseFollowing(){if(props.playback?.playing)following.value=false;}
function clock(ms:number){const seconds=Math.floor(ms/1000);return `${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;}
function seek(id:string){const source=timing.value.get(id);if(source&&source.startMs!==null&&props.playableFiles?.includes(source.sourceFileId))emit('seek',source.sourceFileId,source.startMs);}
function openModal(kind:'edit',id?:string){if(!props.editable||props.blocked||props.busy||pending.value||props.pendingSave)return;editing=id??null;editText.value=props.value.find(s=>s.segmentId===id)?.body??'';modalError.value=null;attempted=false;submittedValue=null;modalRevision=props.revisionToken??'';modalValue=JSON.stringify(props.value);editDialog.value?.showModal();}
function customize(segmentId:string,text:string){
 openModal('edit',segmentId);
 if(editDialog.value?.open)editText.value=text;
}
function closeModal(kind:'edit',event?:Event){if(closeBlocked.value){event?.preventDefault();return;}editDialog.value?.close();composing.value=false;}
function backdrop(kind:'edit',event:MouseEvent){const dialog=editDialog.value;if(!dialog||event.target!==dialog)return;const b=dialog.getBoundingClientRect();if(event.clientX<b.left||event.clientX>b.right||event.clientY<b.top||event.clientY>b.bottom)closeModal(kind,event);}
function changed(){return modalRevision!==(props.revisionToken??'')||modalValue!==JSON.stringify(props.value)&&submittedValue!==JSON.stringify(props.value);}
async function commit(next:TranscriptEdits,kind:'edit'){
 if(modalBusy.value||props.busy||composing.value||props.blocked||!props.editable)return false;
 if(!((pending.value||props.pendingSave)&&attempted)&&changed()){modalError.value='转写或纠错已变化，请核对后重试';return false;}
 if(!props.commit){modalError.value='保存暂不可用，请保留修改并重新读取。';return false;}
 modalBusy.value=true;modalError.value=null;attempted=true;submittedValue=JSON.stringify(next);
 try{const saved=await props.commit(next,modalRevision);if(saved){editDialog.value?.close();composing.value=false;return true;}modalError.value=props.error??'保存尚未确认，修改已保留，请重试原请求。';return false;}
 catch(cause){modalError.value=cause instanceof Error?cause.message:'保存失败，请保留修改并重试。';return false;}finally{modalBusy.value=false;}
}
async function saveSegment(){if(composing.value)return;const text=editText.value.trim();if(!text){modalError.value='请输入有效转写内容';return;}const item=props.value.find(s=>s.segmentId===editing);if(!item){modalError.value='转写片段已变化，请重新核对';return;}await commit(props.value.map(s=>s.segmentId===editing?{...s,body:text}:s),'edit');}
function mark(index:number,key:TranscriptSegment['marks'][number],checked:boolean){emit('edit',props.value.map((s,i)=>i===index?{...s,marks:checked?[...s.marks,key]:s.marks.filter(m=>m!==key)}:s));}

defineExpose({composing});
const fixedDock=ref<HTMLElement>();useFixedDockHeight(fixedDock);
</script>
<template><div v-if="published" class="m19-published-transcript">
 <p v-if="error" class="banner error" role="alert">{{error}}</p>
 <div class="small muted mb12">发布绑定确认转写</div>
 <div v-for="segment in value" :key="segment.segmentId" class="seg" style="display:block;padding:12px;border-bottom:1px solid var(--border)">
  <div class="small muted"><button v-if="timing.get(segment.segmentId)?.startMs!=null" type="button" class="btn sm ghost" :disabled="!playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)" :aria-label="'定位原录音 '+clock(timing.get(segment.segmentId)!.startMs!)" @click="seek(segment.segmentId)">{{clock(timing.get(segment.segmentId)!.startMs!)}}</button><button v-else type="button" class="btn sm ghost" disabled aria-label="此片段无对应原录音" title="此片段没有时间定位">--:--</button> · {{speakerLabel(segment.segmentId)}}</div>
  <div style="white-space:pre-wrap;line-height:1.9">{{segment.body}}</div>
 </div>
 <div v-if="!value.length" class="empty">暂无可用会议转写。</div>
</div><section v-else class="card transcript-section" @wheel.passive="pauseFollowing" aria-labelledby="transcript-title">
 <div class="card-h aci-inline-toolbar"><div class="aci-inline-title-row"><h2 id="transcript-title" class="card-t" style="font-size:14px">转写稿</h2><span class="aci-inline-count" :class="{'is-complete':correctionsReady&&!correctionCount}" aria-live="polite"><template v-if="correctionsReady"><template v-if="correctionCount">待确认 <b>{{correctionCount}}</b> 条</template><template v-else>暂无待处理纠错</template></template><template v-else>{{confirmed?'已确认':'待确认'}} <b>{{value.length}}</b> 段</template></span><button v-if="!editable&&showFooter" class="btn sm transcript-begin" :disabled="busy||blocked" @click="emit('begin')">建立人工工作版本</button></div><div class="aci-inline-controls"><div class="aci-inline-search-row"><div class="search-box"><Icon name="search" :size="13"/><input v-model="query" class="input" placeholder="搜索转写" aria-label="搜索转写"/></div><label class="aci-inline-pending"><input v-model="pendingOnly" type="checkbox"/>待确认问题</label></div><div class="aci-inline-toolbar-actions"><button v-if="showFooter" class="btn sm ghost" type="button" :disabled="!canReview" @click="emit('review')">原稿与审阅</button><button v-if="!following" class="btn sm" type="button" @click="following=true;followPlayback()">恢复播放跟随</button><button class="btn sm aci-inline-accept" :disabled="!correctionsReady||!correctionCount||!editable||busy||blocked||dirty||pending" :title="correctionsReady?'一次接受当前已核对的待处理建议':'纠错建议服务尚未接入'" @click="emit('acceptAll')">全部接受</button><button class="btn sm" :disabled="!correctionsReady||!correctionCount||!editable||busy||blocked||dirty||pending" :title="correctionsReady?'一次拒绝当前已核对的待处理建议':'纠错建议服务尚未接入'" @click="emit('rejectAll')">全部拒绝</button><span class="aci-inline-action-divider"></span><button class="btn sm ghost" :disabled="!editable||blocked||busy||pending||dirty" :title="dirty?'请先保存当前修改':'按具体位置替换并可整体撤销'" @click="emit('tools')">批量替换</button></div></div></div>
 <div class="transcript-notices"><p v-if="simulatedSpeech" role="note">当前音频结果来自联调模拟接口，用于验证操作流程，不代表真实识别结果。</p><p v-if="error" role="alert">{{error}}</p><p v-if="blocked" role="alert">{{blockedReason??'本地恢复记录暂不可用，请保留页面并重新读取。'}}</p><p v-else-if="storageStatus==='INVALID'||storageStatus==='UNAVAILABLE'" role="alert">草稿暂未写入恢复记录，请保持页面打开并重试保存。</p><p v-if="pending" role="status">原操作结果待恢复。重试将保留原请求；后续输入仍保留在本地草稿。<button type="button" class="btn sm" :disabled="busy||blocked" @click="emit('retry')">恢复原操作结果</button></p><div v-if="conflict" class="conflict" role="alert"><h3>服务器已有更新（版本 {{conflictToken}}）</h3><p>你的草稿已保留，请比较后处理。</p><details><summary>查看最新保存内容</summary><p v-for="s in conflict" :key="s.segmentId" class="text">{{s.body}}</p></details><button class="btn" :disabled="busy||pending||blocked" @click="emit('reapply')">将我的修改应用到最新版本</button><button class="btn" :disabled="busy||pending||blocked" @click="emit('discard')">放弃本地修改，读取最新版本</button></div></div>
 <div class="card-p transcript-paragraphs" style="padding-top:12px"><div v-for="{segment,index} in visible" :key="segment.segmentId" class="seg" :class="{playing:playingSegment===segment.segmentId}" :id="'seg-'+segment.segmentId"><button class="s-time" type="button" :aria-label="'定位原录音第 '+(index+1)+' 段'" :title="timing.get(segment.segmentId)?.startMs!=null&&playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)?'点击定位音频':'此片段无对应原录音'" :aria-disabled="timing.get(segment.segmentId)?.startMs==null||!playableFiles?.includes(timing.get(segment.segmentId)!.sourceFileId)" @click="seek(segment.segmentId)">{{timing.get(segment.segmentId)?.startMs!=null?clock(timing.get(segment.segmentId)!.startMs!):'--:--'}}</button><div class="s-body"><div class="s-spk"><span class="spk-av" :style="{background:speakerColor(speakerColorKeys?.[segment.segmentId]??'unknown')}">{{speakerLabel(segment.segmentId).slice(0,1)}}</span><span class="b" style="font-size:12.5px" :style="{color:speakerColor(speakerColorKeys?.[segment.segmentId]??'unknown')}">{{speakerLabel(segment.segmentId)}}</span><button v-if="editable" class="btn sm ghost aci-segment-edit" :disabled="blocked||busy||pending" :aria-label="'编辑第 '+(index+1)+' 段'" @click="openModal('edit',segment.segmentId)"><Icon name="edit" :size="12"/></button></div><div class="s-text">
  <template v-if="inlineSuggestions.get(segment.segmentId)">
   {{inlineSuggestions.get(segment.segmentId)!.change.prefix}}<button type="button" class="inline-suggestion"
    :aria-expanded="expandedSuggestion===inlineSuggestions.get(segment.segmentId)!.id"
    :aria-controls="'correction-'+inlineSuggestions.get(segment.segmentId)!.id"
    @click="toggleSuggestion(inlineSuggestions.get(segment.segmentId)!.id)"><del>{{inlineSuggestions.get(segment.segmentId)!.change.original}}</del><ins>{{inlineSuggestions.get(segment.segmentId)!.change.suggestion||'（删除）'}}</ins></button>{{inlineSuggestions.get(segment.segmentId)!.change.suffix}}
  </template>
  <template v-else>{{segment.body}}</template>
 </div>
 <details v-for="item in suggestions.get(segment.segmentId)" :key="item.id" :id="'correction-'+item.id" :open="expandedSuggestion===item.id" @toggle="trackSuggestion(item.id,$event)" class="segment-suggestion">
  <summary>{{({PENDING:'待确认纠错',ACCEPTED:'已接受纠错',REJECTED:'已拒绝纠错',STALE:'过期纠错',UNDONE:'已撤销纠错'})[item.status]}}</summary>
  <p class="small muted">语音增强建议，作用于本段正文</p>
  <div class="correction-comparison"><del>{{item.source}}</del><ins>{{item.replacement}}</ins></div>
  <p v-if="item.status==='STALE'" class="small muted">正文已变化，请结合当前内容重新核对。</p>
  <div class="flex" v-if="item.status==='PENDING'">
   <button type="button" class="btn sm primary" :disabled="!canDecide||!item.canAccept||busy" @click="emit('decide',item.id,'ACCEPT')">接受本段建议</button>
   <button type="button" class="btn sm" :disabled="!canDecide||!item.canReject||busy" @click="emit('decide',item.id,'REJECT')">拒绝本段建议</button>
  </div>
  <div class="flex wrap" style="gap:6px">
   <button type="button" class="btn sm" :disabled="!canDecide||busy" @click="customize(item.segmentId,item.replacement)">自定义修订</button>
   <button v-if="item.status==='ACCEPTED'&&!item.canUndo" type="button" class="btn sm" :disabled="!canDecide||busy" @click="customize(item.segmentId,item.source)">恢复原文</button>
   <button type="button" class="btn sm ghost" :disabled="!canDecide||busy" @click="emit('feedback',item.id)">提交语料反馈</button>
  </div>
  <button v-if="item.canUndo" type="button" class="btn sm" :disabled="!canDecide||busy" @click="emit('undoCorrection',item.id)">撤销接受</button>
 </details><div class="s-marks"><button v-for="(label,key) in labels" :key="key" class="mk" :class="segment.marks.includes(key)?'on-'+({KEY:'key',CONCLUSION:'con',DISPUTE:'dis',GENERATION_FOCUS:'gen'}[key]):''" :disabled="!editable||blocked" :aria-pressed="segment.marks.includes(key)" @click="mark(index,key,!segment.marks.includes(key))">{{label}}</button></div></div></div><div v-if="!visible.length" class="empty">没有符合条件的转写段落</div></div>
 <footer v-if="showFooter&&(!confirmed||dirty)" ref="fixedDock" class="acf-dock"><div class="acf-inner"><span class="acf-hint">{{dirty?'有尚未保存的修改，确认前将保存当前工作版本。':confirmed?'当前转写已确认，可继续生成会议纪要。':'确认转写后，将使用已保存的转写内容生成纪要。'}}</span><div class="acf-actions"><button v-if="correctionCount" class="btn sm" type="button" @click="pendingOnly=true;query=''">查看待确认纠错</button><button class="btn primary" :disabled="busy||blocked||!!conflict||pending||composing||!canConfirm&&!confirmed||!['EMPTY','ACKNOWLEDGED'].includes(state)" @click="emit('confirm')">确认转写并生成纪要</button></div></div></footer>
 <dialog ref="editDialog" class="modal lg transcript-modal" aria-labelledby="transcript-edit-title" @cancel="closeModal('edit',$event)" @click="backdrop('edit',$event)"><header class="modal-h"><h3 class="m-t" id="transcript-edit-title">编辑转写片段</h3><button type="button" class="m-x" aria-label="关闭" :disabled="closeBlocked" @click="closeModal('edit')">×</button></header><div class="modal-b"><div class="field"><label for="transcript-edit-text">转写内容</label><textarea id="transcript-edit-text" v-model="editText" class="input" rows="8" aria-label="转写内容" :readonly="modalBlocked||blocked" @compositionstart="composing=true" @compositionend="composing=false"/></div><div class="small muted">保存当前工作稿，原始留档不变；本段旧建议需重新核对，既有确认和纪要保留。</div><div v-if="modalError" class="ac-error" role="alert">{{modalError}}</div></div><footer class="modal-f"><button class="btn" :disabled="closeBlocked" @click="closeModal('edit')">取消</button><button class="btn primary" :disabled="modalBusy||busy||blocked||composing||!editable" @click="saveSegment">保存</button></footer></dialog>
</section></template>
<style scoped>.transcript-section{padding:0}.transcript-notices:empty{display:none}.transcript-notices:not(:empty){padding:12px 20px}.transcript-notices [role=alert]{color:#9a3412}.s-text{white-space:pre-wrap;overflow-wrap:anywhere}.segment-input{width:100%;padding:10px;border:1px solid var(--border2);border-radius:6px;font:inherit;line-height:1.8;resize:vertical}.mk{font-family:inherit}.transcript-recovery{padding:12px 20px;border-top:1px solid var(--border);font-size:12px;color:var(--muted)}.transcript-recovery summary{cursor:pointer}.transcript-recovery .flex{margin-top:10px}.acf-inner{max-width:1552px;margin:auto;display:flex;align-items:center;gap:18px}.acf-hint{flex:1;min-width:0;font-size:12px;color:var(--muted)}.acf-actions{display:flex;gap:10px}.conflict{background:#fff5dc;padding:12px}.notice-dialog .input{display:block;width:100%}@media(max-width:800px){.acf-inner{flex-wrap:wrap;gap:10px}.acf-actions{margin-left:auto}}</style>

<style scoped>.aci-inline-toolbar .aci-inline-title-row .transcript-begin.btn.sm{margin-left:auto;height:22px;min-height:22px;padding:2px 8px;line-height:18px}</style>

<style scoped>.s-time{border:0;background:transparent;padding:0;cursor:pointer}.s-time[aria-disabled=true]{cursor:default;color:var(--muted)}</style>

<style scoped>.transcript-modal{border:0;padding:0;margin:auto;color:var(--text)}.transcript-modal:not([open]){display:none}.transcript-modal::backdrop{background:rgba(17,24,73,.42);backdrop-filter:blur(2px)}.transcript-modal .m-t{margin:0}.transcript-modal .input{width:100%}.transcript-modal textarea{resize:vertical}.transcript-modal .ac-error{margin-top:12px}</style>

<style scoped>button.mk{line-height:1.55}</style>

<style scoped>.inline-suggestion{background:#fff2e8;border:0;border-bottom:1px dashed var(--orange);color:inherit;font:inherit;cursor:pointer;padding:0 3px}.inline-suggestion del{color:var(--muted);margin-right:4px}.inline-suggestion ins{color:var(--orange);text-decoration:none}.segment-suggestion{margin:6px 0;color:var(--text)}.segment-suggestion summary{cursor:pointer;color:var(--orange)}.correction-comparison{display:grid;gap:4px;white-space:pre-wrap;overflow-wrap:anywhere}.correction-comparison del{color:var(--muted)}.correction-comparison ins{color:var(--orange)}</style>
