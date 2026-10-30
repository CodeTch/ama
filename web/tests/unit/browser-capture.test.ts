import {test} from 'node:test';import assert from 'node:assert/strict';import {browserCapture} from '../../src/features/recording/browser-capture';
test('explicitly denied microphone permission is respected before asking any device',async()=>{
 const nav=Object.getOwnPropertyDescriptor(globalThis,'navigator'),rec=Object.getOwnPropertyDescriptor(globalThis,'MediaRecorder');let calls=0;
 Object.defineProperty(globalThis,'MediaRecorder',{configurable:true,value:class{static isTypeSupported(){return true;}}});Object.defineProperty(globalThis,'navigator',{configurable:true,value:{permissions:{query:async()=>({state:'denied'})},mediaDevices:{getUserMedia:async()=>{calls++;throw Error('Unexpected device request');}}}});
 try{await assert.rejects(browserCapture(()=>{},()=>{}));assert.equal(calls,0);}finally{if(nav)Object.defineProperty(globalThis,'navigator',nav);else Reflect.deleteProperty(globalThis,'navigator');if(rec)Object.defineProperty(globalThis,'MediaRecorder',rec);else Reflect.deleteProperty(globalThis,'MediaRecorder');}
});

test('cancelling while permission discovery is pending prevents a later device request',async()=>{
 const nav=Object.getOwnPropertyDescriptor(globalThis,'navigator'),rec=Object.getOwnPropertyDescriptor(globalThis,'MediaRecorder');let calls=0,release:(v:unknown)=>void=()=>{};const pending=new Promise(resolve=>{release=resolve;});const controller=new AbortController();
 Object.defineProperty(globalThis,'MediaRecorder',{configurable:true,value:class{static isTypeSupported(){return true;}}});Object.defineProperty(globalThis,'navigator',{configurable:true,value:{permissions:{query:()=>pending},mediaDevices:{getUserMedia:async()=>{calls++;throw Error('Unexpected device request');}}}});
 try{const request=browserCapture(()=>{},()=>{},controller.signal);controller.abort();release({state:'granted'});await assert.rejects(request);assert.equal(calls,0);}finally{if(nav)Object.defineProperty(globalThis,'navigator',nav);else Reflect.deleteProperty(globalThis,'navigator');if(rec)Object.defineProperty(globalThis,'MediaRecorder',rec);else Reflect.deleteProperty(globalThis,'MediaRecorder');}
});
