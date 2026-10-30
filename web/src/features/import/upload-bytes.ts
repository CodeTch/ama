import {importFormat} from '../../shared/document-formats';
import type {components} from '../../shared/api/generated';
import {fitsUtf8} from '../../shared/bounded-text';
// Operational browser allocation limits; not product filename/text limits or server qualification.
export const UPLOAD_FILE_BYTES=32*1024*1024;
export const UPLOAD_PART_BYTES=8*1024*1024;
export const UPLOAD_PART_COUNT=4096;
export type UploadPart=Readonly<{part:number;size:number;sha256:string;accepted:boolean}>;
export type ReselectedIdentity=Readonly<{sizeToken:string;sha256:string}>;
type Receipt=components['schemas']['PartSummary'];
type Begin=components['schemas']['Begin'] & {sha256:string};
function unsigned(value:unknown,positive=false):value is string{return typeof value==='string'&&value.length<=19&&/^(0|[1-9][0-9]*)$/.test(value)&&(!positive||value!=='0')&&(value.length<19||value<='9223372036854775807');}
const hash=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{64}$/.test(value);
const cancelled=(signal:AbortSignal)=>{if(signal.aborted)throw new DOMException('Upload preparation cancelled','AbortError');};
async function checksum(bytes:ArrayBuffer,signal:AbortSignal){cancelled(signal);const value=await crypto.subtle.digest('SHA-256',bytes);cancelled(signal);return Array.from(new Uint8Array(value),b=>b.toString(16).padStart(2,'0')).join('');}
/** Copy/hash bounded actual bytes once. A restored upload must first reselect identical bytes. */
export async function prepareUpload(file:File,signal:AbortSignal,expected?:ReselectedIdentity){
 cancelled(signal);
 if(!Number.isSafeInteger(file.size)||file.size<0||file.size>UPLOAD_FILE_BYTES||typeof file.name!=='string'||!fitsUtf8(file.name,1024*1024)||!importFormat(file.name))throw new TypeError('File exceeds browser intake capability');
 const identity=expected?Object.freeze({sizeToken:expected.sizeToken,sha256:expected.sha256}):null;
 if(identity&&(!unsigned(identity.sizeToken)||identity.sizeToken!==String(file.size)||!hash(identity.sha256)))throw new TypeError('Reselected file identity differs');
 const bytes=await file.arrayBuffer();cancelled(signal);if(bytes.byteLength!==file.size)throw new TypeError('File length changed');
 const sha256=await checksum(bytes,signal);if(identity&&sha256!==identity.sha256)throw new TypeError('Reselected file bytes differ');
 const begin:Readonly<Begin>=Object.freeze({purpose:'TRANSCRIPT_INPUT',name:file.name,size:bytes.byteLength,sha256});
 const slices=new WeakMap<UploadPart,Readonly<{start:number;end:number}>>();
 async function plan(receipts:readonly Receipt[],partSizeToken:string):Promise<readonly UploadPart[]>{
  cancelled(signal);if(!unsigned(partSizeToken,true)||receipts.length>UPLOAD_PART_COUNT)throw new TypeError('Upload part plan unavailable');
  // Convert only after bounding this byte count to 8MiB. Never convert revision/access tokens.
  const suggested=BigInt(partSizeToken),partSize=Number(suggested>BigInt(UPLOAD_PART_BYTES)?BigInt(UPLOAD_PART_BYTES):suggested);
  const captured=receipts.map(r=>Object.freeze({part:r.part,sizeBytesToken:r.sizeBytesToken,sha256:r.sha256}));
  let offset=0;const planned:UploadPart[]=[];
  for(const receipt of captured){
   cancelled(signal);if(!Number.isSafeInteger(receipt.part)||receipt.part!==planned.length+1||!unsigned(receipt.sizeBytesToken)||!hash(receipt.sha256))throw new TypeError('Unresolved upload receipt');
   const length=BigInt(receipt.sizeBytesToken);if(length>BigInt(bytes.byteLength-offset))throw new TypeError('Receipt exceeds selected file');
   const size=Number(length),end=offset+size,sum=await checksum(bytes.slice(offset,end),signal);if(sum!==receipt.sha256)throw new TypeError('Receipt bytes differ');
   const part=Object.freeze({part:receipt.part,size,sha256:sum,accepted:true});slices.set(part,Object.freeze({start:offset,end}));planned.push(part);offset=end;
  }
  const remaining=Math.ceil((bytes.byteLength-offset)/partSize)+(bytes.byteLength===0&&planned.length===0?1:0);
  if(planned.length+remaining>UPLOAD_PART_COUNT)throw new TypeError('Upload part count exceeds browser bound');
  for(let n=0;n<remaining;n++){
   const end=Math.min(bytes.byteLength,offset+partSize),sha256=await checksum(bytes.slice(offset,end),signal),part=Object.freeze({part:planned.length+1,size:end-offset,sha256,accepted:false});slices.set(part,Object.freeze({start:offset,end}));planned.push(part);offset=end;
  }
  cancelled(signal);return Object.freeze(planned);
 }
 function body(part:UploadPart){cancelled(signal);const range=slices.get(part);if(!range)throw new TypeError('Foreign upload part');return new Blob([bytes.slice(range.start,range.end)],{type:'application/octet-stream'});}
 return Object.freeze({begin,plan,body});
}
