/** Names select an advertised intake format; original byte signatures are verified by the Worker parser. */
export const DOCUMENT_FORMATS=['TXT','DOC','DOCX','PDF','PPTX'] as const;
export type DocumentFormat=typeof DOCUMENT_FORMATS[number];
export function documentFormat(name:string):DocumentFormat|null{const extension=name.match(/\.([a-z0-9]+)$/i)?.[1]?.toUpperCase();return DOCUMENT_FORMATS.find(format=>format===extension)??null;}

export const MEDIA_FORMATS=['MP3','WAV','M4A','MP4','MOV','MKV','WEBM','AAC','FLAC','OGG','M4V','AVI','SRT','VTT'] as const;
export const IMPORT_FORMATS=[...DOCUMENT_FORMATS,...MEDIA_FORMATS] as const;
export type ImportFormat=typeof IMPORT_FORMATS[number];
export function importFormat(name:string):ImportFormat|null{const extension=name.match(/\.([a-z0-9]+)$/i)?.[1]?.toUpperCase();return IMPORT_FORMATS.find(format=>format===extension)??null;}
