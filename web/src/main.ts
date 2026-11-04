import DeletionNotice from './features/meetings/DeletionNotice.vue';
import GovernanceNotice from './features/minutes/GovernanceNotice.vue';
import { createApp, h, Fragment } from 'vue';
import { createRouter, createWebHistory, RouterLink } from 'vue-router';
import WorkspaceShell from './shared/WorkspaceShell.vue';
import App from './App.vue';
import MeetingDetail from './features/meetings/MeetingDetail.vue';
import {createTabJournalContext} from './shared/composables/tab-journal';
import {applicationKey,createApplication} from './application';
import './shared/styles/tokens.css';
import './shared/styles/prototype.css';
import './shared/styles/parity.css';
import MeetingLibraryPage from './features/meetings/MeetingLibraryPage.vue';
import FeedbackPage from './features/meetings/FeedbackPage.vue';
import OperationsPage from './features/governance/OperationsPage.vue';
import GovernanceVoices from './features/governance/GovernanceVoices.vue';
import RecordingPage from './features/meetings/RecordingPage.vue';
import RecordingIndicator from './features/recording/RecordingIndicator.vue';
import LoginPage from './features/auth/LoginPage.vue';
import TranscriptCorrectionDemoPage from './features/transcript/TranscriptCorrectionDemoPage.vue';
import KnowledgePublishDemoPage from './features/minutes/KnowledgePublishDemoPage.vue';
import {DEMO_MEETING_ID} from './features/meetings/meeting-prototype-demo';
import {syncNavigationScope} from './shared/navigation-scope';
const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',redirect:'/login'},
 {path:'/login',component:LoginPage,meta:{workspaceHeader:false}},
 {path:'/meetings',component:App},
 {path:'/operations/feedback',redirect:to=>({path:'/operations/corpus',query:{...to.query,selected:to.query.feedbackId??to.query.selected}})},
 {path:'/feedback',beforeEnter:to=>to.query.reviewing==='1'?{path:'/operations/feedback',query:{...to.query,reviewing:undefined}}:true,component:FeedbackPage,meta:{pageName:'我的语料反馈'}},
 {path:'/operations',redirect:to=>({path:'/operations/voiceprints',query:to.query})},
 {path:'/operations/:module(voiceprints|corpus|configuration)',component:OperationsPage,meta:{pageName:'运营治理'}},
 {path:'/operations/evaluation/datasets',component:OperationsPage,meta:{pageName:'评测数据集'}},
 {path:'/operations/evaluation/:id?',component:OperationsPage,meta:{pageName:'质量评测'}},
 {path:'/my/voiceprints',component:GovernanceVoices,props:{personal:true},meta:{pageName:'我的声纹'}},
 {path:'/meeting-library/:id?',component:MeetingLibraryPage,meta:{pageName:'会议库'}},
 {path:'/recording/:id?',name:'recording',component:RecordingPage,meta:{pageName:'在线录音'}},
 {path:'/meeting/:id/:stage?',name:'meeting-detail',component:MeetingDetail,meta:{pageName:'会议详情'}},
 {path:'/transcript-correction-demo',name:'transcript-correction-demo',component:TranscriptCorrectionDemoPage,meta:{pageName:'转写校正'}},
 {path:'/knowledge-publish-demo',name:'knowledge-publish-demo',component:KnowledgePublishDemoPage,meta:{pageName:'知识库发布'}},
 {path:'/:pathMatch(.*)*',meta:{pageName:'页面暂不可用'},component:{render:()=>h('main',{class:'content'},[h('h1','页面暂不可用'),h(RouterLink,{to:'/meetings'},()=> '返回我的会议')])}}
]});
const navigation=performance.getEntriesByType('navigation')[0];
const journalContext=createTabJournalContext(()=>window.sessionStorage,navigation instanceof PerformanceNavigationTiming?navigation.type:'navigate');
const application=createApplication(window.location.origin,journalContext);
router.beforeEach(async to=>{
 // Keep the shell, deferred recording page, and unknown-route boundary
 // renderable while anonymous. They contain no protected data and can show
 // the login/disabled state without creating a session or touching a device.
 // Protected API-backed pages still refresh the session before entering.
 if(to.path==='/login'||to.path==='/meetings'||to.name==='recording'||to.path==='/transcript-correction-demo'||to.path==='/knowledge-publish-demo'||to.name==='meeting-detail'&&to.params.id===DEMO_MEETING_ID||to.matched.some(record=>record.path==='/:pathMatch(.*)*')||application.session.current.value)return true;
 try{await application.session.refresh();return true;}catch{return {path:'/login'};}
});
router.afterEach((to,_from,failure)=>{if(to.name==='meeting-detail'&&to.params.id===DEMO_MEETING_ID)return;syncNavigationScope(to,failure,application.setMeetingScope);});
createApp({render:()=>h(Fragment,null,[h(WorkspaceShell),h(RecordingIndicator),h(GovernanceNotice),h(DeletionNotice)])}).provide(applicationKey,application).use(router).mount('#app');
