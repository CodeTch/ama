<script setup lang="ts">
defineProps<{name:'list'|'mic'|'plus'|'layers'|'play'|'pause'|'file'|'refresh'|'edit'|'search'|'x'|'copy'|'calendar'|'message'|'guide'|'reset'|'arrowL'|'arrowR'|'alert';size?:number}>();
</script>
<template><svg :width="size||16" :height="size||16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-3px" aria-hidden="true">
 <template v-if="name==='list'"><path d="M8 6h13M8 12h13M8 18h13"/><circle cx="4" cy="6" r="1.4"/><circle cx="4" cy="12" r="1.4"/><circle cx="4" cy="18" r="1.4"/></template>
 <template v-else-if="name==='mic'"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0"/><path d="M12 18v3"/></template>
 <path v-else-if="name==='plus'" d="M12 5v14M5 12h14"/>
 <path v-else-if="name==='play'" d="M8 5.5v13l11-6.5-11-6.5Z"/>
 <template v-else-if="name==='pause'"><rect x="7" y="5" width="3.4" height="14" rx="1"/><rect x="13.6" y="5" width="3.4" height="14" rx="1"/></template>
 <template v-else-if="name==='file'"><path d="M6 3h8l4 4v14H6V3Z"/><path d="M14 3v4h4"/><path d="M9 12h6M9 16h6"/></template>
 <template v-else-if="name==='refresh'"><path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 3v4h-4"/></template>
 <template v-else-if="name==='edit'"><path d="M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17l-1 3Z"/><path d="m13.5 6.5 3 3"/></template>
 <template v-else-if="name==='search'"><circle cx="11" cy="11" r="6.5"/><path d="m16 16 5 5"/></template>
 <path v-else-if="name==='x'" d="M6 6l12 12M18 6 6 18"/>
 <template v-else-if="name==='copy'"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M5 16H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h11a1 1 0 0 1 1 1v1"/></template>
 <template v-else-if="name==='calendar'"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18"/><path d="M7 14h2M12 14h2M7 18h2M12 18h2"/></template>
 <path v-else-if="name==='message'" d="M4 5h16v11H9l-5 4V5Z"/>
 <template v-else-if="name==='alert'"><path d="M12 3 2.5 20h19L12 3Z"/><path d="M12 10v4"/><circle cx="12" cy="17" r=".6"/></template>
 <template v-else-if="name==='guide'"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.6 2.2c-.8.4-1.1.9-1.1 1.8"/><circle cx="12" cy="17" r=".6"/></template>
 <template v-else-if="name==='reset'"><path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/></template>
 <path v-else-if="name==='arrowL'" d="M15 5l-7 7 7 7"/>
 <path v-else-if="name==='arrowR'" d="m9 5 7 7-7 7"/>
 <template v-else><path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/><path d="m3 17.5 9 5 9-5"/></template>
</svg></template>
