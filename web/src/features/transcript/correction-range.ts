/** Locate the contiguous changed region in an existing whole-paragraph suggestion. */
export function correctionRange(source:string,replacement:string) {
 const before=Array.from(source),after=Array.from(replacement);
 let start=0;
 while(start<before.length&&start<after.length&&before[start]===after[start])start++;
 let end=before.length,replacementEnd=after.length;
 while(end>start&&replacementEnd>start&&before[end-1]===after[replacementEnd-1]){
  end--;
  replacementEnd--;
 }
 return {
  prefix:before.slice(0,start).join(''),
  original:before.slice(start,end).join(''),
  suggestion:after.slice(start,replacementEnd).join(''),
  suffix:before.slice(end).join('')
 };
}
