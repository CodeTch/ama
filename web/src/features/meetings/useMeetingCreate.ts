import {shallowReadonly,shallowRef,watch} from 'vue';
import {useCommand,type CommandSnapshot} from '../../shared/composables/useCommand';
import {createJournal} from '../../shared/composables/journal';
import {fitsUtf8} from '../../shared/bounded-text';
import type {MeetingMutation} from '../../shared/api/meeting-info';
import type {TabRecovery} from '../../shared/composables/tab-journal';
type StoragePort=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
type StorageStatus='EMPTY'|'RESTORED'|'SAVED'|'INVALID'|'UNAVAILABLE'|'CLEARED';
/** Collection-scoped creation journal. A restored command has no ACK or editing authority. */
export function useMeetingCreate(send:(request:CommandSnapshot)=>Promise<MeetingMutation>,actor:()=>string|null,storage:StoragePort|null,tab:string,recovery:TabRecovery='RESUME_NAMESPACE'){
 if(!/^[-a-zA-Z0-9_]{1,128}$/.test(tab))throw new TypeError('Invalid tab namespace');
 const command=useCommand(send),storageStatus=shallowRef<StorageStatus>(storage?'EMPTY':'UNAVAILABLE'),cleanupFailed=shallowRef(false),recoveryBlocked=shallowRef(false);
 const ownerKey='ama.create-owner.v1:'+tab;
 let owner:string|null=null,ownerState:'EMPTY'|'KNOWN'|'INVALID'|'UNAVAILABLE'='EMPTY';
 let pending:CommandSnapshot|null=null,disposed=false,logoutBlocked=false;
 let namespaceUnused=recovery==='NEW_NAMESPACE',cleanupRequired=false;
 function valid(value:unknown,current:string):value is CommandSnapshot {
  if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).sort().join(',')!=='actor,body,key,operation,path,resource')return false;
  return 'actor' in value&&value.actor===current&&'operation' in value&&value.operation==='CREATE'&&'resource' in value&&value.resource==='meetings'&&'path' in value&&value.path==='/api/v1/meetings'&&'body' in value&&value.body==='{}'&&'key' in value&&typeof value.key==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.key);
 }
 function journal(current:string){if(!storage)throw new Error('Storage unavailable');return createJournal(storage,{actor:current,meeting:'meetings',kind:'create-command',tab},(v):v is CommandSnapshot=>valid(v,current),16384);}
 // One bounded ownership reference allows cold logout/actor-change cleanup without reading old payloads or scanning storage.
 function readOwner(){
  if(!storage){ownerState=namespaceUnused&&owner===null?'EMPTY':'UNAVAILABLE';return;}
  try{const raw=storage.getItem(ownerKey);if(raw===null){ownerState=owner===null?'EMPTY':'INVALID';return;}namespaceUnused=false;if(!fitsUtf8(raw,4096)){ownerState='INVALID';return;}const value:unknown=JSON.parse(raw);
   if(typeof value!=='object'||value===null||Array.isArray(value)||Object.keys(value).sort().join(',')!=='actor,schema'||!('schema' in value)||value.schema!==1||!('actor' in value)||typeof value.actor!=='string'||!fitsUtf8(value.actor,1020)||[...value.actor].length>255||!value.actor.trim()){ownerState='INVALID';return;}
   if(owner!==null&&owner!==value.actor){ownerState='INVALID';return;}
   owner=value.actor;ownerState='KNOWN';
  }catch{ownerState=namespaceUnused&&owner===null?'EMPTY':'UNAVAILABLE';}
 }
 function clearStored(){
  if(ownerState==='INVALID'||ownerState==='UNAVAILABLE'){cleanupFailed.value=true;return false;}
  if(!owner){cleanupFailed.value=false;return true;}
  const cleared=journal(owner).clear();storageStatus.value=cleared.kind;if(cleared.kind==='UNAVAILABLE'){cleanupFailed.value=true;return false;}
  try{storage!.removeItem(ownerKey);}catch{storageStatus.value='UNAVAILABLE';cleanupFailed.value=true;return false;}
  owner=null;ownerState='EMPTY';cleanupFailed.value=false;return true;
 }
 function activate(current:string|null){
  if(command.snapshot.value)pending=command.snapshot.value;command.invalidate();if(!current)return;
  logoutBlocked=false;recoveryBlocked.value=false;
  if((pending&&!valid(pending,current))||(owner&&owner!==current))cleanupRequired=true;
  if(cleanupRequired){pending=null;if(!clearStored()){recoveryBlocked.value=true;return;}cleanupRequired=false;}
  if(pending){command.restore(pending);return;}
  if(recovery==='UNAVAILABLE'||ownerState==='INVALID'||ownerState==='UNAVAILABLE'){storageStatus.value=ownerState==='INVALID'?'INVALID':'UNAVAILABLE';recoveryBlocked.value=true;return;}
  if(!storage){storageStatus.value='UNAVAILABLE';return;}
  const found=journal(current).load();storageStatus.value=found.kind;
  if(found.kind==='EMPTY'&&owner===current){storageStatus.value='INVALID';recoveryBlocked.value=true;return;}
  if(found.kind==='RESTORED'){
   if(owner!==current){storageStatus.value='INVALID';recoveryBlocked.value=true;return;}
   pending=found.value;command.restore(found.value);
  }else if(found.kind==='INVALID'||found.kind==='UNAVAILABLE')recoveryBlocked.value=!(found.kind==='UNAVAILABLE'&&namespaceUnused&&owner===null);
 }
 readOwner();const stop=watch(actor,activate,{immediate:true,flush:'sync'});
 async function submit(){
  const current=actor();if(disposed||logoutBlocked||!current)throw new Error('Active session required');
  if(command.state.value==='ACKNOWLEDGED'){if(!command.ack.value)throw new Error('Missing create ACK');return command.ack.value;}
  if(command.state.value==='EMPTY'){
   if(recoveryBlocked.value)throw new Error('Resolve pending command recovery before creating another meeting');
   namespaceUnused=false;
   command.prepare({actor:current,operation:'CREATE',resource:'meetings',path:'/api/v1/meetings',key:crypto.randomUUID(),body:{}});pending=command.snapshot.value;if(!pending)throw new Error('Missing command');
   storageStatus.value='UNAVAILABLE';
   if(storage){try{storage.setItem(ownerKey,JSON.stringify({schema:1,actor:current}));owner=current;ownerState='KNOWN';storageStatus.value=journal(current).save(pending).kind;}catch{/* Known empty state may degrade to this exact in-memory command; never overwrite after a failed recovery read. */}}
   if(storageStatus.value==='INVALID')throw new Error('Pending command cannot be stored');
  }
  return command.send();
 }
 function retryRecovery(){if(disposed||command.state.value!=='EMPTY')return;readOwner();activate(actor());}
 function navigationComplete(){if(command.state.value!=='ACKNOWLEDGED')throw new Error('Create acceptance unresolved');if(!clearStored())return false;pending=null;command.invalidate();return true;}
 function logout(){logoutBlocked=true;pending=null;command.invalidate();cleanupRequired=!clearStored();recoveryBlocked.value=cleanupRequired;}
 function dispose(){disposed=true;stop();pending=null;command.invalidate();}
 return {command,storageStatus:shallowReadonly(storageStatus),cleanupFailed:shallowReadonly(cleanupFailed),recoveryBlocked:shallowReadonly(recoveryBlocked),submit,retryRecovery,navigationComplete,logout,dispose};
}
