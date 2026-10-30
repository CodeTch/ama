"""Native codec tests: run with the owned ASR venv; no inference or downloaded model needed."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('native_asr', Path(__file__).resolve().parents[2] / 'providers/local_asr.py')
asr = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(asr)
NATIVE = importlib.util.find_spec('av') is not None and importlib.util.find_spec('numpy') is not None and shutil.which('ffmpeg')


@unittest.skipUnless(NATIVE, 'requires owned ASR runtime and fixture-only ffmpeg')
class NativeMediaDecodingTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def encode(self, extension, codec, muxer=None, video=False):
        path = self.root / ('meeting.' + extension.lower())
        command = ['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=16000:duration=0.4']
        if video:
            command += ['-f', 'lavfi', '-i', 'color=c=black:s=32x32:r=10:d=0.4', '-c:v', 'mpeg4', '-shortest']
        command += ['-c:a', codec]
        if muxer:
            command += ['-f', muxer]
        subprocess.run(command + [str(path)], check=True, capture_output=True, timeout=10)
        return path

    def test_extra_and_original_formats_really_decode_into_bounded_16k_mono_samples(self):
        cases = [('AAC', 'aac', 'adts', False), ('FLAC', 'flac', None, False),
                 ('OGG', 'libvorbis', None, False), ('M4V', 'aac', 'ipod', True),
                 ('AVI', 'pcm_s16le', None, True), ('MP3', 'libmp3lame', None, False),
                 ('WAV', 'pcm_s16le', None, False), ('M4A', 'aac', None, False),
                 ('MP4', 'aac', None, True), ('MOV', 'aac', None, True),
                 ('MKV', 'flac', None, True), ('WEBM', 'libopus', None, False)]
        for extension, codec, muxer, video in cases:
            with self.subTest(format=extension):
                path = self.encode(extension, codec, muxer, video)
                path = path.rename(self.root / ('source-' + extension + '.bin'))
                chunks = list(asr._audio_chunks(path, 2, format=extension))
                self.assertTrue(chunks)
                self.assertEqual(chunks[0][1], 0)
                self.assertGreater(sum(len(audio) for audio, _ in chunks), 3000)
                self.assertLess(sum(len(audio) for audio, _ in chunks), 16000)
                self.assertTrue(all(audio.ndim == 1 and str(audio.dtype) == 'float32' and len(audio) <= 480000 for audio, _ in chunks))
                self.assertGreater(max(float(abs(audio).max()) for audio, _ in chunks), 0.01)

    def test_stereo_resampling_keeps_absolute_offsets_across_the_thirty_second_buffer(self):
        path = self.root / 'long-stereo.flac'
        subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i',
                        'sine=frequency=440:sample_rate=48000:duration=31.25', '-ac', '2', '-c:a', 'flac', str(path)],
                       check=True, capture_output=True, timeout=10)
        chunks = list(asr._audio_chunks(path, 40, format='FLAC'))
        self.assertEqual([(len(audio), offset) for audio, offset in chunks], [(480000, 0.0), (20000, 30.0)])

    def test_format_mismatch_playlist_and_missing_audio_fail_in_real_decoder(self):
        flac = self.encode('FLAC', 'flac')
        with self.assertRaisesRegex(asr.AsrError, 'INVALID_MEDIA'):
            list(asr._audio_chunks(flac, 2, format='AAC'))
        playlist = self.root / 'source.bin'
        playlist.write_text('#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\nhttps://example.invalid/never-fetch.aac\n#EXT-X-ENDLIST\n')
        with self.assertRaisesRegex(asr.AsrError, 'INVALID_MEDIA'):
            list(asr._audio_chunks(playlist, 2, format='M4V'))
        silent = self.root / 'no-audio.avi'
        subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=black:s=32x32:r=10:d=0.4', '-an', str(silent)], check=True, capture_output=True, timeout=10)
        with self.assertRaisesRegex(asr.AsrError, 'AUDIO_REQUIRED'):
            list(asr._audio_chunks(silent, 2, format='AVI'))

    def test_decoded_duration_limit_and_truncated_container_never_return_success(self):
        path = self.root / 'long.flac'
        subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i', 'sine=duration=1.2', '-c:a', 'flac', str(path)], check=True, capture_output=True, timeout=10)
        with self.assertRaisesRegex(asr.AsrError, 'AUDIO_DURATION_EXCEEDED'):
            list(asr._audio_chunks(path, 1, format='FLAC'))
        path.write_bytes(b'fLaCbroken')
        with self.assertRaisesRegex(asr.AsrError, 'INVALID_MEDIA'):
            list(asr._audio_chunks(path, 2, format='FLAC'))


if __name__ == '__main__':
    unittest.main()
