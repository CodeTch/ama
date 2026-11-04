type Row={segmentId:string;body:string;sourceFileId:string;startMs:number|null;speakerId:string|null};
type Label={id:string;label:string;segments:number;segmentIds:string[];enterpriseId:null;source:'FILE'};
const headings=new Set(['时间','日期','地点','主题','议题','会议','标题','备注','说明','结论','摘要','任务','负责人','网址','链接','注意','背景','内容']);
/** A conservative display projection only: the original body and confirmed identity never change. */
export function dialogueLabels(rows:readonly Row[]):{labels:Record<string,string>;items:Label[]}{
 const labels:Record<string,string>={},items=new Map<string,Label>();
 for(const row of rows){
  if(row.speakerId)continue;
  const match=/^\s*([\p{Script=Han}]{2,8}|[A-Z][a-z]+(?: [A-Z][a-z]+)?|说话人\s*\d+)\s*[：:]\s*(\S[\s\S]*)$/u.exec(row.body);
  if(!match||headings.has(match[1]!))continue;
  const label=match[1]!,id=JSON.stringify([row.sourceFileId,label]);labels[row.segmentId]=label;
  const found=items.get(id);if(found){found.segments++;found.segmentIds.push(row.segmentId);}else items.set(id,{id,label,segments:1,segmentIds:[row.segmentId],enterpriseId:null,source:'FILE'});
 }
 return {labels,items:[...items.values()]};
}

/** Older snapshots have no original label. Recover a unanimous source prefix when it exists. */
export function originalSpeakerLabel(group:{label:string;segmentIds:readonly string[]},rows:readonly Row[]):string{
 const ids=new Set(group.segmentIds);
 const selected=rows.filter(row=>ids.has(row.segmentId));
 const labels=dialogueLabels(selected.map(row=>({...row,speakerId:null}))).labels;
 const originals=new Set(selected.map(row=>labels[row.segmentId]));
 return originals.size===1&&!originals.has(undefined)?[...originals][0]!:group.label;
}

/** Unassigned segments remain distinct until the owner explicitly names or merges them. */
export function unassignedSpeakerGroups(rows:readonly Row[],known:Readonly<Record<string,string>>,offset=0){
 return rows.filter(row=>!row.speakerId&&!known[row.segmentId]).map((row,index)=>({id:'unassigned:'+row.segmentId,label:'说话人 '+(offset+index+1),enterpriseId:null,segments:1,segmentIds:[row.segmentId]}));
}
