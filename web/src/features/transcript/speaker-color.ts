const colors=['#3f64cc','#40865b','#936421','#7950aa','#ad4d59','#337d85'];

/** The first assigned segment keeps the same color when a speaker is renamed. */
export function speakerColor(key:string):string {
 let hash=0;
 for(const character of key)hash=(Math.imul(hash,31)+character.codePointAt(0)!)>>>0;
 return colors[hash%colors.length]!;
}
