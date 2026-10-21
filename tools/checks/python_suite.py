"""Run isolated unittest discovery and emit actual result counts for the gate."""
import json
import pathlib
import sys
import unittest

def main(discovery, report):
    suite = unittest.defaultTestLoader.discover(str(discovery))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    counts = {
        "discovered": result.testsRun,
        "executed": result.testsRun - len(result.skipped),
        "failed": len(result.failures) + len(result.errors) + len(result.unexpectedSuccesses) + len(result.expectedFailures),
        "skipped": len(result.skipped),
    }
    temporary = report.with_suffix(".tmp")
    temporary.write_text(json.dumps(counts, indent=2) + "\n")
    temporary.replace(report)
    return 0 if counts["executed"] > 0 and counts["failed"] == 0 and counts["skipped"] == 0 else 1

if __name__ == "__main__":
    raise SystemExit(main(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])))
