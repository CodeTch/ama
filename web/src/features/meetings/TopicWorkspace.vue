<script setup lang="ts">
import {nextTick,onUnmounted,ref,watch} from 'vue';
import {useApplication} from '../../application';
import TopicDialogView from './TopicDialogView.vue';
import type {Topic} from '../../shared/api/topics';
import {ApiError} from '../../shared/api/client';
type Target={id:string;name:string|null;revisionToken:string;topicId?:string|null};
type AssignmentContext={targets:Target[];selected:string;meetingNames:string[]};
const app=useApplication(),manager=app.topicCommands,emit=defineEmits<{changed:[notice?:string]}>(),view=ref<InstanceType<typeof TopicDialogView>>(),visible=ref(false),mode=ref<'create'|'update'|'delete'|'assign'>('create'),name=ref(''),description=ref(''),selected=ref(''),composing=ref(false),error=ref<string|null>(null),meetingNames=ref<string[]>([]),deleteCount=ref<number|null>(0),checkingDelete=ref(false);
let topic:Topic|null=null,targets:Target[]=[],assignmentParent:AssignmentContext|null=null,epoch=0,deleteTurn=0,deleteRequest:AbortController|null=null;
function cancelDeleteRead(){deleteTurn++;deleteRequest?.abort();deleteRequest=null;checkingDelete.value=false;deleteCount.value=0;}
function original(){const snapshot=manager.command.snapshot.value,body=manager.original.value;if(!snapshot||!body)return;mode.value=snapshot.operation==='TOPIC_CREATE'?'create':snapshot.operation==='TOPIC_UPDATE'?'update':snapshot.operation==='TOPIC_DELETE'?'delete':'assign';if(typeof body.name==='string')name.value=body.name;else if(mode.value!=='delete')name.value='';description.value=typeof body.description==='string'?body.description:'';selected.value=typeof body.topicId==='string'?body.topicId:'';if(mode.value==='assign'&&meetingNames.value.length===0){meetingNames.value=Array.isArray(body.meetings)?body.meetings.map((row:{meetingId:string})=>row.meetingId):[snapshot.resource];}}
async function display(){const turn=epoch;visible.value=true;await nextTick();if(turn===epoch&&visible.value)view.value?.show();}
async function open(next:typeof mode.value,item:Topic|null=null,meetings:Target[]=[],parent:AssignmentContext|null=null){
 cancelDeleteRead();const turn=deleteTurn;error.value=null;
 if(manager.pending.value){original();await display();return;}
 assignmentParent=parent;mode.value=next;topic=item;targets=meetings.map(row=>({...row}));name.value=item?.name??'';description.value=item?.description??'';selected.value=meetings.length>1?'__keep':meetings.length===1?meetings[0]?.topicId??'':'';meetingNames.value=meetings.map(row=>row.name??'未命名会议');
 const request=new AbortController();deleteRequest=request;
 if(next==='delete'){deleteCount.value=null;checkingDelete.value=true;}
 await display();
 try{
  await manager.reload();if(turn!==deleteTurn||request.signal.aborted)return;
  if(next==='delete'&&item){
   const facts=await app.topics.summary(item.id,AbortSignal.any([request.signal,AbortSignal.timeout(8000)]));
   if(turn!==deleteTurn||request.signal.aborted)return;
   if(facts.revisionToken!==item.revisionToken){error.value='专题已变化，请关闭后重新选择。';return;}
   deleteCount.value=facts.meetingCount;
  }
 }catch(cause){if(turn===deleteTurn&&!request.signal.aborted){if(cause instanceof ApiError&&[401,403].includes(cause.status)){name.value='';description.value='';meetingNames.value=[];}error.value=cause instanceof ApiError?cause.message:'专题状态暂无法核实，请关闭后重试。';}}
 finally{if(turn===deleteTurn)checkingDelete.value=false;}
}
function openFromAssignment(next:'create'|'update'|'delete',item:Topic|null=null){
 if(mode.value!=='assign'||manager.pending.value||manager.busy.value)return;
 const parent={targets:targets.map(row=>({...row})),selected:selected.value,meetingNames:[...meetingNames.value]};
 return open(next,item,[],parent);
}
function returnToAssignment(createdTopicId?:string){
 const parent=assignmentParent!;assignmentParent=null;epoch++;cancelDeleteRead();
 targets=parent.targets;meetingNames.value=parent.meetingNames;selected.value=createdTopicId??parent.selected;
 if(selected.value!=='__keep'&&!manager.topics.value.some(item=>item.id===selected.value))selected.value='';
 mode.value='assign';topic=null;name.value='';description.value='';error.value=null;composing.value=false;
}
function close(createdTopicId?:string){
 if(mode.value!=='assign'&&manager.busy.value)return;
 if(assignmentParent&&!manager.pending.value){returnToAssignment(createdTopicId);return;}
 if(mode.value==='assign'&&['CONFLICT','REJECTED'].includes(manager.command.state.value)){
  try{manager.discardRejected();}catch{/* Keep the recovery entry if storage cannot be cleared; closing stays available. */}
 }
 epoch++;cancelDeleteRead();view.value?.close();visible.value=false;name.value='';description.value='';selected.value='';meetingNames.value=[];targets=[];topic=null;assignmentParent=null;error.value=null;composing.value=false;
}
async function run(action:()=>Promise<unknown>){
 if(composing.value){error.value='请先完成当前输入。';return;}
 if(manager.busy.value){if(mode.value!=='assign')error.value='专题操作正在提交或核实，请稍候。';return;}
 const turn=epoch,operation=mode.value;error.value=null;
 try{
  const result=await action();
  emit('changed',operation==='assign'?'会议专题已调整':undefined);
  if(turn===epoch&&!manager.pending.value)close(operation==='create'?(result as Topic|null)?.id:undefined);
 }
 catch(cause){if(turn===epoch)error.value=cause instanceof Error?cause.message:'专题操作尚未核实，请保留原请求。';}
}
function submit(){if(manager.pending.value)return;if(mode.value==='delete'&&deleteCount.value!==0){error.value='请先核实专题内的会议。';return;}if(mode.value==='assign'&&selected.value==='__keep'){close();return;}if(['create','update'].includes(mode.value)&&!name.value.trim()){error.value='请输入专题名称';return;}void run(async()=>{if(mode.value==='create')return manager.create(name.value.trim(),description.value.trim()||null);if(mode.value==='update'&&topic)return manager.update(topic.id,topic.revisionToken,name.value.trim(),description.value.trim()||null);if(mode.value==='delete'&&topic)return manager.remove(topic.id,topic.revisionToken);if(mode.value==='assign'&&targets.length)return manager.assignMany(targets.map(row=>({meetingId:row.id,expectedRevisionToken:row.revisionToken})),selected.value||null);throw Error('请重新选择专题或会议。');});}
function discard(){try{manager.discardRejected();close();emit('changed');}catch(cause){error.value=cause instanceof Error?cause.message:'原请求暂无法清理。';}}
const stopPrivacy=watch(()=>manager.available.value,available=>{if(!available){cancelDeleteRead();name.value='';description.value='';selected.value='';meetingNames.value=[];targets=[];assignmentParent=null;}},{flush:'sync'});
const stopIdentity=watch(()=>app.session.current.value?.actor.enterpriseId,()=>{epoch++;cancelDeleteRead();visible.value=false;view.value?.close();name.value='';description.value='';targets=[];topic=null;assignmentParent=null;error.value=null;composing.value=false;},{flush:'sync'});
const stopPending=watch(()=>manager.pending.value,pending=>{if(pending&&!visible.value){original();void display();}},{immediate:true});
defineExpose({showRecoveryEntry:()=>!visible.value&&manager.pending.value&&['TOPIC_CREATE','TOPIC_UPDATE','TOPIC_DELETE'].includes(manager.command.snapshot.value?.operation??''),openCreate:()=>open('create'),openEdit:(item:Topic)=>open('update',item),openDelete:(item:Topic)=>open('delete',item),openAssignment:(meetings:Target[])=>open('assign',null,meetings),recover:()=>{original();return display();},beforeLeave:()=>!manager.pending.value&&!manager.busy.value});
onUnmounted(()=>{epoch++;cancelDeleteRead();stopPrivacy();stopIdentity();stopPending();});
</script>
<template><TopicDialogView v-if="visible" ref="view" :mode="mode" v-model:name="name" v-model:description="description" v-model:selected="selected" v-model:composing="composing" :meeting-names="meetingNames" :topics="manager.topics.value" :pending="manager.pending.value" :delete-count="deleteCount" :busy="manager.busy.value||checkingDelete" :state="manager.command.state.value" :error="error??app.topicScopeError.value" @submit="submit" @retry="run(manager.retry)" @discard="discard" @close="close()" @create="openFromAssignment('create')" @edit="item=>openFromAssignment('update',item)" @delete="item=>openFromAssignment('delete',item)"/></template>
