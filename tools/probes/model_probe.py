"""Bounded, explicit-endpoint protocol probe. Python standard library only.

No redirects, proxy inheritance, environment credentials, retries, or response text logs.
Socket timeouts do not establish server cancellation; unknown work retains its slot.
"""
import argparse
import base64
from collections import Counter
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
import http.client
import json
import math
from pathlib import Path
import socket
import sys
import time
from urllib.parse import urlsplit
import wave


PROTOCOL_FIELDS = {'vad': 'segments', 'spk': 'timeline', 'asr': 'text',
                   'embed': 'embedding', 'punc': 'text', 'llm': 'choices'}
MAX_AUDIO_BYTES = 4 * 1024 * 1024
MAX_RESPONSE_BYTES = 2 * 1024 * 1024


def _number(value):
    try:
        return type(value) in (int, float) and math.isfinite(value)
    except OverflowError:
        return False


def _ranges(value, duration_ms, width=2, token=False):
    if not isinstance(value, list):
        raise ValueError('invalid response')
    ranges = []
    for item in value:
        if not isinstance(item, list) or len(item) != width:
            raise ValueError('invalid response')
        start, end = item[1:3] if token else item[:2]
        if not (_number(start) and _number(end) and 0 <= start <= end <= duration_ms):
            raise ValueError('invalid response')
        if token and not isinstance(item[0], str):
            raise ValueError('invalid response')
        if width == 3 and not token and (type(item[2]) is not int or item[2] < 0):
            raise ValueError('invalid response')
        ranges.append([start, end])
    return ranges


def _audio(path):
    try:
        if Path(path).stat().st_size > MAX_AUDIO_BYTES:
            raise ValueError('audio exceeds probe memory limit')
        with wave.open(str(path), 'rb') as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype()) != (1, 2, 16000, 'NONE'):
                raise ValueError('expected 16kHz mono 16bit PCM WAV')
            frames = audio.getnframes()
            if frames == 0 or len(audio.readframes(frames)) != frames * 2:
                raise ValueError('empty or truncated WAV')
            return frames / 16000
    except (OSError, EOFError, wave.Error, TypeError):
        raise ValueError('invalid WAV input') from None


