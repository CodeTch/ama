import json
from pathlib import Path


def test_import_cas_report_has_classified_request_outcomes():
    report = json.loads(
        Path(".tools/local-app/integration-import-cas/report.json").read_text(encoding="utf-8")
    )

    assert report["pass"] is True
    assert report["requests"]
    assert all(item["status"] is not None for item in report["requests"])
    assert all(item["status"] != "PENDING" for item in report["requests"])
    assert report["serverErrors"] == []
    assert report["requestFailures"] == []
