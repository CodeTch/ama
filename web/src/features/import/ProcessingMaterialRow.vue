<script setup lang="ts">
import Icon from '../../shared/Icon.vue';
import {materialParseError} from './material-parse-error';
import type {ProcessingMaterial} from '../recording/processing-api';

defineProps<{item:ProcessingMaterial}>();
</script>

<template>
 <div class="aci-progress-file">
  <div class="aci-progress-file-top">
   <span class="aci-file-type"><Icon :name="item.source==='KNOWLEDGE'?'layers':'file'" :size="18"/></span>
   <div class="aci-progress-name">
    <b :title="item.name">{{item.name}}</b>
    <div class="small muted">
     {{item.source==='KNOWLEDGE'?'知识库引用':item.source==='LOCAL'?'本地文件':'本次处理锁定的材料'}}
     <template v-if="item.sourceLibraryId"> · {{item.sourceLibraryName||item.sourceLibraryId}}</template>
     <span v-if="item.sourceVersion" :title="item.sourceVersion"> · 版本 {{item.sourceVersion.slice(0,12)}}</span>
    </div>
   </div>
   <span class="tag" :class="item.state==='READY'?'green':'red'">{{item.state==='READY'?'已解析':'解析失败 · 本次未参与增强'}}</span>
  </div>
  <div v-if="item.state==='READY'" class="progress mt12" aria-hidden="true"><div class="bar" style="width:100%"></div></div>
  <p v-if="item.state!=='READY'" class="small ac-error mt8">{{materialParseError(item.error??'')}}</p>
 </div>
</template>
