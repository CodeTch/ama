<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import Icon from '../../shared/Icon.vue';
import type { createMeetingOptionsApi, Person } from '../../shared/api/meeting-options';
import SpeakerDialog from './SpeakerDialog.vue';

const props = defineProps<{
  originalLabel: string;
  candidates?: readonly {voiceprintId:string;subjectId:string;displayName:string;score:number;modelVersion:string}[];
  candidatesError?: string;
  name: string;
  enterpriseId: string | null;
  external: boolean;
  lookup: ReturnType<typeof createMeetingOptionsApi>['people'];
  busy: boolean;
  locked: boolean;
  error: string | null;
}>();
const emit = defineEmits<{
  close: [];
  submit: [name: string, enterpriseId: string | null, voice: boolean, voiceprintId?:string];
}>();
const method = ref<'org' | 'ext'>('org');
const scope = ref<'once' | 'voice'>('once');
const externalName = ref(props.external ? props.name : '');
const selectedId = ref('');
const selectedVoice=ref<string>();
function selectCandidate(candidate:NonNullable<typeof props.candidates>[number]){if(props.busy||props.locked)return;selectedId.value=candidate.subjectId;selectedVoice.value=candidate.voiceprintId;method.value='org';scope.value='once';validation.value='';}

const people = ref<Person[]>([]);
const directoryBusy = ref(true);
const directoryError = ref('');
const validation = ref('');
const composing = ref(false);
const review = ref(false);
const request = new AbortController();
const person = computed(() => people.value.find(item => item.stableId === selectedId.value));
const choice = computed(() => method.value === 'org'
  ? { name: person.value?.displayName ?? '', enterpriseId: person.value?.stableId ?? null, department: person.value?.department }
  : { name: externalName.value.trim(), enterpriseId: null, department: null });

onMounted(async () => {
  const seen = new Set<string>();
  const entries = new Map<string, Person>();
  let cursor: string | null = null;
  try {
    do {
      const page = await props.lookup('', cursor, request.signal);
      if (request.signal.aborted) return;
      for (const item of page.items) if (item.active) entries.set(item.stableId, item);
      cursor = page.nextCursor;
      if (cursor !== null && (seen.has(cursor) || seen.size >= 500)) throw Error('人员目录暂不可用，请重新读取。');
      if (cursor !== null) seen.add(cursor);
    } while (cursor !== null);
    people.value = [...entries.values()];
    selectedId.value = props.enterpriseId && entries.has(props.enterpriseId)
      ? props.enterpriseId
      : people.value[0]?.stableId ?? '';
  } catch (cause) {
    if (!request.signal.aborted) directoryError.value = cause instanceof Error ? cause.message : '人员目录暂不可用。';
  } finally {
    directoryBusy.value = false;
  }
});
onUnmounted(() => request.abort());

function confirm() {
  if (props.busy || composing.value) return;
  validation.value = '';
  if (!choice.value.name) {
    validation.value = method.value === 'ext' ? '请输入外部人员名称' : '请选择有效的企业人员';
    return;
  }
  if (scope.value === 'voice') review.value = true;
  else emit('submit', choice.value.name, choice.value.enterpriseId, false,selectedVoice.value);
}
</script>

