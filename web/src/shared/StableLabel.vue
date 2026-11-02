<script setup lang="ts">
defineProps<{labels:readonly string[]}>();
</script>
<template><span class="stable-label"><span v-for="label in labels" :key="label" class="stable-label-reserve" aria-hidden="true">{{label}}</span><span class="stable-label-text"><slot/></span></span></template>
<style scoped>
.stable-label{display:inline-grid;align-items:center;justify-items:center;vertical-align:middle}.stable-label>span{grid-area:1/1}.stable-label-reserve{visibility:hidden;pointer-events:none;user-select:none}
</style>
