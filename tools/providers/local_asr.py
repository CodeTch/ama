"""Offline CPU ASR. Emit a bounded result only after the original media has decoded completely.

Install the pinned model separately. This module never downloads a model or sends audio to a remote service.
"""
from pathlib import Path
import argparse,codecs,hashlib,json,math,os,re,sys

class AsrError(Exception):pass

# Restrict FFmpeg probing to file containers, never playlists or external-reference demuxers.
# MP4/M4V/M4A/MOV are the same ISO BMFF/QuickTime demuxer family.
MEDIA_DEMUXERS={
    'MP3':'mp3','WAV':'wav','M4A':'mov','MP4':'mov','MOV':'mov','MKV':'matroska','WEBM':'matroska',
    'AAC':'aac','FLAC':'flac','OGG':'ogg','M4V':'mov','AVI':'avi',
}


def _object(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise AsrError('MODEL_INTEGRITY')
        result[key]=value
    return result

def verify_model(root,manifest):
    root,manifest=Path(root),Path(manifest)
    try:
        if root.is_symlink() or not root.is_dir() or manifest.is_symlink():raise AsrError('MODEL_INTEGRITY')
        with manifest.open('rb') as stream:raw=stream.read(262145)
        if len(raw)>262144:raise AsrError('MODEL_INTEGRITY')
        value=json.loads(raw,object_pairs_hook=_object,parse_constant=lambda _:(_ for _ in ()).throw(AsrError('MODEL_INTEGRITY')))
        if set(value)!={'schema','repository','revision','engine','engineVersion','device','computeType','files'} or value['schema']!='ama.local-asr-model.v1' or value['repository']!='Systran/faster-whisper-small' or not re.fullmatch('[a-f0-9]{40}',value['revision']) or value['engine']!='faster-whisper' or value['engineVersion']!='1.2.1' or value['device']!='cpu' or value['computeType']!='int8':raise AsrError('MODEL_INTEGRITY')
        if not isinstance(value['files'],list) or not 1<=len(value['files'])<=32:raise AsrError('MODEL_INTEGRITY')
        names=set()
        for item in value['files']:
            if set(item)!={'name','sha256','bytes'} or not isinstance(item['name'],str) or not re.fullmatch('[A-Za-z0-9_.-]{1,128}',item['name']) or '..' in item['name'] or item['name'] in names or type(item['bytes']) is not int or not 0<=item['bytes']<=1073741824 or not re.fullmatch('[0-9a-f]{64}',item['sha256']):raise AsrError('MODEL_INTEGRITY')
            names.add(item['name']);path=root/item['name']
            with os.fdopen(os.open(path,os.O_RDONLY|os.O_NOFOLLOW),'rb') as stream:
                before=os.fstat(stream.fileno())
                if before.st_size!=item['bytes']:raise AsrError('MODEL_INTEGRITY')
                digest=hashlib.sha256();used=0
                while True:
                    chunk=stream.read(min(524288,item['bytes']-used+1))
                    if not chunk:break
                    used+=len(chunk)
                    if used>item['bytes']:raise AsrError('MODEL_INTEGRITY')
                    digest.update(chunk)
                after=os.fstat(stream.fileno())
                if used!=item['bytes'] or digest.hexdigest()!=item['sha256'] or before.st_mtime_ns!=after.st_mtime_ns:raise AsrError('MODEL_INTEGRITY')
        return hashlib.sha256(raw).hexdigest()
    except AsrError:raise
    except (OSError,ValueError,TypeError,KeyError):raise AsrError('MODEL_INTEGRITY') from None

class Segments:
    def __init__(self,max_units,max_segments):
        if type(max_units) is not int or type(max_segments) is not int or max_units<=0 or max_segments<=0:raise AsrError('INVALID_ARGUMENT')
        self.items=[];self.units=0;self.max_units=max_units;self.max_segments=max_segments
    def add(self,text,start,end,offset=0):
        if not isinstance(text,str) or '\x00' in text or any(type(x) not in (float,int) or not math.isfinite(x) for x in (start,end,offset)) or start<0 or end<=start or offset<0:raise AsrError('INVALID_SEGMENT')
        units=len(text.encode('utf-16-le'))//2+1
        if units>self.max_units-self.units:raise AsrError('UTF16_CODE_UNITS_EXCEEDED')
        if not text.strip():self.units+=units;return
        if len(self.items)>=self.max_segments:raise AsrError('SEGMENTS_EXCEEDED')
        first,last=round((start+offset)*1000),round((end+offset)*1000)
        if last<=first or self.items and first<self.items[-1]['startMs']:raise AsrError('INVALID_SEGMENT')
        self.items.append({'text':text,'startMs':first,'endMs':last});self.units+=units


# Subtitle payloads retain their original inline markup; layout and metadata never become segments.
# All decoded source units (including BOM, timing lines, metadata and CRLF) consume the text budget.
def _subtitle_text(path,max_units,max_source_bytes):
    decoder=codecs.getincrementaldecoder('utf-8')('strict');parts=[];used=0;units=0
    try:
        with Path(path).open('rb') as stream:
            while True:
                raw=stream.read(min(8192,max_source_bytes-used+1))
                if len(raw)>max_source_bytes-used:raise AsrError('SOURCE_BYTES_EXCEEDED')
                used+=len(raw);text=decoder.decode(raw,final=not raw)
                units+=len(text.encode('utf-16-le'))//2
                if units>max_units:raise AsrError('UTF16_CODE_UNITS_EXCEEDED')
                if any(ord(c)<32 and c not in '\t\n\r' or ord(c)==127 for c in text):raise AsrError('INVALID_TEXT')
                parts.append(text)
                if not raw:break
    except UnicodeError:raise AsrError('INVALID_UTF8') from None
    except OSError:raise AsrError('SOURCE_READ_FAILED') from None
    text=''.join(parts)
    if text.startswith('\ufeff'):text=text[1:]
    if '\ufeff' in text:raise AsrError('INVALID_TEXT')
    return text.replace('\r\n','\n').replace('\r','\n'),units

def _cue_time(token,format):
    expression=r'([0-9]{2,}):([0-9]{2}):([0-9]{2}),([0-9]{3})' if format=='SRT' else r'(?:([0-9]{2,}):)?([0-9]{2}):([0-9]{2})\.([0-9]{3})'
    match=re.fullmatch(expression,token)
    if not match:raise AsrError('INVALID_TEXT')
    hours,minutes,seconds,millis=match.groups()
    if len(hours or '')>4 or int(minutes)>59 or int(seconds)>59:raise AsrError('INVALID_TEXT')
    value=((int(hours or '0')*60+int(minutes))*60+int(seconds))*1000+int(millis)
    if value>7200000:raise AsrError('INVALID_TEXT')
    return value

def _percentage(value):
    return bool(re.fullmatch(r'[0-9]{1,3}(?:\.[0-9]{1,6})?%',value)) and float(value[:-1])<=100

def _vtt_region(lines,regions):
    values={}
    for line in lines:
        key,separator,value=line.partition(':')
        if not separator or key in values:raise AsrError('INVALID_TEXT')
        if key=='id':valid=bool(value) and not re.search(r'\s|-->',value)
        elif key=='width':valid=_percentage(value)
        elif key=='lines':valid=bool(re.fullmatch(r'[1-9][0-9]{0,5}',value))
        elif key in ('regionanchor','viewportanchor'):
            pair=value.split(',');valid=len(pair)==2 and all(_percentage(part) for part in pair)
        elif key=='scroll':valid=value=='up'
        else:valid=False
        if not valid:raise AsrError('INVALID_TEXT')
        values[key]=value
    if 'id' not in values or values['id'] in regions:raise AsrError('INVALID_TEXT')
    regions.add(values['id'])

def _vtt_settings(settings,regions):
    seen=set()
    for setting in settings:
        key,separator,value=setting.partition(':')
        if not separator or key in seen:raise AsrError('INVALID_TEXT')
        seen.add(key)
        if key=='vertical':valid=value in ('rl','lr')
        elif key=='align':valid=value in ('start','center','end','left','right')
        elif key=='size':valid=_percentage(value)
        elif key=='region':valid=value in regions
        elif key in ('line','position'):
            pair=value.split(',');valid=len(pair)<=2
            valid=valid and (pair[0]=='auto' or _percentage(pair[0]) or key=='line' and bool(re.fullmatch(r'-?[0-9]{1,9}',pair[0])))
            if len(pair)==2:valid=valid and pair[1] in (('start','center','end') if key=='line' else ('line-left','center','line-right','auto'))
        else:valid=False
        if not valid:raise AsrError('INVALID_TEXT')

def subtitle_file(path,format,max_units,max_segments,max_source_bytes=33554432):
    """Parse whole UTF-8 cue files; positive ascending SRT indices, ordered starts, overlap allowed.

    VTT supports identifiers, validated layout settings, NOTE, and pre-cue STYLE/REGION.
    Cue payload lines are preserved literally; no speaker, missing text or timing is inferred.
    """
    if format not in ('SRT','VTT') or any(type(n) is not int or n<=0 for n in (max_units,max_segments,max_source_bytes)):raise AsrError('INVALID_ARGUMENT')
    text,units=_subtitle_text(path,max_units,min(33554432,max_source_bytes))
    if format=='VTT' and not text.startswith('WEBVTT'):raise AsrError('INVALID_TEXT')
    blocks=[block for block in re.split(r'\n(?:[ \t]*\n)+',text.strip('\n')) if block.strip()]
    if format=='VTT':
        if not blocks or not re.fullmatch(r'WEBVTT(?:[ \t][^\n]*)?',blocks[0]) or '-->' in blocks[0]:raise AsrError('INVALID_TEXT')
        blocks=blocks[1:]
    items=[];regions=set();previous_index=0;duration=0
    for block in blocks:
        lines=block.split('\n')
        while lines and not lines[-1].strip():lines.pop()
        if format=='VTT':
            if re.match(r'^NOTE(?:[ \t]|$)',lines[0]):
                if '-->' in block:raise AsrError('INVALID_TEXT')
                continue
            if lines[0] in ('STYLE','REGION'):
                if items or len(lines)<2 or '-->' in block:raise AsrError('INVALID_TEXT')
                if lines[0]=='REGION':_vtt_region(lines[1:],regions)
                continue
            if '-->' not in lines[0]:
                if len(lines)<2 or not lines[0].strip():raise AsrError('INVALID_TEXT')
                lines=lines[1:]
        else:
            if not re.fullmatch(r'[1-9][0-9]{0,9}',lines[0]) or int(lines[0])<=previous_index:raise AsrError('INVALID_TEXT')
            previous_index=int(lines[0]);lines=lines[1:]
        if len(lines)<2:raise AsrError('INVALID_TEXT')
        timing=re.fullmatch(r'([^ \t]+)[ \t]+-->[ \t]+([^ \t]+)(?:[ \t]+(.*))?',lines[0])
        if not timing:raise AsrError('INVALID_TEXT')
        start,end=(_cue_time(timing[i],format) for i in (1,2))
        if end<=start or items and start<items[-1]['startMs']:raise AsrError('INVALID_TEXT')
        settings=(timing[3] or '').split()
        if format=='VTT':_vtt_settings(settings,regions)
        elif settings:raise AsrError('INVALID_TEXT')
        payload='\n'.join(lines[1:])
        if not payload.strip() or '-->' in payload:raise AsrError('INVALID_TEXT')
        if len(items)>=max_segments:raise AsrError('SEGMENTS_EXCEEDED')
        items.append({'text':payload,'startMs':start,'endMs':end});duration=max(duration,end)
    if not items:raise AsrError('NO_TEXT')
    return {'schema':'ama.local-asr-result.v1','language':'und','durationMs':duration,'decodedUtf16CodeUnits':units,'segments':items}

def _audio_chunks(path,max_seconds,format=None):
    # Keep a 30s mono/16k float buffer instead of loading an entire recording into memory.
    import av,numpy as np,itertools
    if format is not None and format not in MEDIA_DEMUXERS:raise AsrError('INVALID_ARGUMENT')
    allowed=','.join(sorted(set(MEDIA_DEMUXERS.values())))
    rate=16000;chunk_samples=rate*30;total=0;filled=0;buffer=np.empty(chunk_samples,dtype=np.float32)
    try:
        with av.open(str(path),options={'protocol_whitelist':'file,pipe','format_whitelist':allowed,'enable_drefs':'0','use_absolute_path':'0','err_detect':'explode'}) as container:
            if format is not None and MEDIA_DEMUXERS[format] not in container.format.name.split(','):raise AsrError('INVALID_MEDIA')
            if not container.streams.audio:raise AsrError('AUDIO_REQUIRED')
            stream=container.streams.audio[0];resampler=av.AudioResampler(format='fltp',layout='mono',rate=rate)
            for frame in itertools.chain(container.decode(stream),[None]):
                for decoded in resampler.resample(frame):
                    values=decoded.to_ndarray().reshape(-1);cursor=0
                    if len(values)>rate*max_seconds-total:raise AsrError('AUDIO_DURATION_EXCEEDED')
                    while cursor<len(values):
                        count=min(chunk_samples-filled,len(values)-cursor);buffer[filled:filled+count]=values[cursor:cursor+count];cursor+=count;filled+=count;total+=count
                        if filled==chunk_samples:yield buffer.copy(),(total-filled)/rate;filled=0
            if filled:yield buffer[:filled].copy(),(total-filled)/rate
    except AsrError:raise
    except Exception:raise AsrError('INVALID_MEDIA') from None

def transcribe_file(path,root,manifest,max_units,max_segments,max_seconds=7200,expected_manifest_sha256=None,format=None,max_source_bytes=33554432):
    if type(max_seconds) is not int or not 1<=max_seconds<=7200 or format is not None and format not in (*MEDIA_DEMUXERS,'SRT','VTT') or type(max_source_bytes) is not int or max_source_bytes<=0:raise AsrError('INVALID_ARGUMENT')
    try:
        if Path(path).stat().st_size>min(33554432,max_source_bytes):raise AsrError('SOURCE_BYTES_EXCEEDED')
    except OSError:raise AsrError('SOURCE_READ_FAILED') from None
    proof=verify_model(root,manifest)
    if expected_manifest_sha256 is not None and proof!=expected_manifest_sha256:raise AsrError('MODEL_INTEGRITY')
    if format in ('SRT','VTT'):
        result=subtitle_file(path,format,max_units,max_segments,max_source_bytes);result['modelManifestSha256']=proof;return result
    result=Segments(max_units,max_segments);model=None;duration=0;language=None;context=''
    for audio,offset in _audio_chunks(path,max_seconds,format):
        if model is None:
            from faster_whisper import WhisperModel
            try:model=WhisperModel(str(root),device='cpu',compute_type='int8',cpu_threads=4,num_workers=1,local_files_only=True)
            except Exception:raise AsrError('MODEL_UNAVAILABLE') from None
        duration=offset+len(audio)/16000
        try:
            segments,info=model.transcribe(audio,beam_size=5,language=language,vad_filter=True,condition_on_previous_text=True,initial_prompt=context or None,word_timestamps=False)
            language=language or info.language
            for segment in segments:
                first=max(0,segment.start);last=min(segment.end,len(audio)/16000)
                if last>first:result.add(segment.text,first,last,offset)
            context=' '.join(item['text'] for item in result.items[-4:])[-200:]
        except AsrError:raise
        except Exception:raise AsrError('INFERENCE_FAILED') from None
    if not result.items:raise AsrError('NO_TEXT')
    return {'schema':'ama.local-asr-result.v1','modelManifestSha256':proof,'language':language,'durationMs':round(duration*1000),'decodedUtf16CodeUnits':result.units,'segments':result.items}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--input',required=True);parser.add_argument('--model',required=True);parser.add_argument('--manifest',required=True);parser.add_argument('--expected-manifest-sha256',required=True);parser.add_argument('--max-units',type=int,required=True);parser.add_argument('--max-segments',type=int,required=True);parser.add_argument('--max-seconds',type=int,default=7200);parser.add_argument('--format');parser.add_argument('--max-source-bytes',type=int,default=33554432);args=parser.parse_args()
    try:
        result=transcribe_file(args.input,args.model,args.manifest,args.max_units,args.max_segments,args.max_seconds,args.expected_manifest_sha256,args.format,args.max_source_bytes)
        print(json.dumps(result,ensure_ascii=True,separators=(',',':')))
    except AsrError as error:print(json.dumps({'error':str(error)},separators=(',',':')));return 2
    except Exception:print('{"error":"INFERENCE_FAILED"}');return 2
    return 0
if __name__=='__main__':sys.exit(main())
