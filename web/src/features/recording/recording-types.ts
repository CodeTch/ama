export const RECORDING_BYTES=32*1024*1024,RECORDING_PARTS=16,RECORDING_CHUNKS=4096,RECORDING_MS=7200000;
export type PartState='RECORDING'|'PAUSED'|'SAVING'|'INTERRUPTED'|'SYSTEM_INTERRUPTED'|'SAVED';
export type RecordingPart={id:string;index:number;name:string;mime:string;state:PartState;elapsedMs:number;savedElapsedMs:number;bytes:number;chunks:string[];incomplete:boolean};
export type RecordingSession={schema:1;actor:string;meeting:string;finished:boolean;parts:RecordingPart[]};
export type RecordingStore={load(actor:string,meeting:string):Promise<RecordingSession|null>;save(record:RecordingSession):Promise<void>;append(record:RecordingSession,chunkId:string,blob:Blob):Promise<void>;file(record:RecordingSession,part:RecordingPart):Promise<File>;clear(actor:string,meeting:string):Promise<void>};
export type Capture={mime:string;state:()=> 'recording'|'paused'|'inactive';start():void;pause():void;resume():void;requestData():void;stop():Promise<void>;release():void};
export type CaptureFactory=(data:(blob:Blob)=>void,lost:()=>void,signal?:AbortSignal)=>Promise<Capture>;
