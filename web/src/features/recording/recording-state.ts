import {ApiError} from '../../shared/api/client';
export type RecordingFailure={stage:'initialize'|'device'|'cache'|'transport'|'seal';code:string;message:string;recoveryAction:'initialize'|'authorize'|'upload'|'reconcile'};
export function recordingFailure(cause:unknown,stage:RecordingFailure['stage']):RecordingFailure{
 const name=cause instanceof Error?cause.name:'';
 const deviceMessages:Record<string,string>={
  NotAllowedError:'麦克风权限未获允许，请在浏览器设置中允许麦克风后重新授权，也可导入音频文件。',
  NotFoundError:'未找到麦克风，请连接设备后重新授权，也可导入音频文件。',
  NotSupportedError:'当前浏览器或音频环境不支持录音，请更换支持的浏览器或导入音频文件。',
  NotReadableError:'麦克风被占用或无法读取，请关闭占用设备的应用后重新授权。',
  OverconstrainedError:'麦克风不支持当前采集设置，请更换设备或导入音频文件。',
 };
 const denied=cause instanceof ApiError&&[401,403,409].includes(cause.status);
 return{stage,code:cause instanceof ApiError?cause.code??String(cause.status):name||'UNKNOWN',message:deviceMessages[name]??(cause instanceof Error?cause.message:'录音操作未完成，请保留当前页面和本地音频'),recoveryAction:denied?'reconcile':stage==='initialize'?'initialize':stage==='device'?'authorize':'upload'};
}
