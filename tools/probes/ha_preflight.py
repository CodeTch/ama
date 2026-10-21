#!/usr/bin/env python3
"""Offline, read-only assessment of supplied two-node prerequisite evidence.

No commands, network requests, mount operations or fencing are performed.
See docs/acceptance/a/ha-prerequisites.md for the input/evidence contract.
"""

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import PurePosixPath
import re
import sys

MAX_EVIDENCE_AGE = timedelta(days=30)
PREREQUISITES = {
    'PR-01': 'Virtualization and stable VM identities',
    'PR-02': 'Independent, verified power fencing',
    'PR-03': 'Host, storage and power fault domains',
    'PR-04': 'Protected block devices and exclusive XFS mounts',
    'PR-05': 'Replication and cluster network',
    'PR-06': 'VIP, DNS/TLS and client access paths',
    'PR-07': 'RHEL kernel, HA subscription and supported components',
    'PR-08': 'Cluster ordering, single writer and recovery constraints',
    'PR-09': 'Private MySQL durability and crash recovery',
    'PR-10': 'Private S3 data, indexes and metadata qualification',
    'PR-11': 'Single-node resource and capacity qualification',
    'PR-12': 'Install sources, time, certificates and SELinux',
    'PR-13': 'Coordinated backup and restore destination',
    'PR-14': 'Monitoring and on-call responsibility',
    'PR-15': 'Local files, staging, mount identity and durable publication',
    'PL-01': 'Enterprise MySQL contract and recovery',
    'PL-02': 'Enterprise S3 contract, consistency and durability',
    'PL-03': 'Selected platform service network and quotas',
    'PL-04': 'Selected platform service fault responsibility and observability',
    'PL-05': 'Application ingress HA',
}


def _object(value):
    return value if isinstance(value, dict) else {}


def _known(value):
    return (isinstance(value, str) and bool(value.strip())
            and value.strip().lower() not in {'unknown', 'pending', 'todo', 'tbd', 'n/a', 'null'}
            and not value.strip().startswith('<'))


def _path(value):
    return (_known(value) and value.startswith('/') and value != '/'
            and '..' not in PurePosixPath(value).parts)


def _number(value, minimum=0):
    try:
        return type(value) in (int, float) and math.isfinite(value) and value > minimum
    except OverflowError:
        return False


def _fingerprint(inventory):
    facts = {key: value for key, value in inventory.items() if key not in ('evidence', 'drills')}
    return hashlib.sha256(json.dumps(facts, sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=False, allow_nan=False).encode('utf-8')).hexdigest()


def _evidence_problem(value, fingerprint, now):
    record = _object(value)
    if record.get('status') != 'passed':
        return 'Missing or unpassed structured evidence'
    if not all(_known(record.get(key)) for key in ('recordedBy', 'reviewedBy', 'summary')):
        return 'Evidence needs operator, independent reviewer and substantive summary'
    if record['recordedBy'].strip() == record['reviewedBy'].strip():
        return 'Evidence cannot be self-reviewed'
    if not fingerprint or record.get('inventorySha256') != fingerprint:
        return 'Evidence does not match current inventory fingerprint'
    artifact = _object(record.get('artifact'))
    digest = artifact.get('sha256')
    if (not _known(artifact.get('reference')) or not isinstance(digest, str)
            or not re.fullmatch(r'[0-9a-f]{64}', digest) or len(set(digest)) == 1):
        return 'Evidence needs an artifact reference and non-placeholder SHA-256'
    timestamp = record.get('observedAt')
    try:
        observed = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
        if observed.utcoffset() != timedelta(0):
            return 'Evidence timestamp must be timezone-aware UTC'
        age = now - observed
        if age < timedelta(0) or age > MAX_EVIDENCE_AGE:
            return 'Evidence is future-dated or older than 30 days'
    except (AttributeError, TypeError, ValueError, OverflowError):
        return 'Evidence timestamp is missing, malformed or timezone-naive'
    return None


