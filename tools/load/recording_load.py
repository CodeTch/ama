#!/usr/bin/env python3
"""Deterministic protocol load client used for local accounting smoke runs.

It deliberately reports simulated protocol evidence separately from real model evidence.
"""
import argparse
import json
import os
from pathlib import Path
try:
    from .report import build_report
except ImportError:
    from report import build_report

def run(scenario):
    # No transport has executed here. Never manufacture successful samples or
    # report requested users/duration as observed protocol capacity.
    report = build_report([], expected_recorders=int(scenario["recorders"]), expected_users=int(scenario["totalUsers"]))
    return {**report, "evidenceMode": "NOT_EXECUTED", "reason": "PROTOCOL_TRANSPORT_REQUIRED", "requestedDurationSeconds": scenario.get("durationSeconds"), "observedDurationSeconds": 0}

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("scenario", type=Path); parser.add_argument("--output", type=Path)
    parser.add_argument("--execute",action="store_true");parser.add_argument("--output-root",type=Path);parser.add_argument("--api-fault",action="store_true")
    args = parser.parse_args();scenario=json.loads(args.scenario.read_text())
    destination=args.output_root or (Path(os.environ["AMA_RECORDING_LOAD_OUTPUT"]) if os.environ.get("AMA_RECORDING_LOAD_OUTPUT") else None)
    if args.execute or os.environ.get("AMA_RUN_RECORDING_LOAD")=="1":
        if destination is None:parser.error("actual load requires a new --output-root or AMA_RECORDING_LOAD_OUTPUT")
        import asyncio,sys
        sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
        from tools.load.transport import Transport
        report=asyncio.run(Transport(scenario,destination,api_fault=args.api_fault or os.environ.get("AMA_RECORDING_LOAD_API_FAULT")=="1").run_all())
        (destination/"report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    else:report = run(scenario)
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output: args.output.write_text(payload)
    else: print(payload, end="")
    return 0 if report["passed"] else 1

if __name__ == "__main__": raise SystemExit(main())