def _validate(config, paths):
    if not isinstance(config, dict):
        raise ValueError('config must be an object')
    c = dict(config)
    allowed = {'endpoint', 'protocol', 'authorization_id', 'max_concurrency', 'timeout_seconds',
               'repeats', 'source_offset_ms', 'window_seal_ms', 'arrival_interval_ms',
               'max_run_seconds', 'host', 'authorization', 'text', 'model', 'vad_segments'}
    if set(c) - allowed:
        raise ValueError('unsupported configuration field')
    if not isinstance(c.get('protocol'), str) or c['protocol'] not in PROTOCOL_FIELDS:
        raise ValueError('explicit supported protocol required')
    endpoint = c.get('endpoint')
    if not isinstance(endpoint, str) or any(ord(ch) < 33 for ch in endpoint):
        raise ValueError('explicit endpoint required')
    try:
        url = urlsplit(endpoint)
        _ = url.port  # Validate port syntax before opening any connection.
    except ValueError:
        raise ValueError('invalid endpoint') from None
    if (url.scheme not in ('http', 'https') or not url.hostname or url.username is not None
            or url.password is not None or url.query or url.fragment):
        raise ValueError('endpoint must be HTTP(S) without credentials, query or fragment')
    if not isinstance(c.get('authorization_id'), str) or not c['authorization_id'].strip():
        raise ValueError('explicit test authorization identifier required')
    if type(c.get('max_concurrency')) is not int or not 1 <= c['max_concurrency'] <= 100:
        raise ValueError('explicit max_concurrency must be 1..100')
    c.setdefault('timeout_seconds', 3)
    c.setdefault('repeats', 1)
    c.setdefault('source_offset_ms', 0)
    c.setdefault('window_seal_ms', 0)
    c.setdefault('arrival_interval_ms', 0)
    c.setdefault('max_run_seconds', 10)
    if not _number(c['timeout_seconds']) or not 0 < c['timeout_seconds'] <= 30:
        raise ValueError('timeout_seconds must be >0 and <=30')
    if type(c['repeats']) is not int or not 1 <= c['repeats'] <= 1000:
        raise ValueError('repeats must be 1..1000')
    for name in ('source_offset_ms', 'window_seal_ms', 'arrival_interval_ms'):
        if not _number(c[name]) or not 0 <= c[name] <= 86400000:
            raise ValueError('time configuration must be finite and nonnegative')
    if not _number(c['max_run_seconds']) or not 0 < c['max_run_seconds'] <= 60:
        raise ValueError('max_run_seconds must be >0 and <=60')
    for name in ('host', 'authorization'):
        if name in c and (not isinstance(c[name], str) or not c[name]
                          or any(ord(ch) < 32 or ord(ch) > 126 for ch in c[name])):
            raise ValueError('invalid HTTP header configuration')
    if not isinstance(paths, list) or any(not isinstance(p, str) for p in paths):
        raise ValueError('WAV paths must be a list of strings')
    if c['protocol'] in ('punc', 'llm'):
        if not isinstance(c.get('text'), str) or not c['text'].strip() or len(c['text'].encode('utf-8')) > 65536:
            raise ValueError('explicit bounded text input required')
        if c['protocol'] == 'llm' and (not isinstance(c.get('model'), str) or not c['model'].strip()):
            raise ValueError('explicit LLM model required')
        # Paths can be carried by a common driver but are never sent for text protocols.
        durations = [0.0] * max(1, len(paths))
    else:
        if not paths:
            raise ValueError('nonempty WAV path list required')
        durations = [_audio(p) for p in paths]
    if len(durations) * c['repeats'] > 1000:
        raise ValueError('probe run limited to 1000 requests')
    if (len(durations) * c['repeats'] - 1) * c['arrival_interval_ms'] >= c['max_run_seconds'] * 1000:
        raise ValueError('arrival schedule must fit the bounded run budget')
    if c['protocol'] == 'spk':
        if 'vad_segments' not in c:
            raise ValueError('explicit VAD segments required for SPK')
        for duration in durations:
            try:
                _ranges(c['vad_segments'], duration * 1000)
            except ValueError:
                raise ValueError('invalid VAD input segments') from None
    return c, url, durations


def _payload(c, path):
    protocol = c['protocol']
    if protocol in ('punc', 'llm'):
        payload = {'text': c['text']} if protocol == 'punc' else {
            'model': c['model'], 'messages': [{'role': 'user', 'content': c['text']}], 'temperature': 0}
    else:
        # Read at most one bounded complete WAV per active worker, never retain all audio.
        with open(path, 'rb') as stream:
            data = stream.read(MAX_AUDIO_BYTES + 1)
        if len(data) > MAX_AUDIO_BYTES:
            raise ValueError('input changed beyond probe limit')
        if protocol in ('asr', 'embed'):
            return data, 'application/octet-stream'
        payload = {'audio_b64': base64.b64encode(data).decode('ascii')}
        if protocol == 'spk':
            payload['vad_segments'] = c['vad_segments']
    return json.dumps(payload, ensure_ascii=False, allow_nan=False).encode('utf-8'), 'application/json'


def _response(body, protocol, duration_ms, offset):
    data = json.loads(body)
    if not isinstance(data, dict) or PROTOCOL_FIELDS[protocol] not in data:
        raise ValueError('invalid response')
    value = data[PROTOCOL_FIELDS[protocol]]
    ranges = []
    optional = []
    if protocol in ('asr', 'punc'):
        if not isinstance(value, str):
            raise ValueError('invalid response')
        if protocol == 'asr' and 'tokens' in data:
            ranges = _ranges(data['tokens'], duration_ms, width=3, token=True)
            optional.append('tokens')
    elif protocol == 'vad':
        ranges = _ranges(value, duration_ms)
    elif protocol == 'spk':
        ranges = _ranges(value, duration_ms, width=3)
    elif protocol == 'embed':
        if not isinstance(value, list) or not value or not all(_number(n) for n in value):
            raise ValueError('invalid response')
    elif protocol == 'llm':
        if not isinstance(value, list) or not value or not isinstance(value[0], dict):
            raise ValueError('invalid response')
        message = value[0].get('message')
        if not isinstance(message, dict) or not isinstance(message.get('content'), str):
            raise ValueError('invalid response')
    return [[start + offset, end + offset] for start, end in ranges], optional


