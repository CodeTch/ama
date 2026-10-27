<script setup lang="ts">
import {computed,ref} from 'vue';
import type {InfoFields} from '../../shared/api/meeting-info';
import type {Person,Category} from '../../shared/api/meeting-options';
import type {CommandState} from '../../shared/composables/useCommand';
const props=defineProps<{value:InfoFields;dirty:boolean;busy:boolean;blocked:boolean;state:CommandState;operation:string|null;conflict:boolean;conflictValue?:InfoFields|null;conflictToken?:string|null;canConfirm:boolean;categories:readonly Category[];people:readonly Person[];optionsBusy:boolean;optionsError:string|null;categoryMore:boolean;peopleMore:boolean;error:string|null;storageStatus:string}>();
const emit=defineEmits<{edit:[value:InfoFields];save:[];confirm:[];retry:[];reload:[];reapply:[];discard:[];search:[query:string];categories:[more:boolean];people:[query:string]} >();
const query=ref(''),external=ref('');
const unresolved=computed(()=>['PREPARED','SENDING','UNCERTAIN','DENIED'].includes(props.state));
function update(key:keyof InfoFields,value:unknown){emit('edit',{...props.value,[key]:value});}
function input(key:'name'|'summary'|'category'|'occurredOn',event:Event){const value=(event.target as HTMLInputElement).value;update(key,value===''?null:value);}
function addPerson(person:Person){if(person.active&&!props.value.rosterDraftIds.includes(person.stableId))update('rosterDraftIds',[...props.value.rosterDraftIds,person.stableId]);}
function addExternal(){if(external.value.trim()){update('externalRosterNames',[...(props.value.externalRosterNames??[]),external.value]);external.value='';}}
</script>
<template>
 <section class="card info-editor" aria-labelledby="info-editor-heading">
  <h2 id="info-editor-heading">编辑会议信息</h2>
  <p class="muted">保存名单草稿不会立即改变参会权限；请核对后单独确认已保存名单。</p>
  <p v-if="blocked" role="alert">恢复记录暂无法读取，已暂停编辑和保存。请保持页面打开并重新加载。</p>
  <p v-if="storageStatus==='UNAVAILABLE'||storageStatus==='INVALID'" role="alert">本地恢复记录未保存，刷新或关闭页面可能丢失修改。</p>
  <p v-if="error" role="alert">{{error}}</p>
  <p v-if="unresolved" role="status">原操作结果尚未确认，请重试原请求。新操作需等待该结果。</p>
  <p v-if="state==='ACKNOWLEDGED'" role="status">已收到原操作成功回执，当前保存内容以重新读取的结果为准。</p>
  <p v-if="conflict" role="alert">服务器已有新版本。本地草稿已保留，请比较已保存信息后选择如何处理。</p>
  <section v-if="conflict&&conflictValue" class="conflict-preview" aria-label="最新保存版本">
   <h3>最新保存版本 · {{conflictToken}}</h3><dl><dt>会议名称</dt><dd>{{conflictValue.name??'未填写'}}</dd><dt>会议日期</dt><dd>{{conflictValue.occurredOn??'未填写'}}</dd><dt>会议类别</dt><dd>{{conflictValue.category??'未填写'}}</dd><dt>会议简介</dt><dd>{{conflictValue.summary??'未填写'}}</dd><dt>最新名单草稿（企业身份）</dt><dd>{{conflictValue.rosterDraftIds.join('、')||'无'}}</dd><dt>最新外部人员草稿</dt><dd>{{(conflictValue.externalRosterNames??[]).join('、')||'无'}}</dd></dl>
  </section>
  <fieldset :disabled="blocked||busy"><legend class="sr-only">会议信息与名单草稿</legend>
   <label>会议名称<input :value="value.name??''" @input="input('name',$event)"></label>
   <div class="info-columns"><label>会议日期<input type="date" :value="value.occurredOn??''" @input="input('occurredOn',$event)"></label>
    <label>会议类别<select :value="value.category??''" @change="input('category',$event)"><option value="">未填写</option><option v-if="value.category&&!categories.some(c=>c.id===value.category)" :value="value.category">{{value.category}}（已选）</option><option v-for="category in categories" :key="category.id" :value="category.id">{{category.label}}</option></select></label></div>
   <div class="actions"><button type="button" class="btn" :disabled="optionsBusy" @click="emit('categories',false)">加载会议类别</button><button v-if="categoryMore" type="button" class="btn" :disabled="optionsBusy" @click="emit('categories',true)">下一页类别</button></div>
   <label>会议简介<textarea rows="4" :value="value.summary??''" @input="input('summary',$event)"></textarea></label>
   <h3>名单草稿</h3><ul class="selected"><li v-for="id in value.rosterDraftIds" :key="id">{{people.find(p=>p.stableId===id)?.displayName??id}} <small>{{id}}</small><button type="button" class="btn" :aria-label="'移除 '+id" @click="update('rosterDraftIds',value.rosterDraftIds.filter(x=>x!==id))">移除</button></li></ul>
   <div class="actions"><label class="grow">搜索企业人员<input v-model="query" placeholder="姓名或企业身份"></label><button type="button" class="btn" :disabled="optionsBusy" @click="emit('search',query)">搜索人员</button></div>
   <ul class="selected"><li v-for="person in people" :key="person.stableId"><span>{{person.displayName}} · {{person.department??'部门未提供'}} <small>{{person.stableId}}</small></span><button type="button" class="btn" :disabled="!person.active||value.rosterDraftIds.includes(person.stableId)" @click="addPerson(person)">{{person.active?'加入名单':'已停用'}}</button></li></ul>
   <button v-if="peopleMore" type="button" class="btn" :disabled="optionsBusy" @click="emit('people',query)">下一页人员</button>
   <p v-if="optionsError" role="alert">{{optionsError}}</p>
   <div class="actions"><label class="grow">外部参会人员<input v-model="external" placeholder="外部人员姓名，不授予系统访问权限"></label><button type="button" class="btn" @click="addExternal">添加外部人员</button></div>
   <ul class="selected"><li v-for="(name,index) in value.externalRosterNames??[]" :key="index">{{name}}<button type="button" class="btn" @click="update('externalRosterNames',(value.externalRosterNames??[]).filter((_,i)=>i!==index))">移除</button></li></ul>
  </fieldset>
  <div class="actions"><button v-if="unresolved" class="btn btn-primary" :disabled="busy||blocked" @click="emit('retry')">重试原请求</button><button v-else class="btn btn-primary" :disabled="busy||blocked||conflict||state==='CONFLICT'||state==='REJECTED'" @click="emit('save')">保存信息与名单草稿</button><button class="btn" :disabled="busy||blocked||unresolved||dirty||conflict||!canConfirm" @click="emit('confirm')">确认已保存名单</button><button class="btn" :disabled="busy" @click="emit('reload')">重新加载</button></div>
  <div v-if="conflict||state==='CONFLICT'||state==='REJECTED'" class="actions"><button v-if="conflict" class="btn" :disabled="busy||unresolved||blocked" @click="emit('reapply')">保留草稿并应用到最新版本</button><button class="btn" :disabled="busy||unresolved||blocked" @click="emit('discard')">放弃本地修改</button></div>
 </section>
