export type Resampler={process(input:Float32Array):Int16Array;flush():Int16Array};

/** Stateful linear resampling retains only the samples needed by the next frame. */
export function createResampler(inputRate:number,outputRate=16000):Resampler{
 if(!Number.isFinite(inputRate)||inputRate<=0||!Number.isFinite(outputRate)||outputRate<=0)throw new TypeError('采样率无效');
 const ratio=inputRate/outputRate;let tail=new Float32Array(0),received=0,emitted=0;
 function produce(input:Float32Array,flush:boolean){
  const base=received-tail.length,buffer=new Float32Array(tail.length+input.length);
  buffer.set(tail);buffer.set(input,tail.length);received+=input.length;
  const limit=Math.floor(received/ratio),values:number[]=[];
  while(emitted<limit){
   const absolute=emitted*ratio,index=Math.floor(absolute)-base,fraction=absolute-Math.floor(absolute);
   if(index<0||index>=buffer.length)throw new Error('重采样边界无效');
   if(fraction>0&&index+1>=buffer.length&&!flush)break;
   const a=buffer[index]!,b=buffer[index+1]??a,value=a+(b-a)*fraction;
   values.push(Math.max(-1,Math.min(1,value)));emitted++;
  }
  tail=buffer.length?buffer.slice(Math.min(buffer.length-1,Math.max(0,Math.floor(emitted*ratio)-base))):tail;
  const out=new Int16Array(values.length);for(let i=0;i<values.length;i++)out[i]=Math.round(values[i]!*32767);
  return out;
 }
 return {
  process(input){if(!(input instanceof Float32Array))throw new TypeError('输入必须是 Float32Array');return produce(input,false);},
  flush(){const result=produce(new Float32Array(0),true);tail=new Float32Array(0);received=0;emitted=0;return result;},
 };
}
