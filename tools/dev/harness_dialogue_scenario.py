"""Install source-bound dialogue fixtures through the sibling harness admin API.
These are explicitly simulated responses, never a claim of acoustic recognition.
"""
import argparse,hashlib,io,json,subprocess,wave
from pathlib import Path
from urllib.request import Request,build_opener,ProxyHandler
HTTP=build_opener(ProxyHandler({}))
def call(path,body=None,method=None):
 data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
 with HTTP.open(Request('http://127.0.0.1:8792'+path,data=data,headers={'Content-Type':'application/json'},method=method),timeout=15) as r:return json.load(r)
def clips(source):
 pcm=subprocess.check_output(['ffmpeg','-nostdin','-v','error','-i',str(source),'-vn','-ar','16000','-ac','1','-f','s16le','pipe:1'])
 for offset in range(0,len(pcm),3840000):
  out=io.BytesIO()
  with wave.open(out,'wb') as w:w.setparams((1,2,16000,0,'NONE','not compressed'));w.writeframes(pcm[offset:offset+3840000])
  yield out.getvalue()
def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('transcript',type=Path);p.add_argument('receipt',type=Path);args=p.parse_args()
 if args.receipt.exists():raise SystemExit('Receipt exists; preserve previous scenario ownership')
 turns=args.transcript.read_text().splitlines();windows=list(clips(args.source));ids=[]
 try:
  for i,clip in enumerate(windows):
   selected=turns[len(turns)*i//len(windows):len(turns)*(i+1)//len(windows)]
   text='\n'.join(selected)
   value=call('/admin/api/scenarios',{'module':'asr','name':'ama-dialogue-source-'+hashlib.sha256(clip).hexdigest()[:16],'enabled':True,'priority':1000,'method':'POST','path':'/inference/asr-nano/modelserving/asr','match':{'binary_sha256':hashlib.sha256(clip).hexdigest()},'status_code':200,'delay_ms':0,'response_type':'json','response_body':{'text':text},'response_headers':{}})
   ids.append(value['id'])
  args.receipt.write_text(json.dumps({'simulated':True,'sourceSha256':hashlib.sha256(args.source.read_bytes()).hexdigest(),'scenarioIds':ids},indent=2))
 except BaseException:
  for id in ids:call('/admin/api/scenarios/'+str(id),method='DELETE')
  raise
if __name__=='__main__':main()
