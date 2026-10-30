import { createApp, h, Fragment } from 'vue';
import { createRouter, createWebHistory, RouterView, RouterLink } from 'vue-router';
import App from './App.vue';
import MeetingDetail from './features/meetings/MeetingDetail.vue';
import {createTabJournalContext} from './shared/composables/tab-journal';
import {applicationKey,createApplication} from './application';
import './shared/styles/tokens.css';
import './shared/styles/prototype.css';
import './shared/styles/parity.css';
import FuturePage from './features/meetings/FuturePage.vue';
import RecordingPage from './features/meetings/RecordingPage.vue';
import RecordingIndicator from './features/recording/RecordingIndicator.vue';
import LoginPage from './features/auth/LoginPage.vue';
import TranscriptCorrectionDemoPage from './features/transcript/TranscriptCorrectionDemoPage.vue';
import {syncNavigationScope} from './shared/navigation-scope';
const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',redirect:'/login'}, {path:'/login',component:LoginPage}, {path:'/meetings',component:App}, {path:'/feedback',component:FuturePage}, {path:'/meeting-library',component:FuturePage}, {path:'/recording/:id?',name:'recording',component:RecordingPage}, {path:'/meeting/:id/:stage?',name:'meeting-detail',component:MeetingDetail}, {path:'/transcript-correction-demo',name:'transcript-correction-demo',component:TranscriptCorrectionDemoPage},
 {path:'/:pathMatch(.*)*',component:{render:()=>h('main',{class:'content'},[h('h1','页面暂不可用'),h(RouterLink,{to:'/meetings'},()=> '返回我的会议')])}}
]});
const navigation=performance.getEntriesByType('navigation')[0];
const journalContext=createTabJournalContext(()=>window.sessionStorage,navigation instanceof PerformanceNavigationTiming?navigation.type:'navigate');
const application=createApplication(window.location.origin,journalContext);
router.beforeEach(async to=>{
 if(to.path==='/login'||to.path==='/transcript-correction-demo'||application.session.current.value)return true;
 try{await application.session.refresh();return true;}catch{return {path:'/login'};}
});
router.afterEach((to,_from,failure)=>syncNavigationScope(to,failure,application.setMeetingScope));
createApp({render:()=>h(Fragment,null,[h(RouterView),h(RecordingIndicator)])}).provide(applicationKey,application).use(router).mount('#app');
