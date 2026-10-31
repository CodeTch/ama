<script setup lang="ts">
import {computed} from 'vue';import Icon from '../../shared/Icon.vue';
import type {Feedback,FeedbackSummary,FeedbackOverview} from '../../shared/api/feedback';
const props=defineProps<{items:readonly FeedbackSummary[];counts:FeedbackOverview['counts'];selected:Feedback['state']|null;loading:boolean;error:string|null}>();
const emit=defineEmits<{select:[state:Feedback['state']|null];open:[id:string]}>();
const tabs:readonly [Feedback['state']|null,string][]=[[null,'全部'],['PENDING','待审核'],['ACCEPTED','已通过'],['REJECTED','已驳回'],['NEEDS_MORE','需要补充']];
const labels=Object.fromEntries(tabs.filter(([key])=>key)),category:Record<string,string>={WORDING:'文字纠错',LABEL:'标签纠错',ACTION_OWNER:'行动负责人'};
const visible=computed(()=>props.items);
function time(value:string){const date=new Date(value);if(!Number.isFinite(+date))return '—';const parts=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date),part=(key:string)=>parts.find(p=>p.type===key)?.value??'';return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`;}
</script>
<template>
 <div class="page-head"><div><div class="page-title">我的语料反馈</div><div class="page-desc">仅展示本人提交的内容 · 会议内单次修改须经平台管理员审核后才会更新全局企业语料</div></div></div>
 <div v-if="error" class="banner error" role="alert">{{error}}</div>
 <div class="tabs" role="tablist" aria-label="反馈状态"><button v-for="[state,label] in tabs" :key="state??'ALL'" class="tab" :class="{active:selected===state}" role="tab" :aria-selected="selected===state" :disabled="loading" @click="emit('select',state)">{{label}}<span class="cnt">{{counts[state??'ALL']}}</span></button></div>
 <div class="card" :aria-busy="loading"><div class="table-wrap"><table class="tbl"><thead><tr><th>类型</th><th>原始值</th><th>建议值 / 人员</th><th>来源会议</th><th>提交时间</th><th>状态</th><th>审核意见</th></tr></thead><tbody>
  <tr v-for="item in visible" :key="item.id"><td><span class="tag navy">{{category[item.category]??item.category}}</span></td><td class="small">—</td><td class="b small"><button class="feedback-value" title="查看反馈详情" @click="emit('open',item.id)">{{item.comment}}</button></td><td class="small mono muted">{{item.meetingId}}</td><td class="small muted num">{{time(item.submittedAt)}}</td><td><span class="tag" :class="item.state==='ACCEPTED'?'green':item.state==='REJECTED'?'red':item.state==='NEEDS_MORE'?'amber':'orange'">{{labels[item.state]}}</span></td><td class="small muted">{{item.reviewReason||'等待平台管理员审核'}}</td></tr>
  <tr v-if="!visible.length"><td colspan="7"><div class="empty"><Icon name="message" :size="34"/><div class="e-t">{{loading?'正在读取反馈':'暂无反馈记录'}}</div><div class="e-d">在转写校正中处理实体纠错或命名说话人后可提交反馈</div></div></td></tr>
 </tbody></table></div></div>
</template>
<style scoped>.feedback-value{font:inherit;color:inherit;background:none;border:0;padding:0;margin:0;text-align:inherit;cursor:pointer}.feedback-value:focus-visible{outline:2px solid var(--orange);outline-offset:2px}</style>
