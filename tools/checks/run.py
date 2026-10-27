"""Fail-closed local verification. All Java counts come from fresh JUnit XML."""
import argparse
import fnmatch
import json
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
PYTHON_SUITE = pathlib.Path(__file__).with_name("python_suite.py")

def execute(command, cwd, reports):
    """Remove stale reports, run the command, and require executed passing tests."""
    for report in reports:
        report.unlink(missing_ok=True)
    try:
        result = subprocess.run(command, cwd=cwd, check=False)
    except OSError as error:
        print(f"FAIL: cannot launch verification: {error}", file=sys.stderr)
        return 1
    if result.returncode:
        print(f"FAIL: subprocess exited {result.returncode}", file=sys.stderr)
        return result.returncode if result.returncode > 0 else 1
    executed = failures = skipped = 0
    try:
        for report in reports:
            if not report.exists():
                continue
            root = ET.parse(report).getroot()
            suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
            # Node 22's built-in JUnit reporter emits direct testcase children.
            if not suites and root.tag == "testsuites":
                cases = root.findall("testcase")
                skipped_cases = sum(case.find("skipped") is not None for case in cases)
                executed += len(cases) - skipped_cases
                skipped += skipped_cases
                failures += sum(case.find("failure") is not None or case.find("error") is not None for case in cases)
            for suite in suites:
                executed += int(suite.get("tests", "0")) - int(suite.get("skipped", "0"))
                skipped += int(suite.get("skipped", "0"))
                failures += int(suite.get("failures", "0")) + int(suite.get("errors", "0"))
    except (ET.ParseError, ValueError) as error:
        print(f"FAIL: invalid test report: {error}", file=sys.stderr)
        return 1
    if executed <= 0 or failures or skipped:
        print(f"FAIL: executed={executed}, failures={failures}, skipped={skipped}; a zero/pending/skipped run is not PASS", file=sys.stderr)
        return 1
    print(f"PASS: executed={executed}, failures=0; evidence=" + ",".join(str(report) for report in reports if report.exists()), flush=True)
    return 0

