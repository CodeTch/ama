"""Fail-closed local verification. All Java counts come from fresh JUnit XML."""
import argparse
import fnmatch
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]

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
    command += [f"-Dit.test={selector}", "-Dfailsafe.failIfNoSpecifiedTests=false", "verify"] if integration else [f"-Dtest={selector}", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    return execute(command, ROOT, sorted(reports))

def contracts():
    try:
        result = subprocess.run(["npm", "run", "check:openapi"], cwd=ROOT, check=False)
        if result.returncode == 0:
            print(f"PASS: checks=2 (OpenAPI validation, external-reference merge); evidence={ROOT / 'artifacts/openapi/api.yaml'}", flush=True)
        return result.returncode
    except OSError as error:
        print(f"FAIL: OpenAPI validator unavailable: {error}", file=sys.stderr)
        return 1

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group", choices=["unit", "it", "web", "e2e", "visual", "contract", "load", "ops", "all"])
    parser.add_argument("selector", nargs="?")
    args = parser.parse_args(argv)
    if args.group in ("unit", "it"):
        return java_tests(args.selector or "*", args.group == "it")
    if args.group == "contract" and args.selector == "OpenApi":
        return contracts()
    if args.group != "all":
        print(f"FAIL: {args.group}/{args.selector} is not registered; required test environment or implementation is pending", file=sys.stderr)
        return 1
    if args.selector:
        parser.error("all does not accept a selector")
    python_result = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "tools/checks/tests"], cwd=ROOT)
    if python_result.returncode:
        return python_result.returncode
    result = contracts()
    if result:
        return result
    return java_tests()

if __name__ == "__main__":
    raise SystemExit(main())