</template>
<style scoped>
.conflict-preview{background:#fff7ed;border:1px solid #fed7aa;padding:16px;border-radius:8px}.conflict-preview dl{display:grid;grid-template-columns:150px minmax(0,1fr);gap:10px;margin:0}.conflict-preview dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.info-editor{padding:24px;margin-bottom:20px}.info-editor h2{margin:0 0 12px;font-size:18px}.info-editor h3{font-size:16px}.muted,small{color:var(--muted)}fieldset{border:0;padding:0;margin:20px 0;min-width:0}label{display:flex;flex-direction:column;gap:8px;margin-bottom:16px}input,textarea,select{box-sizing:border-box;width:100%;padding:10px 12px;border:1px solid var(--border,#dce1ea);border-radius:8px;font:inherit;background:var(--surface,#fff);color:inherit}textarea{resize:vertical}.info-columns{display:grid;grid-template-columns:1fr 1fr;gap:20px}.actions{display:flex;flex-wrap:wrap;align-items:end;gap:12px;margin:12px 0}.grow{flex:1;min-width:180px;margin:0}.selected{list-style:none;padding:0;display:flex;flex-direction:column;gap:10px}.selected li{display:flex;align-items:center;flex-wrap:wrap;gap:12px;overflow-wrap:anywhere}.selected small{display:inline-block}button:disabled{opacity:.5;cursor:not-allowed}[role=alert]{color:#9a3412;overflow-wrap:anywhere}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}@media(max-width:600px){.conflict-preview{background:#fff7ed;border:1px solid #fed7aa;padding:16px;border-radius:8px}.conflict-preview dl{display:grid;grid-template-columns:150px minmax(0,1fr);gap:10px;margin:0}.conflict-preview dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.info-editor{padding:18px}.info-columns{grid-template-columns:1fr;gap:0}.actions>.btn{max-width:100%;white-space:normal}}
</style>