def java_tests(selector="*", integration=False):
    modules = ["backend/integration-tests"] if integration else sorted(str(pom.parent.relative_to(ROOT)) for pom in ROOT.glob("backend/*/pom.xml"))
    sources = [source for module in modules for source in (ROOT / module / "src/test/java").rglob("*IT.java" if integration else "*Test.java")]
    class_selector = selector.split("#", 1)[0]
    matched = [path for path in sources if fnmatch.fnmatchcase(path.stem, class_selector)]
    if not matched:
        print(f"FAIL: zero matching tests for {selector!r}", file=sys.stderr)
        return 1
    if integration and not __import__("os").environ.get("AMA_TEST_DATABASE_URL", "").startswith("jdbc:mysql:"):
        print("FAIL: it requires explicit AMA_TEST_DATABASE_URL for an approved MySQL test environment", file=sys.stderr)
        return 1
    # Include expected report names on first use; glob finds fresh reports from dependencies too.
    report_kind = "failsafe-reports" if integration else "surefire-reports"
    reports = set(ROOT.glob(f"backend/*/target/{report_kind}/TEST-*.xml"))
    for source in matched:
        package = source.read_text().split("package ", 1)[1].split(";", 1)[0].strip()
        module = next(module for module in modules if source.is_relative_to(ROOT / module))
        reports.add(ROOT / module / "target" / report_kind / f"TEST-{package}.{source.stem}.xml")
    command = [str(ROOT / "mvnw"), "-B", "-ntp", "-pl", ",".join(modules), "-am"]
    # Expand discovered classes: Maven wildcard overrides would mix unit and IT cases.
    method = "#" + selector.split("#", 1)[1] if "#" in selector else ""
    discovered_selector = ",".join(sorted({path.stem + method for path in matched}))
    command += [f"-Dit.test={discovered_selector}", "-Dfailsafe.failIfNoSpecifiedTests=false", "verify"] if integration else [f"-Dtest={discovered_selector}", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    return execute(command, ROOT, sorted(reports))

def owned_output(path, group):
    """Reserve a never-used directory within this checkout; never delete existing evidence."""
    root = ROOT.resolve()
    target = pathlib.Path(path)
    if not target.is_absolute():
        target = root / target
    target = target.resolve()
    if not target.is_relative_to(root) or target == root:
        raise ValueError("output root must stay inside this checkout")
    target.mkdir(parents=True, exist_ok=False)
    (target / "owned-output.json").write_text(json.dumps({"schema": 1, "repository": str(root), "output": str(target), "group": group}) + "\n")
    return target


def contracts(output=None):
    try:
        if output is None:
            result = subprocess.run(["npm", "run", "check:openapi"], cwd=ROOT, check=False)
            destination = ROOT / "artifacts/openapi/api.yaml"
        else:
            cli = str(ROOT / "node_modules/@redocly/cli/bin/cli.js")
            destination = output / "api.yaml"
            for action in ("lint", "bundle"):
                command = ["node", cli, action, "contracts/openapi/api.yaml", "--config", "redocly.yaml"]
                if action == "bundle":
                    command += ["--output", str(destination)]
                result = subprocess.run(command, cwd=ROOT, check=False)
                if result.returncode:
                    return result.returncode if result.returncode > 0 else 1
        if output is not None and (not destination.is_file() or destination.is_symlink() or destination.stat().st_size == 0):
            print("FAIL: fresh OpenAPI bundle missing or invalid", file=sys.stderr)
            return 1
        if result.returncode == 0:
            print(f"PASS: checks=2 (OpenAPI validation, external-reference merge); evidence={destination}", flush=True)
        return result.returncode
    except OSError as error:
        print(f"FAIL: OpenAPI validator unavailable: {error}", file=sys.stderr)
        return 1

def web_tests(group, selector="*", output=None):
    """Register delivered web suites, with fresh JUnit counts through execute()."""
    folder = {"web": "unit", "e2e": "e2e", "visual": "visual"}[group]
    suffix = "*.test.ts" if group == "web" else "*.spec.ts"
    sources = sorted((ROOT / "web/tests" / folder).rglob(suffix))
    matched = [path for path in sources if fnmatch.fnmatchcase(path.name, selector)]
    if not matched:
        print(f"FAIL: zero matching {group} tests for {selector!r}", file=sys.stderr)
        return 1
    report = (output if output is not None else ROOT / "artifacts/web") / f"{group}.xml"
    report.parent.mkdir(parents=True, exist_ok=True)
    binaries = ROOT / "web/node_modules/.bin"
    if group == "web":
        command = [str(binaries / "tsx"), "--test", "--test-reporter=junit", f"--test-reporter-destination={report}"]
    else:
        command = ["env", f"PLAYWRIGHT_JUNIT_OUTPUT_FILE={report}"]
        if output is not None:
            command += [f"AMA_CHECK_OUTPUT_ROOT={output}"]
        command += [str(binaries / "playwright"), "test"]
    command += [str(path.relative_to(ROOT / "web")) for path in matched]
    return execute(command, ROOT / "web", [report])

def python_tests():
    """Use the reviewed accounting worker for every delivered Python test directory."""
    import os
    directories = set()
    for parent, children, files in os.walk(ROOT):
        children[:] = [name for name in children if name not in {"node_modules", ".git", ".tools", ".worktrees", "artifacts", "__pycache__", ".venv"}]
        if any(fnmatch.fnmatchcase(name, "test_*.py") for name in files):
            directories.add(pathlib.Path(parent))
    # Preserve the worker's fail-closed missing/empty-discovery behavior on a fresh checkout.
    directories = sorted(directories) or [ROOT / "tools/checks/tests"]
    report = ROOT / "artifacts/checks/python.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.unlink(missing_ok=True)
    log = report.with_suffix(".log")
    totals = dict(discovered=0, executed=0, failed=0, skipped=0)
    evidence = []
    result = None
    status = 0
    try:
        with log.open("w") as output:
            for index, directory in enumerate(directories):
                fresh = report if len(directories) == 1 else report.with_name(f"python-{index}.json")
                fresh.unlink(missing_ok=True)
                result = subprocess.run([sys.executable, str(PYTHON_SUITE), str(directory), str(fresh)], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=False)
                counts = json.loads(fresh.read_text())
                for key in totals:
                    totals[key] += int(counts[key])
                evidence.append({"directory": str(directory.relative_to(ROOT)), "report": str(fresh), **counts})
                passed = result.returncode == 0 and int(counts["executed"]) > 0 and int(counts["failed"]) == 0 and int(counts["skipped"]) == 0
                if not passed and not status:
                    status = result.returncode if result.returncode > 0 else 1
        if len(directories) > 1:
            report.write_text(json.dumps({**totals, "directories": evidence}, indent=2) + "\n")
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"FAIL: invalid or missing fresh Python evidence: {error}; evidence={report}; log={log}", file=sys.stderr)
        return result.returncode if result is not None and result.returncode > 0 else 1
    passed = status == 0 and totals["executed"] > 0 and totals["failed"] == 0 and totals["skipped"] == 0
    print(f"{'PASS' if passed else 'FAIL'}: Python executed={totals['executed']}, failed={totals['failed']}, skipped={totals['skipped']}; directories={len(directories)}; evidence={report}; log={log}", flush=True)
    return status if status else 0 if passed else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group", choices=["unit", "it", "web", "e2e", "visual", "contract", "load", "ops", "all"])
    parser.add_argument("selector", nargs="?")
    parser.add_argument("--output-root", help="Never-used output directory inside this checkout (web/e2e/visual/contract only)")
    args = parser.parse_args(argv)
    output = None
    if args.output_root is not None:
        if args.group not in ("web", "e2e", "visual") and not (args.group == "contract" and args.selector == "OpenApi"):
            print("FAIL: output isolation is only registered for web/e2e/visual/contract OpenApi", file=sys.stderr)
            return 1
        try:
            output = owned_output(args.output_root, args.group)
        except (OSError, ValueError) as error:
            print(f"FAIL: cannot reserve fresh output root: {error}", file=sys.stderr)
            return 1
    if args.group in ("unit", "it"):
        return java_tests(args.selector or "*", args.group == "it")
    if args.group in ("web", "e2e", "visual"):
        return web_tests(args.group, args.selector or "*", output)
    if args.group == "contract" and args.selector == "OpenApi":
        return contracts(output)
    if args.group != "all":
        print(f"FAIL: {args.group}/{args.selector} is not registered; required test environment or implementation is pending", file=sys.stderr)
        return 1
    if args.selector:
        parser.error("all does not accept a selector")
    python_result = python_tests()
    if python_result:
        return python_result
    if (ROOT / "web/tests/unit").exists():
        result = web_tests("web")
        if result:
            return result
    result = contracts()
    if result:
        return result
    return java_tests()

if __name__ == "__main__":
    raise SystemExit(main())
