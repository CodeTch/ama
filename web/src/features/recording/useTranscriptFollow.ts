import {nextTick,readonly,ref,watch,type Ref} from 'vue';
/** Preserve the reader's position; scroll only after Vue commits new captions. */
export function useTranscriptFollow(container:Ref<HTMLElement|undefined>,changeToken:()=>unknown,reading={following:true,scrollTop:0}){
 const following=ref(reading.following);
 watch(()=>reading.following,value=>{following.value=value;},{flush:'sync'});
 function onScroll(){const el=container.value;if(el){following.value=el.scrollHeight-el.clientHeight-el.scrollTop<=40;reading.following=following.value;reading.scrollTop=el.scrollTop;}}
 async function bottom(){await nextTick();const el=container.value;if(el&&following.value)el.scrollTo({top:el.scrollHeight,behavior:'instant'});}
 watch(changeToken,()=>{void bottom();},{flush:'post'});
 watch(container,async el=>{if(!el)return;await nextTick();if(following.value)await bottom();else el.scrollTo({top:reading.scrollTop,behavior:'instant'});},{flush:'post'});
 async function returnToLatest(refresh?:()=>Promise<unknown>){following.value=true;reading.following=true;await bottom();if(refresh)await refresh();await bottom();}
 return{following:readonly(following),onScroll,returnToLatest};
}
