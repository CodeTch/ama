import { createApp, h } from 'vue';
import { createRouter, createWebHistory, RouterView, RouterLink } from 'vue-router';
import App from './App.vue';
import './shared/styles/tokens.css';
const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',redirect:'/meetings'}, {path:'/meetings',component:App},
 {path:'/:pathMatch(.*)*',component:{render:()=>h('main',{class:'content'},[h('h1','页面暂不可用'),h(RouterLink,{to:'/meetings'},()=> '返回我的会议')])}}
]});
createApp({render:()=>h(RouterView)}).use(router).mount('#app');
