"""Real subtitle files exercise the same bounded runner used by the Java media adapter."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[2] / 'providers/local_asr.py'
SPEC = importlib.util.spec_from_file_location('subtitle_asr', RUNNER)
asr = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(asr)


class TimedSubtitleTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        data = b'integrity boundary only; subtitles never invoke a speech model'
        (self.root / 'model.bin').write_bytes(data)
        manifest = {'schema': 'ama.local-asr-model.v1', 'repository': 'Systran/faster-whisper-small',
                    'revision': 'a' * 40, 'engine': 'faster-whisper', 'engineVersion': '1.2.1',
                    'device': 'cpu', 'computeType': 'int8',
                    'files': [{'name': 'model.bin', 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}]}
        self.manifest = self.root / 'manifest.json'
        self.manifest.write_text(json.dumps(manifest))
        self.proof = hashlib.sha256(self.manifest.read_bytes()).hexdigest()

    def run_subtitle(self, raw, format='SRT', units=10000, segments=10, source_bytes=10000):
        path = self.root / 'source.bin'
        path.write_bytes(raw.encode('utf-8') if isinstance(raw, str) else raw)
        result = subprocess.run([sys.executable, str(RUNNER), '--input', str(path), '--model', str(self.root),
                                 '--manifest', str(self.manifest), '--expected-manifest-sha256', self.proof,
                                 '--format', format, '--max-units', str(units), '--max-segments', str(segments),
                                 '--max-source-bytes', str(source_bytes)], capture_output=True, text=True, timeout=5)
        self.assertEqual(result.stderr, '', 'runner must emit only safe structured errors')
        # Argument rejection is a failure of the requested feature, not a valid bad-input result.
        self.assertTrue(result.stdout.strip(), 'runner must understand the subtitle format arguments')
        return result.returncode, json.loads(result.stdout)

    def test_srt_extracts_actual_multiline_unicode_cues_with_absolute_times(self):
        raw = '\ufeff7\r\n00:00:01,250 --> 00:00:03,500\r\n会议 😀\r\n第二行\r\n\r\n9\r\n00:00:02,000 --> 00:00:04,000\r\nOverlap is valid\r\n'
        code, value = self.run_subtitle(raw)
        self.assertEqual(code, 0)
        self.assertEqual(value['segments'], [{'text': '会议 😀\n第二行', 'startMs': 1250, 'endMs': 3500},
                                            {'text': 'Overlap is valid', 'startMs': 2000, 'endMs': 4000}])
        self.assertEqual(value['decodedUtf16CodeUnits'], len(raw.encode('utf-16-le')) // 2)
        self.assertEqual(value['durationMs'], 4000)
        self.assertEqual(value['modelManifestSha256'], self.proof)

    def test_vtt_metadata_and_cue_settings_never_become_transcript_text(self):
        raw = ('WEBVTT Meeting notes\n\nNOTE original metadata\nnot spoken\n\n'
               'STYLE\n::cue { color: lime; }\n\n'
               'REGION\nid:main\nwidth:80%\nlines:3\nregionanchor:0%,100%\nviewportanchor:10%,90%\nscroll:up\n\n'
               'cue-alpha\n00:01.250 --> 00:03.500 region:main line:90% position:20%,line-left size:80% align:start\n'
               'Actual cue\nsecond line\n\nNOTE between cues\n\n'
               '00:02.000 --> 00:04.000 vertical:rl\n会议结论\n')
        code, value = self.run_subtitle(raw, 'VTT')
        self.assertEqual(code, 0)
        self.assertEqual(value['segments'], [{'text': 'Actual cue\nsecond line', 'startMs': 1250, 'endMs': 3500},
                                            {'text': '会议结论', 'startMs': 2000, 'endMs': 4000}])
        self.assertEqual(value['decodedUtf16CodeUnits'], len(raw))

    def test_malformed_timestamps_sequence_missing_separator_and_trailing_garbage_fail_atomically(self):
        good = '1\n00:00:01,000 --> 00:00:02,000\nOriginal text\n'
        cases = [good.replace('00:00:01,000', '00:60:01,000'), good.replace('02,000', '00,999'),
                 good.replace('01,000', '01.000'), good.replace('1\n', 'bad-index\n', 1),
                 good + '\ntrailing garbage', good + '2\n00:00:02,000 --> 00:00:03,000\nmissing separator',
                 good + '\n2\n00:00:00,500 --> 00:00:03,000\nbackwards start',
                 good.replace('02,000', '02,000 align:center'), good.replace('02,000', '00,000'),
                 good.replace('00:00:02,000', '02:00:00,001')]
        for raw in cases:
            with self.subTest(raw=raw):
                code, value = self.run_subtitle(raw)
                self.assertEqual(code, 2)
                self.assertEqual(value, {'error': 'INVALID_TEXT'})

    def test_vtt_rejects_bad_header_invalid_settings_unknown_region_and_misplaced_metadata(self):
        good = 'WEBVTT\n\n00:01.000 --> 00:02.000\nCue\n'
        cases = [good.replace('WEBVTT', 'WEBVTTbad'), good.replace('\n\n', '\n', 1),
                 good.replace('02.000', '02,000'), good.replace('02.000', '02.000 size:101%'),
                 good.replace('02.000', '02.000 align:fake'), good.replace('02.000', '02.000 line:auto line:0'),
                 good.replace('02.000', '02.000 region:missing'), good + '\nSTYLE\n::cue { color:red; }',
                 'WEBVTT\n\nREGION\nid:main\nwidth:101%\n\n00:01.000 --> 00:02.000\nCue',
                 good + '\nnot a cue']
        for raw in cases:
            with self.subTest(raw=raw):
                self.assertEqual(self.run_subtitle(raw, 'VTT'), (2, {'error': 'INVALID_TEXT'}))

    def test_multiple_blank_separators_are_valid_but_vtt_header_must_be_first(self):
        raw = 'WEBVTT\n\n\n00:01.000 --> 00:02.000\nOne\n\n \n\n00:02.000 --> 00:03.000\nTwo\n'
        code, value = self.run_subtitle(raw, 'VTT')
        self.assertEqual(code, 0)
        self.assertEqual([cue['text'] for cue in value['segments']], ['One', 'Two'])
        self.assertEqual(self.run_subtitle('\n' + raw, 'VTT'), (2, {'error': 'INVALID_TEXT'}))

    def test_original_bytes_and_all_decoded_metadata_are_bounded(self):
        raw = '\ufeffWEBVTT\r\n\r\nNOTE metadata 😀\r\n\r\n00:01.000 --> 00:02.000\r\n😀\r\n'
        units = len(raw.encode('utf-16-le')) // 2
        size = len(raw.encode('utf-8'))
        self.assertEqual(self.run_subtitle(raw, 'VTT', units, 1, size)[0], 0)
        self.assertEqual(self.run_subtitle(raw, 'VTT', units - 1), (2, {'error': 'UTF16_CODE_UNITS_EXCEEDED'}))
        self.assertEqual(self.run_subtitle(raw, 'VTT', source_bytes=size - 1), (2, {'error': 'SOURCE_BYTES_EXCEEDED'}))
        two = '1\n00:00:01,000 --> 00:00:02,000\nOne\n\n2\n00:00:02,000 --> 00:00:03,000\nTwo'
        self.assertEqual(self.run_subtitle(two, segments=1), (2, {'error': 'SEGMENTS_EXCEEDED'}))

    def test_invalid_utf8_nul_unpaired_encoding_and_empty_cues_do_not_fall_back_to_plain_text(self):
        for raw, error in [(b'1\n\xff', 'INVALID_UTF8'), (b'\xed\xa0\x80', 'INVALID_UTF8'),
                           ('1\n00:00:01,000 --> 00:00:02,000\nbad\x00text', 'INVALID_TEXT'),
                           ('1\n00:00:01,000 --> 00:00:02,000\n   ', 'INVALID_TEXT'),
                           ('plain text renamed as subtitle', 'INVALID_TEXT'),
                           ('WEBVTT\n\nNOTE no actual cues\n', 'NO_TEXT')]:
            with self.subTest(raw=raw):
                self.assertEqual(self.run_subtitle(raw, 'VTT' if str(raw).startswith('WEBVTT') else 'SRT'),
                                 (2, {'error': error}))


if __name__ == '__main__':
    unittest.main()
