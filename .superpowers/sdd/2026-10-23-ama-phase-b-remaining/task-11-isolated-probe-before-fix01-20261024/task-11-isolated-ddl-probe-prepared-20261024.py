#!/usr/bin/env python3
"""Prepared, approval-gated Task11 isolated MySQL 8.4.6 DDL probe.

This file is data until root supplies the exact public ACK described in its report.
Never import it for inspection: use AST/compile(source, ..., 'exec') only.
"""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone
from uuid import UUID, uuid5

ROOT = Path('/home/tanren/ama/.worktrees/phase-b-completion')
W = ROOT / '.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
SELF = W / 'task-11-isolated-ddl-probe-prepared-20261024.py'
R1 = W / 'task-11-forward-ddl-proposal-r1-20261024.sql'
GUARD = W / 'task-08-owned-resource-ready-20261024.json'
ACK = W / 'task-11-isolated-ddl-probe-root-ack-20261024.json'
R1_SHA = '195bec3b2b85908b708027774a2551bc25e2deda766d2802e3b3f3234befd3c2'
GUARD_SHA = '334d4f881e2b658eb9f5c21217bea7fa3d029dbcbf33f7e9a05e546a1235eec3'
CID = '11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad'
BASELINE = 'de1611a67ba483afe21f3b29bb69c2d32028efef'
MIGRATION = 'backend/adapters/src/main/resources/db/migration/'
BASE_SEALS = {
    'V002__identity_acl.sql': '68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0',
    'V005__tasks_outbox.sql': '55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7',
    'V010__reliable_execution.sql': '03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db',
    'V013__publications.sql': '1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c',
}
CONSTRAINTS = {
    'oneAttempt': True, 'commandTimeoutSeconds': 15, 'maximumMysqlCommands': 300,
    'newSchemaOnly': True, 'noCleanupOrRetry': True, 'foreignKeyChecks': 1,
    'mysqlImageVersion': '8.4.6', 'noPrivateEnvironment': True,
}
COLS = ['task_id', 'publication_id', 'submission_no', 'confirmation_id',
        'provider_id', 'business_key', 'library_id', 'folder_id', 'requester_id',
        'logical_request_hash', 'execution_request_hash', 'request_manifest',
        'created_at', 'local_state', 'local_decided_at', 'local_reason',
        'local_decided_by', 'local_attempt_no', 'local_execution_id']
FKS = {
    'fk_publication_submission_task': ('task_id', 'durable_task', 'id'),
    'fk_publication_submission_publication': ('publication_id', 'publication', 'id'),
    'fk_publication_submission_confirmation': ('confirmation_id', 'final_confirmation', 'id'),
    'fk_publication_submission_requester': ('requester_id', 'enterprise_identity', 'enterprise_id'),
    'fk_publication_submission_decider': ('local_decided_by', 'enterprise_identity', 'enterprise_id'),
    'fk_publication_submission_execution': ('local_execution_id', 'external_execution', 'id'),
}
INDEXES = {
    'publication_submission_number': ['publication_id', 'submission_no'],
    'publication_submission_undecided': ['local_state', 'created_at', 'task_id'],
    'publication_submission_confirmation': ['confirmation_id'],
    'publication_submission_requester': ['requester_id'],
    'publication_submission_decider': ['local_decided_by'],
    'publication_submission_execution': ['local_execution_id'],
}
STUBS = [
    # Exact referenced key definitions from DE V010:72, V013:18/45, V002:5, V010:188.
    'CREATE TABLE durable_task (id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
    'CREATE TABLE publication (id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
    'CREATE TABLE final_confirmation (id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
    'CREATE TABLE enterprise_identity (enterprise_id varchar(255) COLLATE utf8mb4_bin NOT NULL, PRIMARY KEY(enterprise_id)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
    'CREATE TABLE external_execution (id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci',
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def fail(message):
    raise RuntimeError(message)


def public_file(path, expected):
    if path.is_symlink() or not path.is_file() or sha(path.read_bytes()) != expected:
        fail('public sealed input missing or changed: ' + path.name)
    return path.read_bytes()


def write_private(path, obj):
    data = json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=True).encode() + b'\n'
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(data)


def capture(argv, input_bytes=None, timeout=15):
    result = subprocess.run(argv, input=input_bytes, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout, check=False,
                            cwd=ROOT)
    if len(result.stdout) > 250000 or len(result.stderr) > 12000:
        fail('command output exceeded public bound')
    return result.returncode, result.stdout.decode('utf-8', 'replace'), result.stderr.decode('utf-8', 'replace')


