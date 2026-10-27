<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue';import type {useMinutesEditor} from './useMinutesEditor';import {scalarOffset} from '../../shared/scalar-anchors';
const props=defineProps<{editor:ReturnType<typeof useMinutesEditor>}>();const emit=defineEmits<{saved:[]}>();const selection=ref<{startToken:string;endToken:string}|null>(null),localError=ref(''),anchorPage=ref(0);let timer:ReturnType<typeof setTimeout>|null=null;
const d=computed(()=>props.editor.draft.value),message=computed(()=>props.editor.error.value instanceof Error?props.editor.error.value.message:'操作暂不可用。');
const anchorPages=computed(()=>Math.max(1,Math.ceil(props.editor.anchors.value.length/100)));
watch(()=>props.editor.target.value?.id,()=>{anchorPage.value=0;selection.value=null;});watch(anchorPages,total=>anchorPage.value=Math.min(anchorPage.value,total-1));
function schedule(){if(timer)clearTimeout(timer);timer=setTimeout(()=>{timer=null;void props.editor.autosave().then(()=>emit('saved'));},1200);}
function input(field:'title'|'body',event:Event){try{if(!d.value)return;props.editor.edit({...d.value.value.value,[field]:(event.target as HTMLTextAreaElement).value});if(field==='body')selection.value=null;localError.value='';schedule();}catch(e){localError.value=e instanceof Error?e.message:'输入暂不可用。';}}
function select(event:Event){const area=event.target as HTMLTextAreaElement;selection.value=area.selectionEnd>area.selectionStart?{startToken:scalarOffset(area.value,area.selectionStart),endToken:scalarOffset(area.value,area.selectionEnd)}:null;}
function anchor(id:string){try{if(!selection.value)throw new Error('请先在正文中选中来源对应文字。');props.editor.anchor(id,selection.value.startToken,selection.value.endToken);localError.value='';}catch(e){localError.value=e instanceof Error?e.message:'来源位置无效。';}}
onUnmounted(()=>{if(timer)clearTimeout(timer);});
</script>
<template>
 <section v-if="d" class="editor" aria-label="纪要文字编辑">
  <h3>{{editor.target.value?.kind==='BODY'?'正文编辑':editor.target.value?.kind==='ACTION'?'行动字段编辑':'要素编辑'}}</h3>
  <p v-if="!editor.intent.canEdit.value">点击“开始编辑”完成本页编辑确认后可修改文字。</p><button class="btn" :disabled="editor.busy.value||editor.loading.value||editor.targetMissing?.value||!!d.conflict.value" @click="editor.begin">{{editor.intent.state.value==='UNCERTAIN'?'重试原编辑确认':'开始编辑'}}</button>
  <label v-if="editor.target.value?.kind!=='ELEMENT'">{{editor.target.value?.kind==='ACTION'?'负责人显示名（空白表示未指定）':'标题'}}<input :value="d.value.value.title" :readonly="!editor.intent.canEdit.value" @beforeinput="editor.intent.guardInput" @input="input('title',$event)" @compositionstart="editor.intent.beginComposition" @compositionend="editor.intent.endComposition();schedule()"/></label>
  <label v-if="editor.target.value?.kind==='ACTION'">截止日期（空白表示未指定）<input type="date" :value="d.value.value.body" :disabled="!editor.intent.canEdit.value" @input="input('body',$event)"/></label><label v-else>正文<textarea :value="d.value.value.body" :readonly="!editor.intent.canEdit.value" rows="12" @beforeinput="editor.intent.guardInput" @input="input('body',$event)" @select="select" @compositionstart="editor.intent.beginComposition" @compositionend="editor.intent.endComposition();schedule()"/></label>
  <p>{{d.dirty.value?'有未保存修改':'已与读取版本同步'}} · 版本 {{d.baseToken.value}}</p>
  <ul v-if="editor.anchors.value.length"><li v-for="a in editor.anchors.value.slice(anchorPage*100,(anchorPage+1)*100)" :key="a.evidenceId">来源文字：{{a.quote.slice(0,100)}} <button class="btn" :disabled="!editor.intent.canEdit.value||!selection" @click="anchor(a.evidenceId)">关联所选文字</button></li></ul>
  <nav v-if="anchorPages>1" aria-label="来源关联分页"><button class="btn" :disabled="anchorPage===0" @click="anchorPage--">上一页来源</button><span>第 {{anchorPage+1}} / {{anchorPages}} 页，共 {{editor.anchors.value.length}} 条来源关联</span><button class="btn" :disabled="anchorPage+1>=anchorPages" @click="anchorPage++">下一页来源</button></nav>
  <div v-if="d.conflict.value" role="alert"><h4>服务器已有更新，本地文字已保留</h4><pre>{{d.conflict.value.value.title}}
{{d.conflict.value.value.body}}</pre><button class="btn" @click="editor.reapply">将本地修改应用到最新版本</button><button class="btn" @click="editor.discard">放弃修改并读取最新版本</button></div>
  <div class="actions"><button class="btn" :disabled="editor.busy.value||!editor.intent.canAutosave.value||!d.dirty.value||!!d.conflict.value" @click="editor.save().then(()=>emit('saved'))">保存</button><button v-if="['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(editor.command.state.value)&&editor.command.snapshot.value" class="btn" :disabled="editor.busy.value" @click="editor.retry().then(()=>emit('saved'))">查回原保存结果</button><button class="btn" :disabled="editor.busy.value||['PREPARED','SENDING','UNCERTAIN'].includes(editor.command.state.value)" @click="editor.discard">放弃本地修改</button><button class="btn" @click="editor.reload">读取最新版本</button></div>
  <p v-if="editor.error.value" role="alert">{{message}}</p><p v-if="localError" role="alert">{{localError}}</p>
 </section>
</template>
<style scoped>.editor{padding:18px;border:1px solid var(--border);border-radius:12px;margin-top:16px}label{display:block;margin:14px 0}textarea,input{display:block;box-sizing:border-box;width:100%;margin-top:6px;padding:10px;color:var(--text);background:var(--surface);border:1px solid var(--border);border-radius:6px}textarea{line-height:1.6;resize:vertical}.actions{display:flex;flex-wrap:wrap;gap:8px}pre{white-space:pre-wrap;max-height:240px;overflow:auto}li{margin:8px 0;overflow-wrap:anywhere}button:disabled{opacity:.5}</style>
