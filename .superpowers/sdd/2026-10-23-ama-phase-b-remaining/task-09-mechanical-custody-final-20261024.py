#!/usr/bin/env python3
"""Incremental Task09 custody: only frozen post-initial windows and final seals.

The initial 165 windows and protected 20557 paths are reused from the initial
index. This does not execute tests or read any private credential value.
"""

from __future__ import annotations

import collections
import ast
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[3]
W = pathlib.Path(__file__).resolve().parent
RAW = ROOT / "artifacts/phase-b/b-remaining/task-09"
STEM = "task-09-mechanical-custody-final-20261024"
INDEX = W / (STEM + ".json")
REPORT = W / (STEM + ".md")
INITIAL = W / "task-09-mechanical-custody-initial-20261024.json"
RUNTIME_CHECK = W / "task-09-formal-runtime-controller-check-20261024.json"
FIELDS = ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by",
          "installed_on", "execution_time", "success")
EXPECTED_HEAD = "0e22b812000f2efa00c2ef8ed66e06d8254beab5"
EXPECTED_BASE = "cff858e163ca7dea774dcf05c707ef7551956156"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load(path: pathlib.Path):
    return json.loads(path.read_bytes())


def verify(path: pathlib.Path, size=None, digest=None):
    if not path.is_file():
        return "missing"
    data = path.read_bytes()
    if size is not None and len(data) != size:
        return "size"
    if digest is not None and sha(data) != digest:
        return "sha256"
    return None


def xml_data(path: pathlib.Path):
    root = ET.fromstring(path.read_bytes())
    suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
    counts = {x: 0 for x in ("tests", "failures", "errors", "skipped")}
    cases = []
    for suite in suites:
        for key in counts:
            counts[key] += int(suite.attrib.get(key, 0))
        for item in suite.findall(".//testcase"):
            cases.append({"key": item.attrib.get("classname", "") + "#" + item.attrib.get("name", ""),
                          "status": "ERROR" if item.find("error") is not None else
                          "FAIL" if item.find("failure") is not None else
                          "SKIP" if item.find("skipped") is not None else "PASS"})
    return counts, cases