def public_stderr(raw):
    # Never preserve an arbitrary process diagnostic verbatim. Expected MySQL
    # errors still retain exact errno, SQLSTATE, constraint, and public value.
    lines = []
    for line in raw.splitlines()[:8]:
        if re.match(r'^ERROR \d+ \([A-Z0-9]+\) at line \d+: ', line):
            scrubbed = re.sub(r'(?i)(password|passwd|secret|token|credential)\s*[:=]\s*\S+',
                              r'\1=[REDACTED]', line[:1000])
            lines.append(scrubbed)
        else:
            lines.append('[non-MySQL diagnostic redacted]')
    return '\n'.join(lines)


def verify_baseline():
    excerpts = {
        'V002__identity_acl.sql': ['enterprise_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL', 'PRIMARY KEY (enterprise_id)'],
        'V005__tasks_outbox.sql': ['CREATE TABLE durable_task (', 'id CHAR(36) NOT NULL PRIMARY KEY'],
        'V010__reliable_execution.sql': ['MODIFY COLUMN `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL FIRST', '`id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL', 'PRIMARY KEY (id)'],
        'V013__publications.sql': ['CREATE TABLE final_confirmation (', 'CREATE TABLE publication (', '`id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL', 'PRIMARY KEY (id)'],
    }
    for name, seal in BASE_SEALS.items():
        code, out, err = capture(['git', 'show', BASELINE + ':' + MIGRATION + name])
        if code or err or sha(out.encode()) != seal:
            fail('immutable migration baseline mismatch: ' + name)
        if any(fragment not in out for fragment in excerpts[name]):
            fail('immutable parent-key source excerpt mismatch: ' + name)


