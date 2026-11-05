<script setup lang="ts">
import {computed} from 'vue';
const props=defineProps<{text:string;search:RegExp|null}>();
const parts=computed(()=>props.search?props.text.split(props.search):[]);
</script>
<template><template v-if="search"><template v-for="(part,i) in parts" :key="i"><mark v-if="i%2">{{part}}</mark><template v-else>{{part}}</template></template></template><template v-else>{{text}}</template></template>
<style scoped>mark{background:#ffe3a1;color:inherit;border-radius:2px}</style>
