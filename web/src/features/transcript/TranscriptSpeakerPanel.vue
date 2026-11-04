<script setup lang="ts">
import {RouterLink} from 'vue-router';
import {computed,ref,watch,onUnmounted,type DeepReadonly} from 'vue';import type {SpeechSpeakers} from '../../shared/api/transcript-corrections';
import type {createMeetingOptionsApi,Person} from '../../shared/api/meeting-options';
type LabelGroup={id:string;label:string;segmentIds:readonly string[];enterpriseId:string|null};
const props=defineProps<{snapshot:DeepReadonly<SpeechSpeakers>;fileLabels?:readonly LabelGroup[];lookupPeople?:ReturnType<typeof createMeetingOptionsApi>['people'];segments:readonly {segmentId:string;body:string;ordinal:number}[];busy:boolean;editable:boolean}>();
const emit=defineEmits<{label:[segmentIds:string[],label:string,enterpriseId:string|null];merge:[segmentIds:string[],label:string,enterpriseId:string|null]}>();
const root=ref<HTMLElement>(),labelInput=ref<HTMLInputElement>();
const selected=ref<string[]>([]),label=ref(''),linked=ref<string|null>(null),source=ref(''),target=ref('');
const naming=ref<'manual'|'directory'>('manual');
const people=ref<Person[]>([]),personQuery=ref(''),peopleError=ref(''),peopleBusy=ref(false);
let peopleRequest:AbortController|null=null;
function resetDirectory(){
 peopleRequest?.abort();
 peopleRequest=null;
 people.value=[];
 personQuery.value='';
 peopleError.value='';
 peopleBusy.value=false;
}
watch(naming,()=>resetDirectory(),{flush:'sync'});
onUnmounted(resetDirectory);
async function searchPeople(){
 if(!props.lookupPeople||disabled.value)return;
 peopleRequest?.abort();
 const request=new AbortController();
 peopleRequest=request;
 people.value=[];
 peopleError.value='';
 peopleBusy.value=true;
 try{
  const page=await props.lookupPeople(personQuery.value.trim(),null,request.signal);
  if(peopleRequest===request)people.value=page.items.filter(person=>person.active);
 }catch(cause){
  if(peopleRequest===request)peopleError.value=cause instanceof Error?cause.message:'暂时无法查询人员目录。';
 }finally{
  if(peopleRequest===request)peopleBusy.value=false;
 }
}
function choosePerson(person:Person){
 if(disabled.value)return;
 label.value=person.displayName;
 linked.value=person.stableId;
}
function setNaming(value:'manual'|'directory'){
 if(naming.value===value)return;
 naming.value=value;
 linked.value=null;
}
watch(()=>props.snapshot.sourceRevisionId,()=>{resetDirectory();selected.value=[];label.value='';linked.value=null;source.value='';target.value='';naming.value='manual';});
const mode=ref<'edit'|'merge'|'split'>('edit');
const groups=computed<readonly LabelGroup[]>(()=>[...props.snapshot.items,...props.fileLabels??[]]);
const selectedSource=computed(()=>groups.value.find(item=>item.id===source.value));
const selectedTarget=computed(()=>groups.value.find(item=>item.id===target.value));
const visibleSegments=computed(()=>mode.value==='split'
 ?props.segments.filter(segment=>selectedSource.value?.segmentIds.includes(segment.segmentId))
 :props.segments);
