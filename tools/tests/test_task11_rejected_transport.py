"""Synthetic-only transport regressions: never start a listener or contact a service."""
import ast
from contextlib import ExitStack
import json
from pathlib import Path
import types
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / 'task11_rejected_transport.py'
PATH = '/v1/knowledge/publications/synthetic/executions/synthetic'
PORT = 47507
REQUEST = (b'GET ' + PATH.encode() + b' HTTP/1.1\r\n'
           b'hOsT: 127.0.0.1:47507\r\nAuthorization: Bearer synthetic-only\r\n'
           b'X-Controlled-Delegation: synthetic.signature\r\n'
           b'X-Opaque:  keep\tbytes : unchanged\r\nContent-Length: 0\r\n\r\n')
RESPONSE = b'HTTP/1.1 200 OK\r\nContent-Length: 18\r\n\r\n{"state":"FAILED"}'


def definitions():
    # Before the guard exists, execute definitions only. Never execute legacy startup.
    tree = ast.parse(SOURCE.read_text(), filename=str(SOURCE))
    tree.body = [node for node in tree.body if isinstance(node, (ast.Import, ast.ImportFrom, ast.FunctionDef))]
    module = types.ModuleType('transport_definitions')
    exec(compile(tree, str(SOURCE), 'exec'), module.__dict__)
    return module


