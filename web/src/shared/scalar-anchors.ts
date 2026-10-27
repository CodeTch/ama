/** Browser selection offsets are UTF16 indexes; evidence anchors are exact Unicode scalar positions. */
function validate(text:string){for(let i=0;i<text.length;i++){const c=text.charCodeAt(i);if(c>=0xd800&&c<=0xdbff){const low=text.charCodeAt(++i);if(!(low>=0xdc00&&low<=0xdfff))throw new TypeError('Unpaired surrogate');}else if(c>=0xdc00&&c<=0xdfff)throw new TypeError('Unpaired surrogate');}}
export function scalarOffset(text:string,offset:number):string {
 validate(text);if(!Number.isSafeInteger(offset)||offset<0||offset>text.length)throw new RangeError('Invalid selection');let count=0;
 for(let i=0;i<offset;count++){const width=text.codePointAt(i)!>0xffff?2:1;if(i+width>offset)throw new RangeError('Selection splits a scalar');i+=width;}return String(count);
}
export function utf16Offset(text:string,scalar:string):number {
 validate(text);if(!/^(0|[1-9][0-9]{0,18})$/.test(scalar)||BigInt(scalar)>BigInt(text.length))throw new RangeError('Invalid scalar offset');
 // This bounded conversion is a text selection index only. Revision/CAS tokens never enter this adapter.
 const wanted=Number(BigInt(scalar));let count=0,index=0;for(;index<text.length&&count<wanted;count++)index+=text.codePointAt(index)!>0xffff?2:1;
 if(count!==wanted)throw new RangeError('Scalar offset exceeds text');return index;
}
export function uniqueAnchor(text:string,quote:string):{start:string;end:string}|null {
 validate(text);validate(quote);if(!quote)return null;const first=text.indexOf(quote);if(first<0||text.indexOf(quote,first+1)>=0)return null;return {start:scalarOffset(text,first),end:scalarOffset(text,first+quote.length)};
}
