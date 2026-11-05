import type {components} from '../../shared/api/generated';
import type {createUploadsApi} from '../../shared/api/uploads';

type FileItem=components['schemas']['CompletedFileSummary'];
type InputFile=components['schemas']['InputDetail']['files'][number];

/** Read the ordered receipts with bounded concurrency. Publish only a completely verified batch. */
export async function lockedImportFiles(source:readonly InputFile[],receipt:ReturnType<typeof createUploadsApi>['receipt'],meeting:string,signal:AbortSignal){
 const files:FileItem[]=new Array(source.length);
 const group=new AbortController(),active=AbortSignal.any([signal,group.signal]);
 let next=0;
 async function read(){
  while(next<source.length){
   active.throwIfAborted();
   const index=next++,expected=source[index]!;
   const {completed}=await receipt(meeting,expected.uploadId,active);
   if(!completed||completed.uploadId!==expected.uploadId||completed.fileId!==expected.fileId||completed.sha256!==expected.sha256
      ||completed.sizeBytesToken!==expected.sizeBytesToken||completed.originalPurpose!=='TRANSCRIPT_INPUT')
    throw Error('任务锁定文件与原上传结果不匹配');
   files[index]=completed;
  }
 }
 try{await Promise.all(Array.from({length:Math.min(4,source.length)},read));}
 catch(cause){group.abort();throw cause;}
 signal.throwIfAborted();
 return files;
}
