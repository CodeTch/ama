import {createResampler} from './resample';
/** At most one 2s chunk and one worklet block remain in memory when persistence fails. */
export function createPcmPipeline(inputRate:number,write:(pcm:ArrayBuffer)=>Promise<void>){
 if(!Number.isFinite(inputRate)||inputRate<8000||inputRate>192000)throw new TypeError('不支持的麦克风采样率');
 const resampler=createResampler(inputRate),buffer=new Int16Array(32000);let used=0,total=0,queue:Int16Array<ArrayBufferLike>=new Int16Array(0),offset=0,blocked=false,busy=false,flushing=false,pending:ArrayBuffer|null=null;
 async function commit(){if(!pending)return;try{await write(pending.slice(0));pending=null;used=0;}catch(cause){blocked=true;throw cause;}}
 function snapshot(){const bytes=new ArrayBuffer(used*2),view=new DataView(bytes);for(let i=0;i<used;i++)view.setInt16(i*2,buffer[i]!,true);pending=bytes;}
 async function drain(){await commit();while(offset<queue.length){const count=Math.min(32000-used,queue.length-offset);buffer.set(queue.subarray(offset,offset+count),used);used+=count;offset+=count;if(used===32000){snapshot();await commit();}}queue=new Int16Array(0);offset=0;if(flushing&&used){snapshot();await commit();}if(flushing)flushing=false;}
 async function ordered(work:()=>Promise<void>){if(busy)throw new Error('麦克风分片仍在保存');busy=true;try{await work();}finally{busy=false;}}
 return{
  samples:()=>total,
  async process(input:Float32Array){if(blocked||flushing)throw Error('请先恢复未保存的音频');if(!(input instanceof Float32Array)||input.length>4096||input.some(v=>!Number.isFinite(v)))throw new TypeError('麦克风数据超限或无效');const snapshot=input.slice();await ordered(async()=>{queue=resampler.process(snapshot);offset=0;total+=queue.length;await drain();});},
  async flush(){if(blocked)throw Error('请先恢复未保存的音频');await ordered(async()=>{queue=resampler.flush();offset=0;total+=queue.length;flushing=true;await drain();});},
  async retry(){await ordered(async()=>{await drain();blocked=false;});},
 };
}
