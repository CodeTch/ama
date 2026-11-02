import {watch,type Ref} from 'vue';
const owners=new WeakMap<HTMLElement,symbol>();
/** The visible step footer owns the space reserved in its own scroll container. */
export function useFixedDockHeight(dock:Ref<HTMLElement|undefined>){
 watch(dock,(element,_old,cleanup)=>{
  const host=element?.closest<HTMLElement>('.content');if(!element||!host)return;
  const owner=Symbol();let disposed=false,claimed=false;
  const measure=()=>{if(disposed||!element.isConnected)return;const height=element.getBoundingClientRect().height;if(height<=0){if(owners.get(host)===owner){host.style.removeProperty('--acf-height');owners.delete(host);}claimed=false;return;}if(!claimed){owners.set(host,owner);claimed=true;}if(owners.get(host)===owner)host.style.setProperty('--acf-height',height+'px');};
  measure();const observer=typeof ResizeObserver==='undefined'?null:new ResizeObserver(measure);observer?.observe(element);
  cleanup(()=>{disposed=true;observer?.disconnect();if(owners.get(host)===owner){host.style.removeProperty('--acf-height');owners.delete(host);}});
 },{immediate:true,flush:'post'});
}