def _request(c, url, path, duration, index, queued_at):
    start = time.perf_counter()
    row = {'sampleId': 'sample-%06d' % (index + 1), 'audioSeconds': duration,
           'queueMs': (start - queued_at) * 1000, 'serviceMs': 0,
           'sourceRangesMs': [], 'status': 'OK', 'httpStatus': None,
           'serverWorkUnknown': False, 'sent': False, 'optionalFields': []}
    connection = None
    try:
        body, content_type = _payload(c, path)
        headers = {'Content-Type': content_type}
        if c['protocol'] != 'llm':
            headers['Host'] = c.get('host', 'inference.dev.zsfund.com')
        elif 'host' in c:
            headers['Host'] = c['host']
        if 'authorization' in c:
            headers['Authorization'] = c['authorization']
        cls = http.client.HTTPSConnection if url.scheme == 'https' else http.client.HTTPConnection
        # Direct connection intentionally bypasses all ambient proxy / netrc / provider settings.
        connection = cls(url.hostname, url.port, timeout=c['timeout_seconds'])
        row['sent'] = True
        connection.request('POST', url.path or '/', body=body, headers=headers)
        response = connection.getresponse()
        row['httpStatus'] = response.status
        if response.status != 200:
            row['status'] = 'HTTP_%d' % response.status
        else:
            data = response.read(MAX_RESPONSE_BYTES + 1)
            if len(data) > MAX_RESPONSE_BYTES:
                raise ValueError('response limit exceeded')
            # Bounded read(amount) can return early EOF without IncompleteRead.
            # A declared body remainder is unfinished transport, even if its
            # received prefix is valid JSON. Keep the conservative unknown slot.
            if response.length not in (None, 0):
                raise http.client.IncompleteRead(b'', response.length)
            ranges, optional = _response(data, c['protocol'], duration * 1000, c['source_offset_ms'])
            row['sourceRangesMs'], row['optionalFields'] = ranges, optional
    except (TimeoutError, socket.timeout):
        row['status'] = 'TIMEOUT'
        row['serverWorkUnknown'] = row['sent']
    except (http.client.HTTPException, OSError):
        row['status'] = 'NETWORK_ERROR'
        row['serverWorkUnknown'] = row['sent']
    except (ValueError, TypeError, UnicodeError, OverflowError, RecursionError):
        row['status'] = 'INVALID_RESPONSE' if row['sent'] else 'INVALID_INPUT'
    finally:
        if connection:
            connection.close()
    row['serviceMs'] = (time.perf_counter() - start) * 1000
    row['endToEndMs'] = row['queueMs'] + row['serviceMs'] + c['window_seal_ms']
    row['latencyCensored'] = row['serverWorkUnknown']
    return row


def _stats(values):
    values = sorted(v for v in values if v is not None)
    if not values:
        return dict(count=0, p50=None, p95=None, p99=None, max=None)
    def percentile(p):
        return values[max(0, math.ceil(len(values) * p) - 1)]
    return dict(count=len(values), p50=percentile(.5), p95=percentile(.95),
                p99=percentile(.99), max=values[-1])


