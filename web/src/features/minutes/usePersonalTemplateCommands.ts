import {computed,shallowReadonly,shallowRef} from 'vue';
import {useCommand} from '../../shared/composables/useCommand';
import {createScopedPrivateJournal} from '../../shared/composables/scoped-private-journal';
import type {TabJournalContext} from '../../shared/composables/tab-journal';
import {PERSONAL_TEMPLATE_WORKSPACE,validPersonalTemplateCommand,type PersonalTemplate,type PersonalTemplatePage,type createPersonalTemplatesApi} from '../../shared/api/personal-templates';
import {ApiError} from '../../shared/api/client';

/** One private original template mutation per tab. Applying it to a meeting is a separate command. */
export function usePersonalTemplateCommands(api:Pick<ReturnType<typeof createPersonalTemplatesApi>,'send'|'list'>,actor:()=>string|null,context:TabJournalContext){
 const journal=createScopedPrivateJournal(context,'personal-templates',validPersonalTemplateCommand);
 const page=shallowRef<PersonalTemplatePage|null>(null),available=shallowRef(false),reading=shallowRef(false),concealed=shallowRef(false);
 let owner:string|null=null,epoch=0,read:AbortController|null=null;
 const command=useCommand<PersonalTemplate>(async original=>{
  if(!owner||owner!==actor()||!validPersonalTemplateCommand(original,owner,PERSONAL_TEMPLATE_WORKSPACE))throw new DOMException('Template scope changed','AbortError');
  return api.send(original);
 },131072);
 const pending=computed(()=>command.snapshot.value!==null),busy=computed(()=>reading.value||command.state.value==='SENDING');
 const original=computed<Record<string,unknown>|null>(()=>!concealed.value&&command.snapshot.value?JSON.parse(command.snapshot.value.body):null);
 const active=(turn:number)=>turn===epoch&&owner!==null&&owner===actor();
 function dispose(){epoch++;read?.abort();read=null;owner=null;page.value=null;available.value=false;reading.value=false;command.invalidate();}
 function synchronize(){
  dispose();const who=actor();if(!who)return;owner=who;concealed.value=false;
  const found=journal.load(who,PERSONAL_TEMPLATE_WORKSPACE);
  if(found.kind==='INVALID'||found.kind==='UNAVAILABLE')throw new Error('模板恢复记录暂不可用，请保留页面重试。');
  available.value=true;if(found.kind==='RESTORED')command.restore(found.value);
 }
 function denial(cause:unknown,turn:number){if(active(turn)&&cause instanceof ApiError&&cause.kind==='http'&&[401,403].includes(cause.status)){concealed.value=true;page.value=null;available.value=false;}}
 async function reload(cursor:string|null=null){
  const turn=epoch;if(!active(turn)||!available.value||busy.value)return;
  read?.abort();const request=new AbortController();read=request;reading.value=true;
  try{const current=await api.list(cursor,request.signal);if(!active(turn)||request.signal.aborted)throw new DOMException('Template scope changed','AbortError');page.value=current;}
  catch(cause){denial(cause,turn);throw cause;}finally{if(active(turn))reading.value=false;}
 }
 async function retry(){
  const turn=epoch,snapshot=command.snapshot.value;
  if(!active(turn)||!available.value||!snapshot||busy.value||!['PREPARED','UNCERTAIN','ACKNOWLEDGED'].includes(command.state.value))throw new Error('请先核对原模板操作和当前身份。');
  if(journal.save(owner!,PERSONAL_TEMPLATE_WORKSPACE,snapshot)!=='SAVED')throw new Error('模板原请求无法保存，请保留页面重试。');
  try{
   const receipt=command.state.value==='ACKNOWLEDGED'?command.ack.value:await command.send();
   if(!active(turn)||!receipt)throw new DOMException('Template scope changed','AbortError');
   reading.value=true;const request=new AbortController();read=request;const current=await api.list(null,request.signal);
   if(!active(turn)||request.signal.aborted)throw new DOMException('Template scope changed','AbortError');
   page.value=current;if(!journal.clear())throw new Error('模板已受理，恢复记录尚未清理，请重新核实。');
   command.invalidate();return receipt;
  }catch(cause){denial(cause,turn);throw cause;}finally{if(active(turn))reading.value=false;}
 }
 async function prepare(operation:string,resource:string,path:string,body:unknown){
  if(!active(epoch)||!available.value||busy.value||pending.value)throw new Error('请先处理原模板操作。');
  command.prepare({operation,resource,path,actor:owner!,key:crypto.randomUUID(),body});
  if(!validPersonalTemplateCommand(command.snapshot.value,owner!,PERSONAL_TEMPLATE_WORKSPACE)){command.invalidate();throw new Error('请核对模板名称、1–8 个主题和版本。');}
  return retry();
 }
 return {command,pending,busy,original,page:shallowReadonly(page),available:shallowReadonly(available),synchronize,reload,retry,dispose,
  create:(name:string,themes:readonly string[])=>prepare('PERSONAL_TEMPLATE_CREATE',PERSONAL_TEMPLATE_WORKSPACE,'/api/v1/templates',{name,themes}),
  update:(id:string,expectedRevisionToken:string,name:string,themes:readonly string[])=>prepare('PERSONAL_TEMPLATE_UPDATE',id,`/api/v1/templates/${id}`,{expectedRevisionToken,name,themes}),
  remove:(id:string,expectedRevisionToken:string)=>prepare('PERSONAL_TEMPLATE_DELETE',id,`/api/v1/templates/${id}`,{expectedRevisionToken}),
  discardRejected(){if(!['CONFLICT','REJECTED'].includes(command.state.value))throw new Error('不能放弃结果未知的模板操作。');if(!journal.clear())throw new Error('模板恢复记录暂无法清理。');command.invalidate();},
  logout(){const cleared=journal.clear();dispose();return cleared;},
 };
}
