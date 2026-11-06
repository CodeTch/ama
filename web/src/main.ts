import DeletionNotice from './features/meetings/DeletionNotice.vue';
import GovernanceNotice from './features/minutes/GovernanceNotice.vue';
import { createApp, h, Fragment } from 'vue';
import { createRouter, createWebHistory, RouterLink } from 'vue-router';
import WorkspaceShell from './shared/WorkspaceShell.vue';
const App=()=>import('./App.vue');
const MeetingDetail=()=>import('./features/meetings/MeetingDetail.vue');
import {createTabJournalContext} from './shared/composables/tab-journal';
import {applicationKey,createApplication} from './application';
import './shared/styles/tokens.css';
import './shared/styles/prototype.css';
import './shared/styles/parity.css';
const MeetingLibraryPage=()=>import('./features/meetings/MeetingLibraryPage.vue');
const FeedbackPage=()=>import('./features/meetings/FeedbackPage.vue');
const OperationsPage=()=>import('./features/governance/OperationsPage.vue');
const GovernanceVoices=()=>import('./features/governance/GovernanceVoices.vue');
const RecordingPage=()=>import('./features/meetings/RecordingPage.vue');
import RecordingIndicator from './features/recording/RecordingIndicator.vue';
const LoginPage=()=>import('./features/auth/LoginPage.vue');
const TranscriptCorrectionDemoPage=()=>import('./features/transcript/TranscriptCorrectionDemoPage.vue');
const KnowledgePublishDemoPage=()=>import('./features/minutes/KnowledgePublishDemoPage.vue');
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
router.beforeEach(async (to,from)=>{
 application.clearMinutesEntry();
 const directMinutes=!from.matched.length&&to.name==='meeting-detail'&&to.params.stage==='minutes'&&typeof to.params.id==='string'&&to.params.id!==DEMO_MEETING_ID;
 if(directMinutes)void MeetingDetail().catch(()=>{}); // Static route code can load during the session check.
 // Keep the shell, deferred recording page, and unknown-route boundary
 // renderable while anonymous. They contain no protected data and can show
 // the login/disabled state without creating a session or touching a device.
 // Protected API-backed pages still refresh the session before entering.
 if(to.path==='/login'||to.path==='/meetings'||to.name==='recording'||to.path==='/transcript-correction-demo'||to.path==='/knowledge-publish-demo'||to.name==='meeting-detail'&&to.params.id===DEMO_MEETING_ID||to.matched.some(record=>record.path==='/:pathMatch(.*)*') )return true;
 try{if(!application.session.current.value)await application.session.refresh();}catch{return {path:'/login'};}
 if(directMinutes)application.preloadMinutesEntry(String(to.params.id));
 return true;
});
router.afterEach((to,_from,failure)=>{if(to.name==='meeting-detail'&&to.params.id===DEMO_MEETING_ID)return;syncNavigationScope(to,failure,application.setMeetingScope);});
createApp({render:()=>h(Fragment,null,[h(WorkspaceShell),h(RecordingIndicator),h(GovernanceNotice),h(DeletionNotice)])}).provide(applicationKey,application).use(router).mount('#app');
