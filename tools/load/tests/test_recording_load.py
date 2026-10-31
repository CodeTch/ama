import unittest
from unittest.mock import patch
import importlib.util
import pathlib
from tools.load.report import build_report
from tools.load.recording_load import run

class RecordingLoadReportTest(unittest.TestCase):
    def transport_fixture(self):
        scenario={"recorders":2,"totalUsers":4,"backgroundUsers":2,"chunkMs":2000,"durationSeconds":6}
        recordings=[dict(recorderId=f"r{i}",sessionId=f"session{i}",meetingId=f"meeting{i}",sentChunks=3,ackedChunks=3,verifiedChunks=3,sampleCount=96000,observedSeconds=6.01,hashMatched=True,latenciesMs=[1,2,3],errors=0,transientErrors=0,queueAgesMs=[0,1,2]) for i in range(2)]
        readers=[dict(userId=f"b{i}",requests=2,errors=0,observedSeconds=6.01) for i in range(2)]
        return scenario,recordings,readers

    def test_protocol_qualification_requires_duration_real_audio_and_readback_for_every_recorder(self):
        from tools.load.report import qualify_transport
        scenario,recordings,readers=self.transport_fixture()
        self.assertTrue(qualify_transport(scenario,recordings,readers)["passed"])
        for field,value in [("observedSeconds",1),("sentChunks",0),("ackedChunks",2),("verifiedChunks",2),("hashMatched",False),("sampleCount",95999),("latenciesMs",[1,2]),("errors",1)]:
            with self.subTest(field=field):
                damaged=[{**recordings[0],field:value},recordings[1]]
                self.assertFalse(qualify_transport(scenario,damaged,readers)["passed"])

    def test_protocol_qualification_requires_distinct_sessions_and_active_background_users(self):
        from tools.load.report import qualify_transport
        scenario,recordings,readers=self.transport_fixture()
        for damaged,background in [([{**recordings[0],"sessionId":recordings[1]["sessionId"]},recordings[1]],readers),(recordings,[{**readers[0],"requests":0},readers[1]]),(recordings,readers[:1]),(recordings,[{**readers[0],"observedSeconds":1},readers[1]])]:
            self.assertFalse(qualify_transport(scenario,damaged,background)["passed"])

    def test_transient_faults_are_counted_and_latency_does_not_drop_failed_attempts(self):
        from tools.load.report import qualify_transport
        scenario,recordings,readers=self.transport_fixture();recordings[0]["transientErrors"]=2;recordings[0]["latenciesMs"]=[1,2,9000]
        result=qualify_transport(scenario,recordings,readers)
        self.assertTrue(result["passed"]);self.assertEqual(2,result["transientErrorCounts"]);self.assertGreater(result["ackLatencyP95Ms"],6000)
        self.assertIsNone(result["temporarySubtitleP95Ms"]);self.assertFalse(result["realModelQualified"])

    def test_load_selector_does_not_substitute_unit_tests_for_a_load_run(self):
        path=pathlib.Path(__file__).resolve().parents[2]/"checks"/"run.py"
        spec=importlib.util.spec_from_file_location("recording_load_runner",path)
        runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
        with patch.object(runner.subprocess,"run") as run:
            run.return_value.returncode=1
            self.assertEqual(1,runner.main(["load","realtime-100"]))
            self.assertIn("tools/load/recording_load.py",run.call_args.args[0])
            self.assertIn("tools/load/scenarios/realtime-100.json",run.call_args.args[0])
    def test_ack_loss_cannot_pass(self):
        report = build_report([{"recorderId": "r1", "missingAckedChunks": 1, "duplicateChunks": 0, "errors": 0, "latencyMs": 3}], expected_recorders=1, expected_users=3)
        self.assertFalse(report["passed"])

    def test_missing_recorder_cannot_pass(self):
        report = build_report([{"recorderId": "r1", "missingAckedChunks": 0, "duplicateChunks": 0, "errors": 0, "latencyMs": 3}], expected_recorders=2, expected_users=6)
        self.assertFalse(report["passed"])

    def test_accounting_without_actual_transport_cannot_qualify_protocol_load(self):
        report = run({"recorders": 100, "totalUsers": 300, "chunkMs": 2000, "durationSeconds": 7200})
        self.assertFalse(report["passed"])

    def test_missing_metrics_do_not_turn_into_zero_errors(self):
        report = build_report([{"recorderId": "r1", "latencyMs": 3}], expected_recorders=1, expected_users=3)
        self.assertFalse(report["passed"])

    def test_only_observed_users_count_toward_the_capacity_requirement(self):
        sample = {"recorderId": "r1", "missingAckedChunks": 0, "duplicateChunks": 0, "errors": 0, "latencyMs": 3}
        self.assertFalse(build_report([sample], expected_recorders=1, expected_users=3)["passed"])
        sample["backgroundUserIds"] = ["reader1", "reader2"]
        report = build_report([sample], expected_recorders=1, expected_users=3)
        self.assertTrue(report["passed"])
        self.assertEqual(3, report["totalUsers"])

if __name__ == "__main__":
    unittest.main()
