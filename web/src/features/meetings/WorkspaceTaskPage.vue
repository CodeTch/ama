<script setup lang="ts">
import type {WorkspaceTaskPage,WorkspaceTaskKind} from '../../shared/api/workspace-tasks';
defineProps<{page:WorkspaceTaskPage|null;kind:WorkspaceTaskKind;loading:boolean;error:string|null}>();
const emit=defineEmits<{refresh:[];first:[];next:[];select:[kind:WorkspaceTaskKind]}>();
const kinds:Record<WorkspaceTaskKind,string>={ALL:'全部任务',TRANSCRIPT_IMPORT_V1:'文稿导入',MINUTES_GENERATE_V1:'纪要生成',KNOWLEDGE_PUBLISH_V1:'知识库发布'};
const states:Record<WorkspaceTaskPage['items'][number]['state'],string>={READY:'等待处理',RUNNING:'处理中',RECONCILING:'正在核对结果',SUCCEEDED:'处理成功',FAILED:'处理失败',CANCELLED:'已取消',STALE:'已失效'};
function select(event:Event){const value=(event.target as HTMLSelectElement).value;if(value==='ALL'||value==='TRANSCRIPT_IMPORT_V1'||value==='MINUTES_GENERATE_V1'||value==='KNOWLEDGE_PUBLISH_V1')emit('select',value);}
</script>
<template>
 <section class="card task-section" aria-labelledby="workspace-tasks-title">
  <div class="task-heading"><div><h2 id="workspace-tasks-title">处理任务</h2><p class="muted">状态以最近一次读取的结果为准。</p></div><button class="btn" :disabled="loading" @click="emit('refresh')">刷新任务</button></div>
  <label class="task-filter">任务类型 <select :value="kind" @change="select"><option v-for="(label,value) in kinds" :key="value" :value="value">{{label}}</option></select></label>
  <p v-if="loading" role="status">正在读取任务…</p>
  <div v-else-if="error" role="alert"><p>{{error}}</p><div class="task-actions"><button class="btn" @click="emit('refresh')">重试</button><button class="btn" @click="emit('first')">回到第一页</button></div></div>
  <template v-else-if="page">
   <p v-if="page.items.length===0" class="muted">本页没有符合筛选的任务。</p>
   <ul v-else class="task-list"><li v-for="item in page.items" :key="item.taskId"><div class="task-row"><strong>{{kinds[item.kind]}}</strong><span class="task-state">{{states[item.state]}}</span></div><small class="task-id">任务编号：{{item.taskId}}</small></li></ul>
   <p v-if="page.hasMore" class="muted">仍可继续查看后续记录。</p>
   <div class="task-actions"><button class="btn" @click="emit('first')">第一页</button><button class="btn" :disabled="!page.hasMore" @click="emit('next')">下一页</button></div>
  </template>
 </section>
</template>
<style scoped>
.task-section{padding:24px;margin-bottom:20px}.task-heading,.task-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.task-heading h2{font-size:18px;margin:0}.muted,.task-id{color:var(--muted)}.task-heading p{font-size:13px;margin:8px 0 0}.task-filter{display:flex;align-items:center;gap:12px;margin:20px 0}.task-filter select{max-width:100%;padding:8px 12px;border:1px solid var(--border);border-radius:6px;background:var(--card,#fff);color:inherit;font:inherit}.task-list{list-style:none;margin:0;padding:0}.task-list li{padding:16px 0;border-bottom:1px solid var(--border)}.task-row strong{font-size:14px}.task-state{font-size:13px}.task-id{display:block;margin-top:8px;overflow-wrap:anywhere}.task-actions{display:flex;gap:12px;margin-top:16px;flex-wrap:wrap}button:disabled{opacity:.55;cursor:default}@media(max-width:600px){.task-section{padding:18px}.task-heading{align-items:flex-start;flex-wrap:wrap}.task-row{align-items:flex-start;flex-wrap:wrap}}
</style>
