import {computed,shallowRef,watch,onScopeDispose} from 'vue';
import type {Source} from '../../shared/api/minutes-inspection';
import type {TranscriptSource} from '../../shared/api/transcripts';
import type {MaterialListing} from '../../shared/api/materials';
import {ApiError} from '../../shared/api/client';
import {useSourceMedia,type MediaScope,type SourceReader} from '../transcript/useSourceMedia';
type Ports={source:SourceReader;materials?:(meeting:string,signal:AbortSignal)=>Promise<MaterialListing>};
const forbidden=(cause:unknown)=>cause instanceof ApiError&&[401,403].includes(cause.status??0);
/** Resolve only the immutable segment/file declared by this source. Byte validation stays in the existing transcript reader. */
export function useMinutesSourceMedia(ports:Ports,actor:()=>string|null,meeting:()=>string,active:()=>boolean,source:()=>Source|null,denied:(cause:unknown)=>void,urls:Pick<typeof URL,'createObjectURL'|'revokeObjectURL'>=URL){
 const mediaScope=shallowRef<MediaScope|null>(null),plainName=shallowRef<string|null>(null),startMs=shallowRef<number|null>(null),error=shallowRef<unknown>(null),loading=shallowRef(false);let epoch=0,controller:AbortController|null=null;
 function clear(){epoch++;controller?.abort();controller=null;mediaScope.value=null;plainName.value=null;startMs.value=null;error.value=null;loading.value=false;}
 const media=useSourceMedia(async(id,file,signal)=>{const who=actor();try{return await ports.source(id,file,signal);}catch(cause){if(!signal.aborted&&active()&&who===actor()&&id===meeting()&&forbidden(cause)){clear();denied(cause);}throw cause;}},actor,()=>mediaScope.value,urls);
 function identity(){const s=source();return JSON.stringify([actor(),meeting(),active(),s?[s.locator.id,s.locator.kind,s.sourceVersion,s.locator.transcript_revision_id,s.locator.segment_id,s.locator.file_id,s.locator.material_revision_id,s.originalBytes.route,s.observation.revisionToken,s.observation.accessRevisionToken]:null]);}
 async function reload(){
  clear();const who=actor(),id=meeting(),saved=source();if(!who||!active()||!saved)return;
  const version=saved.sourceVersion,key=identity(),turn=epoch,c=new AbortController();controller=c;loading.value=true;
  const accept=()=>turn===epoch&&!c.signal.aborted&&key===identity();
  try{
   if(saved.locator.kind!=='TRANSCRIPT'){
    if(!ports.materials)return;
    const listing=await ports.materials(id,c.signal);if(!accept())return;
    const item=listing.items.find(item=>item.materialRevisionId===version&&saved.originalBytes.route===`/api/v1/files/${item.fileId}/content`&&(saved.locator.file_id===null||saved.locator.file_id===item.fileId));
    if(listing.meetingRevision!==saved.observation.revisionToken||!item||saved.locator.material_revision_id!==null&&saved.locator.material_revision_id!==version)throw Error('来源材料的文件名与版本暂无法核实，请重新打开核对。');
    plainName.value=item.name;return;
   }
   if(version!==saved.locator.transcript_revision_id)throw Error('来源版本无法核实，请重新打开来源。');
   const segment=saved.transcript;
   if(!segment||segment.revisionId!==version||segment.segmentId!==saved.locator.segment_id||saved.locator.file_id!==null&&saved.locator.file_id!==segment.fileId)throw Error('来源文件与已保存段落不一致。');
   const expectedRoute=`/api/v1/meetings/${id}/transcript/${saved.historical?`revisions/${version}/`:''}sources/${segment.fileId}`;
   const size=Number(segment.sizeBytesToken),ordinal=Number(segment.ordinalToken);
   if(saved.originalBytes.route!==expectedRoute||!Number.isSafeInteger(size)||size<0||!Number.isSafeInteger(ordinal)||ordinal<0)throw Error('来源原件当前不可读取。');
   const original:TranscriptSource={fileId:segment.fileId,sha256:segment.sha256,sizeBytes:size,revisionId:saved.historical?version:null,recordingWav:segment.parserVersion==='ama.recording-wav.v1'};
   if(segment.startMs===null){const result=await ports.source(id,original,c.signal);if(accept())plainName.value=result.name;return;}
   if(!Number.isSafeInteger(segment.startMs)||segment.startMs<0||segment.endMs===null||segment.endMs<segment.startMs)throw Error('来源时间无法核实。');
   startMs.value=segment.startMs;mediaScope.value={meeting:id,revisionId:version,accessRevisionToken:saved.observation.accessRevisionToken,sources:[{...original,ordinal}]};
  }catch(cause){if(accept()){if(forbidden(cause)){clear();denied(cause);}else error.value=cause;}}finally{if(accept())loading.value=false;}
 }
 const stop=watch(identity,()=>{void reload();},{immediate:true,flush:'sync'});
 onScopeDispose(()=>{stop();clear();});
 return{media,startMs,error,loading,fileName:computed(()=>media.name.value??plainName.value),playable:computed(()=>!!actor()&&active()&&startMs.value!==null&&!loading.value&&!media.busy.value&&!error.value&&!!media.url.value&&/^(audio|video)\//.test(media.type.value)),reload};
}
