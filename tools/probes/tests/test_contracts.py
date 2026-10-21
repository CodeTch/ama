"""Behavior tests: missing gates, false verification, disclosure and malformed input."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_contracts.py"


def check(manifest):
    spec = importlib.util.spec_from_file_location("contracts_probe", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.check_contracts(manifest)


class ContractTests(unittest.TestCase):
    def test_missing_contract_is_blocked(self):
        report = check({"contracts": []})
        self.assertTrue(report["checks"], "missing contracts must produce pending checks")
        self.assertEqual(report["checks"][0]["id"], "INT-01")
        self.assertEqual(report["checks"][0]["status"], "pending")
        self.assertIn("B-02", report["checks"][0]["blockedTasks"])

    def test_secret_fields_are_redacted(self):
        token = "TEST-TOKEN-never-emit-4987"
        report = check({"contracts": [{"id": "INT-01", "token": token,
                                      "fields": {"protocol": token},
                                      "evidence": [{"reference": token}]}]})
        encoded = json.dumps(report)
        self.assertNotIn(token, encoded)
        self.assertTrue(report["checks"], "redaction must retain actionable checks")

    def test_passed_claim_cannot_verify_external_contract(self):
        report = check({"contracts": [{"id": "INT-01", "status": "verified",
            "fields": {"protocol": "OIDC"},
            "evidence": [{"reference": "controlled:test-approval", "outcome": "passed"}]}]})
        self.assertTrue(report["checks"])
        self.assertNotEqual(report["checks"][0]["status"], "verified")

    def test_complete_passed_evidence_still_requires_platform_validation(self):
        fields = {name: "documented" for name in (
            "protocol callback stableUserId tokenSessionLifetime logoutRevocation "
            "disablePropagation clockSkew testTenant errorFormat").split()}
        fields["credentialReference"] = "controlled:test-identity-credentials"
        report = check({"contracts": [{"id": "INT-01", "fields": fields,
            "evidence": [{"reference": "controlled:identity-report", "outcome": "passed"}]}]})
        first = report["checks"][0]
        self.assertEqual(first["status"], "pending")
        self.assertEqual(first["evidence"]["missingFields"], [])
        self.assertEqual(first["evidence"]["received"], 1)
        self.assertFalse(report["integrationVerified"])

    def test_failed_or_conflicting_evidence_does_not_become_pending(self):
        for outcomes in (["failed"], ["passed", "failed"], ["passed", "pending"]):
            with self.subTest(outcomes=outcomes):
                report = check({"contracts": [{"id": "INT-06", "evidence": [
                    {"reference": "controlled:report", "outcome": outcome} for outcome in outcomes]}]})
                publication = next(c for c in report["checks"] if c["id"] == "INT-06")
                self.assertEqual(publication["status"], "failed")
                self.assertIn("B-08", publication["blockedTasks"])

    def test_distinct_evidence_references_do_not_conflict(self):
        for second_outcome, expected_status in (("pending", "pending"), ("failed", "failed")):
            with self.subTest(second_outcome=second_outcome):
                report = check({"contracts": [{"id": "INT-01", "evidence": [
                    {"reference": "controlled:protocol-review", "outcome": "passed"},
                    {"reference": "controlled:disable-propagation-test", "outcome": second_outcome}]}]})
                identity = report["checks"][0]
                self.assertEqual(identity["status"], expected_status)
                self.assertNotIn("conflicting-evidence-outcomes", identity["evidence"]["issues"])
                if second_outcome == "failed":
                    self.assertIn("reported-evidence-failure", identity["evidence"]["issues"])
                self.assertNotIn("controlled:protocol-review", json.dumps(report))

    def test_references_are_opaque_and_malformed_values_are_suppressed(self):
        for reference in ("file:///etc/passwd", "https://user:SECRET@company.test",
                          "env:SECRET", {"token": "SECRET"}, None):
            with self.subTest(reference=reference):
                report = check({"contracts": [{"id": "INT-01", "fields": {
                    "credentialReference": reference}, "evidence": [
                    {"reference": reference, "outcome": "passed"}]}]})
                self.assertEqual(report["checks"][0]["status"], "failed")
                self.assertNotIn("SECRET", json.dumps(report))

    def test_reference_expiry_policy_is_not_treated_as_a_credential(self):
        report = check({"contracts": [{"id": "INT-05", "fields": {
            "expiredReference": "拒绝失效引用并提示用户重新选择材料"}}]})
        material = next(c for c in report["checks"] if c["id"] == "INT-05")
        self.assertEqual(material["status"], "pending")
        self.assertNotIn("expiredReference", material["evidence"]["missingFields"])

    def test_duplicate_and_unknown_records_fail_closed(self):
        for records in ([{"id": "INT-01"}, {"id": "INT-01"}],
                        [{"id": "invented-secret-id"}], [None]):
            with self.subTest(records=records):
                report = check({"contracts": records})
                self.assertIn("failed", [c["status"] for c in report["checks"]])
                self.assertNotIn("invented-secret-id", json.dumps(report))

    def test_malformed_manifest_never_crashes_or_passes(self):
        for manifest in (None, [], "secret", {"contracts": {}},
                         {"contracts": [{"id": "INT-01", "fields": []}]},
                         {"contracts": [{"id": "INT-01", "evidence": [None]}]}):
            with self.subTest(manifest=manifest):
                report = check(manifest)
                self.assertIn("failed", [c["status"] for c in report["checks"]])

    def test_complete_catalog_does_not_block_independent_implementation(self):
        report = check({"contracts": []})
        self.assertEqual(len(report["checks"]), 30)
        self.assertEqual(len(report["decisions"]), 19)
        self.assertEqual(report["continuation"]["B"]["implementation"], "may-continue")
        self.assertEqual(report["continuation"]["F"]["qualification"], "pending")
        decisions = {d["id"]: d for d in report["decisions"]}
        self.assertEqual(decisions["D-19"]["category"], "adopted")
        self.assertEqual(decisions["D-10"]["category"], "product-specification")
        self.assertEqual(decisions["D-14"]["category"], "platform-evidence")

    def test_approved_ui_decisions_no_longer_request_approval(self):
        report = check({"contracts": []})
        decisions = {d["id"]: d for d in report["decisions"]}
        for ident in ("D-01", "D-03", "D-18"):
            with self.subTest(ident=ident):
                self.assertEqual(decisions[ident]["status"], "adopted")
                self.assertEqual(decisions[ident]["pendingFields"], [])
                self.assertEqual(decisions[ident]["blockedTasks"], [])
                self.assertTrue(decisions[ident]["affectedTasks"])
                self.assertTrue(all(ident not in phase["decisionGates"]
                                    for phase in report["continuation"].values()))

    def test_partial_decisions_only_request_unresolved_fields(self):
        decisions = {d["id"]: d for d in check({"contracts": []})["decisions"]}
        resolved = {
            "D-04": {"multiplePrimaryFiles", "audioVideoMix", "transcriptAudioMix", "ordering"},
            "D-06": {"conflictRecovery"},
            "D-10": {"recipientScope", "externalRecipients", "linkBodyAttachment",
                     "snapshotVersion", "permissionRecheck", "unknownResultReconciliation", "retryPolicy"},
            "D-11": {"reviewStates", "consent", "revocation", "configurationApproval", "publishRollback"},
            "D-15": {"voiceConsent", "humanConfirmation", "accessGrantRules"},
        }
        unresolved = {
            "D-04": {"formats", "sizes", "durations"},
            "D-05": {"requestBudget", "parsingBudget", "uploadBudget"},
            "D-06": {"perEditorSaveTiming", "saveIndicator", "leaveReminder"},
            "D-07": {"degradedConfirmationAllowed", "explicitApproval", "auditReason", "eligibility"},
            "D-10": {"senderAuthorization", "template", "messageKey"},
            "D-11": {"roles", "dataPurpose", "evaluationDataset", "retention"},
            "D-15": {"externalIdentity", "responsiblePersonMapping"},
            "D-16": {"formats", "ocrAvailability"},
            "D-17": {"temporaryRetention", "backupDeletion", "auditRetention"},
        }
        for ident, fields in resolved.items():
            with self.subTest(resolved=ident):
                self.assertTrue(fields.isdisjoint(decisions[ident]["pendingFields"]))
        for ident, fields in unresolved.items():
            with self.subTest(unresolved=ident):
                self.assertEqual(decisions[ident]["status"], "pending")
                self.assertTrue(fields.issubset(decisions[ident]["pendingFields"]))
                self.assertTrue(decisions[ident]["blockedTasks"])

    def test_governance_framework_allows_independent_work_without_qualification(self):
        fields = {name: "documented" for name in (
            "protocol authorizedSender quotas queryableMessageId deduplication "
            "recipients contentMode unknownResultPolicy").split()}
        fields["credentialReference"] = "controlled:test-mail-credentials"
        complete_mail = {"id": "INT-07", "fields": fields, "evidence": [
            {"reference": "controlled:mail-report", "outcome": "passed"}]}
        for records in ([], [complete_mail]):
            with self.subTest(mail_evidence=bool(records)):
                report = check({"contracts": records})
                governance = report["continuation"]["E"]
                self.assertEqual(governance["implementation"], "may-continue")
                self.assertTrue(all(phase["qualification"] == "pending"
                                    for phase in report["continuation"].values()))
                self.assertTrue({"D-10", "D-11", "D-15"}.issubset(governance["decisionGates"]))
                self.assertTrue({"INT-07", "INT-09"}.issubset(governance["externalGates"]))
                self.assertTrue(all(c["status"] == "pending" for c in report["checks"]))
                self.assertFalse(report["integrationVerified"])

    def test_cli_is_offline_explicit_and_errors_do_not_leak(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.json"
            manifest.write_text('{"token":"CLI-SECRET-917", broken}', encoding="utf-8")
            result = subprocess.run([sys.executable, str(SCRIPT), "--manifest", str(manifest),
                                     "--offline"], text=True, capture_output=True)
            self.assertEqual(result.returncode, 2)
            self.assertNotIn("CLI-SECRET-917", result.stdout + result.stderr)
            self.assertTrue(result.stdout, "CLI must emit a sanitized structured failure")
            self.assertEqual(json.loads(result.stdout)["checks"][0]["status"], "failed")

    def test_cli_pending_exit_and_strict_input_errors(self):
        cases = [('{"contracts":[]}', ["--offline"], 1),
                 ('{"contracts":[],"contracts":[]}', ["--offline"], 2),
                 ('{"contracts":[],"token":NaN}', ["--offline"], 2),
                 ('{"contracts":[]}', [], 2),
                 ('{"contracts":[]}', ["--offline", "--SECRET"], 2)]
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.json"
            for content, options, expected_exit in cases:
                with self.subTest(content=content, options=options):
                    manifest.write_text(content, encoding="utf-8")
                    result = subprocess.run([sys.executable, str(SCRIPT), "--manifest", str(manifest),
                                             *options], text=True, capture_output=True)
                    self.assertEqual(result.returncode, expected_exit)
                    self.assertFalse(json.loads(result.stdout)["integrationVerified"])
                    self.assertNotIn("SECRET", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
