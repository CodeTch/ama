<script setup lang="ts">
import type {components} from '../../shared/api/generated';
defineProps<{items:components['schemas']['MeetingItem'][]}>();
const labels:Record<components['schemas']['MeetingItem']['stage'],string>={ENTRY:'待导入',TRANSCRIPT_PENDING:'转写处理中',TRANSCRIPT_FAILED:'转写待处理',MINUTES_PENDING:'纪要处理中',MINUTES_FAILED:'纪要待处理',READY_TO_PUBLISH:'待发布',PUBLISH_FAILED:'发布待处理',PUBLISHED:'已发布'};
</script>
<template>
 <div class="meeting-table-scroll"><table class="meeting-table"><caption class="sr-only">当前范围的会议</caption><thead><tr><th scope="col">会议</th><th scope="col">会议日期</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
 <tbody><tr v-for="meeting in items" :key="meeting.id"><td><RouterLink :to="`/meeting/${meeting.id}`" class="meeting-name">{{meeting.name===null?'未命名会议':meeting.name}}</RouterLink><span class="meeting-number">{{meeting.id}}</span></td><td>{{meeting.occurredOn??'未设置'}}</td><td><span class="meeting-stage">{{labels[meeting.stage]}}</span></td><td><RouterLink :to="`/meeting/${meeting.id}`" class="btn sm">{{meeting.writeState==='CHECK_WORKSPACE'?'打开会议':'只读查看'}}</RouterLink></td></tr></tbody></table></div>
</template>
<style scoped>
.meeting-table-scroll{width:100%;overflow-x:auto}.meeting-table{width:100%;border-collapse:collapse;text-align:left}.meeting-table th{font-size:12px;font-weight:500;color:var(--muted);background:var(--bg)}.meeting-table th,.meeting-table td{padding:16px;border-bottom:1px solid var(--border);vertical-align:top}.meeting-table tr:last-child td{border-bottom:0}.meeting-table th:first-child{width:60%}.meeting-name{display:block;color:var(--navy);font-weight:600;overflow-wrap:anywhere;white-space:pre-wrap}.meeting-number{display:block;color:var(--muted);font-size:11px;margin-top:5px;overflow-wrap:anywhere}.meeting-stage{white-space:nowrap;font-size:12px;color:var(--navy)}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
@media(max-width:600px){.meeting-table th,.meeting-table td{padding:12px 8px}.meeting-table th:nth-child(2),.meeting-table td:nth-child(2){display:none}.meeting-table th:first-child{width:auto}.meeting-table td:first-child{min-width:140px}}
</style>
