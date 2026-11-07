import {computed,ref,shallowRef,watch} from 'vue';
import type {createPublicationsApi} from '../../shared/api/publications';
import type {components} from '../../shared/api/generated';
import {ApiError} from '../../shared/api/client';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';

type Configuration=components['schemas']['PublicationConfiguration'];
type Api=Pick<ReturnType<typeof createPublicationsApi>,'configuration'|'saveConfiguration'>;
export type PublicationTarget={providerId:string;libraryId:string;folderId:string};
type Draft={baseVersion:string;tagsText:string};
const savedText=(value:Configuration|null)=>value?.configuration?.tags.join(', ')??'';
const sameTarget=(a:PublicationTarget|null|undefined,b:PublicationTarget)=>!!a&&a.providerId===b.providerId&&a.libraryId===b.libraryId&&a.folderId===b.folderId;
function validDraft(value:unknown):value is Draft {
 if(!value||typeof value!=='object'||Array.isArray(value))return false;const d=value as Record<string,unknown>;
 return Object.keys(d).sort().join(',')==='baseVersion,tagsText'&&typeof d.baseVersion==='string'&&/^(0|[1-9][0-9]{0,18})$/.test(d.baseVersion)&&typeof d.tagsText==='string'&&d.tagsText.length<=65536;
}

export function usePublicationConfiguration(api:Api,actor:()=>string|null,meeting:()=>string,journals?:TabJournalContext){
 const current=shallowRef<Configuration|null>(null),tagsText=ref(''),error=shallowRef<unknown>(null),busy=ref(false),message=ref(''),conflict=ref(false),storageMessage=ref('');
 const journal=journals?createScopedPrivateJournal(journals,'publication-configuration-draft',validDraft):null;
 const dirty=computed(()=>tagsText.value!==savedText(current.value));
 let epoch=0,disposed=false,applying=false,restored=false,baseVersion:string|null=null,controller:AbortController|null=null,flight:Promise<Configuration|undefined>|null=null;
 function apply(text:string){applying=true;tagsText.value=text;applying=false;}
 function clear(){epoch++;controller?.abort();controller=null;flight=null;current.value=null;apply('');error.value=null;busy.value=false;message.value='';conflict.value=false;storageMessage.value='';restored=false;baseVersion=null;journal?.synchronize(actor(),meeting());}
 const stop=watch(()=>[actor(),meeting()],(now,prior)=>{if(!now[0]&&prior?.[0])journal?.clear();clear();},{flush:'sync'});
 function persist(){const who=actor();if(applying||!who||baseVersion===null)return;if(journal&&journal.save(who,meeting(),{baseVersion,tagsText:tagsText.value})!=='SAVED')storageMessage.value='当前标签未能在此标签页保存，请在离开前保存配置。';}
 const stopDraft=watch(tagsText,persist,{flush:'sync'});
 function accept(value:Configuration){
  current.value=value;baseVersion=value.configurationVersionToken;apply(savedText(value));conflict.value=false;message.value='发布配置已保存';journal?.clear();storageMessage.value='';
 }
 function load():Promise<Configuration|undefined>{
  if(flight)return flight;if(disposed||!actor())return Promise.resolve(undefined);
  const turn=++epoch,who=actor(),id=meeting();controller?.abort();const request=new AbortController();controller=request;busy.value=true;error.value=null;
  const active=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting()&&!request.signal.aborted;
  const task=(async()=>{try{
   if(!restored){restored=true;const found=journal?.load(who!,id);if(found?.kind==='RESTORED'){baseVersion=found.value.baseVersion;apply(found.value.tagsText);}else if(found&&['INVALID','UNAVAILABLE'].includes(found.kind))storageMessage.value='上次标签草稿暂不可恢复，请核对当前输入。';}
   const local=baseVersion!==null&&(dirty.value||!current.value),value=await api.configuration(id,request.signal);if(!active())return;
   const previous=baseVersion;current.value=value;
   if(local){conflict.value=previous!==value.configurationVersionToken&&tagsText.value!==savedText(value);if(tagsText.value===savedText(value)){baseVersion=value.configurationVersionToken;journal?.clear();}else persist();}
   else{baseVersion=value.configurationVersionToken;apply(savedText(value));}
   return value;
  }catch(cause){if(active())error.value=cause;}
  finally{if(active()){busy.value=false;flight=null;}}})();flight=task;return task;
 }
 function tags(){
  const rules=current.value?.tagRules;if(!rules)throw Error('标签规则尚未读取，请重试读取发布配置。');
  const result:string[]=[],seen=new Set<string>();
  for(const raw of tagsText.value.split(/[,，]/)){if(!raw.trim())continue;const tag=rules.trim?raw.trim():raw;
   if([...tag].length>rules.maxLength||/[\u0000-\u001f\u007f-\u009f]/.test(tag))throw Error('每个标签最多 '+rules.maxLength+' 个字符，不能包含控制字符。');
   const key=rules.caseSensitive?tag:tag.toLowerCase();if(!seen.has(key)){seen.add(key);result.push(tag);}
  }
  if(result.length>rules.maxCount)throw Error('最多可填写 '+rules.maxCount+' 个标签。');if(!result.length&&!rules.allowEmpty)throw Error('目标知识库要求至少填写一个标签。');return result;
 }
 async function save(target:PublicationTarget){
  if(disposed||!actor()||busy.value||!current.value||conflict.value)return false;
  const turn=++epoch,who=actor(),id=meeting();busy.value=true;error.value=null;message.value='';let submitted:string[]|null=null;
  const active=()=>!disposed&&turn===epoch&&who===actor()&&id===meeting();
  try{submitted=tags();const value=await api.saveConfiguration(id,{providerId:target.providerId,libraryId:target.libraryId,folderId:target.folderId,expectedConfigurationVersionToken:current.value.configurationVersionToken,tags:submitted});if(!active())return false;accept(value);return true;}
  catch(cause){
   if(!active())return false;
   // A lost PUT response may already have committed. Query before offering another write.
   if(submitted&&(!(cause instanceof ApiError)||cause.kind==='network'||[502,503,504].includes(cause.status))){
    try{controller=new AbortController();const value=await api.configuration(id,controller.signal);if(!active())return false;if(sameTarget(value.configuration,target)&&JSON.stringify(value.configuration?.tags)===JSON.stringify(submitted)){accept(value);message.value='已查回并确认发布配置保存成功';return true;}}catch{/* Preserve the exact local draft until the original result can be read. */}
   }
   if(cause instanceof ApiError&&cause.status===409){try{controller=new AbortController();const latest=await api.configuration(id,controller.signal);if(!active())return false;current.value=latest;conflict.value=true;}catch{/* A later explicit read can reconcile the retained draft. */}}
   if(active()){error.value=cause;persist();}return false;
  }finally{if(active())busy.value=false;}
 }
 function discard(){apply(savedText(current.value));baseVersion=current.value?.configurationVersionToken??null;conflict.value=false;error.value=null;journal?.clear();storageMessage.value='';}
 return{current,tagsText,error,busy,message,dirty,conflict,storageMessage,tags,load,save,clear,discard,keepDraft(){baseVersion=current.value?.configurationVersionToken??null;conflict.value=false;error.value=null;message.value='已保留本地输入，请核对位置后保存';persist();},dispose(){disposed=true;stop();stopDraft();clear();}};
}
