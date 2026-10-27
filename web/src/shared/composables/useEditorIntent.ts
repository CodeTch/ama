import {shallowRef,toRef} from 'vue';
import {useCommand,type CommandSnapshot} from './useCommand';
import {ApiError} from '../api/client';
import {intentAck,revisionToken} from '../api/intent-contract';
import type {components} from '../api/generated';
type IntentBody=components['schemas']['SavedIntentCommand'];
export type IntentScope={actor:string;meetingId:string;session:object};

/** Instance-local authority; neither persisted journals nor observation readers can set ACK. */
export function useEditorIntent(scope:()=>IntentScope|null,frozen:()=>boolean,post:(request:CommandSnapshot)=>Promise<unknown>,newKey:()=>string=()=>crypto.randomUUID()) {
 let held:IntentScope|null=null,base:string|null=null;const acknowledged=shallowRef(false);
 const composing=shallowRef(false);let authorityEpoch=0,preparedEpoch=-1,invalidated=false;
 const same=()=>{const now=scope();return now!==null&&held!==null&&now.actor===held.actor&&now.meetingId===held.meetingId&&now.session===held.session;};
 const command=useCommand(async request=>{
  const response=await post(request);
  if(!intentAck(response,request.resource))throw new ApiError('response',200,'编辑确认响应不可用，请重试原请求。');
  return response;
 });
 function revokePermission(){if(held&&!invalidated){authorityEpoch++;invalidated=true;}acknowledged.value=false;}
 const canEdit=toRef(()=>{const permitted=acknowledged.value;if(!same()||frozen()){revokePermission();return false;}return permitted;});
 function invalidate(){authorityEpoch++;invalidated=false;acknowledged.value=false;held=null;base=null;composing.value=false;command.invalidate();}
 async function request(expectedRevisionToken:string) {
  const now=scope();if(!now||frozen()){revokePermission();throw new Error('Editor unavailable');}
  if(!revisionToken(expectedRevisionToken))throw new TypeError('Invalid revision token');
  if(held&&!same()||invalidated&&command.state.value==='ACKNOWLEDGED')invalidate();
  if(base!==null&&base!==expectedRevisionToken)throw new Error('Reconcile existing intent before changing its base');
  if(command.state.value==='EMPTY') {
   held={...now};base=expectedRevisionToken;preparedEpoch=authorityEpoch;
   const body:IntentBody={expectedRevisionToken};command.prepare({operation:'EDIT_INTENT',actor:now.actor,resource:now.meetingId,path:`/api/v1/meetings/${encodeURIComponent(now.meetingId)}/minutes/editor-intents`,key:newKey(),body});
  }
  if(canEdit.value)return;
  const grantingEpoch=preparedEpoch;await command.send();if(grantingEpoch!==authorityEpoch||!same()||frozen())throw new DOMException('Editor scope changed','AbortError');acknowledged.value=true;
 }
 function guardInput(event:Event){if(!canEdit.value)event.preventDefault();}
 function beginComposition(){if(!canEdit.value)return false;composing.value=true;return true;}
 function endComposition(){composing.value=false;}
 function reconcileConflict(){if(command.state.value!=='CONFLICT')throw new Error('No intent conflict');invalidate();}
 return {state:command.state,canEdit,canAutosave:toRef(()=>canEdit.value&&!composing.value),request,invalidate,reconcileConflict,guardInput,beginComposition,endComposition};
}
