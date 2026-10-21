<script setup lang="ts">
defineProps<{name:'list'|'mic'|'plus'|'layers';size?:number}>();
</script>
<template><svg :width="size||16" :height="size||16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-3px" aria-hidden="true">
 <template v-if="name==='list'"><path d="M8 6h13M8 12h13M8 18h13"/><circle cx="4" cy="6" r="1.4"/><circle cx="4" cy="12" r="1.4"/><circle cx="4" cy="18" r="1.4"/></template>
 <template v-else-if="name==='mic'"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0"/><path d="M12 18v3"/></template>
 <path v-else-if="name==='plus'" d="M12 5v14M5 12h14"/>
 <template v-else><path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/><path d="m3 17.5 9 5 9-5"/></template>
</svg></template>
