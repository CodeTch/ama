import {ref,shallowRef,watch} from 'vue';
import type {createPublicationsApi} from '../../shared/api/publications';
import type {components} from '../../shared/api/generated';

type Configuration=components['schemas']['PublicationConfiguration'];
type Api=Pick<ReturnType<typeof createPublicationsApi>,'configuration'|'saveConfiguration'>;
export type PublicationTarget={providerId:string;libraryId:string;folderId:string};

export function usePublicationConfiguration(api:Api,actor:()=>string|null,meeting:()=>string){
 const current=shallowRef<Configuration|null>(null),tagsText=ref(''),error=shallowRef<unknown>(null),busy=ref(false),message=ref('');
 let epoch=0,disposed=false,controller:AbortController|null=null;
 function clear(){epoch++;controller?.abort();controller=null;current.value=null;tagsText.value='';error.value=null;busy.value=false;message.value='';}
 const stop=watch(()=>[actor(),meeting()],clear,{flush:'sync'});
 async function load(){
  if(disposed||!actor())return;const turn=++epoch,who=actor(),id=meeting();controller?.abort();controller=new AbortController();const request=controller;busy.value=true;error.value=null;
  try{const value=await api.configuration(id,request.signal);if(disposed||turn!==epoch||who!==actor()||id!==meeting())return;current.value=value;tagsText.value=value.configuration?.tags.join(', ')??'';return value;}
  catch(cause){if(!disposed&&turn===epoch)error.value=cause;}
  finally{if(!disposed&&turn===epoch)busy.value=false;}
 }
 function tags(){
  const rules=current.value?.tagRules;if(!rules)throw Error('标签规则尚未读取，请重试读取发布配置。');
  const result:string[]=[],seen=new Set<string>();
  for(const raw of tagsText.value.split(/[,，]/)){if(!raw.trim())continue;const tag=rules.trim?raw.trim():raw;
   if([...tag].length>rules.maxLength||/[\u0000-\u001f\u007f-\u009f]/.test(tag))throw Error(`每个标签最多 ${rules.maxLength} 个字符，不能包含控制字符。`);
   const key=rules.caseSensitive?tag:tag.toLowerCase();if(!seen.has(key)){seen.add(key);result.push(tag);}
  }
  if(result.length>rules.maxCount)throw Error(`最多可填写 ${rules.maxCount} 个标签。`);if(!result.length&&!rules.allowEmpty)throw Error('目标知识库要求至少填写一个标签。');return result;
 }
 async function save(target:PublicationTarget){
  if(disposed||!actor()||busy.value||!current.value)return false;
  const turn=++epoch,who=actor(),id=meeting();busy.value=true;error.value=null;message.value='';
  try{const value=await api.saveConfiguration(id,{providerId:target.providerId,libraryId:target.libraryId,folderId:target.folderId,expectedConfigurationVersionToken:current.value.configurationVersionToken,tags:tags()});if(disposed||turn!==epoch||who!==actor()||id!==meeting())return false;current.value=value;tagsText.value=value.configuration?.tags.join(', ')??'';message.value='发布配置已保存';return true;}
  catch(cause){if(!disposed&&turn===epoch)error.value=cause;return false;}
  finally{if(!disposed&&turn===epoch)busy.value=false;}
 }
 return{current,tagsText,error,busy,message,tags,load,save,clear,dispose(){disposed=true;stop();clear();}};
}
