import {computed,onUnmounted,ref,watch,type Ref} from 'vue';
/** Variable-height window; measured heights and stable keys preserve the reading anchor. */
export function useTranscriptWindow<T extends {key:string}>(rows:Ref<T[]>,container:()=>HTMLElement|undefined,measurements={sizes:new Map<string,number>(),width:0}){
 const top=ref(0),height=ref(480),measureVersion=ref(0),width=ref(measurements.width),sizes=measurements.sizes,nodes=new Map<Element,string>();
 let observer:ResizeObserver|null=null,frame=0;
 const prefix=computed(()=>{void measureVersion.value;let sum=0;return [0,...rows.value.map(row=>(sum+=sizes.get(row.key)??96))];});
 const locate=(values:number[],offset:number)=>{let lo=0,hi=values.length-1;while(lo<hi){const mid=(lo+hi+1)>>>1;if(values[mid]!<=offset)lo=mid;else hi=mid-1;}return Math.min(lo,Math.max(0,values.length-2));};
 const range=computed(()=>rows.value.length<=80?{start:0,end:rows.value.length}:({start:locate(prefix.value,Math.max(0,top.value-600)),end:Math.min(rows.value.length,locate(prefix.value,top.value+height.value+600)+1)}));
 const visible=computed(()=>rows.value.slice(range.value.start,range.value.end));
 const before=computed(()=>prefix.value[range.value.start]??0),after=computed(()=>prefix.value.at(-1)!-(prefix.value[range.value.end]??0));
 function scroll(){const el=container();if(el){top.value=el.scrollTop;height.value=el.clientHeight;}}
 function observe(element:unknown,key:string){if(!(element instanceof Element))return;nodes.set(element,key);observer?.observe(element);}
 function measure(entries:ResizeObserverEntry[]){
  const el=container();if(!el)return;const old=prefix.value,anchor=locate(old,el.scrollTop),offset=el.scrollTop-old[anchor]!,atBottom=old.at(-1)!-el.scrollTop-el.clientHeight<40;let changed=false;
  for(const entry of entries){if(entry.target===el){if(width.value!==el.clientWidth){width.value=measurements.width=el.clientWidth;sizes.clear();changed=true;}height.value=el.clientHeight;continue;}const key=nodes.get(entry.target);if(!key)continue;const value=entry.borderBoxSize?.[0]?.blockSize??entry.target.getBoundingClientRect().height;if(value>0&&Math.abs((sizes.get(key)??96)-value)>.5){sizes.set(key,value);changed=true;}}
  if(changed){measureVersion.value++;if(rows.value.length>80){const next=prefix.value;el.scrollTop=atBottom?next.at(-1)!-el.clientHeight:(next[anchor]??0)+offset;}scroll();}
 }
 watch(container,(el,previous)=>{
  previous?.removeEventListener('scroll',scroll);observer?.disconnect();
  if(!el)return;observer=new ResizeObserver(measure);observer.observe(el);for(const node of nodes.keys())if(node.isConnected)observer.observe(node);else nodes.delete(node);el.addEventListener('scroll',scroll,{passive:true});scroll();
 },{flush:'post',immediate:true});
 // Disconnect recycled elements so long sessions do not retain detached nodes.
 watch(visible,()=>{if(frame)cancelAnimationFrame(frame);if(typeof requestAnimationFrame==='function')frame=requestAnimationFrame(()=>{for(const [node] of nodes)if(!node.isConnected){observer?.unobserve(node);nodes.delete(node);}});},{flush:'post'});
 onUnmounted(()=>{container()?.removeEventListener('scroll',scroll);observer?.disconnect();if(frame)cancelAnimationFrame(frame);nodes.clear();});
 return{visible,before,after,start:computed(()=>range.value.start),observe};
}
