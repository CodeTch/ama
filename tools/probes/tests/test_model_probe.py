"""Boundary tests: regressions named in each test exercise real loopback HTTP."""
import base64
from contextlib import contextmanager
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
try:
    from tools.probes.model_probe import probe
except ModuleNotFoundError:
    probe = None


@contextmanager
def fake_service(reply, delay=0, status=200, headers=None):
    records = []
    lock = threading.Lock()
    active = peak = 0

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            nonlocal active, peak
            body = self.rfile.read(int(self.headers['Content-Length']))
            with lock:
                active += 1
                peak = max(peak, active)
                records.append((dict(self.headers), body))
            try:
                time.sleep(delay)
                self.send_response(status)
                self.send_header('Content-Type', 'application/json')
                for name, value in (headers or {}).items():
                    self.send_header(name, value)
                self.end_headers()
                encoded = reply if isinstance(reply, bytes) else json.dumps(reply).encode()
                try:
                    self.wfile.write(encoded)
                except (BrokenPipeError, ConnectionResetError):
                    pass
            finally:
                with lock:
                    active -= 1

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = False
    server.block_on_close = True
    thread = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01})
    thread.start()
    try:
        yield 'http://127.0.0.1:%d/probe' % server.server_port, records, lambda: peak
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class ModelProbeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.wav = Path(self.temp.name) / 'fixture.wav'
        stream = io.BytesIO()
        with wave.open(stream, 'wb') as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(16000)
            out.writeframes(b'\x00\x00' * 3200)
        self.wav.write_bytes(stream.getvalue())

    def run_probe(self, endpoint, protocol='asr', **options):
        self.assertIsNotNone(probe, 'model probe is not implemented')
        config = dict(endpoint=endpoint, protocol=protocol, authorization_id='local-test',
                      max_concurrency=1, timeout_seconds=1)
        config.update(options)
        return probe(config, [str(self.wav)])

    def test_wav_encoding_matches_endpoint(self):
        # Sending raw PCM or base64 to the wrong endpoint breaks the actual wire contract.
        cases = [('vad', {'segments': [[10, 120]]}),
                 ('spk', {'timeline': [[10, 120, 0]]}),
                 ('asr', {'text': 'private transcription'}),
                 ('embed', {'embedding': [0.2, -0.1]})]
        for protocol, reply in cases:
            with self.subTest(protocol=protocol), fake_service(reply) as (url, records, _):
                report = self.run_probe(url, protocol, vad_segments=[[10, 120]])
                headers, body = records[0]
                self.assertEqual(headers['Host'], 'inference.dev.zsfund.com')
                if protocol in ('vad', 'spk'):
                    payload = json.loads(body)
                    self.assertEqual(base64.b64decode(payload['audio_b64']), self.wav.read_bytes())
                    self.assertTrue(base64.b64decode(payload['audio_b64']).startswith(b'RIFF'))
                    if protocol == 'spk':
                        self.assertEqual(payload['vad_segments'], [[10, 120]])
                    self.assertEqual(headers['Content-Type'], 'application/json')
                else:
                    self.assertEqual(body, self.wav.read_bytes())
                    self.assertEqual(headers['Content-Type'], 'application/octet-stream')
                self.assertEqual(report['validCount'], 1)
                self.assertAlmostEqual(report['audioSeconds'], 0.2)

    def test_missing_text_is_invalid_response(self):
        with fake_service({'unexpected': 'secret-body'}) as (url, _, _):
            report = self.run_probe(url)
        self.assertEqual(report['errors']['INVALID_RESPONSE'], 1)
        self.assertEqual(report['validCount'], 0)
        self.assertEqual(report['serviceMs']['count'], 1)
        self.assertNotIn('secret-body', json.dumps(report))

    def test_timeout_keeps_request_accounted(self):
        with fake_service({'text': 'late'}, delay=0.15) as (url, records, peak):
            report = self.run_probe(url, timeout_seconds=0.03, repeats=4)
            self.assertEqual(report['errors']['TIMEOUT'], 1)
            self.assertEqual(len(records), 1)
            self.assertEqual(peak(), 1)
        self.assertEqual(report['requestCount'], 4)
        self.assertEqual(report['sentCount'], 1)
        self.assertEqual(report['notSentCount'], 3)
        self.assertEqual(report['inflight']['clientAtEnd'], 0)
        self.assertEqual(report['inflight']['unknownServerAtEnd'], 1)
        self.assertEqual(report['inflight']['reservedAtEnd'], 1)
        self.assertEqual(report['serviceMs']['count'], 1)
        self.assertEqual(report['endToEndMs']['count'], 4)
        self.assertEqual(len(report['samples']), 4)

    def test_text_protocols_use_documented_payload_and_explicit_auth(self):
        for protocol, reply in [('punc', {'text': 'secret text'}),
                                ('llm', {'choices': [{'message': {'content': 'secret text'}}]})]:
            with self.subTest(protocol=protocol), fake_service(reply) as (url, records, _):
                report = self.run_probe(url, protocol, text='input secret', model='test-model',
                                        authorization='Bearer test-secret')
                headers, body = records[0]
                self.assertEqual(headers['Authorization'], 'Bearer test-secret')
                self.assertEqual(headers['Content-Type'], 'application/json')
                payload = json.loads(body)
                if protocol == 'punc':
                    self.assertEqual(payload, {'text': 'input secret'})
                    self.assertEqual(headers['Host'], 'inference.dev.zsfund.com')
                else:
                    self.assertEqual(payload, {'model': 'test-model', 'temperature': 0,
                                              'messages': [{'role': 'user', 'content': 'input secret'}]})
                    self.assertTrue(headers['Host'].startswith('127.0.0.1:'))
                self.assertEqual(report['audioSeconds'], 0)
                for private in ('input secret', 'test-secret', 'secret text'):
                    self.assertNotIn(private, json.dumps(report))

    def test_time_units_map_milliseconds_to_source_offset(self):
        for protocol, reply in [('vad', {'segments': [[10, 120]]}),
                                ('spk', {'timeline': [[10, 120, 2]]}),
                                ('asr', {'text': '', 'tokens': [['private', 10, 120]]})]:
            with self.subTest(protocol=protocol), fake_service(reply) as (url, _, _):
                report = self.run_probe(url, protocol, vad_segments=[[10, 120]], source_offset_ms=1000)
                self.assertEqual(report['samples'][0]['sourceRangesMs'], [[1010, 1120]])
                self.assertNotIn('private', json.dumps(report))

    def test_optional_tokens_are_observed_without_becoming_required(self):
        with fake_service({'text': ''}) as (url, _, _):
            report = self.run_probe(url)
        self.assertEqual(report['validCount'], 1)
        self.assertEqual(report['observedFields']['documented'], ['text'])
        self.assertEqual(report['observedFields']['optional'], [])
        with fake_service({'text': '', 'tokens': []}) as (url, _, _):
            report = self.run_probe(url)
        self.assertEqual(report['observedFields']['optional'], ['tokens'])

    def test_invalid_response_shapes_are_rejected(self):
        cases = [('vad', {'segments': [[100, 10]]}), ('vad', {'segments': [[0, 201]]}),
                 ('spk', {'timeline': [[0, 10, True]]}), ('embed', {'embedding': []}),
                 ('embed', {'embedding': [float('nan')]}), ('asr', {'text': 3}),
                 ('asr', {'text': '', 'tokens': [['x', 0, 999]]}),
                 ('punc', {}), ('llm', {'choices': []}),
                 ('llm', {'choices': [{'message': {'content': None}}]}),
                 ('asr', b'not-json'), ('asr', b'[]')]
        for protocol, reply in cases:
            with self.subTest(protocol=protocol, reply=reply), fake_service(reply) as (url, _, _):
                report = self.run_probe(url, protocol, text='input', model='m', vad_segments=[])
                self.assertEqual(report['errors']['INVALID_RESPONSE'], 1)

    def test_http_errors_count_and_do_not_expose_response_or_retry(self):
        for status, code in [(429, 'HTTP_429'), (500, 'HTTP_500'), (302, 'HTTP_302')]:
            with self.subTest(status=status), fake_service(b'sensitive server message', status=status) as (url, records, _):
                report = self.run_probe(url)
                self.assertEqual(report['errors'][code], 1)
                self.assertEqual(report['sentCount'], 1)
                self.assertEqual(len(records), 1)
                self.assertEqual(report['serviceMs']['count'], 1)
                self.assertNotIn('sensitive server message', json.dumps(report))

    def test_actual_inflight_is_bounded_and_queue_delay_is_counted(self):
        with fake_service({'text': ''}, delay=0.03) as (url, records, peak):
            report = self.run_probe(url, repeats=6, max_concurrency=2, window_seal_ms=20)
        self.assertEqual(len(records), 6)
        self.assertLessEqual(peak(), 2)
        self.assertEqual(report['inflight']['peakClient'], 2)
        self.assertEqual(report['inflight']['reservedAtEnd'], 0)
        self.assertGreater(report['queueMs']['max'], 20)
        for row in report['samples']:
            self.assertAlmostEqual(row['endToEndMs'], row['queueMs'] + row['serviceMs'] + 20, places=5)
        self.assertEqual(report['serviceMs']['count'], 6)
        self.assertTrue(report['rawSamplesPath'].endswith('/samples'))

    def test_invalid_configuration_never_reaches_server(self):
        with fake_service({'text': ''}) as (url, records, _):
            self.assertIsNotNone(probe, 'model probe is not implemented')
            base = dict(endpoint=url, protocol='asr', authorization_id='local-test', max_concurrency=1)
            cases = [{k: v for k, v in base.items() if k != key}
                     for key in ('endpoint', 'authorization_id', 'max_concurrency')]
            cases += [dict(base, **override) for override in [
                {'max_concurrency': 0}, {'max_concurrency': True}, {'repeats': 1001},
                {'timeout_seconds': 0}, {'endpoint': url + '?secret=oops'},
                {'endpoint': 'http://user:pass@127.0.0.1/'}, {'protocol': 'unknown'},
                {'authorization': 'x\r\nInjected: yes'}, {'host': 'x\ny'},
                {'protocol': 'spk'}, {'protocol': 'punc'}, {'protocol': 'llm', 'text': 'x'},
                {'source_offset_ms': -1}]]
            for config in cases:
                with self.subTest(config=config), self.assertRaises(ValueError):
                    probe(config, [str(self.wav)])
            self.assertEqual(len(records), 0)

    def test_unknown_configuration_does_not_silently_drop_platform_options(self):
        with fake_service({'text': ''}) as (url, records, _):
            self.assertIsNotNone(probe, 'model probe is not implemented')
            with self.assertRaises(ValueError):
                probe(dict(endpoint=url, protocol='asr', authorization_id='local', max_concurrency=1,
                           speaker_count=3), [str(self.wav)])
            self.assertEqual(len(records), 0)

    def test_timeout_statistics_mark_censored_observations(self):
        with fake_service({'text': ''}, delay=.1) as (url, _, _):
            report = self.run_probe(url, timeout_seconds=.02, repeats=2)
        self.assertIn('censoredCount', report['serviceMs'])
        self.assertEqual(report['serviceMs']['censoredCount'], 1)
        self.assertEqual(report['endToEndMs']['censoredCount'], 2)
        self.assertTrue(all(row['latencyCensored'] for row in report['samples']))

    def test_run_budget_stops_admissions_without_dropping_planned_work(self):
        with fake_service({'text': ''}, delay=.04) as (url, records, _):
            report = self.run_probe(url, repeats=3, max_run_seconds=.015)
        self.assertEqual(len(records), 1)
        self.assertEqual(report['errors']['NOT_SENT_RUN_BUDGET'], 2)
        self.assertEqual(report['requestCount'], 3)
        self.assertEqual(report['validCount'], 1)
        self.assertEqual(report['inflight']['unknownServerAtEnd'], 0)

    def test_invalid_audio_is_rejected_before_any_request(self):
        with fake_service({'text': ''}) as (url, records, _):
            self.wav.write_bytes(b'RIFFwrong')
            self.assertIsNotNone(probe, 'model probe is not implemented')
            with self.assertRaises(ValueError):
                probe(dict(endpoint=url, protocol='asr', authorization_id='local', max_concurrency=1), [str(self.wav)])
            self.assertEqual(len(records), 0)

    def test_paced_arrivals_do_not_count_prearrival_wait_as_queue(self):
        # Burst-only dispatch cannot measure an offered 50 requests/sec independently of service rate.
        with fake_service({'text': ''}, delay=0.005) as (url, records, _):
            report = self.run_probe(url, repeats=4, arrival_interval_ms=40)
        self.assertEqual(len(records), 4)
        self.assertGreaterEqual(report['elapsedMs'], 120)
        self.assertEqual(report['offeredRequestsPerSecond'], 25)
        self.assertLess(report['queueMs']['max'], 30)

    def test_ambiguous_network_failure_stops_admissions_and_retains_capacity(self):
        # Invalid HTTP framing may arrive after server work began; it is not cancellation.
        class DropHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                self.rfile.read(int(self.headers['Content-Length']))
                self.connection.shutdown(2)
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), DropHandler)
        thread = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': .01})
        thread.start()
        try:
            report = self.run_probe('http://127.0.0.1:%d/' % server.server_port, repeats=3)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        self.assertEqual(report['errors']['NETWORK_ERROR'], 1)
        self.assertEqual(report['sentCount'], 1)
        self.assertEqual(report['inflight']['unknownServerAtEnd'], 1)
        self.assertEqual(report['notSentCount'], 2)

    def test_environment_proxy_credentials_and_redirects_are_not_inherited(self):
        # An ambient proxy or credential fallback must never receive synthetic audio or auth.
        with fake_service({'text': ''}) as (url, records, _), fake_service({}, status=500) as (proxy, proxy_records, _):
            with patch.dict(os.environ, {'http_proxy': proxy, 'HTTP_PROXY': proxy,
                                         'LLM_API_KEY': 'private-env-key', 'DASHSCOPE_API_KEY': 'other-key'}, clear=False):
                report = self.run_probe(url, host='explicit.local')
            self.assertEqual(report['validCount'], 1)
            self.assertEqual(records[0][0]['Host'], 'explicit.local')
            self.assertNotIn('Authorization', records[0][0])
            self.assertEqual(proxy_records, [])
            with fake_service({}, status=302, headers={'Location': url}) as (redirect, _, _):
                report = self.run_probe(redirect)
                self.assertEqual(report['errors']['HTTP_302'], 1)
                self.assertEqual(len(records), 1)

    def test_empty_audio_and_oversized_config_preflight_without_requests(self):
        with fake_service({'text': ''}) as (url, records, _):
            self.assertIsNotNone(probe, 'model probe is not implemented')
            config = dict(endpoint=url, protocol='asr', authorization_id='local', max_concurrency=1)
            for override in ({'arrival_interval_ms': -1}, {'arrival_interval_ms': float('nan')},
                             {'arrival_interval_ms': 1000, 'repeats': 100},
                             {'max_run_seconds': 0}):
                with self.subTest(override=override), self.assertRaises(ValueError):
                    probe(dict(config, **override), [str(self.wav)])
            with self.assertRaises(ValueError):
                probe(config, [])
            self.assertEqual(len(records), 0)

    def test_cli_writes_report_and_safe_diagnostics(self):
        script = Path(__file__).resolve().parents[1] / 'model_probe.py'
        with fake_service({'text': 'do not log'}) as (url, records, _):
            config_path = Path(self.temp.name) / 'config.json'
            output = Path(self.temp.name) / 'report.json'
            config_path.write_text(json.dumps(dict(endpoint=url, protocol='asr', authorization_id='local', max_concurrency=1)))
            result = subprocess.run([sys.executable, str(script), '--config', str(config_path),
                                     '--output', str(output), str(self.wav)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(output.read_text())
            self.assertEqual(report['validCount'], 1)
            self.assertEqual(len(records), 1)
            self.assertEqual(report['rawSamplesPath'], str(output.resolve()) + '#/samples')
            self.assertNotIn('do not log', output.read_text() + result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