def probe(config: dict, wav_paths: list[str]) -> dict:
    """Run one bounded batch; all paths are prevalidated before any HTTP request.

    requestCount includes unsent jobs. A timeout/ambiguous transport failure stops
    new admissions. Already submitted jobs drain; unknown work stays reserved.
    rawSamplesPath is an RFC6901 reference into the returned report, replaced
    with an absolute JSON-file reference by the CLI.
    """
    c, url, durations = _validate(config, wav_paths)
    jobs = [(i, i % len(durations)) for i in range(len(durations) * c['repeats'])]
    started = time.perf_counter()
    pending, rows = {}, []
    cursor = peak = unknown = 0
    stop_reason = None
    with ThreadPoolExecutor(max_workers=c['max_concurrency']) as pool:
        while pending or (cursor < len(jobs) and not stop_reason):
            now = time.perf_counter()
            if now - started >= c['max_run_seconds'] and not stop_reason:
                stop_reason = 'NOT_SENT_RUN_BUDGET'
            while not stop_reason and cursor < len(jobs) and len(pending) + unknown < c['max_concurrency']:
                index, source = jobs[cursor]
                arrival = started + index * c['arrival_interval_ms'] / 1000
                if arrival > time.perf_counter():
                    break
                path = wav_paths[source] if c['protocol'] not in ('punc', 'llm') else None
                future = pool.submit(_request, c, url, path, durations[source], index, arrival)
                pending[future] = index
                cursor += 1
                peak = max(peak, len(pending))
            if not pending:
                if stop_reason or cursor == len(jobs):
                    break
                time.sleep(min(.01, max(0, started + cursor * c['arrival_interval_ms'] / 1000 - time.perf_counter())))
                continue
            done, _ = wait(pending, timeout=.01, return_when=FIRST_COMPLETED)
            for future in done:
                pending.pop(future)
                row = future.result()
                rows.append(row)
                if row['serverWorkUnknown']:
                    unknown += 1
                    stop_reason = 'NOT_SENT_UNKNOWN_SERVER_WORK'
    elapsed = time.perf_counter() - started
    for index, source in jobs[cursor:]:
        queue_ms = max(0, elapsed * 1000 - index * c['arrival_interval_ms'])
        rows.append(dict(sampleId='sample-%06d' % (index + 1), audioSeconds=durations[source],
                         queueMs=queue_ms, serviceMs=None,
                         endToEndMs=queue_ms + c['window_seal_ms'],
                         status=stop_reason, httpStatus=None,
                         serverWorkUnknown=False, latencyCensored=True, sent=False,
                         optionalFields=[], sourceRangesMs=[]))
    rows.sort(key=lambda row: row['sampleId'])
    valid = sum(row['status'] == 'OK' for row in rows)
    sent = sum(row['sent'] for row in rows)
    errors = Counter(row['status'] for row in rows if row['status'] != 'OK')
    optional = sorted({field for row in rows for field in row['optionalFields']})
    timing = {}
    for field in ('queueMs', 'serviceMs', 'endToEndMs'):
        timing[field] = _stats([r[field] for r in rows])
        # Worker start completes queue measurement, even if service later times
        # out. Only jobs without a worker/service observation have censored queue.
        timing[field]['censoredCount'] = sum(
            (r['serviceMs'] is None if field == 'queueMs' else r['latencyCensored'])
            and r[field] is not None for r in rows)
    return dict(protocol=c['protocol'], capacityStatus='PENDING_REAL_ENVIRONMENT',
                audioSeconds=sum(row['audioSeconds'] for row in rows),
                submittedAudioSeconds=sum(row['audioSeconds'] for row in rows if row['sent']),
                requestCount=len(jobs), sentCount=sent, validCount=valid,
                notSentCount=len(jobs) - sent, elapsedMs=elapsed * 1000,
                observedRequestsPerSecond=sent / elapsed if elapsed else 0,
                offeredRequestsPerSecond=1000 / c['arrival_interval_ms'] if c['arrival_interval_ms'] else None,
                inflight=dict(limit=c['max_concurrency'], peakClient=peak, clientAtEnd=0,
                              unknownServerAtEnd=unknown, reservedAtEnd=unknown),
                queueMs=timing['queueMs'], serviceMs=timing['serviceMs'],
                endToEndMs=timing['endToEndMs'], errors=dict(errors),
                observedFields=dict(documented=[PROTOCOL_FIELDS[c['protocol']]] if valid else [], optional=optional),
                timeUnit='milliseconds', timeoutSemantics='socket_io_wait_not_server_cancellation',
                rawSamplesPath='#/samples', samples=rows)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, help='explicit JSON configuration; never logged')
    parser.add_argument('--output', required=True, help='sanitized JSON metrics output')
    parser.add_argument('wav_paths', nargs='*')
    args = parser.parse_args(argv)
    try:
        with open(args.config, encoding='utf-8') as stream:
            config = json.load(stream)
        report = probe(config, args.wav_paths)
        output = Path(args.output).resolve()
        report['rawSamplesPath'] = str(output) + '#/samples'
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    except (OSError, ValueError, TypeError):
        print('Probe rejected configuration/input or could not write report.', file=sys.stderr)
        return 2
    print('Probe report written; real model capacity remains pending.')
    return 0 if report['validCount'] == report['requestCount'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
