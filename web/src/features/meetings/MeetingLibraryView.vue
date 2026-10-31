<script setup lang="ts">
import type {LibraryContext} from '../../shared/api/meeting-library';import type {LibraryRow} from './useMeetingLibrary';
withDefaults(defineProps<{library:LibraryContext|null;rows:readonly LibraryRow[];source:LibraryRow|null;loading:boolean;unavailable:boolean;demo?:boolean}>(),{demo:false});defineEmits<{open:[row:LibraryRow];back:[];home:[]}>();
</script>
<template>
 <div v-if="!library" class="card card-p"><div class="empty" role="status">{{loading?'正在核实知识库访问权限…':unavailable?'知识库暂不可用':'暂无有权查看的会议纪要'}}</div><button class="btn" @click="$emit('home')">返回我的会议</button></div>
 <template v-else><div class="page-head"><div><div class="page-title">{{library.libraryName}}</div><div class="page-desc">{{library.libraryDescription}} · 知识库只读浏览{{demo?'（原型演示）':''}}</div></div></div>
 <div v-if="source" class="banner info">来源会议：{{source.meeting.name??'未命名会议'}}<span class="ml-a"></span><button class="btn sm" @click="$emit('back')">返回会议详情</button></div>
 <div class="card card-p"><div class="b mb16">已发布会议纪要</div><div class="small muted mb16">仅展示当前账号有权查看的已发布会议成果；知识库访问不扩大原录音、转写及源文件权限。</div><div v-for="row in rows" :key="row.meeting.id" class="file-item"><div style="flex:1;min-width:0"><button class="m19-name" @click="$emit('open',row)">{{row.meeting.name??'未命名会议'}}</button><div class="small muted mt8">{{row.context.folderPath.join(' / ')||'--'}} · {{row.context.versionToken}}</div></div></div><div v-if="!rows.length" class="empty">暂无有权查看的会议纪要</div></div>
 </template>
</template>
