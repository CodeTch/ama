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
