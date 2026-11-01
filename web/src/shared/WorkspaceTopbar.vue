<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {RouterLink,useRouter} from 'vue-router';
import {useApplication} from '../application';
import Icon from './Icon.vue';
withDefaults(defineProps<{breadcrumb:string;roleName?:string}>(),{roleName:'会议负责人'});
const {session,options}=useApplication(),router=useRouter();
const profile=ref<{displayName:string;department:string|null}|null>(null),notice=ref<HTMLDialogElement>(),noticeTitle=ref(''),noticeBody=ref('');
const profileName=computed(()=>profile.value?.displayName||session.current.value?.actor.enterpriseId||(session.status.value==='LOADING'?'正在检查登录':'尚未登录'));
watch(()=>session.current.value?.actor.enterpriseId,(actor,_old,cleanup)=>{
 profile.value=null;if(!actor||!options?.people)return;const request=new AbortController();cleanup(()=>request.abort());
 void options.people(actor,null,request.signal).then(result=>{if(!request.signal.aborted&&session.current.value?.actor.enterpriseId===actor)profile.value=result.items.find(p=>p.stableId===actor)??null;}).catch(()=>{});
},{immediate:true});
function show(title:string,body:string){noticeTitle.value=title;noticeBody.value=body;notice.value?.showModal();}
</script>
<template>
 <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home"><Icon name="list"/> 我的会议</RouterLink><span class="topbar-crumb">/ {{breadcrumb}}</span></nav><div class="topbar-right">
  <button class="btn sm ghost" @click="router.push('/feedback')"><Icon name="message" :size="15"/> 我的语料反馈</button>
  <button class="btn sm ghost" @click="show('演示引导','可通过“开始录音”采集会议，或通过“导入会议”上传音视频和文档；在会议工作台核对转写、纪要与来源，再确认并发布。')"><Icon name="guide" :size="15"/> 演示引导</button>
  <button class="btn sm ghost" @click="show('重置演示数据','当前会议保存在本地数据库中。此页面不执行数据删除；如需清空首页筛选，请使用“清空筛选”。')"><Icon name="reset" :size="15"/> 重置演示数据</button>
  <details class="home-profile"><summary class="role-chip" aria-label="当前用户"><div class="avatar o">{{Array.from(profileName)[0]}}</div><div><div class="rc-name">{{profileName}}</div><div class="rc-role">{{roleName}} · {{profile?.department??'--'}}</div></div></summary><div class="home-profile-menu"><RouterLink to="/meeting-library" class="btn sm ghost">会议库</RouterLink><a v-if="session.current.value" class="btn sm ghost" href="/auth/cas/logout">退出登录</a><RouterLink v-else class="btn sm ghost" to="/login">企业登录</RouterLink></div></details>
 </div></header>
 <dialog ref="notice" class="notice-dialog" aria-labelledby="workspace-notice-title"><form method="dialog"><h2 id="workspace-notice-title">{{noticeTitle}}</h2><p>{{noticeBody}}</p><button class="btn primary" autofocus>知道了</button></form></dialog>
</template>
<style scoped>
.home-profile{position:relative}.home-profile>summary{cursor:pointer;list-style:none}.home-profile>summary::-webkit-details-marker{display:none}.home-profile-menu{position:absolute;right:0;top:calc(100% + 4px);z-index:45;display:flex;flex-direction:column;align-items:stretch;min-width:150px;padding:8px;background:white;border:1px solid var(--border);border-radius:8px;box-shadow:var(--shadow-lg)}
@media(max-width:600px){.topbar-right{min-width:0;max-width:55%;overflow:hidden}.topbar-right>.btn{display:none}.home-profile{min-width:0;max-width:100%}.home-profile>summary{display:flex;align-items:center;gap:8px;max-width:100%}.home-profile>summary>div:last-child{min-width:0;overflow:hidden}.home-profile .rc-name,.home-profile .rc-role{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
</style>
