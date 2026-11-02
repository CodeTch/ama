import {watch} from 'vue';
/** Navigation refreshes do not themselves invalidate content. Preserve pending edits. */
export function useTranscriptActivation(active:()=>boolean,result:()=>string,blocked:()=>boolean,reload:()=>unknown){
 let pending=false;
 return watch(()=>[active(),result(),blocked()] as const,([visible,key,locked],[wasVisible,previous])=>{
  if(visible&&!wasVisible||key!==previous)pending=true;
  if(visible&&pending&&!locked){pending=false;void reload();}
 });
}
