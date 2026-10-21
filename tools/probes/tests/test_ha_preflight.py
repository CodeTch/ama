from pathlib import Path
import copy
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

MODULE = Path(__file__).resolve().parents[1] / 'ha_preflight.py'
REPO = MODULE.parents[2]


def supplied_inventory(database='private-ha', storage='local', provider=None):
    """Synthetic, internally complete facts; never operational evidence."""
    inventory = {
        'schemaVersion': 1,
        'inventoryId': 'synthetic-test-revision-1',
        'database': {'mode': database},
        'storage': {'type': storage, 'backendId': 'test-files'},
        'nodes': [
            {'id': node, 'vmId': 'vm-' + node, 'hostId': 'host-' + node,
             'storageDomain': 'disk-' + node, 'powerDomain': 'power-' + node,
             'os': 'RHEL', 'version': '8.10', 'arch': 'x86_64',
             'vcpus': 16, 'memoryGiB': 32}
            for node in ['test-a', 'test-b']
        ],
        'virtualization': {'product': 'test-hypervisor', 'version': 'test-version'},
        'faultDomains': {'antiAffinity': True, 'sharedRiskAcknowledged': True},
        'ingress': {'mode': 'enterprise-lb'},
        'fencing': {
            'enabled': True, 'provider': 'test-management-plane', 'agent': 'test-fence-agent',
            'managementPathIndependent': True, 'powerOffVerified': True,
            'targets': [{'nodeId': n, 'vmId': 'vm-' + n} for n in ['test-a', 'test-b']],
        },
        'replication': {
            'protocol': 'C',
            'network': {'id': 'test-sync-net', 'independent': True, 'rttMs': 1,
                        'throughputMbps': 1000, 'packetLossPercent': 0},
            'volumes': [],
        },
        'cluster': {
            'manager': 'pacemaker', 'singleLifecycleManager': True,
            'fencingBeforePromotion': True, 'twoNodePolicyReviewed': True,
            'startupFencing': True, 'singlePrimary': True, 'mountExclusive': True,
            'orderedColocated': True, 'automaticFailback': False,
            'rejoinRequiresResync': True, 'refusePromotionOnUnknown': True,
        },
    }
    components = []
    if database == 'private-ha':
        components.append('mysql-data')
    if storage == 'local':
        inventory['storage']['root'] = '/srv/test-files'
        components += ['local-files', 'upload-staging', 'recovery-manifests']
    else:
        inventory['storage']['provider'] = provider or 'enterprise-platform'
        if provider == 'private':
            inventory['storage']['metadataDatabaseMode'] = database
            inventory['storage']['metadataSchema'] = 'test_filer'
            components += ['s3-objects', 's3-indexes', 's3-master-state', 's3-config']
            if database == 'private-ha':
                components.append('s3-filer-metadata')
    if components:
        inventory['replication']['volumes'] = [{
            'id': 'test-volume', 'filesystem': 'xfs', 'mountPoint': '/srv',
            'filesystemUuid': '11111111-2222-3333-4444-555555555555',
            'observedMountUuid': '11111111-2222-3333-4444-555555555555',
            'devices': {'test-a': '/dev/test-data', 'test-b': '/dev/test-data'},
            'capacityGiB': {'test-a': 100, 'test-b': 100},
            'mountedOn': ['test-a'], 'primaryOn': ['test-a'],
            'replicas': {'test-a': 'UpToDate', 'test-b': 'UpToDate'},
            'protectedComponents': components,
            'componentPaths': {component: '/srv/' + ('test-files' if component == 'local-files' else component)
                               for component in components},
        }]
    return inventory


def attach_evidence(inventory, observed_at=None):
    """Produce schema-valid synthetic records for every possible identifier."""
    inventory.pop('evidence', None)
    inventory.pop('drills', None)
    fingerprint = hashlib.sha256(json.dumps(
        inventory, sort_keys=True, separators=(',', ':'), ensure_ascii=False,
        allow_nan=False).encode('utf-8')).hexdigest()
    record = {
        'status': 'passed',
        'observedAt': observed_at or datetime.now(timezone.utc).isoformat(),
        'recordedBy': 'test-operator', 'reviewedBy': 'test-independent-reviewer',
        'inventorySha256': fingerprint,
        'summary': 'Synthetic isolated test evidence for validator tests only',
        'artifact': {'reference': 'test-evidence/run.log',
                     'sha256': hashlib.sha256(b'synthetic log').hexdigest()},
    }
    inventory['evidence'] = {f'PR-{n:02}': copy.deepcopy(record) for n in range(1, 16)}
    inventory['evidence'].update({f'PL-{n:02}': copy.deepcopy(record) for n in range(1, 6)})
    inventory['drills'] = {f'HA-{n:02}': copy.deepcopy(record) for n in range(1, 15)}
    return inventory


