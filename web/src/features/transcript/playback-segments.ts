import type {TranscriptSegment} from '../../shared/api/transcripts';

export type PlaybackPosition = {fileId:string;ms:number;playing:boolean};
type SegmentTime = Pick<TranscriptSegment,'segmentId'|'sourceFileId'|'startMs'|'endMs'>;
type Interval = {id:string;start:number;end:number};

/** Ordered intervals allow fast seeks. For imported, out-of-order timings keep transcript order as the tie breaker. */
export function playbackSegments(segments:readonly SegmentTime[]) {
 const files=new Map<string,{ordered:boolean;rows:Interval[]}>();
 for(const segment of segments){
  if(segment.startMs===null||segment.endMs===null||segment.endMs<=segment.startMs)continue;
  let file=files.get(segment.sourceFileId);
  if(!file){file={ordered:true,rows:[]};files.set(segment.sourceFileId,file);}
  const previous=file.rows.at(-1);
  if(previous&&(previous.start>segment.startMs||previous.end>segment.endMs))file.ordered=false;
  file.rows.push({id:segment.segmentId,start:segment.startMs,end:segment.endMs});
 }
 return (position:PlaybackPosition|null)=>{
  if(!position?.playing)return;
  const file=files.get(position.fileId);
  if(!file)return;
  const {ms}=position;
  if(!file.ordered)return file.rows.find(row=>row.start<=ms&&ms<row.end)?.id;
  let low=0,high=file.rows.length;
  while(low<high){
   const middle=Math.floor((low+high)/2);
   if(file.rows[middle]!.end<=ms)low=middle+1;
   else high=middle;
  }
  const row=file.rows[low];
  return row&&row.start<=ms?row.id:undefined;
 };
}
