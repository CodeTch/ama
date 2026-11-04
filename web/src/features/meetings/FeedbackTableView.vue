<script setup lang="ts">
import {computed} from 'vue';import {usageLabels} from '../governance/workbench-types';import Icon from '../../shared/Icon.vue';
import type {Feedback,FeedbackSummary,FeedbackOverview} from '../../shared/api/feedback';
const props=defineProps<{items:readonly FeedbackSummary[];counts:FeedbackOverview['counts'];selected:Feedback['state']|null;loading:boolean;error:string|null;reviewing?:boolean}>();
const emit=defineEmits<{select:[state:Feedback['state']|null];open:[id:string]}>();
const tabs:readonly [Feedback['state']|null,string][]=[[null,'全部'],['PENDING','待审核'],['ACCEPTED','已通过'],['REJECTED','已驳回'],['NEEDS_MORE','需要补充']];
const labels=Object.fromEntries(tabs.filter(([key])=>key)),category:Record<string,string>={PERSON:'人名纠错',DEPARTMENT:'部门纠错',PRODUCT:'产品纠错',FINANCIAL_TERM:'金融术语纠错',PRODUCT_ALIAS:'产品别名',TERM_PRONUNCIATION:'术语读音',VOICEPRINT:'声纹样本',WORDING:'文字纠错',LABEL:'标签纠错',ACTION_OWNER:'行动负责人'};
const visible=computed(()=>props.items);
function time(value:string){const date=new Date(value);if(!Number.isFinite(+date))return '—';const parts=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date),part=(key:string)=>parts.find(p=>p.type===key)?.value??'';return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`;}
</script>
<template>
 <div class="page-head"><div><div class="page-title">{{reviewing?'语料与声纹审核':'我的语料反馈'}}</div><div class="page-desc">{{reviewing?'审核用户主动提交的内容 · 审核不授予来源会议访问权限':'仅展示本人提交的内容 · 会议内单次修改须经平台管理员审核后才会更新全局企业语料'}}</div></div></div>
 <div v-if="error" class="banner error" role="alert">{{error}}</div>
 <div class="tabs" role="tablist" aria-label="反馈状态"><button v-for="[state,label] in tabs" :key="state??'ALL'" class="tab" :class="{active:selected===state}" role="tab" :aria-selected="selected===state" :disabled="loading" @click="emit('select',state)">{{label}}<span class="cnt">{{error||loading?'—':counts[state??'ALL']}}</span></button></div>
 <div class="card" :aria-busy="loading"><div class="table-wrap"><table class="tbl"><thead><tr><th>类型</th><th>原始值</th><th>建议值 / 人员</th><th>来源会议</th><th>提交时间</th><th>状态</th><th>审核意见</th></tr></thead><tbody>
  <tr v-for="item in visible" :key="item.id"><td><span class="tag" :class="item.kind==='VOICEPRINT'?'blue':'navy'">{{category[item.feedbackType??item.category]??item.category}}</span></td><td class="small">{{item.originalValue??'—'}}</td><td class="b small"><button class="feedback-value" title="查看反馈详情" @click="emit('open',item.id)">{{item.suggestedValue??item.comment}}</button></td><td class="small mono muted">{{item.meetingId}}</td><td class="small muted num">{{time(item.submittedAt)}}</td><td><span class="tag" :class="item.state==='ACCEPTED'?'green':item.state==='REJECTED'?'red':item.state==='NEEDS_MORE'?'amber':'orange'">{{labels[item.state]}}</span><span v-if="item.usageState" class="feedback-usage">{{usageLabels[item.usageState]}}</span></td><td class="small muted">{{item.reviewReason||'等待平台管理员审核'}}</td></tr>
  <tr v-if="!visible.length&&!error"><td colspan="7"><div class="empty"><Icon name="message" :size="34"/><div class="e-t">{{loading?'正在读取反馈':'暂无反馈记录'}}</div><div class="e-d">在转写校正中处理实体纠错或命名说话人后可提交反馈</div></div></td></tr>
 </tbody></table></div></div>
</template>
<style scoped>.feedback-usage{display:block;font-size:11px;color:var(--muted);margin-top:5px}.tbl td{overflow-wrap:anywhere}.tbl .num{white-space:nowrap}.feedback-value{font:inherit;color:inherit;background:none;border:0;padding:0;margin:0;text-align:inherit;cursor:pointer}.feedback-value:focus-visible{outline:1px solid var(--navy);outline-offset:2px}</style>
