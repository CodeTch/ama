import json
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
    def test_revised_fifty_recorder_two_hundred_user_scenario_is_registered(self):
        path=pathlib.Path(__file__).resolve().parents[2]/"checks"/"run.py"
        spec=importlib.util.spec_from_file_location("recording_load_runner_revised",path)
        runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
        with patch.object(runner.subprocess,"run") as execute:
            execute.return_value.returncode=1
            self.assertEqual(1,runner.main(["load","realtime-50"]))
            self.assertTrue(execute.called)
            self.assertIn("tools/load/scenarios/realtime-50.json",execute.call_args.args[0])
        scenario=json.loads((path.parents[1]/"load"/"scenarios"/"realtime-50.json").read_text())
        self.assertEqual((50,200,150,7200),(scenario["recorders"],scenario["totalUsers"],scenario["backgroundUsers"],scenario["durationSeconds"]))
        self.assertEqual("F",scenario["acceptancePhase"])

    def test_c_ten_recorder_fifty_user_continuous_thirty_minutes_is_registered(self):
        path=pathlib.Path(__file__).resolve().parents[2]/"checks"/"run.py"
        spec=importlib.util.spec_from_file_location("recording_load_runner_phase_c",path)
        runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
        with patch.object(runner.subprocess,"run") as execute:
            execute.return_value.returncode=1
            self.assertEqual(1,runner.main(["load","realtime-10-30m"]))
            self.assertTrue(execute.called)
            self.assertIn("tools/load/scenarios/realtime-10-30m.json",execute.call_args.args[0])
        scenario=json.loads((path.parents[1]/"load"/"scenarios"/"realtime-10-30m.json").read_text())
        self.assertEqual((10,50,40,1800),(scenario["recorders"],scenario["totalUsers"],scenario["backgroundUsers"],scenario["durationSeconds"]))
        self.assertEqual("C",scenario["acceptancePhase"])
        self.assertTrue(scenario["observeSubtitles"])
        self.assertFalse(scenario["dropAck"])
        self.assertEqual(1,scenario["longFileRecorderCount"])
        self.assertEqual(2000,scenario["chunkMs"])

    def test_subtitle_qualification_requires_every_source_chunk_and_actual_arrival_latency(self):
        from tools.load.report import qualify_subtitles
        scenario,recordings,_=self.transport_fixture()
        for r in recordings:
            r["subtitleState"]="DONE";r["subtitleErrors"]=0
            r["subtitleEvents"]=[dict(eventId=str(i),sessionId=r["sessionId"],partId="part",sourceFinalSeq=i,endMs=i*2000,status="stable" if i==3 else "temporary",latencyMs=500) for i in range(1,4)]
        self.assertTrue(qualify_subtitles(scenario,recordings)["passed"])
        for field,value in [("subtitleEvents",recordings[0]["subtitleEvents"][:-1]),("subtitleState","DELAYED"),("subtitleErrors",1)]:
            damaged=[{**recordings[0],field:value},recordings[1]]
            self.assertFalse(qualify_subtitles(scenario,damaged)["passed"])
        events=recordings[0]["subtitleEvents"]
        for field,value in [("latencyMs",None),("latencyMs",-1),("latencyMs",6000),("endMs",2000),("sourceFinalSeq",1),("sessionId","foreign")]:
            damaged=[{**recordings[0],"subtitleEvents":[*events[:-1],{**events[-1],field:value}]},recordings[1]]
            self.assertFalse(qualify_subtitles(scenario,damaged)["passed"])
        self.assertFalse(qualify_subtitles(scenario,[])["passed"])
        self.assertFalse(qualify_subtitles(scenario,[{**r,"subtitleEvents":[]} for r in recordings])["passed"])

    def test_long_file_qualification_requires_completed_actual_raw_and_exact_archive_identity(self):
        from tools.load.report import qualify_long_file
        scenario,recordings,_=self.transport_fixture();scenario["longFileRecorderCount"]=1
        recordings[0].update(longFileSelected=True,wavSha256="a"*64,wavBytes=192044)
        result=dict(passed=True,state="SUCCEEDED",sourceSha256="a"*64,sourceBytes=192044,sourceSamples=96000,lastEndMs=6000,taskId="task",inputId="input",sourceFileId="file",rawRevisionId="raw",segments=1)
        recordings[0]["longFileTranscription"]=result
        self.assertTrue(qualify_long_file(scenario,recordings)["passed"])
        for key,value in [("passed",False),("state","RUNNING"),("sourceSha256","b"*64),("sourceBytes",1),("sourceSamples",0),("lastEndMs",0),("rawRevisionId",None),("segments",0)]:
            recordings[0]["longFileTranscription"]={**result,key:value}
            self.assertFalse(qualify_long_file(scenario,recordings)["passed"])
        recordings[0].pop("longFileTranscription")
        self.assertFalse(qualify_long_file(scenario,recordings)["passed"])

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
