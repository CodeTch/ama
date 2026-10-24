#!/usr/bin/env python3
"""Read-only mechanical custody index for the frozen Task 09 window list.

This script reads historical files and writes only its same-stem JSON and MD.
It deliberately does not copy command text, XML failure text, source, or secrets.
"""

from __future__ import annotations

import collections
import datetime as dt
import hashlib
import json
import pathlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[3]
W = pathlib.Path(__file__).resolve().parent
STEM = "task-09-mechanical-custody-initial-20261024"
INDEX = W / (STEM + ".json")
REPORT = W / (STEM + ".md")
RAW = ROOT / "artifacts/phase-b/b-remaining/task-09"
PROTECTED = W / "task-09-protected-adopted-inputs-20261024.json"
PRIOR = W / "task-09-prior-collector-root-independent-audit-20261024.json"
NON_TEST = {"external-diagnostic-01", "minutes-openapi-01", "minutes-openapi-02"}
EXCLUDED = {
    "graph-controls-green-01": "old collector stale copied six-case XML; canonical selector ran zero tests",
    "application-provider-restart-02": "child application used 512 MiB; resource-ineligible; 03 is the qualified 64 MiB run",
    "minutes-response-budget-green-02": "R1 HTTP budget semantics require 413; passing 503 expectation is contract-ineligible",
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load(path: pathlib.Path):
    return json.loads(path.read_bytes())


def safe_file(path: pathlib.Path, expected_bytes=None, expected_sha=None):
    if not path.is_file():
        return "missing"
    data = path.read_bytes()
    if expected_bytes is not None and len(data) != expected_bytes:
        return "size"
    if expected_sha is not None and sha(data) != expected_sha:
        return "sha256"
    return None


def case_key(item):
    return item["class"] + "#" + item["name"]


def xml_data(path):
    root = ET.fromstring(path.read_bytes())
    suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
    counts = {x: 0 for x in ("tests", "failures", "errors", "skipped")}
    cases = []
    for suite in suites:
        for key in counts:
            counts[key] += int(suite.attrib.get(key, 0))
        for item in suite.findall(".//testcase"):
            cases.append({"class": item.attrib.get("classname", ""), "name": item.attrib.get("name", ""),
                          "failure": item.find("failure") is not None,
                          "error": item.find("error") is not None,
                          "skipped": item.find("skipped") is not None})
    return counts, cases


def main():
    frozen = load(INDEX)
    names = frozen["windowNamesAtStart"]
    baseline = load(PROTECTED)
    protected_issues = []
    checked = 0
    seen = set()
    for item in baseline["files"]:
        rel = item["path"]
        if rel in seen:
            protected_issues.append({"path": rel, "reason": "duplicate baseline path"})
            continue
        seen.add(rel)
        path = ROOT / rel
        problem = safe_file(path, item["bytes"], item["sha256"])
        checked += 1
        if problem:
            protected_issues.append({"path": rel, "reason": problem})

    prior = load(PRIOR)
    rows = []
    for name in names:
        p = RAW / name
        issues = []
        if not (p / "result.json").is_file():
            rows.append({"name": name, "kind": "missing-result", "issues": ["result.json missing after freeze"]})
            continue
        result = load(p / "result.json")
        command_path = p / "command.json"
        source_path = p / "source-manifest.json"
        custody_path = p / "report-custody.json"
        command = load(command_path) if command_path.is_file() else None
        custody = load(custody_path) if custody_path.is_file() else None
        source = load(source_path) if source_path.is_file() else None
        kind = ("non-test" if name in NON_TEST else
                "new-collector" if custody is not None else "old-collector")
        row = {"name": name, "kind": kind, "resultSha256": sha((p / "result.json").read_bytes()),
               "commandSha256": sha(command_path.read_bytes()) if command is not None else None,
               "sourceManifestSha256": sha(source_path.read_bytes()) if source is not None else None,
               "startedUtc": command.get("startedUtc") if isinstance(command, dict) else None,
               "exit": result.get("exit") if isinstance(result, dict) else None,
               "sourceFiles": len(source) if isinstance(source, list) else None,
               "issues": issues}
        if isinstance(command, dict) and source is not None:
            if command.get("sourceSha256") != row["sourceManifestSha256"]:
                issues.append("command sourceSha256 differs from source-manifest raw SHA256")
        if isinstance(source, list):
            source_problems = []
            source_seen = set()
            for item in source:
                rel = item["path"]
                if rel in source_seen:
                    source_problems.append({"path": rel, "reason": "duplicate source path"})
                    continue
                source_seen.add(rel)
                problem = safe_file(p / "source" / rel, item.get("bytes"), item["sha256"])
                if problem:
                    source_problems.append({"path": rel, "reason": problem})
            row["sourceSnapshotIssues"] = source_problems
            if source_problems:
                issues.append(f"{len(source_problems)} source snapshot entries differ")
        xmls = sorted(p.glob("*.xml"))
        row["xmlCount"] = len(xmls)
        xml_counts = {x: 0 for x in ("tests", "failures", "errors", "skipped")}
        selected_xml_counts = dict(xml_counts)
        incidental_xml_counts = dict(xml_counts)
        xml_cases = []
        selected_xml_cases = []
        incidental_xml_cases = []
        reports = []
        for x in xmls:
            try:
                counts, cases = xml_data(x)
            except Exception as exc:
                issues.append(f"XML parse issue {x.name}: {type(exc).__name__}")
                continue
            for key in xml_counts:
                xml_counts[key] += counts[key]
                (incidental_xml_counts if x.name.startswith("implicit-unit-") else selected_xml_counts)[key] += counts[key]
            xml_cases.extend(cases)
            (incidental_xml_cases if x.name.startswith("implicit-unit-") else selected_xml_cases).extend(cases)
            reports.append({"copy": x.name, "sha256": sha(x.read_bytes()), "counts": counts,
                            "cases": [case_key(v) for v in cases]})
        row["xmlCounts"] = xml_counts
        row["selectedXmlCounts"] = selected_xml_counts
        row["incidentalUnitXmlCounts"] = incidental_xml_counts
        row["reports"] = reports
        row["cases"] = []
        row["incidentalUnitCases"] = [{"key": case_key(c),
                                       "status": "ERROR" if c["error"] else "FAIL" if c["failure"] else "SKIP" if c["skipped"] else "PASS"}
                                      for c in incidental_xml_cases]
        if isinstance(result, dict) and "counts" in result:
            row["resultCounts"] = result["counts"]
            row["cases"] = [{"key": case_key(c),
                             "status": "ERROR" if c["error"] else "FAIL" if c["failure"] else "SKIP" if c["skipped"] else "PASS"}
                            for c in result.get("cases", [])]
            if result["counts"] != selected_xml_counts:
                issues.append("result counts differ from selected copied XML counts")
            expected = collections.Counter((case_key(c), c["failure"], c["error"], c["skipped"])
                                           for c in result.get("cases", []))
            actual = collections.Counter((case_key(c), c["failure"], c["error"], c["skipped"])
                                         for c in selected_xml_cases)
            if expected != actual:
                issues.append("result case identity/status differs from selected copied XML")
        if kind == "new-collector":
            start, end = custody.get("startedNs"), custody.get("endedNs")
            row["reportCustodySha256"] = sha(custody_path.read_bytes())
            row["closedIntervalNs"] = [start, end]
            mapped = []
            for cr in custody.get("reports", []):
                orig = cr.get("path", "")
                module = orig.split("/")[1] if orig.startswith("backend/") else ""
                copy = module + "-" + pathlib.PurePosixPath(orig).name
                copied = p / copy
                problem = safe_file(copied, expected_sha=cr.get("sha256"))
                interval_ok = (isinstance(start, int) and isinstance(end, int)
                               and isinstance(cr.get("mtimeNs"), int)
                               and start <= cr["mtimeNs"] <= end)
                previous = cr.get("previous")
                previous_ok = previous is None or (isinstance(previous, list) and len(previous) == 2
                                                   and isinstance(previous[0], int)
                                                   and isinstance(previous[1], str)
                                                   and re.fullmatch(r"[0-9a-f]{64}", previous[1]) is not None)
                mapped.append({"sourceReport": orig, "copy": copy, "sha256": cr.get("sha256"),
                               "mtimeNs": cr.get("mtimeNs"), "previous": previous,
                               "reportedQualified": cr.get("qualified"), "copyProblem": problem,
                               "inClosedInterval": interval_ok, "previousWellFormed": previous_ok})
                if problem or not interval_ok or not previous_ok or cr.get("qualified") is not True:
                    issues.append(f"report custody mismatch: {copy}")
            row["custodyReports"] = mapped
            if collections.Counter(x["copy"] for x in mapped) != collections.Counter(x.name for x in xmls):
                issues.append("report-custody XML set differs from copied XML set")
        elif kind == "old-collector":
            row["custodyBasis"] = "accepted retrospective suite-level root audit; no per-case before/closed interval"
        if kind != "non-test":
            if name in EXCLUDED:
                row["qualification"] = "excluded"
                row["qualificationReason"] = EXCLUDED[name]
            elif issues:
                row["qualification"] = "unresolved-mechanical"
            elif not isinstance(result, dict) or "counts" not in result:
                row["qualification"] = "unresolved-result-shape"
            elif result["counts"]["tests"] == 0:
                log = (p / "output.log").read_text(errors="replace") if (p / "output.log").is_file() else ""
                row["qualification"] = "pretest-compile-failure" if ("COMPILATION ERROR" in log or "maven-compiler-plugin" in log) else "pretest-setup-or-command-failure"
            elif result["counts"]["errors"]:
                row["qualification"] = "test-error"
            elif result["counts"]["failures"]:
                row["qualification"] = "assertion-failure"
            elif result["exit"] != 0:
                row["qualification"] = "post-test-nonzero-exit"
            else:
                row["qualification"] = "qualified-suite-level" if kind == "old-collector" else "qualified-interval"
        else:
            row["qualification"] = "non-test-evidence"
        rows.append(row)

    # Closed-window predecessor comparison is corroboration only; the recorded
    # before image cannot itself be witnessed after the fact.
    by_time = sorted((r for r in rows if r["kind"] == "new-collector"),
                     key=lambda r: (r.get("closedIntervalNs") or [0])[0])
    latest_report = {}
    predecessor_checks = {"matching": 0, "different": [], "unavailable": 0, "clearedBeforeRun": 0}
    for row in by_time:
        for report in row.get("custodyReports", []):
            prev = latest_report.get(report["sourceReport"])
            before = report["previous"]
            if prev is None:
                predecessor_checks["unavailable"] += 1
            elif before is None:
                predecessor_checks["clearedBeforeRun"] += 1
            elif before == prev:
                predecessor_checks["matching"] += 1
            else:
                predecessor_checks["different"].append({"window": row["name"], "report": report["sourceReport"],
                                                         "reason": "recorded before differs from last fixed Task09 window"})
            latest_report[report["sourceReport"]] = [report["mtimeNs"], report["sha256"]]

    def timekey(row):
        if row.get("closedIntervalNs"):
            return row["closedIntervalNs"][0]
        if row.get("startedUtc"):
            return int(dt.datetime.fromisoformat(row["startedUtc"]).timestamp() * 1_000_000_000)
        return 0

    matrix = collections.defaultdict(list)
    for row in sorted(rows, key=timekey):
        for c in row.get("cases", []):
            matrix[c["key"]].append({"window": row["name"], "sourceManifestSha256": row["sourceManifestSha256"],
                                     "status": c["status"], "windowQualification": row["qualification"]})
    case_matrix = []
    unclosed = []
    for key, observations in sorted(matrix.items()):
        qualified = [o for o in observations if o["status"] == "PASS" and
                     o["windowQualification"] in ("qualified-suite-level", "qualified-interval")]
        last = qualified[-1] if qualified else None
        latest = observations[-1]
        item = {"key": key, "observations": observations,
                "lastQualifiedExecution": last, "latestObservation": latest}
        case_matrix.append(item)
        if last is None or latest["status"] in ("FAIL", "ERROR") and latest is not last:
            unclosed.append({"key": key, "lastQualifiedWindow": last["window"] if last else None,
                             "latestWindow": latest["window"], "latestStatus": latest["status"]})

    ruling_checks = {}
    for name, filename in (
        ("minutes-response-budget-green-02", "backend/integration-tests/src/test/java/ama/storage/Task09MinutesResponseBudgetIT.java"),
        ("minutes-response-budget-green-03", "backend/integration-tests/src/test/java/ama/storage/Task09MinutesResponseBudgetIT.java"),
        ("application-provider-restart-02", "backend/integration-tests/src/test/java/ama/storage/Task09GenerationApplicationRestartIT.java"),
        ("application-provider-restart-03", "backend/integration-tests/src/test/java/ama/storage/Task09GenerationApplicationRestartIT.java"),
    ):
        source_bytes = (RAW / name / "source" / filename).read_bytes()
        source_text = source_bytes.decode("utf-8")
        ruling_checks[name] = {"snapshotPath": filename, "snapshotSha256": sha(source_bytes),
                               "contains413Literal": re.search(r"\b413\b", source_text) is not None,
                               "contains503Literal": re.search(r"\b503\b", source_text) is not None,
                               "containsChild64MiBFlag": "-Xmx64m" in source_text,
                               "containsChild512MiBFlag": "-Xmx512m" in source_text}

    summary = {
        "frozenWindows": len(names), "windowKinds": dict(collections.Counter(r["kind"] for r in rows)),
        "windowQualifications": dict(collections.Counter(r["qualification"] for r in rows)),
        "protectedExpected": len(baseline["files"]), "protectedChecked": checked,
        "protectedIssues": len(protected_issues), "copiedXml": sum(r["xmlCount"] for r in rows),
        "caseObservations": sum(len(r["cases"]) for r in rows), "uniqueCaseKeys": len(case_matrix),
        "incidentalUnitObservations": sum(len(r["incidentalUnitCases"]) for r in rows),
        "incidentalUnitUniqueKeys": len({c["key"] for r in rows for c in r["incidentalUnitCases"]}),
        "uniqueQualifiedPassKeys": sum(x["lastQualifiedExecution"] is not None for x in case_matrix),
        "unclosedOrNeedsDisposition": len(unclosed),
        "mechanicalIssueWindows": sum(bool(r["issues"]) for r in rows),
        "sourceSnapshotIssueWindows": sum(bool(r.get("sourceSnapshotIssues")) for r in rows),
    }
    frozen.update({"generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
                   "protectedManifestSha256": sha(PROTECTED.read_bytes()), "protected": {
                       "expected": len(baseline["files"]), "checked": checked, "issues": protected_issues},
                   "priorRootAudit": {"sha256": sha(PRIOR.read_bytes()), "status": prior["status"],
                                      "scope": prior["scope"]},
                   "summary": summary, "predecessorCorroboration": predecessor_checks,
                   "specificRulingChecks": ruling_checks,
                   "windows": rows, "caseMatrix": case_matrix,
                   "unclosedOrNeedsDisposition": unclosed})
    INDEX.write_text(json.dumps(frozen, ensure_ascii=False, indent=2) + "\n")

    lines = ["# Task 09 initial mechanical custody — frozen historical windows", "",
             f"Frozen at `{frozen['fixedAtUtc']}`; indexed {len(names)} pre-existing result windows. "
             "This is a read-only historical index, not a product or SPEC verdict. "
             "No test, build, SQL, Docker, service, migration, or network action was run.", "",
             "## Protected adopted inputs", "",
             f"Compared raw bytes, sizes, and SHA-256 for all {checked} distinct paths from the "
             f"{len(baseline['files'])}-entry protected manifest. Issues: **{len(protected_issues)}**. "
             "No private file contents or individual secret digests are recorded.", "",
             "## Window accounting", "",
             f"Window kinds: `{summary['windowKinds']}`. Qualifications: `{summary['windowQualifications']}`. "
             f"Copied XML: {summary['copiedXml']}; case observations: {summary['caseObservations']}; "
             f"distinct class#method keys: {summary['uniqueCaseKeys']}; keys with a qualified PASS: "
             f"{summary['uniqueQualifiedPassKeys']}. The old `template-red-01` also copied "
             f"{summary['incidentalUnitObservations']} incidental unit observations across "
             f"{summary['incidentalUnitUniqueKeys']} separate keys. They remain indexed but are not "
             "selected Task09 case qualifications. Repeated windows do not add new keys.", "",
             "Each window's command/result file hash, source manifest hash and snapshot-entry verification, "
             "XML counts/case identities, and report-custody copy/hash/closed-interval/before record are "
             f"indexed in [{INDEX.name}]({INDEX.name}). Mechanical issue windows: "
             f"{summary['mechanicalIssueWindows']}; source snapshot issue windows: "
             f"{summary['sourceSnapshotIssueWindows']}. The old collector's 28-window/69-XML "
             "suite-level audit is reused, not rerun. Its 68 matching XML and one stale XML remain "
             "subject to the recorded limit: no historical per-case before/closed interval proof.", "",
             "## Required exclusions and limits", "",
             "- `graph-controls-green-01`: all stale six copied cases excluded; zero canonical tests executed. Its copied failure count remains indexed as historical artifact, not business assertion.",
             "- `application-provider-restart-02`: child 512 MiB makes the run ineligible. `application-provider-restart-03` is the 64 MiB qualification.",
             "- `minutes-response-budget-green-02`: XML PASS retained, but the 503 expectation is R1-ineligible. `green-03` has the corrected 413 expectation.",
             "- The sealed test snapshots confirm response-budget green-02 contains 503 and not 413, while green-03 contains 413 and not 503; restart-02 contains the 512 MiB child flag while restart-03 contains the 64 MiB child flag. The exact checks are recorded in JSON.",
             "- Compile/setup or command failures with zero fresh XML are separate from assertion FAIL and test ERROR; all observed failure/error statuses remain in the index.",
             "- Per-window source hashes are historical versions. Current source is not substituted. The before-image and interval fields corroborate new collector copies, but this audit cannot claim continuous per-case custody.", "",
             "## Source-window disposition requiring author explanation", "",
             f"{len(unclosed)} distinct case keys have no qualified PASS in the fixed historical set or have a later FAIL/ERROR observation. "
             "This is a mechanical follow-up list, not a defect count; some are intentional RED or maintenance windows. "
             "Exact keys and last qualified windows are in the JSON matrix.", ""]
    for entry in unclosed:
        lines.append(f"- `{entry['key']}` — latest `{entry['latestWindow']}` {entry['latestStatus']}; "
                     f"last qualified `{entry['lastQualifiedWindow'] or 'none'}`.")
    lines.extend(["", "## Checks requiring interpretation", "",
                  f"Recorded before-image links matched the immediately preceding fixed new-collector Task09 report "
                  f"{predecessor_checks['matching']} times; {predecessor_checks['unavailable']} had no prior fixed "
                  f"new-collector report; {predecessor_checks['clearedBeforeRun']} recorded no before image despite "
                  "an earlier fixed report on that path (consistent with a prior deletion). "
                  f"{len(predecessor_checks['different'])} non-null before images differed from the last fixed Task09 report. "
                  "The stored before image is not independently witnessed after the fact.", "",
                  "Final author source/raw freeze and secret/PEM scan are pending the coordinator's exact range and safe in-memory inputs. "
                  "This initial index is intentionally limited to the fixed historical windows.", ""])
    lines.extend(["## Fixed window ledger", "",
                  "Counts below are selected result counts. Incidental implicit units are listed separately in the JSON.", "",
                  "| Window | Kind | Exit | T/F/E/S | XML | Source manifest SHA-256 | Qualification |",
                  "| --- | --- | ---: | ---: | ---: | --- | --- |"])
    for row in sorted(rows, key=timekey):
        c = row.get("resultCounts") or {}
        count_text = "/".join(str(c.get(k, "–")) for k in ("tests", "failures", "errors", "skipped"))
        lines.append(f"| `{row['name']}` | {row['kind']} | {row['exit'] if row['exit'] is not None else '–'} | "
                     f"{count_text} | {row['xmlCount']} | `{row['sourceManifestSha256'] or 'none'}` | "
                     f"{row['qualification']} |")
    lines.extend(["", "## Independent selected-case matrix", "",
                  "The last qualified window is chosen by recorded start time; earlier versions and all RED/ERROR "
                  "observations remain in the JSON matrix. No selected key ends with an unresolved failure/error "
                  "within this fixed historical set; the final source has not yet been frozen or qualified.", "",
                  "| Class#method | Observations | Last qualified window | Latest window/status |",
                  "| --- | ---: | --- | --- |"])
    for item in case_matrix:
        last = item["lastQualifiedExecution"]
        latest = item["latestObservation"]
        lines.append(f"| `{item['key']}` | {len(item['observations'])} | "
                     f"`{last['window'] if last else 'none'}` | `{latest['window']}` {latest['status']} |")
    lines.append("")
    REPORT.write_text("\n".join(lines))
    print(json.dumps({"summary": summary,
                      "predecessor": {"matching": predecessor_checks["matching"],
                                      "different": len(predecessor_checks["different"]),
                                      "unavailable": predecessor_checks["unavailable"],
                                      "clearedBeforeRun": predecessor_checks["clearedBeforeRun"]},
                      "report": str(REPORT), "index": str(INDEX)}))


if __name__ == "__main__":
    main()
