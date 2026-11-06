import type {ExportFormat} from '../../shared/api/minutes-exports';
export interface MinutesReadingPosition {
 meeting:string;actor:string;stage:string;access:string;revision:string;
 view:'body'|'elements';scroll:number;coreScroll:number;
 source:{kind:'BODY'|'ELEMENT'|'SET';id:string;locatorId?:string}|null;
 exportFormat:ExportFormat|null;
}
