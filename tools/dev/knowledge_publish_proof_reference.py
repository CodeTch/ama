"""Read-only MySQL regression for the production publication evidence projections.

Uses the existing owned local application database; creates no tables or fixtures.
Run from the project root with the same Python runtime as local_app.py.
"""
import datetime
import json
from pathlib import Path
import re
import subprocess
import local_app


def main():
    source = (local_app.ROOT / 'backend/adapters/src/main/java/ama/transcript/PublicationProofReference.java').read_text()
    expressions = {key: re.search(r'static final String ' + key + r'="([^"]+)";', source).group(1)
                   for key in ('STORE', 'RECEIPT', 'DIGEST', 'SHAPE')}
    receipt, digest = 'receipt-' + 'a' * 32, 'b' * 64
    stores = ['12345678-1234-4234-8234-123456789ac2', 'local-store-692731c0919e',
              '存储:实例😀', '库' * 255, 'first:receipt-fake:last']
    cases = [('valid-' + str(n), 'knowledge:' + store + ':' + receipt + ':' + digest, 1, store)
             for n, store in enumerate(stores)]
    for name, value in [
        ('empty-store', 'knowledge::' + receipt + ':' + digest),
        ('long-store', 'knowledge:' + 'x' * 256 + ':' + receipt + ':' + digest),
        ('bad-prefix', 'Knowledge:store:' + receipt + ':' + digest),
        ('uppercase-digest', 'knowledge:store:' + receipt + ':' + 'B' * 64),
        ('bad-receipt', 'knowledge:store:receipt-' + 'g' * 32 + ':' + digest),
        ('trailing-data', 'knowledge:store:' + receipt + ':' + digest + 'x'),
        ('local-only', 'publication-local:task'),
        ('oversize-untrusted', 'x' * 10000),
    ]:
        cases.append((name, value, 0, None))
    queries = []
    for n, (_, value, _, _) in enumerate(cases):
        # Hex literals avoid interpreting any identifier as SQL or shell syntax.
        queries.append('SELECT ' + str(n) + ' n,' + ','.join(expression + ' AS ' + key for key, expression in expressions.items())
                       + ' FROM (SELECT CONVERT(0x' + value.encode().hex() + ' USING utf8mb4) AS no_effect_evidence) evidence')
    local_app.owned_container()
    result = subprocess.run(['docker', 'exec', local_app.NAME, 'sh', '-c',
                             'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names -e "$1"',
                             'query', ' UNION ALL '.join(queries)], text=True, capture_output=True, check=True)
    rows = result.stdout.splitlines()
    assert len(rows) == len(cases), (len(rows), result.stderr)
    for line in rows:
        n, store, actual_receipt, actual_digest, shape = line.split('\t')
        name, _, expected, expected_store = cases[int(n)]
        assert int(shape) == expected, (name, shape, expected)
        assert len(store) <= 255, (name, len(store))
        if expected:
            assert (store, actual_receipt, actual_digest) == (expected_store, receipt, digest), (name, line)
    report = {'pass': True, 'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'cases': [{'name': name, 'pass': True, 'expectedShape': shape} for name, _, shape, _ in cases]}
    (local_app.ROOT / '.tools/knowledge-publish-proof-reference-tests.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({'pass': True, 'cases': len(cases), 'database': 'existing owned application MySQL'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