<template>
  <SpeakerDialog v-if="review" key="review" title="提交声纹库审核" :busy="busy" @close="emit('close')">
    <div class="banner info">
      <Icon name="shield" :size="16" />
      <div>
        请确认以下规则：<br>
        · 提交声纹需要<b>对应人员授权</b>；<br>
        · 提交的是符合质量要求的声音片段；<br>
        · 提交后进入<b>平台管理员审核队列</b>，审核通过前不会成为正式声纹；<br>
        · 会议负责人<b>不能下载声纹特征</b>。
      </div>
    </div>
    <label class="field">被命名人员
      <input class="input" :value="[choice.name, choice.department].filter(Boolean).join(' · ')" disabled>
    </label>
    <p v-if="error" class="small muted" role="alert">{{ error }}</p>
    <template #actions>
      <button type="button" class="btn" :disabled="busy" @click="emit('close')">取消</button>
      <button type="button" class="btn primary" :disabled="busy"
        @click="emit('submit', choice.name, choice.enterpriseId, true,selectedVoice)">确认提交审核</button>
    </template>
  </SpeakerDialog>
  <SpeakerDialog v-else key="naming" :title="'命名说话人 · ' + originalLabel" :busy="busy" @close="emit('close')">
    <section v-if="candidates?.length||candidatesError" class="voice-candidates" aria-label="声纹匹配候选">
      <b>声纹匹配候选 · 请人工核对</b><p class="small muted">候选来自本段独立说话人的声音。请选择并核对，确认后仅命名本次会议。</p>
      <p v-if="candidatesError" role="status" class="small muted">{{candidatesError}}</p>
      <button v-for="candidate in candidates" :key="candidate.voiceprintId" type="button" class="btn sm" :class="{primary:selectedVoice===candidate.voiceprintId}" :aria-pressed="selectedVoice===candidate.voiceprintId" :disabled="busy||locked||directoryBusy||!people.some(p=>p.stableId===candidate.subjectId)" @click="selectCandidate(candidate)">{{candidate.displayName}} · 相似度 {{candidate.score.toFixed(3)}}</button>
    </section>
    <div class="field">
      <label>命名方式</label>
      <div class="tabs naming-tabs">
        <button type="button" class="tab" :class="{ active: method === 'org' }" :aria-pressed="method === 'org'"
          :disabled="busy || locked" @click="method = 'org'; validation = '';selectedVoice=undefined">从组织架构选择</button>
        <button type="button" class="tab" :class="{ active: method === 'ext' }" :aria-pressed="method === 'ext'"
          :disabled="busy || locked" @click="method = 'ext'; validation = '';selectedVoice=undefined">输入外部人员名称</button>
      </div>
      <div v-show="method === 'org'">
        <select v-model="selectedId" @change="selectedVoice=undefined" class="select" aria-label="从组织架构选择" :disabled="busy || locked || directoryBusy">
          <option v-for="entry in people" :key="entry.stableId" :value="entry.stableId">
            {{ [entry.displayName, entry.department, entry.title].filter(Boolean).join(' · ') }}
          </option>
        </select>
        <p v-if="directoryError" role="alert">{{ directoryError }}</p>
      </div>
      <input v-show="method === 'ext'" v-model="externalName" class="input" :disabled="busy || locked" maxlength="256"
        aria-label="外部人员名称" placeholder="例如：陈分析师（某券商）"
        @compositionstart="composing = true" @compositionend="composing = false" @input="validation = ''">
    </div>
    <fieldset class="field scope-field" :disabled="busy || locked">
      <legend>生效范围</legend>
      <label class="radio-row" :class="{ sel: scope === 'once' }">
        <input v-model="scope" type="radio" value="once"><span class="radio-dot"></span>
        <span><b>仅用于本次会议</b><span class="small muted">不进入声纹库，其他会议不受影响</span></span>
      </label>
      <label class="radio-row" :class="{ sel: scope === 'voice' }">
        <input v-model="scope" type="radio" value="voice"><span class="radio-dot"></span>
        <span><b>提交声纹库审核</b><span class="small muted">进入平台管理员审核队列，审核通过前不会成为正式声纹</span></span>
      </label>
    </fieldset>
    <div v-if="external" class="banner warn external-note">
      <Icon name="alert" :size="15" /> 外部人员或声纹无匹配结果时，可继续保留匿名说话人，不强制实名。
    </div>
    <p v-if="validation || error" class="small muted" role="alert">{{ validation || error }}</p>
    <template #actions>
      <button type="button" class="btn ghost" :disabled="busy" @click="emit('close')">保留匿名</button>
      <button type="button" class="btn primary" :disabled="busy || (method === 'org' && directoryBusy)" @click="confirm">确认命名</button>
    </template>
  </SpeakerDialog>
</template>

<style scoped>
.voice-candidates{padding:14px;background:#f6f7fb;border-radius:8px;margin-bottom:18px}.voice-candidates .btn{margin:4px 8px 0 0}
.naming-tabs { margin-bottom: 10px; }
.scope-field { border: 0; padding: 0; margin: 0 0 14px; min-width: 0; }
.scope-field legend { padding: 0; margin-bottom: 6px; color: #6f7482; font-size: 12.5px; font-weight: 500; }
.scope-field .radio-row { display: flex; position: relative; }
.scope-field input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.scope-field .radio-row:focus-within { outline: 2px solid var(--orange); outline-offset: 2px; }
.scope-field .small, .scope-field b { display: block; }
.external-note { margin: 0; }
</style>
