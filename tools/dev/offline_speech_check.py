"""Run AMA's six-endpoint contract and correction-flow tests against the sibling harness.

Uses an owned disposable schema on the existing local development MySQL container.
Run from the repository root after sourcing .tools/env.sh. Never starts or restarts the harness.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

import local_app

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / ".tools/offline-speech"
TESTS = "SpeechPipelineIT,SpeechExecutionObserverIT,ControlledSpeechImportIT,SpeechReviewIT,OfflineSpeechReviewIT,RecordingTranscriptSourceIT#actualReadyCopyEntersTheConfinedImportQueueAndProducesIndependentRawFromFullArchive+aSecondArchiveWindowKeepsExactSourceIdentityAtRawCompletion"


def run_logged(command, name, env=None):
    with (EVIDENCE / name).open("w") as output:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"{name} failed; inspect {EVIDENCE / name}")


def database_sql(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", local_app.NAME, "sh", "-c",
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch'],
        input=sql, text=True, capture_output=True,
    )
    if result.returncode:
        raise RuntimeError("Owned test database operation failed")


def preflight():
    payload = {"model": "deepseek-v4-flash", "temperature": 0,
               "messages": [{"role": "user", "content": "只输出 JSON 数组，没有修改输出 []。\n转写稿：\n[0] ?：本地接口格式核对。"}]}
    request = urllib.request.Request("http://127.0.0.1:8792/v1/chat/completions",
        data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    try:
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request, timeout=3) as response:
            value = json.load(response)
        if not isinstance(json.loads(value["choices"][0]["message"]["content"]), list):
            raise ValueError("array required")
    except Exception as error:
        raise RuntimeError("Start the updated ama-integration-harness on 127.0.0.1:8792; its LLM must return a JSON correction array") from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true", help="Use an already packaged current checkout")
    args = parser.parse_args()
    if Path.cwd() != ROOT or subprocess.check_output(["git", "branch", "--show-current"], text=True).strip() != "main":
        raise RuntimeError("Run from /home/tanren/ama on main")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    preflight()
    container = local_app.owned_container()
    if not container or not container["State"]["Running"]:
        raise RuntimeError("Start AMA's local development MySQL first")
    state = json.loads((ROOT / ".tools/local-app/state.json").read_text())
    if not args.skip_build:
        run_logged(["./mvnw", "-B", "-ntp", "-pl", "backend/integration-tests", "-am", "-DskipTests", "package"], "package.log")
    # Extract only the current build's dependencies into this task's evidence directory.
    libraries = EVIDENCE / "lib"
    libraries.mkdir(exist_ok=True)
    jars = []
    with zipfile.ZipFile(ROOT / "backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar") as archive:
        for name in archive.namelist():
            if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                target = libraries / Path(name).name
                target.write_bytes(archive.read(name))
                jars.append(str(target))
    classpath = ":".join([str(ROOT / f"backend/{module}/target/classes") for module in ("adapters", "core", "contracts")] + jars)
    schema = "ama_c_recording_" + secrets.token_hex(6)
    database_sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON `{schema}`.* TO 'ama_local'@'%';")
    started = time.time()
    report = {"harness": "http://127.0.0.1:8792", "schema": schema, "tests": TESTS.split(","), "passed": False}
    try:
        url = f"jdbc:mysql://127.0.0.1:{state['databasePort']}/{schema}"
        run_logged(["java", "-cp", classpath, "ama.db.MigrationMain"], "migration.log",
                   os.environ | {"DATABASE_URL": url, "DATABASE_USERNAME": "root", "DATABASE_PASSWORD": state["rootPassword"]})
        env = os.environ | {"AMA_C_RECORDING_DATABASE_URL": url, "AMA_C_RECORDING_DATABASE_USERNAME": "ama_local",
            "AMA_C_RECORDING_DATABASE_PASSWORD": state["databasePassword"], "AMA_SPEECH_HARNESS_ORIGIN": "http://127.0.0.1:8792",
            "AMA_LOCAL_SPEECH_RUNTIME": str(ROOT / ".tools/local-models/speech-runtime-v1.json"),
            "AMA_LOCAL_ASR_RUNTIME": str(ROOT / ".tools/local-models/asr-runtime.json"), "AMA_TASK11_REPOSITORY_ROOT": str(ROOT)}
        # Same isolation as tools/checks/run.py it: old evidence-dependent helper
        # unit suites are not prerequisites for an explicit integration selection.
        run_logged(["./mvnw", "-B", "-ntp", "-pl", "backend/integration-tests", "-am",
                    "-Dtest=__AMA_INTEGRATION_SELECTION_ONLY__", "-Dsurefire.failIfNoSpecifiedTests=false",
                    f"-Dit.test={TESTS}", "verify"], "verify.log", env)
        suites = []
        for file in ROOT.glob("backend/*/target/*-reports/TEST-*.xml"):
            if file.stat().st_mtime >= started:
                suite = ET.parse(file).getroot()
                suites.append({"name": suite.attrib["name"], **{key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}})
        for expected in TESTS.split(","):
            matches = [suite for suite in suites if suite["name"].endswith("." + expected.split("#", 1)[0])]
            if len(matches) != 1 or matches[0]["tests"] == 0 or any(matches[0][key] for key in ("failures", "errors", "skipped")):
                raise RuntimeError(f"Required integration suite did not pass: {expected}")
        report.update(passed=True, suites=suites, executed=sum(suite["tests"] for suite in suites))
    finally:
        database_sql(f"DROP DATABASE `{schema}`;")
        report.update(schemaRemoved=True, elapsedSeconds=round(time.time() - started, 2))
        (EVIDENCE / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"PASS: {report['executed']} tests; {EVIDENCE / 'report.json'}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error))
        raise SystemExit(1) from None