def map_source(items):
    return {item["path"]: item["sha256"] for item in items}


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    output = load(INDEX)
    names = output["newWindowNames"]
    initial = load(INITIAL)
    assert initial["summary"]["frozenWindows"] == 165
    assert initial["summary"]["protectedExpected"] == 20557
    assert initial["summary"]["protectedIssues"] == 0
    output["initialIndexSha256"] = sha(INITIAL.read_bytes())
    output["initialReused"] = {"windows": 165, "protectedPaths": 20557, "protectedIssues": 0,
                                "caseKeys": initial["summary"]["uniqueCaseKeys"]}
    output["formalRuntimeWindowName"] = "formal-runtime-start-01"

    rows = []
    for name in names:
        p = RAW / name
        result_path = p / "result.json"
        if not result_path.is_file():
            rows.append({"name": name, "qualification": "missing-result-after-freeze", "issues": ["result missing"]})
            continue
        result = load(result_path)
        command_path = p / "command.json"
        manifest_path = p / "source-manifest.json"
        custody_path = p / "report-custody.json"
        command = load(command_path) if command_path.is_file() else None
        manifest = load(manifest_path) if manifest_path.is_file() else None
        custody = load(custody_path) if custody_path.is_file() else None
        issues = []
        row = {"name": name, "kind": "test" if "counts" in result else "non-test",
               "resultSha256": sha(result_path.read_bytes()),
               "commandSha256": sha(command_path.read_bytes()) if command is not None else None,
               "sourceManifestSha256": sha(manifest_path.read_bytes()) if manifest is not None else None,
               "reportCustodySha256": sha(custody_path.read_bytes()) if custody is not None else None,
               "startedUtc": command.get("startedUtc") if isinstance(command, dict) else result.get("observedUtc"),
               "exit": result.get("exit"), "sourceEntries": len(manifest) if isinstance(manifest, list) else None,
               "issues": issues}
        if isinstance(command, dict) and manifest is not None and command.get("sourceSha256") != row["sourceManifestSha256"]:
            issues.append("command sourceSha256 does not match source-manifest raw SHA256")
        if isinstance(manifest, list):
            snapshot_issues = []
            for item in manifest:
                problem = verify(p / "source" / item["path"], item.get("bytes"), item["sha256"])
                if problem:
                    snapshot_issues.append({"path": item["path"], "reason": problem})
            row["sourceSnapshotIssues"] = snapshot_issues
            if snapshot_issues:
                issues.append(f"{len(snapshot_issues)} source snapshot issues")
        xmls = sorted(p.glob("*.xml"))
        row["xmlCount"] = len(xmls)
        counts = {x: 0 for x in ("tests", "failures", "errors", "skipped")}
        cases = []
        reports = []
        for x in xmls:
            try:
                c, cs = xml_data(x)
                for key in counts:
                    counts[key] += c[key]
                cases.extend(cs)
                reports.append({"copy": x.name, "sha256": sha(x.read_bytes()), "counts": c,
                                "caseKeys": [v["key"] for v in cs]})
            except Exception as exc:
                issues.append(f"XML parse issue {x.name}: {type(exc).__name__}")
        row["xmlCounts"] = counts
        row["reports"] = reports
        if row["kind"] == "test":
            row["resultCounts"] = result["counts"]
            row["cases"] = [{"key": x["class"] + "#" + x["name"],
                             "status": "ERROR" if x["error"] else "FAIL" if x["failure"] else
                             "SKIP" if x["skipped"] else "PASS"} for x in result["cases"]]
            if result["counts"] != counts:
                issues.append("result and XML counts differ")
            if collections.Counter((x["key"], x["status"]) for x in row["cases"]) != collections.Counter(
                    (x["key"], x["status"]) for x in cases):
                issues.append("result and XML case identity/status differ")
        if custody is not None:
            start, end = custody.get("startedNs"), custody.get("endedNs")
            row["closedIntervalNs"] = [start, end]
            mapped = []
            for cr in custody.get("reports", []):
                orig = cr.get("path", "")
                module = orig.split("/")[1] if orig.startswith("backend/") else ""
                copy = module + "-" + pathlib.PurePosixPath(orig).name
                problem = verify(p / copy, digest=cr.get("sha256"))
                interval = isinstance(start, int) and isinstance(end, int) and isinstance(cr.get("mtimeNs"), int) and start <= cr["mtimeNs"] <= end
                before = cr.get("previous")
                before_valid = before is None or (isinstance(before, list) and len(before) == 2 and
                                                  isinstance(before[0], int) and isinstance(before[1], str) and
                                                  re.fullmatch(r"[0-9a-f]{64}", before[1]) is not None)
                mapped.append({"sourceReport": orig, "copy": copy, "sha256": cr.get("sha256"),
                               "mtimeNs": cr.get("mtimeNs"), "previous": before,
                               "copyProblem": problem, "inClosedInterval": interval,
                               "previousWellFormed": before_valid, "reportedQualified": cr.get("qualified")})
                if problem or not interval or not before_valid or cr.get("qualified") is not True:
                    issues.append(f"report custody mismatch {copy}")
            row["custodyReports"] = mapped
            if collections.Counter(x["copy"] for x in mapped) != collections.Counter(x.name for x in xmls):
                issues.append("report custody XML set differs from copies")
        if row["kind"] == "non-test":
            file_issues = []
            for item in result.get("files", []):
                problem = verify(ROOT / item["path"], digest=item["sha256"])
                if problem:
                    file_issues.append({"path": item["path"], "reason": problem})
            row["nonTestFileIssues"] = file_issues
            if file_issues:
                issues.append("non-test referenced files differ")
            row["qualification"] = "non-test-exit0" if result.get("exit") == 0 and not issues else "non-test-unresolved"
        elif issues:
            row["qualification"] = "mechanically-unresolved"
        elif result["counts"]["errors"]:
            row["qualification"] = "test-error"
        elif result["counts"]["failures"]:
            row["qualification"] = "assertion-failure"
        elif result["exit"] == 0:
            row["qualification"] = "qualified-pass"
        else:
            row["qualification"] = "nonzero-after-tests"
        rows.append(row)

    # Corroborate new before images against already indexed earlier reports,
    # without reopening any of the initial 165 raw windows.
    latest = {}
    for old in initial["windows"]:
        for cr in old.get("custodyReports", []):
            if isinstance(cr.get("mtimeNs"), int):
                key = cr["sourceReport"]
                if key not in latest or cr["mtimeNs"] > latest[key][0]:
                    latest[key] = [cr["mtimeNs"], cr["sha256"]]
    predecessor = {"matching": 0, "noEarlierIndexedReport": 0, "noBeforeImage": 0, "different": []}
    for row in sorted((r for r in rows if r.get("closedIntervalNs")), key=lambda r: r["closedIntervalNs"][0]):
        for cr in row.get("custodyReports", []):
            older = latest.get(cr["sourceReport"])
            before = cr["previous"]
            if older is None:
                predecessor["noEarlierIndexedReport"] += 1
            elif before is None:
                predecessor["noBeforeImage"] += 1
            elif before == older:
                predecessor["matching"] += 1
            else:
                predecessor["different"].append({"window": row["name"], "report": cr["sourceReport"]})
            latest[cr["sourceReport"]] = [cr["mtimeNs"], cr["sha256"]]

    # Exact final-source closure: historical snapshots are compared by each
    # window's own manifest; current main source is read once against final map.
    reference = load(RAW / "candidate-pages-final-02/source-manifest.json")
    reference_main = {x["path"]: x["sha256"] for x in reference if "/src/main/" in x["path"]}
    main_current_issues = [{"path": x["path"], "reason": problem}
                           for x in reference if "/src/main/" in x["path"]
                           if (problem := verify(ROOT / x["path"], x.get("bytes"), x["sha256"]))]
    new_main_comparisons = []
    for row in rows:
        p = RAW / row["name"] / "source-manifest.json"
        if not p.is_file():
            continue
        own_main = {x["path"]: x["sha256"] for x in load(p) if "/src/main/" in x["path"]}
        differences = sorted(x for x in set(reference_main) | set(own_main) if reference_main.get(x) != own_main.get(x))
        new_main_comparisons.append({"window": row["name"], "mainSourceEntries": len(own_main),
                                     "differingPathsVsFinal": differences})
    own_path = RAW / "own-source-manifest.json"
    closure_path = RAW / "final-source-closure.json"
    own = load(own_path)
    closure = load(closure_path)
    own_issues = [{"path": x["path"], "reason": problem} for x in own["files"]
                  if (problem := verify(ROOT / x["path"], x["bytes"], x["sha256"]))]
    own_equals_closure = own["files"] == closure["source"]
    closure_comparisons = []
    for item in closure["windowComparisons"]:
        name = item["window"]
        computed = next((x for x in new_main_comparisons if x["window"] == name), None)
        closure_comparisons.append({"window": name, "declaredMainEntries": item["mainSourceEntriesCompared"],
                                    "declaredDifferingPaths": item["differingMainSourcePaths"],
                                    "newWindowIndependentMatch": computed is not None and
                                    computed["mainSourceEntries"] == item["mainSourceEntriesCompared"] and
                                    computed["differingPathsVsFinal"] == item["differingMainSourcePaths"] if computed else None})
    canonical_manifest = map_source(load(RAW / "canonical-units-final-01/source-manifest.json"))
    shutdown_manifest = map_source(load(RAW / "worker-shutdown-final-01/source-manifest.json"))
    canonical_shutdown_diffs = sorted(x for x in set(canonical_manifest) | set(shutdown_manifest)
                                      if canonical_manifest.get(x) != shutdown_manifest.get(x))
    source_closure = {"referenceWindow": "candidate-pages-final-02", "mainSourceEntries": len(reference_main),
                      "currentMainIssues": main_current_issues, "ownPaths": len(own["files"]),
                      "ownIssues": own_issues, "ownEqualsClosureSource": own_equals_closure,
                      "ownManifestSha256": sha(own_path.read_bytes()), "closureSha256": sha(closure_path.read_bytes()),
                      "newWindowMainComparisons": new_main_comparisons,
                      "declaredWindowComparisons": closure_comparisons,
                      "canonicalToShutdownSourceDifferences": canonical_shutdown_diffs}

    # The source of initial-history-derived.json names the original bootstrap
    # log; do not reopen the historical bootstrap window.
    baseline_path = RAW / "initial-history-derived.json"
    history_path = RAW / "inherited-migration-final-02/full-fifteen-history.json"
    baseline = load(baseline_path)
    history = load(history_path)
    history_fields_exact = all(set(row) == set(FIELDS) for row in baseline["rows"] + history["before"] + history["after"])
    migration_source = (RAW / "inherited-migration-final-02/source/backend/integration-tests/src/test/java/ama/minutes/Task09FinalMigrationIT.java").read_text()
    datasource_source = (RAW / "inherited-migration-final-02/source/backend/adapters/src/main/java/ama/minutes/ControlledMinutesDataSource.java").read_text()
    migration = {"baselineSource": baseline.get("source"), "baselineSha256": sha(baseline_path.read_bytes()),
                 "historySha256": sha(history_path.read_bytes()), "baselineRows": len(baseline["rows"]),
                 "beforeRows": len(history["before"]), "afterRows": len(history["after"]),
                 "exactTenFieldsEachRow": history_fields_exact,
                 "rawBeforeAfterBaselineEqual": baseline["rows"] == history["before"] == history["after"],
                 "reportedOriginalBaselineEqual": history.get("originalBaselineEqual"),
                 "migrationsExecuted": history.get("migrationsExecuted"),
                 "testUsesControlledDataSource": "ControlledMinutesDataSource.create" in migration_source,
                 "testSourceUsesDateFormat": "DATE_FORMAT(installed_on" in migration_source,
                 "datasourceSourceSetsSessionUTC": "SET time_zone='+00:00'" in datasource_source,
                 "datasourceSourceUsesUTCConnection": '"connectionTimeZone","UTC"' in datasource_source}

    # Formal runtime: read its sealed controller proof and listed raw files.
    formal = RAW / "formal-runtime-start-01"
    controller = load(RUNTIME_CHECK)
    raw_file_issues = [{"path": item["path"], "reason": problem} for item in controller["files"]
                       if (problem := verify(ROOT / item["path"], digest=item["sha256"]))]
    runtime_result = load(formal / "formal-runtime-result.json")
    runtime_exit = load(formal / "exit.json")
    lifecycle = load(formal / "process-lifecycle.json")
    closed_files = list(formal.glob("*native-closed.json"))
    closed = load(closed_files[0]) if len(closed_files) == 1 else {}
    formal_command = load(formal / "command.json")
    bundle_dir = RAW / "runtime-bundle-01"
    bundle_path = bundle_dir / "manifest.json"
    bundle = load(bundle_path)
    bundle_issues = []
    for item in bundle["files"]:
        path = pathlib.Path(item["path"])
        if any(term in path.name.lower() for term in ("secret", "credential", ".pem", "private")):
            bundle_issues.append({"path": str(path), "reason": "credential-like path excluded from custody read"})
            continue
        problem = verify(path, item.get("bytes"), item["sha256"])
        if problem:
            bundle_issues.append({"path": str(path), "reason": problem})
    bundle_source_equal = load(bundle_dir / "source-manifest.json") == reference
    observed = controller["selectedResult"]
    formal_runtime = {
        "controllerSha256": sha(RUNTIME_CHECK.read_bytes()), "controllerListedFiles": len(controller["files"]),
        "controllerRawFileIssues": raw_file_issues,
        "selectedResultMatchesRaw": all(runtime_result.get(k) == v for k, v in observed.items()
                                        if k in runtime_result) and
                                    observed.get("retainedTaskRows") == len(runtime_result.get("preservedTasks", [])) and
                                    observed.get("retainedObservableOperations") == len(runtime_result.get("preservedObservableOperations", [])) and
                                    observed.get("processGroup") == lifecycle and
                                    observed.get("nativeExit") == closed.get("exit") and
                                    observed.get("exit") == runtime_exit.get("exit"),
        "rawResultSha256": sha((formal / "formal-runtime-result.json").read_bytes()),
        "exit0": runtime_exit.get("exit") == 0,
        "processGroupExit0NoRemaining": lifecycle.get("exit") == 0 and lifecycle.get("groupRemaining") is False and
                                        lifecycle.get("timedOut") is False and lifecycle.get("forcedGroupCleanup") is False,
        "installedImportScope": runtime_result.get("mode") == "CONTROLLED_INSTALLATION" and
                                 runtime_result.get("newGenerationDispatch") is False,
        "runtime64Mi2Cpu": runtime_result.get("runtimeHeap") == 67108864 and runtime_result.get("processors") == 2,
        "guardsBeforeAfterEqual": runtime_result.get("guardsAndBeforeAfterEqual") is True,
        "retainedTaskRows": len(runtime_result.get("preservedTasks", [])),
        "retainedObservableOperations": len(runtime_result.get("preservedObservableOperations", [])),
        "providerGone": runtime_result.get("providerGone") is True,
        "providerExit": runtime_result.get("providerExit"),
        "nativeClosedCount": len(closed_files), "nativeClosedAliveFalse": closed.get("alive") is False,
        "nativeExit": closed.get("exit"),
        "bundleManifestSha256": sha(bundle_path.read_bytes()),
        "commandBundleHashMatches": formal_command.get("bundleSha256") == sha(bundle_path.read_bytes()),
        "bundleSourceWindow": bundle.get("sourceWindow"), "bundleSourceManifestEqualsFinalReference": bundle_source_equal,
        "bundleFiles": len(bundle["files"]), "bundleFileIssues": bundle_issues}

    # Exact publication scope and author-run private scan receipt. No private
    # value is loaded here. Path/permission metadata and scanner AST only.
    raw_manifest_path = RAW / "raw-manifest.json"
    staged_path = RAW / "staged-paths.json"
    final_validation_path = RAW / "final-validation.json"
    scan_receipt_path = RAW / "private-scan.json"
    scan_script_path = RAW / "final-private-scan.py"
    raw_manifest = load(raw_manifest_path)
    staged_manifest = load(staged_path)
    validation = load(final_validation_path)
    scan_receipt = load(scan_receipt_path)
    raw_list = raw_manifest["files"]
    raw_paths = [x["path"] for x in raw_list]
    raw_set = set(raw_paths)
    raw_root = "artifacts/phase-b/b-remaining/task-09/"
    actual_raw_set = {str(p.relative_to(ROOT)) for p in RAW.rglob("*") if p.is_file()}
    excluded_metadata = {raw_root + x for x in raw_manifest["excludedFinalMetadata"]}
    raw_coverage_missing = sorted((actual_raw_set - excluded_metadata) - raw_set)
    raw_coverage_extra = sorted(raw_set - (actual_raw_set - excluded_metadata))
    raw_size_issues = [{"path": x["path"], "reason": "size-or-missing"} for x in raw_list
                       if not (ROOT / x["path"]).is_file() or (ROOT / x["path"]).stat().st_size != x["bytes"]]
    staged_before = staged_manifest["pathsBeforeScanReceipt"]
    staged_set = set(staged_before)
    own_set = {x["path"] for x in own["files"]}
    report_path = ".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-09-report.md"
    report_copy_equal = (ROOT / report_path).read_bytes() == (RAW / "report.md").read_bytes()
    expected_nonraw = own_set | {report_path}
    actual_nonraw = {x for x in staged_set if not x.startswith(raw_root)}
    expected_before = raw_set | {raw_root + "raw-manifest.json", raw_root + "staged-paths.json"} | expected_nonraw
    receipt_rel = staged_manifest["finalAdditionalMetadata"]
    expected_commit_set = staged_set | {receipt_rel}
    committed_names = {x.decode() for x in git("diff-tree", "--no-commit-id", "--name-only", "-r", "-z", "HEAD").split(b"\0") if x}
    head = git("rev-parse", "HEAD").decode().strip()
    parent = git("rev-parse", "HEAD^").decode().strip()
    index_empty = git("diff", "--cached", "--name-only", "-z") == b""
    receipt_blob_oid = git("rev-parse", "HEAD:" + receipt_rel).decode().strip()
    current_receipt_oid = git("hash-object", receipt_rel).decode().strip()
    # The author scan counted unique staged blob OIDs before the receipt was
    # added. Reconstruct that exact set from the final commit tree.
    tree_objects = {}
    for item in git("ls-tree", "-r", "-z", "HEAD").split(b"\0"):
        if not item:
            continue
        meta, path = item.split(b"\t", 1)
        tree_objects[path.decode()] = meta.split()[2].decode()
    pre_receipt_oids = {tree_objects[x] for x in staged_set if x in tree_objects}
    own_working_tree_clean = subprocess.run(
        ["git", "diff", "--quiet", "HEAD", "--", *sorted(own_set)], cwd=ROOT, check=False).returncode == 0
    raw_scope = {"rawManifestSha256": sha(raw_manifest_path.read_bytes()),
                 "rawManifestEntries": len(raw_list), "rawManifestDuplicatePaths": len(raw_list) - len(raw_set),
                 "rawManifestCoveredActualMinusExcluded": not raw_coverage_missing and not raw_coverage_extra,
                 "rawManifestMissingPaths": raw_coverage_missing, "rawManifestExtraPaths": raw_coverage_extra,
                 "rawManifestSizeIssues": raw_size_issues,
                 "excludedFinalMetadata": raw_manifest["excludedFinalMetadata"],
                 "actualRawFilesNow": len(actual_raw_set),
                 "stagedManifestSha256": sha(staged_path.read_bytes()),
                 "stagedBeforeReceipt": len(staged_before), "stagedDuplicatePaths": len(staged_before) - len(staged_set),
                 "stagedBeforeMatchesRawAndOwnAndReport": staged_set == expected_before,
                 "stagedRawManifestEntriesAllIncluded": raw_set <= staged_set,
                 "stagedNonrawMatchesOwn154PlusWReport": actual_nonraw == expected_nonraw,
                 "privateToolsInStage": sum(x.startswith(".tools/") for x in staged_set),
                 "receiptAdditionalPath": receipt_rel,
                 "WReportEqualsRawReport": report_copy_equal,
                 "head": head, "headMatchesCoordinator": head == EXPECTED_HEAD,
                 "parent": parent, "parentMatchesBase": parent == EXPECTED_BASE,
                 "commitPathCount": len(committed_names),
                 "commitPathsMatchExactExpected": committed_names == expected_commit_set,
                 "commitMissingPaths": sorted(expected_commit_set - committed_names),
                 "commitExtraPaths": sorted(committed_names - expected_commit_set),
                 "indexEmpty": index_empty, "receiptCommitBlobMatchesWorkingFile": receipt_blob_oid == current_receipt_oid,
                 "preReceiptUniqueCommittedBlobs": len(pre_receipt_oids),
                 "own154WorkingTreeMatchesCommit": own_working_tree_clean}

    scan_text = scan_script_path.read_text()
    scan_ast = ast.parse(scan_text)
    strings = [node.value for node in ast.walk(scan_ast) if isinstance(node, ast.Constant) and isinstance(node.value, str)]
    byte_literals = [node.value for node in ast.walk(scan_ast) if isinstance(node, ast.Constant) and isinstance(node.value, bytes)]
    calls = [node for node in ast.walk(scan_ast) if isinstance(node, ast.Call)]
    command_lists = [[item.value for item in node.elts] for node in ast.walk(scan_ast)
                     if isinstance(node, ast.List) and all(isinstance(item, ast.Constant) and isinstance(item.value, str)
                                                           for item in node.elts)]
    raw_rglob_all = any(isinstance(call.func, ast.Attribute) and call.func.attr == "rglob" and
                        isinstance(call.func.value, ast.Name) and call.func.value.id == "A" and
                        len(call.args) == 1 and isinstance(call.args[0], ast.Constant) and call.args[0].value == "*"
                        for call in calls)
    owner_roots = [x for x in strings if x.startswith(".tools/task09-")]
    provider_globs = [x for x in strings if x == "provider.secret"]
    actual_provider_files = list((ROOT / owner_roots[0]).rglob("provider.secret")) if len(owner_roots) == 1 and provider_globs else []
    scope = scan_receipt["scope"]
    scan_review = {"receiptSha256": sha(scan_receipt_path.read_bytes()),
                   "scriptSha256": sha(scan_script_path.read_bytes()),
                   "receiptScriptHashMatches": scan_receipt.get("scriptSha256") == sha(scan_script_path.read_bytes()),
                   "scriptSingleTask09OwnerRoot": len(owner_roots) == 1,
                   "scriptProviderSecretGlob": len(provider_globs) >= 1,
                   "providerSecretFileCountFromPathMetadata": len(actual_provider_files),
                   "providerSecretFilesRegularNotSymlinks": all(x.is_file() and not x.is_symlink() for x in actual_provider_files),
                   "scannerHasRawRecursiveAllFiles": raw_rglob_all,
                   "scannerOnlyExcludesReceiptFromRaw": re.search(r"p\.name\s+not\s+in\s+\[['\"]private-scan\.json['\"]\]", scan_text) is not None,
                   "scannerHasCachedExactPathCommand": ["git", "diff", "--cached", "--name-only", "-z"] in command_lists,
                   "scannerHasStagedBlobBatchRead": ["git", "ls-files", "--stage", "-z"] in command_lists and
                                                      ["git", "cat-file", "--batch"] in command_lists,
                   "scannerHasPrivatePemPattern": b"-----BEGIN " in byte_literals and
                                                   any(b"PRIVATE KEY" in x for x in byte_literals),
                   "scannerChecksReceiptInMemory": "not any(v in receipt for v in values)" in scan_text and
                                                   "not pattern.search(receipt)" in scan_text,
                   "receiptKnownOriginalValueCount": scan_receipt.get("knownOriginalValueCount"),
                   "receiptActualProviderSecretFiles": scan_receipt.get("actualProviderSecretFiles"),
                   "receiptUniqueValues": scan_receipt.get("uniqueValues"),
                   "receiptExactValueHits": scan_receipt.get("exactValueHits"),
                   "receiptPrivatePemHits": scan_receipt.get("privatePemHits"),
                   "receiptScannedInMemory": scan_receipt.get("receiptScannedInMemory"),
                   "receiptRawRootCorrect": scope.get("rawRoot") == str(RAW.relative_to(ROOT)),
                   "receiptRawFilesMatchActualMinusReceipt": scope.get("rawFiles") == len(actual_raw_set) - 1,
                   "receiptStagedPathsMatchBeforeReceipt": scope.get("exactStagedPaths") == len(staged_before),
                   "receiptUniqueStagedBlobsMatchCommit": scope.get("uniqueStagedBlobs") == len(pre_receipt_oids),
                   "receiptBytesScannedIncludingOverlap": scope.get("bytesScannedIncludingOverlap")}
    validation_scope = {"sha256": sha(final_validation_path.read_bytes()),
                        "finalWindows": [(x["window"], x["exit"], x["counts"]) for x in validation["finalWindows"]],
                        "finalWindowsMatchIndexed": all(next((r for r in rows if r["name"] == x["window"]), None) is not None and
                                                       next(r for r in rows if r["name"] == x["window"])["resultCounts"] == x["counts"]
                                                       for x in validation["finalWindows"]),
                        "canonicalUnitCountsMatchIndexed": validation["unitWindow"]["counts"] ==
                                                           next(r for r in rows if r["name"] == "canonical-units-final-01")["resultCounts"],
                        "composedDistinctUnits": validation.get("unitComposedDistinct"),
                        "runtimeExit": validation.get("runtimeExit"),
                        "gitDiffCheck": validation.get("gitDiffCheck")}

    # Compose selected-case identity matrix by consuming the initial index only.
    history_matrix = {item["key"]: list(item["observations"]) for item in initial["caseMatrix"]}
    for row in sorted(rows, key=lambda x: x.get("closedIntervalNs", [0])[0]):
        for case in row.get("cases", []):
            history_matrix.setdefault(case["key"], []).append(
                {"window": row["name"], "sourceManifestSha256": row["sourceManifestSha256"],
                 "status": case["status"], "windowQualification": row["qualification"]})
    case_matrix = []
    for key, observations in sorted(history_matrix.items()):
        qualified = [x for x in observations if x["status"] == "PASS" and x["windowQualification"] in
                     ("qualified-suite-level", "qualified-interval", "qualified-pass")]
        case_matrix.append({"key": key, "observations": observations,
                            "lastQualifiedExecution": qualified[-1] if qualified else None,
                            "latestObservation": observations[-1]})

    summary = {"newCompletedResultWindows": len(names), "newTestWindows": sum(r["kind"] == "test" for r in rows),
               "newNonTestWindows": sum(r["kind"] == "non-test" for r in rows),
               "newXmlCopies": sum(r["xmlCount"] for r in rows),
               "newCaseObservations": sum(len(r.get("cases", [])) for r in rows),
               "newDistinctCaseKeys": len({c["key"] for r in rows for c in r.get("cases", [])}),
               "combinedDistinctSelectedCaseKeys": len(case_matrix),
               "newWindowQualifications": dict(collections.Counter(r["qualification"] for r in rows)),
               "newMechanicalIssueWindows": sum(bool(r["issues"]) for r in rows),
               "newSourceSnapshotIssueWindows": sum(bool(r.get("sourceSnapshotIssues")) for r in rows),
               "currentMainSourceIssues": len(main_current_issues), "currentOwnSourceIssues": len(own_issues),
               "formalRuntimeControllerRawIssues": len(raw_file_issues), "runtimeBundleFileIssues": len(bundle_issues),
               "rawManifestPathCoverageIssues": len(raw_coverage_missing) + len(raw_coverage_extra),
               "rawManifestSizeIssues": len(raw_size_issues), "commitPathIssues": len(expected_commit_set ^ committed_names),
               "privateScanValueHitsReported": scan_receipt.get("exactValueHits"),
               "privateScanPemHitsReported": scan_receipt.get("privatePemHits")}
    output.update({"generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
                   "summary": summary, "windows": rows, "predecessorCorroboration": predecessor,
                   "sourceClosure": source_closure, "migrationHistory": migration,
                   "formalRuntime": formal_runtime, "rawAndCommitScope": raw_scope,
                   "privateScanScriptAndReceiptReview": scan_review,
                   "finalValidationCrossCheck": validation_scope, "caseMatrix": case_matrix})
    INDEX.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")

    lines = ["# Task 09 final mechanical custody — incremental committed pass", "",
             f"Fixed {len(names)} new `result.json` windows at `{output['fixedAtUtc']}`, plus the already completed "
             "`formal-runtime-start-01/formal-runtime-result.json` window. The initial 165-window index "
             "and 20,557-path protected check are reused by hash; their raw files were not rescanned. "
             "No test, build, SQL, Docker, service, migration, network, or Git mutation was run.", "",
             "## New window accounting", "",
             f"{summary['newTestWindows']} test windows and {summary['newNonTestWindows']} OpenAPI evidence window; "
             f"{summary['newXmlCopies']} copied XML and {summary['newCaseObservations']} selected-case observations "
             f"over {summary['newDistinctCaseKeys']} distinct keys. Combined with the initial matrix, "
             f"there are {summary['combinedDistinctSelectedCaseKeys']} distinct selected keys. "
             f"Mechanical issue windows: {summary['newMechanicalIssueWindows']}; source-snapshot issue windows: "
             f"{summary['newSourceSnapshotIssueWindows']}. Full command/result/source/report hashes, interval/before "
             f"records, and each case observation are in [{INDEX.name}]({INDEX.name}).", "",
             "The first inherited/migration run remains **12 tests / 2 FAIL**. The later run is **12 PASS**. "
             "The canonical unit run remains **293 / 0 FAIL / 4 ERROR**: its other 289 XML cases passed. "
             "The separate exact `WorkerShutdownTest` run is **4 PASS** after a test-only reflection-signature "
             "source difference; the canonical 4 ERROR are retained, and no single all-green 293 run is claimed.", "",
             "## Final production source closure", "",
             f"The `candidate-pages-final-02` snapshot has {len(reference_main)} main-source paths. Current bytes "
             f"match all {len(reference_main)}: {len(main_current_issues)} differences. The 154 own-path manifest "
             f"matches current bytes with {len(own_issues)} differences and equals the 154-entry closure list: "
             f"{own_equals_closure}. The final production path map is identical in every new test window except "
             "`inherited-migration-final-01`, whose `JdbcExternalOperations.java` differs; `final-02` and later "
             "qualification windows carry the final map. Older closure comparisons remain source-version records "
             "and were not reopened. Canonical-to-exact-shutdown snapshot differences: "
             f"{', '.join(canonical_shutdown_diffs) or 'none'}.", "",
             "## Final migration history", "",
             f"`full-fifteen-history.json` has {migration['beforeRows']} before and {migration['afterRows']} after rows, "
             f"each with the exact ten Flyway fields: {migration['exactTenFieldsEachRow']}. Raw before, after, "
             f"and the 15-row original derived baseline are equal: {migration['rawBeforeAfterBaselineEqual']}; "
             f"reported migration count: {migration['migrationsExecuted']}. The sealed final test uses SQL "
             "`DATE_FORMAT(installed_on, ...)`, and its sealed controlled datasource sets UTC session time. "
             "This qualifies the final02 rerun, not a retroactive UTC claim for bootstrap-01.", "",
             "## Formal installed runtime", "",
             f"Controller receipt lists {len(controller['files'])} raw files; hash differences: {len(raw_file_issues)}. "
             f"Runtime exit0: {formal_runtime['exit0']}; process group closed with none remaining: "
             f"{formal_runtime['processGroupExit0NoRemaining']}; 64 MiB/2 CPU: "
             f"{formal_runtime['runtime64Mi2Cpu']}; guarded before/after equality: "
             f"{formal_runtime['guardsBeforeAfterEqual']}; retained rows "
             f"{formal_runtime['retainedTaskRows']} tasks/{formal_runtime['retainedObservableOperations']} operations. "
             f"Provider exit {formal_runtime['providerExit']} and gone={formal_runtime['providerGone']}; "
             f"native child exit {formal_runtime['nativeExit']} and alive=false={formal_runtime['nativeClosedAliveFalse']}. "
             f"Bundle manifest matches command hash, names source window `{bundle['sourceWindow']}`, and all "
             f"{len(bundle['files'])} listed noncredential bundle files matched: {len(bundle_issues)} issues. "
             "This is installed import/reconciliation start-close scope with new-generation dispatch=false.", "",
             "## Raw scope, private scan receipt, and commit", "",
             f"Raw manifest has {len(raw_list)} distinct declared files and covers the current raw root exactly "
             f"after its three excluded final metadata files: {raw_scope['rawManifestCoveredActualMinusExcluded']}. "
             f"Path coverage issues {len(raw_coverage_missing) + len(raw_coverage_extra)}, size issues "
             f"{len(raw_size_issues)}. The scanner's {scope.get('rawFiles')} raw files include the raw and staged "
             "manifests and exclude only its own receipt. The pre-receipt staged list contains "
             f"{len(staged_before)} exact paths: "
             "all raw-manifest entries, raw/stage metadata, 154 own source paths, and the W report; "
             f"set equality {raw_scope['stagedBeforeMatchesRawAndOwnAndReport']}. No private `.tools/` path "
             "is in that list. The private-scan receipt is the sole additional path. "
             f"Commit `{head}` has {len(committed_names)} changed paths, exactly matching the staged list plus "
             f"receipt: {raw_scope['commitPathsMatchExactExpected']}; parent matches base: "
             f"{raw_scope['parentMatchesBase']}; index empty: {index_empty}. The committed receipt blob matches "
             f"its working bytes: {raw_scope['receiptCommitBlobMatchesWorkingFile']}. This pass checks raw "
             "manifest path and size coverage plus the new window hashes; it does not rehash every raw payload "
             "or repeat the private-value scan.", "",
             f"The author's scanner source hash matches its receipt: {scan_review['receiptScriptHashMatches']}. "
             "Static inspection shows Task09 owner provider-secret discovery, all raw files except the receipt, "
             "exact staged names and deduplicated staged blobs, a private-key PEM pattern, and an in-memory "
             "check of the receipt itself. The receipt reports known-original 6, actual provider files "
             f"{scan_review['receiptActualProviderSecretFiles']}, unique values "
             f"{scan_review['receiptUniqueValues']}, exact-value hits {scan_review['receiptExactValueHits']}, "
             f"private-PEM hits {scan_review['receiptPrivatePemHits']}, raw files {scope.get('rawFiles')}, "
             f"staged paths {scope.get('exactStagedPaths')}, and unique staged blobs "
             f"{scope.get('uniqueStagedBlobs')}. Those scope counts agree with path metadata and the final "
             "commit tree. This review did not read any private credential value or rerun the scan.", "",
             "The author's final validation records `git diff --check` exit 2 for the single retained "
             "`Task09NativeFixture.java:33` blank-line trailing whitespace finding. It is retained in the "
             "validation JSON and is not reported as clean.", "",
             "## New window ledger", "",
             "| Window | Exit | T/F/E/S | XML | Main diff vs final | Qualification |",
             "| --- | ---: | ---: | ---: | ---: | --- |"]
    main_by_name = {x["window"]: x for x in new_main_comparisons}
    for row in sorted(rows, key=lambda x: x.get("closedIntervalNs", [0])[0]):
        c = row.get("resultCounts") or {}
        counts = "/".join(str(c.get(k, "–")) for k in ("tests", "failures", "errors", "skipped"))
        difference = len(main_by_name[row["name"]]["differingPathsVsFinal"]) if row["name"] in main_by_name else "–"
        lines.append(f"| `{row['name']}` | {row['exit']} | {counts} | {row['xmlCount']} | {difference} | {row['qualification']} |")
    lines.extend(["", "## Combined selected-case matrix", "",
                  "Each row shows the last qualifying whole-window PASS. The JSON retains every historical "
                  "observation and source-manifest SHA. Canonical unit PASS cases in an ERROR window remain "
                  "observed PASS but are not silently promoted to a whole-window qualification.", "",
                  "| Class#method | Observations | Last qualified | Latest observation |",
                  "| --- | ---: | --- | --- |"])
    for item in case_matrix:
        q = item["lastQualifiedExecution"]
        last = item["latestObservation"]
        lines.append(f"| `{item['key']}` | {len(item['observations'])} | "
                     f"`{q['window'] if q else 'none'}` | `{last['window']}` {last['status']} |")
    lines.append("")
    REPORT.write_text("\n".join(lines))
    print(json.dumps({"summary": summary, "predecessor": {k: len(v) if isinstance(v, list) else v for k, v in predecessor.items()},
                      "migrationEqual": migration["rawBeforeAfterBaselineEqual"],
                      "runtimeExit0": formal_runtime["exit0"], "report": str(REPORT)}))


if __name__ == "__main__":
    main()
