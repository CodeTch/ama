/** A literal, case-insensitive match shared by filtering and highlighting. */
export function transcriptSearch(query:string):RegExp|null {
 const term=query.trim();
 return term?new RegExp('('+term.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+')','iu'):null;
}