watch(source,()=>{if(mode.value==='split')selected.value=[];});
function merge(){
 const from=selectedSource.value,to=selectedTarget.value;
 if(disabled.value||!from||!to||from.id===to.id)return;
 emit('merge',[...new Set([...from.segmentIds,...to.segmentIds])],to.label,to.enterpriseId);
}
const disabled=computed(()=>props.busy||!props.editable),labels=computed(()=>new Map(groups.value.flatMap(x=>x.segmentIds.map(s=>[s,x.label] as const))));
function edit(id:string){const item=groups.value.find(x=>x.id===id);if(!item||disabled.value)return;selected.value=[...item.segmentIds];label.value=item.label;linked.value=item.enterpriseId;naming.value=item.enterpriseId?'directory':'manual';}
function submit(){
 if(disabled.value||!selected.value.length)return;
 if(mode.value==='split'){
  const from=selectedSource.value;
  if(!from||selected.value.length>=from.segmentIds.length||!selected.value.every(id=>from.segmentIds.includes(id)))return;
  const names=new Set(groups.value.map(item=>item.label));
  let number=1;
  while(names.has('说话人 '+number))number++;
  emit('label',[...selected.value],'说话人 '+number,null);
 }else if(label.value.trim()&&(naming.value!=='directory'||linked.value)){
  emit('label',[...selected.value],label.value.trim(),linked.value);
 }
}
defineExpose({focus(operation:'edit'|'merge'|'split'='edit',id?:string){mode.value=operation;naming.value='manual';resetDirectory();source.value='';target.value='';selected.value=[];label.value='';linked.value=null;if(id)edit(id);root.value?.scrollIntoView({block:'center',behavior:'smooth'});labelInput.value?.focus({preventScroll:true});}});
</script>
<template><section ref="root" class="speaker-workbench" aria-label="人工说话人标注"><header><h3>人工说话人标注</h3><span class="small muted">{{snapshot.items.length}} 个标签</span></header><p class="small muted">请结合原录音核对。标注、改名和合并均保留历史版本；标签不授予会议访问权限。</p><ul class="speaker-labels"><li v-for="speaker in groups" :key="speaker.id"><span>{{speaker.label}} · {{snapshot.items.some(item=>item.id===speaker.id)?'人工标注':'文件标签'}} · {{speaker.segmentIds.length}} 段</span><button class="btn sm ghost" :disabled="disabled" @click="edit(speaker.id)">编辑说话人 {{speaker.label}}</button></li></ul><fieldset v-if="mode!=='merge'" :disabled="disabled"><legend>{{mode==='split'?'拆分说话人':'选择需要标注的片段'}}</legend><label v-if="mode==='split'" class="field">拆分来源<select v-model="source" class="select"><option value="">选择来源</option><option v-for="speaker in groups" :key="speaker.id" :value="speaker.id">{{speaker.label}} · {{speaker.segmentIds.length}} 段</option></select></label><label v-for="segment in visibleSegments" :key="segment.segmentId" class="speaker-segment"><input v-model="selected" type="checkbox" :value="segment.segmentId"/><span>第 {{segment.ordinal}} 段 · {{labels.get(segment.segmentId)??'尚未标注'}} · {{segment.body.slice(0,50)}}</span></label><div v-if="mode==='edit'&&lookupPeople" class="flex wrap" style="gap:8px">
 <button type="button" class="btn sm" :aria-pressed="naming==='manual'" @click="setNaming('manual')">自定义名称</button>
 <button type="button" class="btn sm" :aria-pressed="naming==='directory'" @click="setNaming('directory')">企业人员</button>
 </div>
 <div v-if="mode==='edit'&&naming==='directory'&&lookupPeople">
  <label class="field">人员姓名或部门<input v-model="personQuery" class="input" @keydown.enter.prevent="searchPeople"/></label>
  <button type="button" class="btn sm" :disabled="peopleBusy" @click="searchPeople">搜索企业人员</button>
  <p v-if="peopleError" role="alert">{{peopleError}}</p>
  <ul class="speaker-labels"><li v-for="person in people" :key="person.stableId"><button type="button" class="btn sm" :aria-pressed="linked===person.stableId" @click="choosePerson(person)">{{person.displayName}} · {{person.department??'部门未登记'}} · {{person.stableId}}</button></li></ul>
 </div>
 <label v-if="mode!=='split'" class="field">说话人名称<input ref="labelInput" v-model="label" :readonly="naming==='directory'" class="input" maxlength="256" aria-label="说话人名称"/></label><button class="btn sm" :disabled="!selected.length||(mode==='split'?selected.length>=(selectedSource?.segmentIds.length??0):!label.trim()||naming==='directory'&&!linked)" @click="submit">{{mode==='split'?'拆分为匿名说话人':'保存说话人标注'}}</button></fieldset><fieldset v-if="mode!=='split'&&groups.length>1" :disabled="disabled"><legend>合并当前稿件的说话人</legend><div class="merge-fields"><select v-model="source" class="select" aria-label="合并来源说话人"><option value="">选择来源</option><option v-for="speaker in groups" :key="speaker.id" :value="speaker.id">{{speaker.label}}</option></select><select v-model="target" class="select" aria-label="合并目标说话人"><option value="">选择目标</option><option v-for="speaker in groups" :key="speaker.id" :value="speaker.id">{{speaker.label}}</option></select><button class="btn sm" :disabled="!source||!target||source===target" @click="merge">合并说话人</button></div><p v-if="selectedSource&&selectedTarget&&source!==target" class="small muted">将 {{selectedSource.segmentIds.length}} 段发言归入 {{selectedTarget.label}}，保留目标身份。</p></fieldset><p class="small muted mt12">需要登记本人声纹时，可使用声纹管理中的授权登记流程。</p><RouterLink class="btn sm ghost" to="/operations">登记本人声纹</RouterLink></section></template>
<style scoped>.speaker-workbench{margin-top:16px;min-width:0}.speaker-workbench header,.speaker-labels li{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap}.speaker-workbench h3{font-size:14px}.speaker-labels{list-style:none;padding:0}.speaker-labels li{padding:6px 0}.speaker-workbench fieldset{min-width:0;border:1px solid var(--border);border-radius:6px;margin-top:12px;padding:12px}.speaker-workbench legend{font-size:12px;color:var(--muted)}.speaker-segment{display:flex;gap:8px;align-items:flex-start;margin:8px 0;overflow-wrap:anywhere}.speaker-segment input{flex:none}.speaker-workbench .field{margin:12px 0}.merge-fields{display:flex;gap:8px;flex-wrap:wrap}.merge-fields .select{min-width:0;max-width:100%;flex:1}</style>
