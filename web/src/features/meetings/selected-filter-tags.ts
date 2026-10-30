/** The prototype hides complete overflow chips and reserves room for its ellipsis. */
export function fitSelectedTags(root:ParentNode):void {
 for(const menu of root.querySelectorAll<HTMLElement>('.m19-multi-selected')){
  const main=menu.querySelector<HTMLElement>('.m19-selected-main'),track=menu.querySelector<HTMLElement>('.m19-selected-track'),more=menu.querySelector<HTMLElement>('.m19-selected-more');if(!main||!track||!more)continue;
  const tags=[...track.children].filter((el):el is HTMLElement=>el instanceof HTMLElement);more.hidden=true;
  for(const tag of tags){tag.hidden=false;tag.style.maxWidth='';}
  const width=main.clientWidth,total=tags.reduce((sum,t)=>sum+t.getBoundingClientRect().width,0)+Math.max(0,tags.length-1)*4;if(total<=width)continue;
  more.hidden=false;const available=Math.max(0,width-more.getBoundingClientRect().width-4);let used=0,shown=0,overflow=false;
  for(const tag of tags){const w=tag.getBoundingClientRect().width,extra=shown?4:0;if(!overflow&&used+extra+w<=available){used+=extra+w;shown++;}else{overflow=true;tag.hidden=true;}}
  if(!shown&&tags.length){tags[0]!.hidden=false;tags[0]!.style.maxWidth=available+'px';}
 }
}
export function positionSelectedPopover(summary:HTMLElement):void {
 const popup=summary.querySelector<HTMLElement>('.m19-selected-popover');if(!popup)return;popup.style.transform='';const r=popup.getBoundingClientRect(),delta=r.left<12?12-r.left:r.right>window.innerWidth-12?window.innerWidth-12-r.right:0;popup.style.transform=`translateX(${delta}px)`;
}
