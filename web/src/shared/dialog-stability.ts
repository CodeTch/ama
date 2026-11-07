/** Native dialogs share one background scroll lock, including nested dialogs. */
export function installDialogStability(){
 let locked:{element:HTMLElement;overflow:string;top:number;left:number}[]=[];
 let focus:HTMLElement|null=null;
 function sync(){
  const open=!!document.querySelector('dialog[open]');
  if(open&&!locked.length){
   focus=document.activeElement instanceof HTMLElement?document.activeElement:null;
   locked=[document.documentElement,...document.querySelectorAll<HTMLElement>('.workspace-shell .layout')].map(element=>({element,overflow:element.style.overflow,top:element.scrollTop,left:element.scrollLeft}));
   for(const entry of locked)entry.element.style.overflow='hidden';
  }else if(!open&&locked.length){
   const previous=locked;locked=[];
   for(const entry of previous){entry.element.style.overflow=entry.overflow;entry.element.scrollTop=entry.top;entry.element.scrollLeft=entry.left;}
   // The browser owns dialog focus restoration. Do not focus a control inside a closed dialog.
   if(focus?.isConnected&&!focus.closest('dialog'))focus.focus({preventScroll:true});focus=null;
  }
 }
 const observer=new MutationObserver(records=>{
  if(records.some(record=>record.type==='attributes'&&record.target instanceof HTMLDialogElement||record.type==='childList'&&[...record.addedNodes,...record.removedNodes].some(node=>node instanceof Element&&(node instanceof HTMLDialogElement||node.querySelector('dialog[open]')))))sync();
 });
 observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['open']});sync();
 return ()=>{observer.disconnect();for(const entry of locked)entry.element.style.overflow=entry.overflow;locked=[];};
}