def validate_topology(inventory: dict) -> dict:
    """Assess supplied facts/evidence without probing or changing any machine.

    canDeploy describes selected-profile prerequisite readiness. A recovery claim
    additionally requires every applicable drill. Neither value is certification.
    """
    blockers = []

    def require(condition, code, reason):
        if not condition:
            blockers.append({'id': code, 'stage': 'prerequisite', 'reason': reason})

    require(isinstance(inventory, dict), 'INPUT', 'Inventory must be a JSON object')
    inventory = _object(inventory)
    try:
        fingerprint = _fingerprint(inventory)
    except (TypeError, ValueError, RecursionError, OverflowError):
        fingerprint = None
        require(False, 'INPUT', 'Inventory facts must be finite JSON values with string keys')
    require(type(inventory.get('schemaVersion')) is int and inventory.get('schemaVersion') == 1,
            'INPUT', 'schemaVersion must be integer 1')
    require(_known(inventory.get('inventoryId')), 'INPUT', 'Inventory needs a non-placeholder revision ID')
    database = _object(inventory.get('database'))
    storage = _object(inventory.get('storage'))
    ingress = _object(inventory.get('ingress'))
    db_mode = database.get('mode')
    storage_type = storage.get('type')
    provider = storage.get('provider')
    require(db_mode in ('private-ha', 'enterprise-platform'), 'INPUT', 'Unknown database.mode')
    require(storage_type in ('local', 's3'), 'INPUT', 'Unknown storage.type')
    require(_known(storage.get('backendId')), 'INPUT', 'Storage needs a stable backend ID')
    if storage_type == 's3':
        require(provider in ('private', 'enterprise-platform'), 'INPUT', 'S3 provider must be explicit')
    require(ingress.get('mode') in ('enterprise-lb', 'cluster-vip'), 'PL-05', 'Ingress HA mode must be explicit')
    local = storage_type == 'local'
    private_s3 = storage_type == 's3' and provider == 'private'
    private_data = db_mode == 'private-ha' or local or private_s3
    managed_ha = private_data or ingress.get('mode') == 'cluster-vip'
    platform = db_mode == 'enterprise-platform' or (storage_type == 's3' and provider == 'enterprise-platform')

    applicable = {'PR-03', 'PR-06', 'PR-11', 'PR-12', 'PR-13', 'PR-14', 'PL-05'}
    if managed_ha:
        applicable.update({'PR-01', 'PR-02', 'PR-05', 'PR-07', 'PR-08'})
    if private_data:
        applicable.add('PR-04')
    if db_mode == 'private-ha':
        applicable.add('PR-09')
    if local:
        applicable.add('PR-15')
        require(_path(storage.get('root')), 'PR-15', 'Local root must be an absolute protected directory')
    if private_s3:
        applicable.add('PR-10')
        require(storage.get('metadataDatabaseMode') == db_mode and _known(storage.get('metadataSchema')),
                'PR-10', 'Filer metadata must use the selected MySQL mode and a dedicated schema')
    if db_mode == 'enterprise-platform':
        applicable.add('PL-01')
    if storage_type == 's3' and provider == 'enterprise-platform':
        applicable.add('PL-02')
    if platform:
        applicable.update({'PL-03', 'PL-04'})

    nodes = inventory.get('nodes')
    require(isinstance(nodes, list) and len(nodes) == 2, 'PR-03', 'Exactly two project VMs are required')
    nodes = nodes if isinstance(nodes, list) else []
    nodes = [_object(node) for node in nodes]
    ids = [node.get('id') for node in nodes]
    valid_ids = len(ids) == 2 and all(_known(node) for node in ids) and ids[0] != ids[1]
    require(valid_ids, 'PR-03', 'Two distinct stable node IDs are required')
    node_ids = set(ids) if valid_ids else set()
    vm_ids = [node.get('vmId') for node in nodes]
    require(len(vm_ids) == 2 and all(_known(vm) for vm in vm_ids) and vm_ids[0] != vm_ids[1],
            'PR-03', 'Two distinct virtualization VM IDs are required')
    hosts = [node.get('hostId') for node in nodes]
    require(len(hosts) == 2 and all(_known(host) for host in hosts) and hosts[0] != hosts[1],
            'PR-03', 'VMs require distinct known physical hosts')
    domains = _object(inventory.get('faultDomains'))
    require(domains.get('antiAffinity') is True and domains.get('sharedRiskAcknowledged') is True,
            'PR-03', 'Anti-affinity and documented shared storage/power risk scope are required')
    for node in nodes:
        require(all(_known(node.get(key)) for key in ('storageDomain', 'powerDomain')),
                'PR-03', 'Storage and power fault domains must be identified for each VM')
        require((node.get('os'), node.get('version'), node.get('arch')) == ('RHEL', '8.10', 'x86_64'),
                'PR-12', 'Each VM must use RHEL 8.10 x86_64')
        require(type(node.get('vcpus')) is int and node.get('vcpus') == 16
                and type(node.get('memoryGiB')) is int and node.get('memoryGiB') == 32,
                'PR-11', 'Each VM must have 16 vCPUs and 32 GiB memory')

    if managed_ha:
        virtualization = _object(inventory.get('virtualization'))
        require(all(_known(virtualization.get(key)) for key in ('product', 'version')),
                'PR-01', 'Virtualization platform product and version are pending')
        fencing = _object(inventory.get('fencing'))
        require(fencing.get('enabled') is True
                and all(_known(fencing.get(key)) for key in ('provider', 'agent'))
                and fencing.get('managementPathIndependent') is True
                and fencing.get('powerOffVerified') is True,
                'PR-02', 'Independent out-of-band power fencing and verification are mandatory')
        targets = fencing.get('targets')
        targets = targets if isinstance(targets, list) else []
        expected_targets = {(node.get('id'), node.get('vmId')) for node in nodes
                            if _known(node.get('id')) and _known(node.get('vmId'))}
        actual_targets = [(_object(target).get('nodeId'), _object(target).get('vmId')) for target in targets]
        require(len(actual_targets) == 2 and all(_known(a) and _known(b) for a, b in actual_targets)
                and len(expected_targets) == 2 and all(target in expected_targets for target in actual_targets)
                and actual_targets[0] != actual_targets[1],
                'PR-02', 'Fencing must target exactly the two inventoried VM identities')
        cluster = _object(inventory.get('cluster'))
        required_flags = ('singleLifecycleManager', 'fencingBeforePromotion', 'twoNodePolicyReviewed',
                          'startupFencing', 'singlePrimary', 'mountExclusive', 'orderedColocated',
                          'rejoinRequiresResync', 'refusePromotionOnUnknown')
        require(cluster.get('manager') == 'pacemaker'
                and all(cluster.get(flag) is True for flag in required_flags)
                and cluster.get('automaticFailback') is False,
                'PR-08', 'Cluster must enforce fencing first, exclusive writes, ordered recovery and no automatic failback')
        network = _object(_object(inventory.get('replication')).get('network'))
        loss = network.get('packetLossPercent')
        require(_known(network.get('id')) and network.get('independent') is True
                and _number(network.get('rttMs'), -1) and network['rttMs'] >= 0
                and _number(network.get('throughputMbps'))
                and _number(loss, -1) and 0 <= loss <= 100,
                'PR-05', 'Measured independent synchronization/cluster network is required')

    if private_data:
        replication = _object(inventory.get('replication'))
        require(replication.get('protocol') == 'C', 'PR-04', 'Synchronous Protocol C replication is required')
        volumes = replication.get('volumes')
        require(isinstance(volumes, list) and bool(volumes), 'PR-04', 'Protected volumes are required')
        volumes = volumes if isinstance(volumes, list) else []
        protected = set()
        component_paths = {}
        volume_ids = set()
        active_nodes = set()
        active_mounts = set()
        for raw_volume in volumes:
            volume = _object(raw_volume)
            volume_id = volume.get('id')
            require(_known(volume_id) and volume_id not in volume_ids, 'PR-04', 'Volume IDs must be known and unique')
            if _known(volume_id):
                volume_ids.add(volume_id)
            devices = _object(volume.get('devices'))
            capacities = _object(volume.get('capacityGiB'))
            require(bool(node_ids) and set(devices) == node_ids
                    and all(_path(device) and device.startswith('/dev/') for device in devices.values())
                    and set(capacities) == node_ids and all(_number(size) for size in capacities.values())
                    and len(set(capacities.values())) == 1,
                    'PR-04', 'Both nodes require matching positive capacity and explicit block device paths')
            filesystem_uuid = volume.get('filesystemUuid')
            require(isinstance(filesystem_uuid, str)
                    and bool(re.fullmatch(r'[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}', filesystem_uuid))
                    and volume.get('observedMountUuid') == filesystem_uuid,
                    'PR-04', 'Observed active mount identity must match the expected XFS filesystem UUID')
            mounted = volume.get('mountedOn')
            primary = volume.get('primaryOn')
            single_active = (isinstance(mounted, list) and isinstance(primary, list)
                             and len(mounted) == len(primary) == 1 and mounted == primary
                             and _known(mounted[0]) and mounted[0] in node_ids)
            require(volume.get('filesystem') == 'xfs' and _path(volume.get('mountPoint')) and single_active,
                    'PR-04', 'XFS must be mounted only on its sole active Primary')
            if single_active:
                active_nodes.add(mounted[0])
                if _path(volume.get('mountPoint')):
                    mount_path = PurePosixPath('/' + volume['mountPoint'].lstrip('/'))
                    mount_identity = (mounted[0], mount_path)
                    require(mount_identity not in active_mounts, 'PR-04',
                            'Distinct volumes cannot share a normalized active mount path')
                    active_mounts.add(mount_identity)
            replicas = _object(volume.get('replicas'))
            require(bool(node_ids) and set(replicas) == node_ids
                    and all(value == 'UpToDate' for value in replicas.values()),
                    'PR-04', 'Both replicas must explicitly be UpToDate; unknown or stale replicas cannot qualify')
            components = volume.get('protectedComponents')
            require(isinstance(components, list) and all(_known(item) for item in components),
                    'PR-04', 'Each volume needs an explicit recovery component list')
            paths = _object(volume.get('componentPaths'))
            mount = volume.get('mountPoint')
            for component in components if isinstance(components, list) else []:
                if not _known(component):
                    continue
                path = paths.get(component)
                require(component not in protected, 'PR-04', 'Recovery components must map to exactly one volume')
                require(_path(path) and _path(mount)
                        and (PurePosixPath(path) == PurePosixPath(mount)
                             or PurePosixPath(mount) in PurePosixPath(path).parents),
                        'PR-04', 'Every recovery component path must be inside its protected mount')
                protected.add(component)
                component_paths[component] = path
            require(isinstance(components, list) and set(paths) == set(item for item in components if _known(item)),
                    'PR-04', 'Component path keys must exactly match protected components')
        if local:
            require(component_paths.get('local-files') == storage.get('root'),
                    'PR-15', 'Local root must match the protected local-files component path')
        require(len(active_nodes) == 1, 'PR-08', 'Private service volumes must share a single active node')
        required_components = {}
        if db_mode == 'private-ha':
            required_components['PR-09'] = {'mysql-data'}
        if local:
            required_components['PR-15'] = {'local-files', 'upload-staging', 'recovery-manifests'}
        if private_s3:
            required_components['PR-10'] = {'s3-objects', 's3-indexes', 's3-master-state', 's3-config'}
            if db_mode == 'private-ha':
                required_components['PR-10'].add('s3-filer-metadata')
        for code, components in required_components.items():
            require(components <= protected, code, 'Unprotected recovery components: ' + ', '.join(sorted(components - protected)))

    evidence = _object(inventory.get('evidence'))
    now = datetime.now(timezone.utc)
    checks = []
    for code in sorted(PREREQUISITES):
        applies = code in applicable
        problem = _evidence_problem(evidence.get(code), fingerprint, now) if applies else None
        fact_errors = [item['reason'] for item in blockers if item['id'] == code]
        if problem:
            require(False, code, problem)
        checks.append({'id': code, 'description': PREREQUISITES[code], 'applicable': applies,
                       'status': 'pending' if problem or fact_errors else ('passed' if applies else 'not-applicable')})
    can_deploy = not blockers
    drills = {f'HA-{n:02}' for n in (1, 2, 8, 9, 10, 11, 12, 14)}
    if managed_ha:
        drills.update({'HA-03', 'HA-04', 'HA-05', 'HA-07'})
    if private_data:
        drills.add('HA-06')
    if local:
        drills.add('HA-13')
    drill_evidence = _object(inventory.get('drills'))
    for code in sorted(drills):
        problem = _evidence_problem(drill_evidence.get(code), fingerprint, now)
        if problem:
            blockers.append({'id': code, 'stage': 'recovery-claim', 'reason': problem})
    return {
        'assessment': 'supplied-evidence-only', 'readOnly': True,
        'canDeploy': can_deploy, 'canClaimAutomaticRecovery': not blockers,
        'inventorySha256': fingerprint, 'evidenceMaxAgeDays': 30,
        'privateDataHaRequired': private_data, 'managedHaRequired': managed_ha,
        'applicablePrerequisites': sorted(applicable), 'applicableDrills': sorted(drills),
        'checks': checks, 'blockers': blockers,
        'limitations': ['No live probes or artifact authentication performed; not production certification',
                        'Common storage/power/site failure remains outside two-node single-fault guarantees'],
    }


def _input_failure(reason):
    return {'assessment': 'supplied-evidence-only', 'readOnly': True,
            'canDeploy': False, 'canClaimAutomaticRecovery': False,
            'blockers': [{'id': 'INPUT', 'stage': 'prerequisite', 'reason': reason}]}


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON object key')
        result[key] = value
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', nargs='?', help='Local JSON inventory file; omit for a blocked result')
    args = parser.parse_args(argv)
    if not args.inventory:
        result = _input_failure('No inventory supplied; no actions performed')
        status = 2
    else:
        try:
            with open(args.inventory, encoding='utf-8-sig') as source:
                inventory = json.load(source, object_pairs_hook=_unique_object)
            result = validate_topology(inventory)
            status = 0 if result['canClaimAutomaticRecovery'] else 1
        except (OSError, UnicodeError, ValueError, RecursionError, OverflowError):
            result = _input_failure('Inventory could not be read as valid unambiguous JSON')
            status = 2
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
    return status


if __name__ == '__main__':
    sys.exit(main())