class PreflightTests(unittest.TestCase):
    def validator(self):
        self.assertTrue(MODULE.exists(), 'Missing read-only topology validator')
        spec = importlib.util.spec_from_file_location('ha_preflight', MODULE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.validate_topology

    # Removing the fencing/domain gate must expose this unsafe topology.
    def test_unsafe_topology_cannot_pass(self):
        for change in ['same-host', 'no-fence']:
            with self.subTest(change=change):
                inventory = supplied_inventory()
                if change == 'same-host':
                    inventory['nodes'][1]['hostId'] = inventory['nodes'][0]['hostId']
                else:
                    inventory['fencing']['enabled'] = False
                result = self.validator()(attach_evidence(inventory))
                self.assertFalse(result['canClaimAutomaticRecovery'])
                self.assertFalse(result['canDeploy'])
                self.assertTrue(result['blockers'])

    # An external database must not bypass fencing for local files.
    def test_localStillNeedsFencingWithExternalDatabase(self):
        inventory = supplied_inventory('enterprise-platform')
        inventory['fencing']['enabled'] = False
        result = self.validator()(attach_evidence(inventory))
        self.assertFalse(result['canClaimAutomaticRecovery'])
        self.assertIn('PR-02', [item['id'] for item in result['blockers']])
        self.assertIn('PR-15', result['applicablePrerequisites'])
        self.assertNotIn('PR-10', result['applicablePrerequisites'])

    # Prevent permanent-false implementations and accidental coupling of DB/storage.
    def test_complete_scoped_evidence_can_satisfy_each_supported_combination(self):
        for db in ['private-ha', 'enterprise-platform']:
            for storage, provider in [('local', None), ('s3', 'private'),
                                      ('s3', 'enterprise-platform')]:
                with self.subTest(db=db, storage=storage, provider=provider):
                    inventory = attach_evidence(supplied_inventory(db, storage, provider))
                    before = copy.deepcopy(inventory)
                    result = self.validator()(inventory)
                    self.assertEqual([], result['blockers'])
                    self.assertTrue(result['canDeploy'])
                    self.assertTrue(result['canClaimAutomaticRecovery'])
                    self.assertEqual(inventory, before, 'Validator must not mutate its input')
                    self.assertEqual('supplied-evidence-only', result['assessment'])

    # Platform data services must not invent a private disk or fence prerequisite.
    def test_platform_database_and_s3_use_platform_and_ingress_evidence(self):
        inventory = supplied_inventory('enterprise-platform', 's3', 'enterprise-platform')
        for key in ['fencing', 'replication', 'cluster', 'virtualization']:
            inventory.pop(key)
        result = self.validator()(attach_evidence(inventory))
        self.assertTrue(result['canDeploy'])
        for code in ['PR-01', 'PR-02', 'PR-04', 'PR-09', 'PR-10', 'PR-15']:
            self.assertNotIn(code, result['applicablePrerequisites'])
        for code in ['PL-01', 'PL-02', 'PL-03', 'PL-04', 'PL-05']:
            self.assertIn(code, result['applicablePrerequisites'])
        self.assertNotIn('HA-03', result['applicableDrills'])
        self.assertNotIn('HA-13', result['applicableDrills'])

    # A project-managed VIP reintroduces cluster fencing even with platform data.
    def test_cluster_ingress_still_needs_fencing_with_all_platform_data(self):
        inventory = supplied_inventory('enterprise-platform', 's3', 'enterprise-platform')
        inventory['ingress']['mode'] = 'cluster-vip'
        inventory['fencing']['enabled'] = False
        result = self.validator()(attach_evidence(inventory))
        self.assertFalse(result['canDeploy'])
        self.assertIn('PR-02', result['applicablePrerequisites'])
        self.assertNotIn('PR-04', result['applicablePrerequisites'])

    # Indexes, master state and filer metadata are recoverable data, not disposable caches.
    def test_private_s3_missing_protected_component_is_blocked(self):
        for component in ['s3-objects', 's3-indexes', 's3-master-state', 's3-config',
                          's3-filer-metadata']:
            with self.subTest(component=component):
                inventory = supplied_inventory('private-ha', 's3', 'private')
                inventory['replication']['volumes'][0]['protectedComponents'].remove(component)
                result = self.validator()(attach_evidence(inventory))
                self.assertFalse(result['canDeploy'])
                self.assertIn('PR-10', [item['id'] for item in result['blockers']])

    def test_external_filer_metadata_requires_platform_database_evidence(self):
        inventory = attach_evidence(supplied_inventory('enterprise-platform', 's3', 'private'))
        del inventory['evidence']['PL-01']
        self.assertFalse(self.validator()(inventory)['canDeploy'])

    # Inventory facts remain mandatory even when a report claims success.
    def test_unsafe_mount_replica_disk_and_resource_facts_cannot_be_overridden_by_evidence(self):
        mutations = [
            ('missing-mount-identity', lambda x: x['replication']['volumes'][0].pop('observedMountUuid')),
            ('wrong-mount-identity', lambda x: x['replication']['volumes'][0].update(observedMountUuid='aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')),
            ('double-mount', lambda x: x['replication']['volumes'][0].update(mountedOn=['test-a', 'test-b'])),
            ('double-primary', lambda x: x['replication']['volumes'][0].update(primaryOn=['test-a', 'test-b'])),
            ('old-replica', lambda x: x['replication']['volumes'][0]['replicas'].update({'test-b': 'Outdated'})),
            ('replicas-not-object', lambda x: x['replication']['volumes'][0].update(replicas=True)),
            ('missing-device', lambda x: x['replication']['volumes'][0]['devices'].pop('test-b')),
            ('capacity-not-number', lambda x: x['replication']['volumes'][0]['capacityGiB'].update({'test-b': True})),
            ('wrong-kernel-target', lambda x: x['nodes'][0].update(version='9.0')),
            ('wrong-resources', lambda x: x['nodes'][0].update(memoryGiB=16)),
            ('wrong-fence-target', lambda x: x['fencing']['targets'][0].update(vmId='unrelated-vm')),
            ('guest-stop-not-fence', lambda x: x['fencing'].update(powerOffVerified=False)),
            ('shared-management', lambda x: x['fencing'].update(managementPathIndependent=False)),
            ('async-replication', lambda x: x['replication'].update(protocol='A')),
            ('auto-failback', lambda x: x['cluster'].update(automaticFailback=True)),
            ('no-cluster', lambda x: x.update(cluster=[])),
            ('unsafe-root', lambda x: x['storage'].update(root='/srv/../etc')),
            ('root-outside-volume', lambda x: x['storage'].update(root='/var/unprotected')),
            ('missing-component-path', lambda x: x['replication']['volumes'][0]['componentPaths'].pop('upload-staging')),
            ('component-outside-volume', lambda x: x['replication']['volumes'][0]['componentPaths'].update({'mysql-data': '/outside/mysql'})),
            ('duplicate-component', lambda x: x['replication']['volumes'].append(copy.deepcopy(x['replication']['volumes'][0]) | {'id': 'test-volume-two'})),
            ('overflow-throughput', lambda x: x['replication']['network'].update(throughputMbps=10 ** 400)),
            ('overflow-loss', lambda x: x['replication']['network'].update(packetLossPercent=10 ** 400)),
            ('negative-rtt', lambda x: x['replication']['network'].update(rttMs=-0.5)),
        ]
        for name, mutate in mutations:
            with self.subTest(name=name):
                inventory = supplied_inventory()
                mutate(inventory)
                result = self.validator()(attach_evidence(inventory))
                self.assertFalse(result['canDeploy'])
                self.assertFalse(result['canClaimAutomaticRecovery'])

    # A bool, stale report, self-review or changed topology must never qualify.
    def test_evidence_must_be_current_structured_independently_reviewed_and_scope_bound(self):
        for bad in [True, {}, {'status': 'passed'}, None]:
            with self.subTest(bad=bad):
                inventory = attach_evidence(supplied_inventory())
                inventory['evidence']['PR-02'] = bad
                self.assertFalse(self.validator()(inventory)['canDeploy'])
        for field, value in [
            ('observedAt', '2000-01-01T00:00:00Z'),
            ('observedAt', '2099-01-01T00:00:00Z'),
            ('observedAt', '2026-10-21T00:00:00'),
            ('observedAt', 'garbage'),
            ('inventorySha256', '0' * 64), ('reviewedBy', 'test-operator'),
            ('status', 'pending'), ('summary', 'pending'),
            ('observedAt', (datetime.now(timezone.utc) - timedelta(days=31)).isoformat()),
            ('observedAt', (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()),
            ('artifact', {'reference': 'run.log', 'sha256': 'not-a-hash'}),
        ]:
            with self.subTest(field=field, value=value):
                inventory = attach_evidence(supplied_inventory())
                inventory['evidence']['PR-02'][field] = value
                self.assertFalse(self.validator()(inventory)['canDeploy'])
        inventory = attach_evidence(supplied_inventory())
        inventory['nodes'][0]['hostId'] = 'changed-host'
        self.assertFalse(self.validator()(inventory)['canDeploy'])

    # Prerequisites alone permit readiness, never claim a completed fault drill.
    def test_missing_or_malformed_drill_evidence_blocks_only_recovery_claim(self):
        for bad in [None, True, {'status': 'passed'}]:
            with self.subTest(bad=bad):
                inventory = attach_evidence(supplied_inventory())
                inventory['drills']['HA-05'] = bad
                result = self.validator()(inventory)
                self.assertTrue(result['canDeploy'])
                self.assertFalse(result['canClaimAutomaticRecovery'])
                self.assertIn('HA-05', [item['id'] for item in result['blockers']])

    def test_malformed_inputs_return_blockers_instead_of_crashing(self):
        for bad in [None, True, [], 'x', {},
                    {'database': [], 'storage': True, 'nodes': {}},
                    {'schemaVersion': True, 'nodes': [None, 'x']},
                    {'nodes': [{'id': 'same'}, {'id': 'same'}]},
                    {'nodes': [{}, {}, {}]}, {'badNumber': float('nan')},
                    {'database': {'mode': []}, 'storage': {'type': {}}},
                    {'nodes': [{'id': []}, {'id': {}}]}]:
            with self.subTest(bad=bad):
                result = self.validator()(bad)
                self.assertFalse(result['canDeploy'])
                self.assertFalse(result['canClaimAutomaticRecovery'])
                self.assertTrue(result['blockers'])

    # Any shell, subprocess or socket attempt in the actual CLI process fails the test.
    def test_cli_never_executes_commands_or_opens_network_even_without_configuration(self):
        guard = """import sys, runpy
blocked = {'os.system', 'subprocess.Popen', 'socket.__new__', 'os.posix_spawn', 'os.exec'}
def audit(event, args):
    if event in blocked:
        raise RuntimeError('Unexpected external operation: ' + event)
sys.addaudithook(audit)
sys.argv = sys.argv[1:]
runpy.run_path(sys.argv[0], run_name='__main__')
"""
        completed = subprocess.run([sys.executable, '-c', guard, str(MODULE)],
                                   capture_output=True, text=True, timeout=10)
        self.assertEqual(2, completed.returncode, completed.stderr)
        self.assertFalse(completed.stderr)
        self.assertFalse(json.loads(completed.stdout)['canClaimAutomaticRecovery'])
        with tempfile.TemporaryDirectory() as directory:
            inventory_path = Path(directory) / 'inventory.json'
            marker = Path(directory) / 'must-not-exist'
            inventory = attach_evidence(supplied_inventory())
            inventory['command'] = 'touch ' + str(marker)
            inventory_path.write_text(json.dumps(inventory))
            completed = subprocess.run([sys.executable, '-c', guard, str(MODULE), str(inventory_path)],
                                       capture_output=True, text=True, timeout=10)
            self.assertEqual(1, completed.returncode, completed.stderr)
            self.assertFalse(completed.stderr)
            self.assertFalse(marker.exists())

    def test_example_preserves_pending_operations_and_requires_no_s3_for_local(self):
        self.assertTrue((REPO / 'deploy/inventory.example.json').exists(), 'Missing safe inventory example')
        with (REPO / 'deploy/inventory.example.json').open() as stream:
            example = json.load(stream)
        result = self.validator()(example)
        self.assertFalse(result['canDeploy'])
        self.assertFalse(result['canClaimAutomaticRecovery'])
        self.assertEqual('local', example['storage']['type'])
        self.assertNotIn('s3', example['storage'])
        self.assertIn('PR-01', [item['id'] for item in result['blockers']])

    # Exercise the real process boundary, including malformed JSON and exit status.
    def test_cli_outputs_json_and_safe_exit_codes_without_evaluating_inventory_text(self):
        for payload, expected_status in [
            (json.dumps(attach_evidence(supplied_inventory())), 0),
            ('{}', 1), ('{broken', 2),
            ('{"command":"touch /tmp/ha-preflight-MUST-NOT-EXECUTE"}', 1),
        ]:
            with self.subTest(payload=payload[:40]), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'inventory.json'
                path.write_text(payload)
                completed = subprocess.run([sys.executable, str(MODULE), str(path)],
                                           capture_output=True, text=True, timeout=10)
                self.assertEqual(expected_status, completed.returncode, completed.stderr)
                self.assertFalse(completed.stderr)
                self.assertTrue(completed.stdout.strip(), 'Missing JSON CLI output')
                self.assertIsInstance(json.loads(completed.stdout)['blockers'], list)
        missing = subprocess.run([sys.executable, str(MODULE), '/nonexistent/test.json'],
                                 capture_output=True, text=True, timeout=10)
        self.assertEqual(2, missing.returncode)
        self.assertFalse(json.loads(missing.stdout)['canDeploy'])


if __name__ == '__main__':
    unittest.main()
