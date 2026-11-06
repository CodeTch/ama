<script setup lang="ts">
import type {MinutesCurrent} from '../../shared/api/minutes-saved';
defineProps<{current:MinutesCurrent}>();
defineEmits<{sensitive:[]}>();
</script>
<template><details class="mt16"><summary>当前生成设置</summary>
 <p>正文模板：{{current.presentation?.appliedTemplateName??'未应用'}} · 已选模板：{{current.presentation?.selectedTemplateName??'未选择'}}</p>
 <p>已选择排除 {{current.presentation?.exclusions.filter(row=>row.selected).length??0}} 项</p>
 <button class="btn sm" @click="$emit('sensitive')">前往敏感信息</button>
</details></template>
