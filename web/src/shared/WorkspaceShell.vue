<script setup lang="ts">
import {computed} from 'vue';
import {RouterView,useRoute} from 'vue-router';
import WorkspaceTopbar from './WorkspaceTopbar.vue';

const route=useRoute();
const showHeader=computed(()=>route.matched.length>0&&route.meta.workspaceHeader!==false);
const pageName=computed(()=>typeof route.meta.pageName==='string'?route.meta.pageName:'');
</script>

<template>
 <div class="workspace-shell" :class="{'workspace-shell-with-header':showHeader,'workspace-shell-meetings':route.path==='/meetings'}">
  <WorkspaceTopbar v-if="showHeader" :breadcrumb="pageName"/>
  <RouterView/>
 </div>
</template>

<style scoped>
.workspace-shell{min-height:100vh}
.workspace-shell-with-header :deep(.layout){min-height:calc(100vh - 56px)}
.workspace-shell-meetings{display:flex;flex-direction:column;height:100dvh;min-height:0}
.workspace-shell-meetings :deep(.layout){flex:1;min-height:0;overflow:auto}
.workspace-shell :deep(.layout){scrollbar-gutter:stable}
</style>
