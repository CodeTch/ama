import hashlib
import struct
import unittest
from tools.load import transport

class FrameTest(unittest.TestCase):
    def test_frame_binds_exact_original_pcm_and_new_writer_epoch_without_reencoding_audio(self):
        part="12345678-1234-4234-8234-123456789abc"
        pcm=transport.pcm_bytes("owned-run","recorder",2,32000)
        frame=transport.encode_frame(part,7,2,32000,pcm)
        self.assertEqual(64080,len(frame));self.assertEqual(b"AMA1",frame[:4])
        self.assertEqual((7,2,32000,32000),struct.unpack(">QQQI",frame[20:48]))
        self.assertEqual(hashlib.sha256(pcm).digest(),frame[48:80]);self.assertEqual(pcm,frame[80:])
        self.assertEqual(pcm,transport.encode_frame(part,8,2,32000,pcm)[80:])
        self.assertNotEqual(pcm,transport.pcm_bytes("owned-run","recorder",1,32000))

    def test_invalid_or_unbounded_frame_is_rejected_before_transport(self):
        for epoch,seq,start,pcm in [(0,1,0,b"aa"),(1,0,0,b"aa"),(1,1,-1,b"aa"),(1,1,0,b"a"),(1,1,0,b"a"*64002)]:
            with self.subTest(epoch=epoch,seq=seq,start=start,length=len(pcm)):
                with self.assertRaises(ValueError):transport.encode_frame("12345678-1234-4234-8234-123456789abc",epoch,seq,start,pcm)

class SubtitleObservationTest(unittest.TestCase):
    def test_arrival_latency_uses_actual_capture_end_and_checks_exact_source(self):
        recording=dict(sessionId="session",session=dict(partId="part"),proofs=[dict(start=0,samples=32000)])
        event=dict(eventId="1",sessionId="session",partId="part",sourceFinalSeq=1,endMs=2000,text="临时字")
        actual=transport.observe_subtitle_event(recording,event,102.75,100)
        self.assertEqual(750,actual["latencyMs"])
        self.assertNotIn("text",actual)
        for change in [dict(sessionId="foreign"),dict(partId="foreign"),dict(sourceFinalSeq=2),dict(endMs=4000)]:
            with self.assertRaises(ValueError):transport.observe_subtitle_event(recording,event|change,102.75,100)
        with self.assertRaises(ValueError):transport.observe_subtitle_event(recording,event,101,100)

class ContinuousProducerTest(unittest.IsolatedAsyncioTestCase):
    async def test_chunks_are_sealed_at_capture_end_instead_of_sent_two_seconds_early(self):
        import asyncio
        from unittest.mock import patch
        client=transport.Transport(dict(durationSeconds=4,chunkMs=2000),"unused");client.epoch=100
        row=dict(recorderId="r",proofs=[],sentChunks=0,sampleCount=0);queue=asyncio.Queue();delays=[]
        async def sleep(delay):delays.append(delay)
        with patch.object(transport.time,"monotonic",return_value=100),patch.object(transport.asyncio,"sleep",sleep):await client.produce(row,queue)
        first=queue.get_nowait();second=queue.get_nowait()
        self.assertEqual(102,first[2]);self.assertEqual(104,second[2]);self.assertEqual(2,delays[0]);self.assertEqual(64000,row["sampleCount"])

class SubtitleReplayProbeTest(unittest.IsolatedAsyncioTestCase):
    async def test_probe_preserves_ordered_events_and_reports_cursor_gap_as_failure(self):
        client=transport.Transport(dict(durationSeconds=6,chunkMs=2000),"unused");client.epoch=transport.time.monotonic()-6;client.ready.set()
        def row():return dict(recorderId="r",meetingId="meeting",sessionId="session",session=dict(partId="part"),captureFinished=True,client=object(),proofs=[dict(start=i*32000,samples=32000) for i in range(3)])
        events=[dict(eventId=str(i),sessionId="session",partId="part",sourceFinalSeq=i,endMs=i*2000,status="stable" if i==3 else "temporary",text="字幕") for i in range(1,4)]
        async def read(*args,**kw):return dict(sessionId="session",meetingId="meeting",state="DONE",nextCursor="3",hasMore=False,events=events)
        client.http=read;recording=row();await client.subtitles(recording)
        self.assertEqual("DONE",recording["subtitleState"]);self.assertEqual(0,recording["subtitleErrors"]);self.assertEqual(3,len(recording["subtitleEvents"]))
        events.pop(1);broken=row();await client.subtitles(broken)
        self.assertEqual(1,broken["subtitleErrors"]);self.assertEqual("ValueError",broken["subtitleErrorType"])

