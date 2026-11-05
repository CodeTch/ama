<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import SpeakerDialog from './SpeakerDialog.vue';

type Group = { id: string; label: string; segmentIds: readonly string[] };
type Segment = { segmentId: string; sourceFileId: string; startMs: number | null; body: string };
const props = defineProps<{
  mode: 'merge' | 'split';
  groups: readonly Group[];
  segments: readonly Segment[];
  playable: readonly string[];
  busy: boolean;
  error: string | null;
}>();
const emit = defineEmits<{
  close: [];
  merge: [source: string, target: string];
  split: [source: string, segments: string[]];
  seek: [fileId: string, ms: number];
}>();
const sources = computed(() => props.mode === 'merge' ? props.groups : props.groups.filter(group => group.segmentIds.length > 1));
const source = ref(sources.value[0]?.id ?? '');
const targets = computed(() => props.groups.filter(group => group.id !== source.value));
const target = ref(targets.value[0]?.id ?? '');
const selected = ref<string[]>([]);
const validation = ref('');
const sourceSegments = computed(() => {
  const ids = new Set(props.groups.find(group => group.id === source.value)?.segmentIds);
  return props.segments.filter(segment => ids.has(segment.segmentId));
});
watch(source, () => {
  selected.value = []; validation.value = '';
  if (!targets.value.some(group => group.id === target.value)) target.value = targets.value[0]?.id ?? '';
});
function clock(ms: number | null) {
  if (ms === null) return '--:--';
  const seconds = Math.floor(ms / 1000);
  return [Math.floor(seconds / 60), seconds % 60].map(n => String(n).padStart(2, '0')).join(':');
}
function submit() {
  if (props.busy) return;
  validation.value = '';
  if (props.mode === 'merge') {
    if (!target.value || source.value === target.value) { validation.value = '来源与目标不能相同'; return; }
    emit('merge', source.value, target.value);
  } else {
    if (!selected.value.length) { validation.value = '请选择至少一个片段'; return; }
    if (selected.value.length === sourceSegments.value.length) { validation.value = '请为原说话人保留至少一个片段'; return; }
    emit('split', source.value, [...selected.value]);
  }
}
</script>
<template>
  <SpeakerDialog :title="mode === 'merge' ? '合并说话人' : '拆分说话人'" :busy="busy" @close="emit('close')">
    <p class="small muted mb12">{{ mode === 'merge'
      ? '将「来源说话人」的全部发言合并到「目标说话人」，可撤销。'
      : '选择要拆分出去的说话片段，将生成新的匿名说话人。' }}</p>
    <label class="field">{{ mode === 'merge' ? '来源说话人' : '源说话人' }}
      <select v-model="source" class="select" :disabled="busy">
        <option v-for="group in sources" :key="group.id" :value="group.id">
          {{ mode === 'merge' ? group.label + '（' + group.segmentIds.length + '段）' : group.label }}
        </option>
      </select>
    </label>
    <label v-if="mode === 'merge'" class="field">目标说话人
      <select v-model="target" class="select" :disabled="busy">
        <option v-for="group in targets" :key="group.id" :value="group.id">{{ group.label }}</option>
      </select>
    </label>
    <p v-if="mode === 'merge' && target" class="small muted">将移动 {{ sourceSegments.length }} 段发言，保留「{{ targets.find(group => group.id === target)?.label }}」的身份。</p>
    <fieldset v-else-if="mode === 'split'" class="field segments" :disabled="busy">
      <legend>选择片段</legend>
      <div class="choices">
        <div v-for="segment in sourceSegments" :key="segment.segmentId" class="radio-row choice">
          <input :id="'split-' + segment.segmentId" v-model="selected" type="checkbox" :value="segment.segmentId">
          <button v-if="segment.startMs !== null && playable.includes(segment.sourceFileId)" type="button"
            class="segment-time" :aria-label="'回听 ' + clock(segment.startMs)"
            @click="emit('seek', segment.sourceFileId, segment.startMs)">{{ clock(segment.startMs) }}</button>
          <b v-else>{{ clock(segment.startMs) }}</b>
          <label :for="'split-' + segment.segmentId">{{ segment.body.slice(0,40) }}…</label>
        </div>
        <div v-if="!sourceSegments.length" class="empty">无片段</div>
      </div>
    </fieldset>
    <p v-if="validation || error" role="alert" class="small muted">{{ validation || error }}</p>
    <template #actions>
      <button class="btn" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="btn primary" type="button" :disabled="busy" @click="submit">{{ mode === 'merge' ? '合并' : '拆分为新说话人' }}</button>
    </template>
  </SpeakerDialog>
</template>
<style scoped>
.field { display: block; }
.segments { border: 0; padding: 0; min-width: 0; }
.segments legend { padding: 0; margin-bottom: 6px; color: var(--muted); font-size: 12.5px; }
.choices { max-height: 240px; overflow-y: auto; }
.choice { padding: 7px 10px; font-size: 12px; }
.choice label { flex: 1; margin: 0; cursor: pointer; overflow-wrap: anywhere; }
.segment-time { color: inherit; font: inherit; font-weight: 700; background: none; border: 0; cursor: pointer; padding: 0; }
</style>
