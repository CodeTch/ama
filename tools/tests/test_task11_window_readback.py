"""Run real dispatch/validators against temporary public inputs; stop before environment preparation."""
from contextlib import ExitStack
import datetime
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import task11_window as window

READBACK = ('b2-exact-retry-readback-01', 'it', 'Task11B2ExactRetryReadbackIT', 'task11-b2-exact-isolated')
ACK_NAME = 'task-11-b2-exact-retry-readback-promoted-configuration-ack-20261026.json'


class EnvironmentBoundary(Exception):
    pass


class BranchBoundary(Exception):
    pass


class Environment(dict):
    def copy(self):
        raise EnvironmentBoundary('environment preparation reached')


class ReadbackDispatchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='task11-dispatch-offline-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.w = self.root / '.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
        self.w.mkdir(parents=True)
        self.out = self.root / 'windows'
        self.out.mkdir()
        self.copy_ack('task-11-b2-exact-retry-readback-configuration-ack-20261026.json')
        self.ack = self.copy_ack(ACK_NAME)
        # A synthetic, hash-bound source/checkpoint, never the live selector or a live release.
        self.source = self.root / 'synthetic-source.txt'
        self.source.write_text('inert source fixture\n')
        checkpoint = {'files': [{'path': self.source.name, 'sha256': window.digest(self.source.read_bytes())}]}
        self.checkpoint = self.w / self.ack['runtimeSourceCheckpoint']
        self.checkpoint.write_text(json.dumps(checkpoint))
        now = datetime.datetime.now(datetime.timezone.utc)
        self.release = dict(status='EXACT_RETRY_READBACK_RUNTIME_RELEASED_ONCE',
                            label=READBACK[0], group=READBACK[1], selector=READBACK[2], fixtureName=READBACK[3],
                            configurationAckSha256=window.digest((self.w / ACK_NAME).read_bytes()),
                            sourceCheckpointSha256=window.digest(self.checkpoint.read_bytes()), maxInvocations=1,
                            oneGrantAuthorized=False, receiptOnlyCallsAuthorized=7,
                            issuedUtc=(now-datetime.timedelta(minutes=1)).isoformat(),
                            startBeforeUtc=(now+datetime.timedelta(minutes=5)).isoformat())
        self.release_path = self.w / self.ack['runtimeReleaseFile']
        self.write_release()

    def copy_ack(self, name):
        raw = (window.W / name).read_bytes()
        ack = json.loads(raw)
        (self.w / name).write_bytes(raw)
        # Copy only ACK-declared public evidence, with its real bytes and pins.
        for key, value in ack.items():
            if key + 'Sha256' in ack and isinstance(value, str):
                original = window.W / value
                if not original.is_file():
                    original = window.ROOT / value
                if original.is_file():
                    self.copy_public(original)
        for name in ack.get('publicInputPins', {}):
            self.copy_public(window.W / name)
        # Only stat() is reached before the gate. Never load the retained runtime environment.
        private = self.root / ack['privateEnv']
        private.parent.mkdir(parents=True, exist_ok=True)
        private.parent.chmod(0o700)
        private.write_text('# synthetic environment, never loaded\n')
        private.chmod(0o600)
        return ack

    def copy_public(self, original):
        relative = original.relative_to(window.ROOT)
        self.assertNotIn('.tools', relative.parts)
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, target)

    def write_release(self):
        self.release_path.write_text(json.dumps(self.release))

    def dispatch(self, args=READBACK, validator='validate_exact_readback_release', branch_only=False):
        validated = []
        actual = getattr(window, validator)
        def validate(*values):
            if not branch_only:
                actual(*values)
            validated.append(values[2:])
            if branch_only:
                raise BranchBoundary()
        with ExitStack() as stack:
            for name, value in [('ROOT', self.root), ('W', self.w), ('OUT', self.out)]:
                stack.enter_context(patch.object(window, name, value))
            stack.enter_context(patch.object(sys, 'argv', ['task11_window.py', *args]))
            stack.enter_context(patch.object(window.os, 'environ', Environment()))
            stack.enter_context(patch.object(window, validator, validate))
            launch = stack.enter_context(patch.object(window.subprocess, 'run', side_effect=AssertionError('process launch forbidden')))
            popen = stack.enter_context(patch.object(window.subprocess, 'Popen', side_effect=AssertionError('process launch forbidden')))
            try:
                window.run_window({'synthetic': True})
            finally:
                launch.assert_not_called()
                popen.assert_not_called()
                self.assertFalse((self.out / args[0]).exists(), 'dispatch consumed the target window')
                self.validated = validated

    def assert_gate_rejected(self):
        try:
            self.dispatch()
        except EnvironmentBoundary:
            self.fail('invalid release reached environment preparation before rejection')
        except AssertionError:
            self.assertEqual([], self.validated)
            return
        self.fail('invalid release was accepted')

    def test_valid_release_reaches_real_readback_validator_before_environment(self):
        with self.assertRaises(EnvironmentBoundary):
            self.dispatch()
        self.assertEqual([READBACK], self.validated)

    def test_missing_release_fails_before_environment_or_window(self):
        self.release_path.unlink()
        self.assert_gate_rejected()

    def test_expired_or_future_release_fails_before_environment_or_window(self):
        for delta in (-10, 10):
            with self.subTest(delta=delta):
                now = datetime.datetime.now(datetime.timezone.utc)
                self.release['issuedUtc'] = (now+datetime.timedelta(minutes=delta)).isoformat()
                self.release['startBeforeUtc'] = (now+datetime.timedelta(minutes=delta+1)).isoformat()
                self.write_release()
                self.assert_gate_rejected()

    def test_release_tuple_pins_and_budgets_fail_before_environment_or_window(self):
        invalid = {'status': 'WRONG', 'label': 'wrong', 'group': 'unit', 'selector': 'WrongIT',
                   'fixtureName': 'task11-primary', 'configurationAckSha256': '0'*64,
                   'sourceCheckpointSha256': '0'*64, 'oneGrantAuthorized': True,
                   'receiptOnlyCallsAuthorized': 8, 'maxInvocations': 2}
        for key, value in invalid.items():
            with self.subTest(key=key):
                previous = self.release[key]
                self.release[key] = value
                self.write_release()
                self.assert_gate_rejected()
                self.release[key] = previous
        self.write_release()

    def test_changed_source_fails_before_environment_or_window(self):
        self.source.write_text('tampered fixture\n')
        self.assert_gate_rejected()

    def test_promoted_ack_binds_corrected_review_and_adoption(self):
        for key in ('correctedSourceReview', 'correctedSourceAdoption'):
            with self.subTest(key=key):
                target = self.w / self.ack[key]
                original = target.read_bytes()
                target.write_text('tampered\n')
                self.assert_gate_rejected()
                target.write_bytes(original)

    def test_exact_retry_mode_keeps_its_existing_release_branch(self):
        self.copy_ack('task-11-b2-exact-retry-configuration-ack-r1-20261026.json')
        args = ('b2-exact-retry-01', 'it', 'Task11B2ExactRetryIT', READBACK[3])
        with self.assertRaises(BranchBoundary):
            self.dispatch(args, 'validate_exact_retry_release', branch_only=True)
        self.assertEqual([args], self.validated)

    def test_host_mode_keeps_its_existing_release_branch(self):
        self.copy_ack('task-11-b6-host-matrix-configuration-ack-20261026.json')
        args = ('b2-rejected-transport-host-01', 'it', 'Task11B2RejectedTransportHostContinuationIT', READBACK[3])
        with self.assertRaises(BranchBoundary):
            self.dispatch(args, 'validate_host_matrix_release', branch_only=True)
        self.assertEqual([args], self.validated)

    def test_preflight_mode_does_not_acquire_a_readback_release_requirement(self):
        self.copy_ack('task-11-b2-runtime-fixture-application-ack-20261026.json')
        args = ('b2-runtime-preflight-01', 'it', 'Task11B2RuntimePreflightIT', READBACK[3])
        with self.assertRaises(EnvironmentBoundary):
            self.dispatch(args)
        self.assertEqual([], self.validated)


if __name__ == '__main__':
    unittest.main()