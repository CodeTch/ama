type StoragePort=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
export type TabRecovery='NEW_NAMESPACE'|'RESUME_NAMESPACE'|'UNAVAILABLE';
export type TabJournalContext={tab:string;storage:StoragePort|null;recovery:TabRecovery};
/** Only reload resumes a prior namespace. This is a navigation branch, not proof of real-browser duplicate-tab classification. */
export function createTabJournalContext(getStorage:()=>StoragePort,navigationType:string):TabJournalContext {
 const fresh=crypto.randomUUID(),reload=navigationType==='reload';
 try{
  const storage=getStorage();
  if(reload){const prior=storage.getItem('ama.tab.v1');if(prior!==null&&prior.length===36&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(prior))return {tab:prior,storage,recovery:'RESUME_NAMESPACE'};return {tab:fresh,storage:null,recovery:'UNAVAILABLE'};}
  try{storage.setItem('ama.tab.v1',fresh);return {tab:fresh,storage,recovery:'NEW_NAMESPACE'};}catch{return {tab:fresh,storage:null,recovery:'NEW_NAMESPACE'};}
 }catch{return {tab:fresh,storage:null,recovery:reload?'UNAVAILABLE':'NEW_NAMESPACE'};}
}
