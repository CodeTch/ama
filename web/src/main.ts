import { createApp, h } from 'vue';
import { createRouter, createWebHistory, RouterView, RouterLink } from 'vue-router';
import App from './App.vue';
import MeetingDetail from './features/meetings/MeetingDetail.vue';
import {createTabJournalContext} from './shared/composables/tab-journal';
import {applicationKey,createApplication} from './application';
import './shared/styles/tokens.css';
const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',redirect:'/meetings'}, {path:'/meetings',component:App}, {path:'/meeting/:id',name:'meeting-detail',component:MeetingDetail},
 {path:'/:pathMatch(.*)*',component:{render:()=>h('main',{class:'content'},[h('h1','页面暂不可用'),h(RouterLink,{to:'/meetings'},()=> '返回我的会议')])}}
]});
const navigation=performance.getEntriesByType('navigation')[0];
const journalContext=createTabJournalContext(()=>window.sessionStorage,navigation instanceof PerformanceNavigationTiming?navigation.type:'navigate');
const application=createApplication(window.location.origin,journalContext);
router.afterEach(to=>application.setMeetingScope(to.name==='meeting-detail'&&typeof to.params.id==='string'?to.params.id:null));
createApp({render:()=>h(RouterView)}).provide(applicationKey,application).use(router).mount('#app');