class LongFileProbeTest(unittest.IsolatedAsyncioTestCase):
    async def test_uncertain_intake_reuses_its_original_key_and_body_and_binds_real_archive_source(self):
        from unittest.mock import patch
        client=transport.Transport(dict(durationSeconds=6),"unused");commands=[];reads=[];mutation={}
        row=dict(recorderId="r",meetingId="meeting",sessionId="session",client=object(),sampleCount=96000,wavBytes=192044,wavSha256="a"*64,transientErrors=0)
        source=dict(state="READY",fileId="copy",sourceSha256="a"*64,sourceBytes=192044,sourceSamples=96000)
        async def request(_client,method,path,body=None,**kw):
            reads.append(path)
            if path.endswith('/transcription-source'):
                self.assertEqual("POST",method);self.assertEqual({},body);return source
            if path.endswith('/processing-capabilities'):return dict(canSubmit=True,revisionToken="fixed-original")
            if path.endswith('/transcript-imports'):
                commands.append((body.copy(),kw['headers'].copy()))
                if len(commands)==1:raise transport.HttpFailure(503,method,path)
                return dict(meetingId="meeting",taskId="task",inputId="input")
            if '/tasks?' in path:return dict(items=[dict(taskId="task",state="SUCCEEDED")])
            if '/transcript/history?' in path:return dict(items=[] if mutation.get('missingRaw') else [dict(kind="RAW",id="raw")])
            if path.endswith('/transcript'):return dict(saved=None,sources=[dict(fileId="copy",sha256="a"*64)])
            if path.endswith('/transcript/revisions/raw'):return dict(meetingId='meeting',selected=dict(id='raw',kind='RAW',segments=[dict(sourceFileId=mutation.get('source','copy'),startMs=0,endMs=mutation.get('endMs',6000))]),sources=[dict(fileId='copy',sha256=mutation.get('sha256','a'*64))])
            self.fail(path)
        async def sleep(_):pass
        client.http=request
        with patch.object(transport.asyncio,'sleep',sleep):await client.transcribe_archive(row)
        self.assertEqual(2,len(commands));self.assertEqual(commands[0],commands[1]);self.assertEqual(["copy"],commands[0][0]['orderedFileIds']);self.assertEqual("fixed-original",commands[0][0]['expectedRevisionToken']);self.assertEqual("raw",row['longFileTranscription']['rawRevisionId'])
        source['sourceSha256']="b"*64
        with self.assertRaises(ValueError):await client.transcribe_archive(row)
        self.assertEqual(2,len(commands))

        self.assertIn('/api/v1/meetings/meeting/transcript/revisions/raw',reads)
        source['sourceSha256']='a'*64
        for corrupt in [dict(missingRaw=True),dict(source='foreign'),dict(endMs=5999),dict(sha256='b'*64)]:
            mutation.clear();mutation.update(corrupt)
            with self.subTest(corrupt=corrupt):
                with self.assertRaises(ValueError):await client.transcribe_archive(row)

class RoleReadinessTest(unittest.TestCase):
    def test_actual_listener_must_be_ready_and_owned_process_still_alive(self):
        import socket,time,threading
        with socket.socket() as listener:
            listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
            with self.assertRaises(TimeoutError):transport.wait_for_owned_listener(port,lambda:True,timeout=.05)
            timer=threading.Timer(.1,lambda:listener.listen(1));timer.start();started=time.monotonic()
            try:transport.wait_for_owned_listener(port,lambda:True,timeout=2)
            finally:timer.join()
            self.assertGreaterEqual(time.monotonic()-started,.08)
            with self.assertRaises(RuntimeError):transport.wait_for_owned_listener(port,lambda:False,timeout=1)