class Stream:
    def __init__(self, incoming=b'', error=None):
        self.incoming = incoming
        self.sent = b''
        self.error = error
        self.timeout = None
    def recv(self, size):
        if self.error is not None:
            raise self.error
        chunk, self.incoming = self.incoming[:size], self.incoming[size:]
        return chunk
    def sendall(self, data):
        self.sent += data
    def settimeout(self, value):
        self.timeout = value
    def __enter__(self):
        return self
    def __exit__(self, *args):
        return False


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.m = definitions()
        self.m.c = {'path': PATH, 'maxRequests': 24}
        self.m.state = {'port': PORT, 'sinkPort': 45000, 'requests': 0, 'forwards': 0,
                        'posts': 0, 'bytesSent': 0}
        self.saved = []
        self.m.save = lambda: self.saved.append(json.loads(json.dumps(self.m.state)))
        self.waits = []
        self.m.stop = types.SimpleNamespace(wait=lambda seconds: self.waits.append(seconds))
        self.row = {'received': False, 'forwarded': False, 'bytesSent': 0}
        self.connections = []

    def deliver(self, request=REQUEST, response=RESPONSE, mode='NORMAL', error=None):
        self.client = Stream(request)
        self.up = Stream(response, error)
        def connect(address, timeout):
            self.assertEqual(('127.0.0.1', 45519), address)
            self.assertEqual(3, timeout)
            self.connections.append(address)
            return self.up
        with patch.object(self.m.socket, 'create_connection', connect):
            self.m.delivery(self.client, mode, self.row)

    def test_two_hop_host_composition_and_other_bytes_preserved(self):
        self.deliver()
        expected = REQUEST.replace(b'hOsT: 127.0.0.1:47507', b'Host: 127.0.0.1:45519')
        self.assertEqual(expected, self.up.sent)
        # Exact existing original-relay expression, applied to synthetic bytes only.
        second_hop = self.up.sent.replace(b'Host: 127.0.0.1:45519', b'Host: 127.0.0.1:45709')
        self.assertEqual(REQUEST.replace(b'hOsT: 127.0.0.1:47507', b'Host: 127.0.0.1:45709'), second_hop)
        self.assertTrue(self.row['upstream200'])
        self.assertEqual(200, self.row['upstreamStatus'])
        self.assertTrue(self.client.sent.endswith(b'{"state":"FAILED"}'))

    def test_case_insensitive_host_names(self):
        for name in (b'Host', b'host', b'HOST', b'hOsT'):
            with self.subTest(name=name):
                self.setUp()
                self.deliver(REQUEST.replace(b'hOsT:', name + b':'))
                self.assertIn(b'\r\nHost: 127.0.0.1:45519\r\n', self.up.sent)

    def test_missing_duplicate_and_wrong_host_rejected_before_connect(self):
        host = b'hOsT: 127.0.0.1:47507\r\n'
        for replacement in (b'', host + b'HOST: 127.0.0.1:47507\r\n',
                            b'Host: localhost:47507\r\n', b'Host: 127.0.0.1:45519\r\n',
                            b'Host: 127.0.0.1:47507.extra\r\n'):
            with self.subTest(replacement=replacement):
                self.setUp()
                with self.assertRaises(ValueError):
                    self.deliver(REQUEST.replace(host, replacement))
                self.assertEqual([], self.connections)
                self.assertFalse(self.row['forwarded'])

    def test_non_get_wrong_path_and_body_still_rejected(self):
        for request in (REQUEST.replace(b'GET ', b'POST ', 1),
                        REQUEST.replace(PATH.encode(), b'/wrong'), REQUEST + b'x',
                        REQUEST.replace(b'Content-Length: 0', b'Content-Length: 1'),
                        REQUEST.replace(b'Content-Length: 0\r\n', b'')):
            with self.subTest(request=request):
                self.setUp()
                with self.assertRaises(ValueError):
                    self.deliver(request)
                self.assertEqual([], self.connections)
        self.setUp()
        with self.assertRaises(ValueError):
            self.deliver(REQUEST.replace(b'GET ', b'POST ', 1))
        self.assertEqual(1, self.m.state['posts'])

    def test_non200_status_recorded_without_response_secrets(self):
        with self.assertRaises(ValueError) as caught:
            self.deliver(response=b'HTTP/1.1 400 secret-status-text\r\nX-Secret: secret-header\r\n\r\nsecret-body')
        self.assertEqual('ValueError', type(caught.exception).__name__)
        self.assertEqual(400, self.row.get('upstreamStatus'))
        self.assertEqual('UPSTREAM_STATUS', self.row.get('safeFailureStage'))
        self.assertEqual('UPSTREAM_NON_200', self.row.get('safeFailureReason'))
        self.assertNotIn('secret', json.dumps(self.row))
        self.assertEqual(b'', self.client.sent)

    def test_missing_malformed_and_incomplete_upstream_are_distinct(self):
        cases = [(b'', 'UPSTREAM_NO_STATUS', None),
                 (b'HTTP/1.1 xyz private\r\n\r\n', 'UPSTREAM_MALFORMED_STATUS', None),
                 (b'HTTP/1.1 999 private\r\n\r\n', 'UPSTREAM_MALFORMED_STATUS', None),
                 (b'HTTP/1.1 200 OK\r\nX: private', 'UPSTREAM_INCOMPLETE_HEADERS', 200)]
        for response, reason, status in cases:
            with self.subTest(reason=reason, response=response):
                self.setUp()
                with self.assertRaises(ValueError):
                    self.deliver(response=response)
                self.assertEqual(reason, self.row.get('safeFailureReason'))
                self.assertEqual(status, self.row.get('upstreamStatus'))
                self.assertNotIn('private', json.dumps(self.row))

    def test_io_failure_preserves_original_exception_without_message(self):
        original = TimeoutError('synthetic-credential-must-not-be-logged')
        with self.assertRaises(TimeoutError) as caught:
            self.deliver(error=original)
        self.assertIs(original, caught.exception)
        self.assertEqual('UPSTREAM_READ', self.row.get('safeFailureStage'))
        self.assertEqual('IO_ERROR', self.row.get('safeFailureReason'))
        self.assertNotIn('synthetic-credential', json.dumps(self.row))

    def test_request_and_response_bounds_and_budget_remain(self):
        for request in (b'GET ' + b'x' * 16384, b'GET /incomplete'):
            self.setUp()
            with self.assertRaises(ValueError): self.deliver(request)
            self.assertEqual([], self.connections)
        self.setUp()
        with self.assertRaises(ValueError): self.deliver(response=b'x' * 81921)
        self.assertEqual(b'', self.client.sent)
        self.setUp()
        self.m.state['requests'] = 24
        with self.assertRaises(ValueError): self.deliver()
        self.assertEqual([], self.connections)
        self.assertEqual(25, self.m.state['requests'])

    def test_existing_mutation_modes_still_emit_expected_wire(self):
        cases = {'DECLARED_OVERSIZE': b'Content-Length: 65537', 'ABSENT_LENGTH': b'X-Test: absent',
                 'DUP_LENGTH': b'Content-Length: 18\r\nContent-Length: 18',
                 'MALFORMED_LENGTH': b'Content-Length: +1', 'DUP_HEADER': b'X-Test: a\r\nX-Test: b',
                 'TRANSFER_ENCODING': b'Transfer-Encoding: chunked', 'COMPRESSION': b'Content-Encoding: gzip',
                 'REDIRECT': b'Location: http://127.0.0.1:45000/forbidden',
                 'DUP_JSON': b'{"state":"FAILED","state":"FAILED"}', 'TRAILING_JSON': b'{}',
                 'INVALID_UTF8': b'\xff', 'LONE_SURROGATE': b'\\ud800', 'NAME_BOUNDS': b'a' * 129,
                 'IDENTITY_MISMATCH': b'00000000-0000-0000-0000-000000000000',
                 'PROOF_MISMATCH': b'0' * 64}
        for mode, fragment in cases.items():
            with self.subTest(mode=mode):
                self.setUp(); self.deliver(mode=mode)
                self.assertIn(fragment, self.client.sent)
        for mode in ('HEADER_STALL', 'BODY_STALL', 'ERROR_STALL', 'CANCEL', 'CLOSE_BLOCKED', 'INTERRUPT', 'SLOW_PROGRESS', 'TRUNCATED'):
            with self.subTest(mode=mode):
                self.setUp(); self.deliver(mode=mode)
                if mode == 'HEADER_STALL': self.assertEqual(b'', self.client.sent)
                elif mode == 'TRUNCATED': self.assertTrue(self.client.sent.endswith(b'{"state":"FAILED"'))
                elif mode == 'SLOW_PROGRESS': self.assertEqual([1] * 18, self.waits)
                else: self.assertEqual(1, self.row['bodyBytesSent'])
                if mode not in ('SLOW_PROGRESS', 'TRUNCATED'): self.assertEqual([7], self.waits)

    def test_import_does_not_create_resources_or_read_config(self):
        code = compile(SOURCE.read_text(), str(SOURCE), 'exec')
        def forbidden(*args, **kwargs):
            raise AssertionError('import attempted resource creation or config access')
        with ExitStack() as stack:
            for target in ('builtins.open', 'os.sched_setaffinity', 'os.sched_getaffinity',
                           'resource.setrlimit', 'socket.socket', 'socket.create_connection',
                           'threading.Event', 'threading.Thread', 'signal.signal', 'os.replace'):
                stack.enter_context(patch(target, forbidden))
            exec(code, {'__name__': 'transport_import_only'})


if __name__ == '__main__':
    unittest.main(verbosity=2)
