<script setup lang="ts">
import { onMounted, ref, useId } from 'vue';
import Icon from '../../shared/Icon.vue';

const props = defineProps<{ title: string; busy?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
const titleId = useId();
const pressedBackdrop = ref(false);
onMounted(() => dialog.value?.showModal());
function dismiss() {
  if (!props.busy) emit('close');
}
function rememberPointerDown(event: PointerEvent) {
  pressedBackdrop.value = event.target === dialog.value;
}
</script>

<template>
  <dialog ref="dialog" class="speaker-dialog" :aria-labelledby="titleId"
    @cancel.prevent @pointerdown="rememberPointerDown"
    @click="pressedBackdrop && $event.target === dialog && dismiss()">
    <header>
      <h3 :id="titleId">{{ title }}</h3>
      <button type="button" class="close-button" aria-label="关闭" :disabled="busy" @click="dismiss">
        <Icon name="x" :size="18" />
      </button>
    </header>
    <div class="speaker-dialog-body"><slot /></div>
    <footer><slot name="actions" /></footer>
  </dialog>
</template>

<style scoped>
.speaker-dialog {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  margin: auto;
  width: 520px;
  max-width: 92vw;
  max-height: 86vh;
  padding: 0;
  border: 0;
  border-radius: 16px;
  background: white;
  color: #252836;
  font: 14px/1.55 "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  box-shadow: 0 8px 30px rgba(17,24,73,.14);
  animation: speaker-pop .2s ease;
}
.speaker-dialog::backdrop {
  background: rgba(17,24,73,.42);
  backdrop-filter: blur(2px);
  animation: speaker-fade .18s ease;
}
header, footer { flex-shrink: 0; display: flex; align-items: center; }
header { padding: 16px 22px; border-bottom: 1px solid #e7e7e2; }
h3 { margin: 0; color: #111849; font-size: 16px; font-weight: 700; }
.close-button {
  margin-left: auto; border: 0; background: transparent; padding: 4px;
  border-radius: 6px; color: #6f7482; cursor: pointer; display: flex;
}
.close-button:hover { background: #f0f0ec; color: #252836; }
.speaker-dialog-body { padding: 20px 22px; overflow-y: auto; }
footer { padding: 14px 22px; border-top: 1px solid #e7e7e2; justify-content: flex-end; gap: 10px; }
@keyframes speaker-pop { from { transform: scale(.96) translateY(8px); opacity: 0; } }
@keyframes speaker-fade { from { opacity: 0; } }
</style>