def q(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    if not isinstance(value, str) or '\x00' in value:
        fail('unsafe fixture value')
    return "'" + value.replace("'", "''") + "'"


class Probe:
    def __init__(self, owner, schema, state_dir):
        self.owner, self.schema, self.state_dir = owner, schema, state_dir
        self.commands, self.cases = [], []
        self.status = 'INTENT_RECORDED'
        self.mysql_count = 0
        self.namespace = UUID(owner.split('task11-probe-')[1])
        self.ids = {name: str(uuid5(self.namespace, name)) for name in
                    ['task', 'publication', 'confirmation', 'execution', 'missing']}
        self.ids['requester'] = 'probe-requester'
        self.ids['decider'] = 'probe-decider'
        self.ids['long_identity'] = 'x' * 255

    def record(self):
        result = {'utc': datetime.now(timezone.utc).isoformat(), 'status': self.status,
                  'owner': self.owner, 'schema': self.schema, 'cid': CID,
                  'helperSha256': sha(SELF.read_bytes()), 'r1Sha256': R1_SHA,
                  'commands': self.commands, 'cases': self.cases,
                  'mysqlCommands': self.mysql_count,
                  'qualification': 'Isolated exact CREATE/CHECK/FK behavior only; full parent prefix, future migration, writer, protocol and runtime remain unproven.'}
        # Append-only snapshots preserve every partial outcome, including a process crash.
        path = self.state_dir / ('result-%03d.json' % (len(list(self.state_dir.glob('result-*.json'))) + 1))
        write_private(path, result)

    def command(self, label, argv, stdin=None, expect_error=None, timeout=15):
        try:
            code, out, err = capture(argv, None if stdin is None else stdin.encode(), timeout)
        except BaseException as exc:
            self.commands.append({'label': label, 'argv': argv, 'stdinSql': stdin,
                                  'exitCode': None, 'stdout': '[command incomplete]',
                                  'stderr': '[command diagnostic redacted]',
                                  'failureType': type(exc).__name__})
            self.record()
            raise
        entry = {'label': label, 'argv': argv, 'stdinSql': stdin, 'exitCode': code,
                 'stdout': out, 'stderr': public_stderr(err)}
        self.commands.append(entry)
        self.record()
        if expect_error is None:
            if code or err:
                fail(label + ': unexpected command failure; see private public-SQL result')
        return code, out, err

    def mysql(self, label, sql, force=False):
        self.mysql_count += 1
        if self.mysql_count > CONSTRAINTS['maximumMysqlCommands']:
            fail('finite mysql command bound reached')
        prefix = ("SET NAMES utf8mb4; SET time_zone = '+00:00'; "
                  "SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'; "
                  "SET SESSION lock_wait_timeout = 5; SET SESSION innodb_lock_wait_timeout = 5; "
                  "SET SESSION max_execution_time = 5000; SET SESSION foreign_key_checks = 1; ")
        argv = ['docker', 'exec', '-i', CID, 'mysql',
                '--defaults-extra-file=/run/ama-task08/root.cnf', '--batch', '--raw',
                '--skip-column-names', '--connect-timeout=5']
        if force:
            argv.append('--force')
        return self.command(label, argv, prefix + sql + '\n', 'expected' if force else None)

    def scalar(self, label, sql):
        _, out, _ = self.mysql(label, sql)
        lines = [x for x in out.strip().splitlines() if x]
        if len(lines) != 1:
            fail(label + ': expected one scalar row')
        return lines[0]

    def valid(self, label, **overrides):
        d = dict(task_id=self.ids['task'], publication_id=self.ids['publication'],
                 submission_no=1, confirmation_id=self.ids['confirmation'], provider_id='provider',
                 business_key='business', library_id='library', folder_id='folder',
                 requester_id=self.ids['requester'], logical_request_hash='a'*64,
                 execution_request_hash='b'*64, request_manifest='{}',
                 created_at='2026-10-24 00:00:00.000000', local_state='UNDECIDED',
                 local_decided_at=None, local_reason=None, local_decided_by=None,
                 local_attempt_no=None, local_execution_id=None)
        d.update(overrides)
        return d

    def insert(self, row):
        values = [q(row[c]) for c in COLS]
        return ('INSERT INTO publication_submission (' + ','.join('`'+c+'`' for c in COLS)
                + ') VALUES (' + ','.join(values) + ')')

    def positive(self, label, row):
        key = q(row['task_id'])
        sql = ('START TRANSACTION; ' + self.insert(row) + '; '
               + 'SELECT COUNT(*) FROM publication_submission WHERE task_id=' + key
               + '; ROLLBACK; SELECT COUNT(*) FROM publication_submission WHERE task_id=' + key + ';')
        _, out, _ = self.mysql('positive:' + label, sql)
        if out.strip().splitlines() != ['1', '0']:
            fail('positive case count/rollback failed: ' + label)
        self.cases.append({'name': label, 'expected': 'insert accepted, 1 then 0', 'observed': out.strip()})
        self.record()

    def negative(self, label, row, errno, constraint, failing_sql=None, before_sql=None, parent_check=None):
        control = self.valid('control:' + label)
        self.positive('control:' + label, control)
        key = q(row['task_id'])
        attempted = failing_sql or self.insert(row)
        sql = ('START TRANSACTION; ' + (before_sql + '; ' if before_sql else '')
               + attempted + '; ROLLBACK; SELECT \'END_TXN\';')
        code, out, err = self.mysql('negative:' + label, sql, force=True)
        lines = [line for line in err.splitlines() if line.startswith('ERROR ')]
        match = re.fullmatch(r'ERROR (\d+) \(([A-Z0-9]+)\) at line \d+: (.+)', lines[0]) if len(lines) == 1 else None
        if (not match or int(match.group(1)) != errno or constraint.lower() not in match.group(3).lower()
                or out.strip().splitlines()[-1:] != ['END_TXN'] or code not in (0, 1)):
            fail('negative case wrong server error or rollback footer: ' + label)
        residual = self.scalar('residual:' + label,
                               'SELECT COUNT(*) FROM publication_submission WHERE task_id=' + key + ';')
        if residual != '0':
            fail('negative case persisted a row: ' + label)
        if parent_check and self.scalar('parent-fixed:' + label, parent_check) != '1':
            fail('referenced parent changed: ' + label)
        self.cases.append({'name': label, 'expectedErrno': errno, 'constraint': constraint,
                           'observedError': public_stderr(match.group(0)), 'exitCode': code,
                           'rolledBack': True, 'residualRows': 0})
        self.record()


def verify_resource(probe, guard):
    selected = guard['selectedReady']
    if selected['id'] != CID or selected['running'] is not True or selected['oom_killed'] is not False:
        fail('public selected resource guard mismatch')
    # Deliberately select only public fields; never inspect Config.Env or full object.
    template = ('{{json .Id}}\t{{json .Name}}\t{{json .Config.Image}}\t{{json .Image}}\t'
                '{{json (index .Config.Labels "ama.test-fixture")}}\t{{json .State.Running}}\t'
                '{{json .State.OOMKilled}}\t{{json .HostConfig.Memory}}\t'
                '{{json .HostConfig.MemorySwap}}\t{{json .HostConfig.NanoCpus}}\t'
                '{{json .HostConfig.PidsLimit}}\t{{json .NetworkSettings.Ports}}\t{{json .Mounts}}')
    _, out, _ = probe.command('selected-resource-fields', ['docker', 'inspect', '--format', template, CID])
    fields = out.strip().split('\t')
    if len(fields) != 13:
        fail('selected resource field count mismatch')
    actual = [json.loads(x) for x in fields]
    if len(actual[12]) != len(selected['mounts']):
        fail('selected resource mount count changed')
    actual[12] = [{key: mount[key] for key in selected_mount}
                  for mount, selected_mount in zip(actual[12], selected['mounts'])]
    expected = [selected['id'], selected['name'], selected['image'], selected['image_id'],
                selected['owner'], True, False, selected['memory'], selected['memory_swap'],
                selected['nano_cpus'], selected['pids'], selected['ports'], selected['mounts']]
    if actual != expected:
        fail('selected resource identity/image/owner/limits/ports/mounts changed')
    if selected['ports'] != {'3306/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '44727'}]}:
        fail('resource port is not the exact accepted loopback binding')


def verify_metadata(probe):
    s = q(probe.schema)
    def rows(label, query):
        return probe.mysql(label, query)[1].strip().splitlines()
    cols = rows('columns', 'SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLLATION_NAME,\'\') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='+s+' AND TABLE_NAME=\'publication_submission\' ORDER BY ORDINAL_POSITION;')
    if [line.split('\t')[0] for line in cols] != COLS:
        fail('19 column names/order mismatch')
    expected_types = ['char(36)','char(36)','bigint unsigned','char(36)','varchar(64)',
                      'varchar(255)','varchar(255)','varchar(255)','varchar(255)',
                      'char(64)','char(64)','longtext','datetime(6)','varchar(32)',
                      'datetime(6)','varchar(64)','varchar(255)','bigint unsigned','char(36)']
    if [line.split('\t')[1] for line in cols] != expected_types:
        fail('19 column type mismatch')
    expected_nulls = ['NO']*14 + ['YES']*5
    expected_collations = ['ascii_bin','ascii_bin','', 'ascii_bin','ascii_bin',
                           'utf8mb4_0900_bin','utf8mb4_0900_bin','utf8mb4_0900_bin',
                           'utf8mb4_0900_bin','ascii_bin','ascii_bin','utf8mb4_0900_ai_ci',
                           '', 'ascii_bin', '', 'ascii_bin','utf8mb4_0900_bin','','ascii_bin']
    if [line.split('\t')[2] for line in cols] != expected_nulls or \
       [line.split('\t')[3] for line in cols] != expected_collations:
        fail('19 column nullability/collation mismatch')
    checks = rows('checks', 'SELECT CONSTRAINT_NAME,ENFORCED FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='+s+' AND TABLE_NAME=\'publication_submission\' AND CONSTRAINT_TYPE=\'CHECK\' ORDER BY CONSTRAINT_NAME;')
    if checks != ['ck_publication_submission_'+str(i)+'\tYES' for i in range(4)]:
        fail('four enforced CHECK mismatch')
    fks = rows('foreign-keys', 'SELECT k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA='+s+' AND k.TABLE_NAME=\'publication_submission\' AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.CONSTRAINT_NAME;')
    parsed = {v[0]: v[1:] for line in fks if len(v:=line.split('\t')) == 6}
    if set(parsed) != set(FKS):
        fail('six named FK mismatch')
    for name, keys in FKS.items():
        if parsed[name][:3] != list(keys) or parsed[name][3] not in ('RESTRICT','NO ACTION') or parsed[name][4] not in ('RESTRICT','NO ACTION'):
            fail('FK target or immediate rule mismatch: ' + name)
    idx = rows('indexes', 'SELECT INDEX_NAME,SEQ_IN_INDEX,COLUMN_NAME,NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='+s+' AND TABLE_NAME=\'publication_submission\' ORDER BY INDEX_NAME,SEQ_IN_INDEX;')
    found = {}
    for line in idx:
        name, seq, col, nonunique = line.split('\t')
        found.setdefault(name, []).append((int(seq),col,int(nonunique)))
    if set(found) != set(INDEXES)|{'PRIMARY'}:
        fail('six secondary index names mismatch')
    if found['PRIMARY'] != [(1,'task_id',0)]:
        fail('primary index mismatch')
    for name, columns in INDEXES.items():
        expected = [(i+1,c,0 if name == 'publication_submission_number' else 1)
                    for i,c in enumerate(columns)]
        if found[name] != expected:
            fail('secondary index mismatch: '+name)
    show = probe.mysql('show-create', 'SHOW CREATE TABLE publication_submission;')[1]
    if not show.startswith('publication_submission\t') or any(name not in show for name in list(FKS)+['ck_publication_submission_'+str(i) for i in range(4)]):
        fail('SHOW CREATE missing named constraints')


def run_cases(p):
    for label, expression, expected in [
        ('ascii-65536',"REPEAT('a',65536)",'65536'),
        ('ascii-65537',"REPEAT('a',65537)",'65537'),
        ('utf8-65535',"REPEAT('你',21845)",'65535'),
        ('utf8-65538',"REPEAT('你',21846)",'65538')]:
        if p.scalar('octet-length-'+label,'SELECT OCTET_LENGTH('+expression+');') != expected:
            fail('server fixture OCTET_LENGTH mismatch: '+label)
    p.positive('undecided-all-null', p.valid('undecided-all-null'))
    for reason in ['PREPARATION_EXHAUSTED','TERMINAL_UNMARKED_FAILURE','EXPIRED_UNMARKED_ABANDONED']:
        p.positive('never:'+reason, p.valid('never:'+reason, local_state='NEVER_MARKED',
                   local_decided_at='2026-10-24 00:00:01.000000',local_reason=reason,
                   local_decided_by=p.ids['decider'],local_attempt_no=1))
    p.positive('never-execution-present', p.valid('never-execution-present', local_state='NEVER_MARKED',
               local_decided_at='2026-10-24 00:00:01.000000', local_reason='PREPARATION_EXHAUSTED',
               local_decided_by=p.ids['decider'], local_attempt_no=1,
               local_execution_id=p.ids['execution']))
    p.positive('submission-min', p.valid('submission-min', submission_no=1))
    p.positive('submission-max', p.valid('submission-max', submission_no=9223372036854775806))
    p.positive('manifest-65536-ascii', p.valid('manifest-65536-ascii', request_manifest='a'*65536))
    p.positive('manifest-65535-multibyte', p.valid('manifest-65535-multibyte',request_manifest='你'*21845))
    p.positive('provider-64', p.valid('provider-64',provider_id='x'*64))
    for col in ['business_key','library_id','folder_id']:
        p.positive(col+'-255', p.valid(col+'-255', **{col:'x'*255}))
    p.positive('requester-255',p.valid('requester-255',requester_id=p.ids['long_identity']))
    p.positive('decider-255',p.valid('decider-255',local_state='NEVER_MARKED',
               local_decided_at='2026-10-24 00:00:01.000000',local_reason='PREPARATION_EXHAUSTED',
               local_decided_by=p.ids['long_identity'],local_attempt_no=1))
    for col in ['logical_request_hash','execution_request_hash']:
        p.positive(col+'-64', p.valid(col+'-64',**{col:'x'*64}))
    def neg(label, row, constraint, errno=3819):
        p.negative(label, row, errno, constraint)
    for col,value in [('local_decided_at','2026-10-24 00:00:01.000000'),
                      ('local_reason','PREPARATION_EXHAUSTED'),('local_decided_by',p.ids['decider']),
                      ('local_attempt_no',1),('local_execution_id',p.ids['execution'])]:
        neg('undecided-nonnull-'+col,p.valid('undecided-nonnull-'+col,**{col:value}),'ck_publication_submission_3')
    base = dict(local_state='NEVER_MARKED',local_decided_at='2026-10-24 00:00:01.000000',
                local_reason='PREPARATION_EXHAUSTED',local_decided_by=p.ids['decider'],local_attempt_no=1)
    for col in ['local_decided_at','local_reason','local_decided_by','local_attempt_no']:
        neg('never-null-'+col,p.valid('never-null-'+col,**(base|{col:None})),'ck_publication_submission_3')
    for value in ['UNDECIDED ','NEVER_MARKED ','undecided','NEVER_marked','','OTHER']:
        neg('state-'+repr(value),p.valid('state-'+repr(value),local_state=value),'ck_publication_submission_1')
    for value in ['PREPARATION_EXHAUSTED ','TERMINAL_UNMARKED_FAILURE ',
                  'EXPIRED_UNMARKED_ABANDONED ','preparation_exhausted','','OTHER']:
        neg('reason-'+repr(value),p.valid('reason-'+repr(value),**(base|{'local_reason':value})),'ck_publication_submission_3')
    for value in [0,9223372036854775807]:
        neg('submission-'+str(value),p.valid('submission-'+str(value),submission_no=value),'ck_publication_submission_0')
    neg('manifest-65537-ascii',p.valid('manifest-65537-ascii',request_manifest='a'*65537),'ck_publication_submission_2')
    neg('manifest-65538-multibyte',p.valid('manifest-65538-multibyte',request_manifest='你'*21846),'ck_publication_submission_2')
    for col,limit in [('provider_id',64),('business_key',255),('library_id',255),
                      ('folder_id',255),('requester_id',255),
                      ('logical_request_hash',64),('execution_request_hash',64),
                      ('task_id',36),('publication_id',36),('confirmation_id',36)]:
        neg('too-long-'+col,p.valid('too-long-'+col,**{col:'x'*(limit+1)}),col,1406)
    neg('too-long-decider',p.valid('too-long-decider',**(base|{'local_decided_by':'x'*256})),
        'local_decided_by',1406)
    neg('too-long-execution-id',p.valid('too-long-execution-id',
        **(base|{'local_execution_id':'x'*37})),'local_execution_id',1406)
    for name,(col,_,_) in FKS.items():
        changes = base.copy() if col in ('local_decided_by','local_execution_id') else {}
        changes[col] = p.ids['missing']
        neg('missing-parent-'+name,p.valid('missing-parent-'+name,**changes),name,1452)
    # Parent mutations occur only inside one transaction with a valid child.
    parent = {'fk_publication_submission_task':('durable_task','id',p.ids['task']),
              'fk_publication_submission_publication':('publication','id',p.ids['publication']),
              'fk_publication_submission_confirmation':('final_confirmation','id',p.ids['confirmation']),
              'fk_publication_submission_requester':('enterprise_identity','enterprise_id',p.ids['requester']),
              'fk_publication_submission_decider':('enterprise_identity','enterprise_id',p.ids['decider']),
              'fk_publication_submission_execution':('external_execution','id',p.ids['execution'])}
    for name,(table,col,value) in parent.items():
        for action in ['delete','update']:
            label = name+'-'+action
            changes = {}
            if name.endswith('decider'):
                changes = base.copy()
            if name.endswith('execution'):
                changes = base|{'local_execution_id':p.ids['execution']}
            row = p.valid(label,**changes)
            where = '`'+col+'`='+q(value)
            statement = ('DELETE FROM '+table+' WHERE '+where if action=='delete' else
                         'UPDATE '+table+' SET `'+col+'`='+q(p.ids['missing'])+' WHERE '+where)
            p.negative(label,row,1451,name,failing_sql=statement,
                       before_sql=p.insert(row),
                       parent_check='SELECT COUNT(*) FROM '+table+' WHERE '+where+';')


def main():
    if sys.argv != [str(SELF), '--execute-reviewed-isolated-task11-probe']:
        fail('execution requires the exact reviewed public flag')
    if Path(__file__).resolve() != SELF or SELF.is_symlink() or ACK.is_symlink() or ACK.resolve().parent != W:
        fail('helper/ACK canonical path mismatch')
    helper_sha = sha(SELF.read_bytes())
    public_file(R1,R1_SHA)
    guard = json.loads(public_file(GUARD,GUARD_SHA))
    verify_baseline()
    if not ACK.is_file():
        fail('root public application ACK absent')
    ack = json.loads(ACK.read_text())
    expected_keys = {'status','helperSha256','r1Sha256','owner','schema','cid','constraints'}
    if set(ack) != expected_keys or ack['status'] != 'ROOT_ACK_EXACT_ISOLATED_TASK11_SQL_PROBE' or \
       ack['helperSha256'] != helper_sha or ack['r1Sha256'] != R1_SHA or \
       ack['cid'] != CID or ack['constraints'] != CONSTRAINTS:
        fail('root ACK contract mismatch')
    owner, schema = ack['owner'], ack['schema']
    if not isinstance(owner,str) or not re.fullmatch(r'task11-probe-[0-9a-f]{32}',owner) or \
       schema != 'ama_t11_'+owner[-32:-20]+'_probe':
        fail('root ACK owner/schema contract mismatch')
    state_dir = ROOT/'.tools'/owner
    if state_dir.exists() or state_dir.is_symlink() or list((ROOT/'.tools').glob('task11-probe-*')):
        fail('preexisting or orphan probe state')
    state_dir.mkdir(mode=0o700)
    p = Probe(owner,schema,state_dir)
    p.record()
    try:
        verify_resource(p,guard)
        p.status = 'RESOURCE_VERIFIED'; p.record()
        if p.scalar('server-version','SELECT VERSION();') != '8.4.6':
            fail('server is not exact MySQL 8.4.6')
        if p.scalar('schema-absent','SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='+q(schema)+';') != '0':
            fail('target schema already exists')
        if p.scalar('other-probe-schemas-absent',
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE LEFT(SCHEMA_NAME,8)='ama_t11_';") != '0':
            fail('preexisting or orphan Task11 probe schema')
        p.status = 'SCHEMA_ABSENT_VERIFIED'; p.record()
        p.mysql('create-isolated-schema','CREATE DATABASE `'+schema+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
        p.status = 'SCHEMA_CREATED'; p.record()
        for i,stub in enumerate(STUBS):
            p.mysql('create-parent-stub-'+str(i+1),'USE `'+schema+'`; '+stub+';')
        p.status = 'PARENT_STUBS_CREATED'; p.record()
        # Verify exact parent PK column type/collation before the prospective CREATE.
        expect = {'durable_task':('id','char(36)','ascii_bin'),
                  'publication':('id','char(36)','ascii_bin'),
                  'final_confirmation':('id','char(36)','ascii_bin'),
                  'enterprise_identity':('enterprise_id','varchar(255)','utf8mb4_bin'),
                  'external_execution':('id','char(36)','ascii_bin')}
        for table,(col,typ,coll) in expect.items():
            query = ('SELECT COLUMN_NAME,COLUMN_TYPE,COLLATION_NAME FROM information_schema.COLUMNS '
                     'WHERE TABLE_SCHEMA='+q(schema)+' AND TABLE_NAME='+q(table)+';')
            if p.scalar('parent-column-'+table,query) != '\t'.join([col,typ,coll]):
                fail('parent key metadata mismatch: '+table)
            query = ('SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='+q(schema)
                     +' AND TABLE_NAME='+q(table)+' AND INDEX_NAME=\'PRIMARY\';')
            if p.scalar('parent-pk-'+table,query) != col:
                fail('parent primary key mismatch: '+table)
        r1_sql = R1.read_text()
        p.status = 'EXACT_R1_CREATE_ATTEMPTED'; p.record()
        p.mysql('exact-byte-r1-create','USE `'+schema+'`; '+r1_sql)
        p.status = 'EXACT_R1_CREATED'; p.record()
        p.mysql('use-schema','USE `'+schema+'`;')
        # Every later invocation selects this one isolated schema explicitly.
        old_mysql = p.mysql
        def in_schema(label,sql,force=False):
            return old_mysql(label,'USE `'+schema+'`; '+sql,force)
        p.mysql = in_schema
        verify_metadata(p)
        p.status = 'METADATA_VERIFIED'; p.record()
        for table,col,value in [('durable_task','id',p.ids['task']),
                                ('publication','id',p.ids['publication']),
                                ('final_confirmation','id',p.ids['confirmation']),
                                ('external_execution','id',p.ids['execution']),
                                ('enterprise_identity','enterprise_id',p.ids['requester']),
                                ('enterprise_identity','enterprise_id',p.ids['decider']),
                                ('enterprise_identity','enterprise_id',p.ids['long_identity'])]:
            p.mysql('seed-parent-'+table+'-'+value[:12],
                    'INSERT INTO '+table+' (`'+col+'`) VALUES ('+q(value)+');')
        p.status = 'PARENTS_FIXED'; p.record()
        run_cases(p)
        p.status = 'QUALIFIED_ISOLATED_SQL_PROBE_ONLY'; p.record()
    except BaseException as exc:
        p.status = 'PARTIAL_HARD_STOP'
        p.cases.append({'failureType':type(exc).__name__, 'publicMessage':str(exc)[:500]})
        p.record()
        raise


if __name__ == '__main__':
    main()
