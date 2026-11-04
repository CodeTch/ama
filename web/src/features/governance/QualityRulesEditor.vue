<script setup lang="ts">
import type {Rules} from './workbench-types';
const props=defineProps<{modelValue:Rules;disabled?:boolean}>(),emit=defineEmits<{'update:modelValue':[Rules]}>();
const fields:readonly [keyof Rules,string,string][]=[['maxCer','识别 CER 上限','字符替换、删除、插入 / 参考字符数'],['maxDer','分离 DER 上限','按时间计错，包含重叠语音'],['minEntityF1','实体 F1 下限','兼顾查准率与查全率'],['minEvidenceCoverage','证据覆盖率下限','有正确证据支持的结论比例'],['minEvidenceCorrectness','证据链接正确率下限','正确链接 / 全部预测链接'],['minMinutesRubric','纪要人工评分下限','事实、行动项、证据三项均须达标'],['maxFar','声纹 FAR 上限','不同人员被错误接受的比例'],['maxFrr','声纹 FRR 上限','同一人员被错误拒绝的比例']];
function update(key:keyof Rules,event:Event){emit('update:modelValue',{...props.modelValue,[key]:Number((event.target as HTMLInputElement).value)});}
</script>
<template><div class="gov-fields"><div class="gov-note">这些规则会随候选配置冻结。各类样本分别判定；失败样本和缺失标注会阻止发布。后续修改规则需要重新评测与复核。</div><div class="gov-two"><label v-for="[key,label,hint] in fields" :key="key">{{label}}<input type="number" :aria-label="label" :value="modelValue[key]" min="0" max="1" step="0.01" :disabled="disabled" @input="update(key,$event)"/><small>{{hint}} · 使用 0–1 小数</small></label></div><label>每类最少样本数<input type="number" :value="modelValue.minimumPerKind" min="1" max="50" step="1" :disabled="disabled" @input="update('minimumPerKind',$event)"/><small>声纹同人、异人分别计数。当前每个数据集版本最多 50 个样本。</small></label></div></template>
