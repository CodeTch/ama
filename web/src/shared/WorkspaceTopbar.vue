<script setup lang="ts">
import {computed} from 'vue';
import {RouterLink,useRoute,useRouter} from 'vue-router';
import {useApplication} from '../application';
import Icon from './Icon.vue';
const props=withDefaults(defineProps<{breadcrumb?:string;sessionChecking?:boolean}>(),{breadcrumb:'',sessionChecking:false});
const {session,currentUser}=useApplication(),router=useRouter(),route=useRoute();
const profilePending=computed(()=>props.sessionChecking||session.status.value==='LOADING'||currentUser.loading.value);
const profileName=currentUser.name;
</script>
<template>
  <header class="topbar"><nav class="topbar-heading" aria-label="主导航"><RouterLink to="/meetings" class="topbar-home" :aria-current="route.path==='/meetings'?'page':undefined"><img class="topbar-logo" src="/cmf-login-logo.png" alt="招商基金" width="28" height="28"/> 智会助手</RouterLink><span v-if="breadcrumb" class="topbar-crumb">/ {{breadcrumb}}</span></nav><div class="topbar-right">
   <button class="btn sm ghost" @click="router.push('/feedback')"><Icon name="message" :size="15"/> 我的语料反馈</button>
   <details class="home-profile"><summary class="role-chip" aria-label="当前用户" :aria-busy="profilePending" :title="profilePending?undefined:profileName"><div class="avatar o" :class="{'home-avatar-placeholder':profilePending}" aria-hidden="true">{{profilePending?'':Array.from(profileName)[0]}}</div><div class="rc-name"><span v-if="profilePending" class="home-name-placeholder" aria-hidden="true"></span><template v-else>{{profileName}}</template></div></summary><div class="home-profile-menu"><RouterLink to="/meeting-library" class="btn sm ghost">会议库</RouterLink><RouterLink v-if="session.current.value" to="/my/voiceprints" class="btn sm ghost">我的声纹</RouterLink><RouterLink v-if="session.current.value?.actor.roles.includes('PLATFORM_ADMIN')" to="/operations" class="btn sm ghost">运营治理</RouterLink><a v-if="session.current.value" class="btn sm ghost" href="/auth/cas/logout">退出登录</a><RouterLink v-else class="btn sm ghost" to="/login">企业登录</RouterLink></div></details>
  </div></header>
</template>
<style scoped>
.topbar{position:relative;top:auto;flex-shrink:0}
.topbar-crumb{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.topbar-logo{display:block;flex-shrink:0;border-radius:50%}
.topbar-home:hover{color:var(--navy)}
.home-profile{position:relative;width:144px;flex-shrink:0}.home-profile>summary{cursor:pointer;list-style:none;border:0;width:100%;height:40px}.home-profile .rc-name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.home-avatar-placeholder{background:var(--border)}.home-name-placeholder{display:block;width:72px;height:12px;background:var(--border);border-radius:4px}.home-profile>summary::-webkit-details-marker{display:none}.home-profile-menu{position:absolute;right:0;top:calc(100% + 4px);z-index:45;display:flex;flex-direction:column;align-items:stretch;min-width:150px;padding:8px;background:white;border:1px solid var(--border);border-radius:8px;box-shadow:var(--shadow-lg)}
@media(max-width:600px){
 .topbar-right{min-width:0;max-width:40%}
 .topbar-heading{gap:8px}
 .topbar-right>.btn{display:none}
 .home-profile{min-width:0;max-width:100%}
 .home-profile>summary{display:flex;align-items:center;gap:8px;max-width:100%}
 .home-profile>summary>div:last-child{min-width:0;overflow:hidden}
 .home-profile .rc-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
}
</style>
