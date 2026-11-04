import {nextTick,readonly,ref,watch,type Ref} from 'vue';
/** Preserve the reader's position; scroll only after Vue commits new captions. */
export function useTranscriptFollow(container:Ref<HTMLElement|undefined>,changeToken:()=>unknown){
 const following=ref(true);
 function onScroll(){const el=container.value;if(el)following.value=el.scrollHeight-el.clientHeight-el.scrollTop<=40;}
 async function bottom(){await nextTick();const el=container.value;if(el&&following.value)el.scrollTo({top:el.scrollHeight,behavior:'instant'});}
 watch(changeToken,()=>{void bottom();},{flush:'post'});
 async function returnToLatest(refresh?:()=>Promise<unknown>){following.value=true;if(refresh)await refresh();await bottom();}
 return{following:readonly(following),onScroll,returnToLatest};
}
