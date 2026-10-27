import {responseSchemas} from './minutes-response-schemas';import {object} from './minutes-wire';import {revisionToken} from './intent-contract';
/** Closed schema subset, generated from source contracts. Shared node/depth limits bound validation work. */
export function responseMatches(name:string,value:unknown):boolean{
 let remaining=500000;
 const check=(raw:unknown,v:unknown,depth:number):boolean=>{
  if(--remaining<0||depth>64||!object(raw))return false;const s=raw;
  if(typeof s.$ref==='string')return check(responseSchemas[s.$ref],v,depth+1);
  if('const' in s&&v!==s.const)return false;if(Array.isArray(s.enum)&&!s.enum.includes(v))return false;
  if(Array.isArray(s.oneOf)&&s.oneOf.filter(x=>check(x,v,depth+1)).length!==1)return false;
  if(Array.isArray(s.anyOf)&&!s.anyOf.some(x=>check(x,v,depth+1)))return false;
  if(Array.isArray(s.allOf)&&!s.allOf.every(x=>check(x,v,depth+1)))return false;
  if(s.type!==undefined){const kind=Array.isArray(v)?'array':v===null?'null':typeof v;if(s.type==='integer'?!Number.isInteger(v):s.type!==kind)return false;}
  if(typeof v==='string'){
   if(typeof s.maxLength==='number'&&[...v].length>s.maxLength||typeof s.minLength==='number'&&[...v].length<s.minLength||typeof s.pattern==='string'&&!new RegExp(s.pattern).test(v))return false;
   if(s.format==='uuid'&&!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v))return false;
   if(s.format==='date-time'&&(!/^\d{4}-\d\d-\d\dT/.test(v)||!Number.isFinite(Date.parse(v))))return false;
   if(s.pattern==='^(0|[1-9][0-9]{0,18})$'&&!revisionToken(v))return false;
  }
  if(typeof v==='number'&&(!Number.isFinite(v)||typeof s.minimum==='number'&&v<s.minimum||typeof s.maximum==='number'&&v>s.maximum||typeof s.exclusiveMinimum==='number'&&v<=s.exclusiveMinimum||typeof s.exclusiveMaximum==='number'&&v>=s.exclusiveMaximum))return false;
  if(Array.isArray(v)){
   if(v.length>100000||typeof s.maxItems==='number'&&v.length>s.maxItems||typeof s.minItems==='number'&&v.length<s.minItems)return false;
   if(s.uniqueItems===true&&new Set(v.map(x=>JSON.stringify(x))).size!==v.length)return false;
   if(s.items&&!v.every(x=>check(s.items,x,depth+1)))return false;
  }
  if(object(v)){
   if(Array.isArray(s.required)&&!s.required.every(k=>typeof k==='string'&&Object.hasOwn(v,k)))return false;
   const props=object(s.properties)?s.properties:{};
   for(const [k,x] of Object.entries(v)){if(Object.hasOwn(props,k)){if(!check(props[k],x,depth+1))return false;}else if(s.additionalProperties===false||object(s.additionalProperties)&&!check(s.additionalProperties,x,depth+1))return false;}
  }
  return true;
 };try{return check(responseSchemas[name],value,0);}catch{return false;}
}
