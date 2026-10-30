import unittest
from pathlib import Path


SOURCE = Path(__file__).parents[1] / "seed_account1_scenarios.py"


class SeedAccount1ScenarioScriptTest(unittest.TestCase):
    def test_cleanup_preserves_task_outbox_sequence(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("task_outbox_sequence", source)
        self.assertRegex(source, r"table_name\s*!=\s*['\"]flyway_schema_history['\"]")
        self.assertRegex(source, r"task_outbox_sequence")

    def test_fixture_declares_permission_negative_identity_and_fixed_harness_guard(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("dev-revoked", source)
        self.assertIn("FIXED_PORTS", (SOURCE.parent / "import_cas_integration.mjs").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
