"""Fail-closed aggregation for recording protocol load evidence."""
from __future__ import annotations

from statistics import quantiles
from math import isfinite

def qualify_transport(scenario, recordings, background):
    """Qualify executed recording protocol only; speech latency requires separate observed evidence."""
    from math import ceil
    recordings,background=list(recordings),list(background)
    duration=int(scenario["durationSeconds"]);chunk_ms=int(scenario["chunkMs"])
    if duration<1 or chunk_ms<1 or chunk_ms>2000:
        raise ValueError("Positive bounded recording duration/chunk required")
    expected_chunks=ceil(duration*1000/chunk_ms);expected_samples=duration*16000
    reasons=[];latencies=[];queue_ages=[]
    def finite(value):
        return type(value) in (int,float) and isfinite(value) and value>=0
    def unique(items,field):
        values=[item.get(field) for item in items]
        return all(isinstance(v,str) and v for v in values) and len(set(values))==len(values)
    if len(recordings)!=int(scenario["recorders"]):reasons.append("RECORDER_COUNT")
    for field in ("recorderId","sessionId","meetingId"):
        if not unique(recordings,field):reasons.append("DISTINCT_"+field.upper())
    if len(background)!=int(scenario["totalUsers"])-int(scenario["recorders"]) or not unique(background,"userId"):reasons.append("BACKGROUND_USERS")
    if {v.get("recorderId") for v in recordings}&{v.get("userId") for v in background}:reasons.append("SHARED_USER")
    for index,r in enumerate(recordings):
        valid=(all(type(r.get(k)) is int and r[k]==expected_chunks for k in ("sentChunks","ackedChunks","verifiedChunks"))
               and type(r.get("sampleCount")) is int and r["sampleCount"]==expected_samples and r.get("hashMatched") is True
               and finite(r.get("observedSeconds")) and r["observedSeconds"]>=duration and type(r.get("errors")) is int and r["errors"]==0
               and type(r.get("transientErrors")) is int and r["transientErrors"]>=0)
        values=r.get("latenciesMs",[]);ages=r.get("queueAgesMs",[])
        if not isinstance(values,list) or len(values)!=expected_chunks or not all(finite(v) for v in values):valid=False
        else:latencies.extend(values)
        if not isinstance(ages,list) or len(ages)!=expected_chunks or not all(finite(v) for v in ages):valid=False
        else:queue_ages.extend(ages)
        if not valid:reasons.append("INCOMPLETE_RECORDING_"+str(index))
    for index,b in enumerate(background):
        if not(type(b.get("requests")) is int and b["requests"]>=max(2,ceil(duration/30)) and type(b.get("errors")) is int and b["errors"]==0 and finite(b.get("observedSeconds")) and b["observedSeconds"]>=duration):reasons.append("INACTIVE_BACKGROUND_"+str(index))
    percentile=lambda values:quantiles(values,n=20,method="inclusive")[18] if len(values)>1 else values[0] if values else None
    return dict(passed=not reasons,scope="RECORDING_PROTOCOL_ONLY",reasons=reasons,recorders=len(recordings),totalUsers=len(recordings)+len(background),
                requestedDurationSeconds=duration,observedDurationSeconds=min((r.get("observedSeconds",0) for r in recordings),default=0),
                sentChunks=sum(r.get("sentChunks",0) for r in recordings),verifiedChunks=sum(r.get("verifiedChunks",0) for r in recordings),
                missingAckedChunks=sum(max(0,r.get("ackedChunks",0)-r.get("verifiedChunks",0)) for r in recordings),
                errorCounts=sum(r.get("errors",0) for r in recordings)+sum(b.get("errors",0) for b in background),
                transientErrorCounts=sum(r.get("transientErrors",0) for r in recordings),ackLatencyP95Ms=percentile(latencies),queueAgeP95Ms=percentile(queue_ages),
                realModelQualified=False,temporarySubtitleP95Ms=None,stableSubtitleP95Ms=None)

def build_report(samples, *, expected_recorders: int, expected_users: int):
    samples = list(samples)
    missing = sum(int(s.get("missingAckedChunks", 0)) for s in samples)
    duplicate = sum(int(s.get("duplicateChunks", 0)) for s in samples)
    errors = sum(int(s.get("errors", 0)) for s in samples)
    latencies = [float(s["latencyMs"]) for s in samples if "latencyMs" in s]
    queue_ages = [float(s.get("queueAgeMs", 0)) for s in samples]
    cpu_peaks = [float(s.get("cpuPercent", 0)) for s in samples]
    memory_peaks = [float(s.get("memoryBytes", 0)) for s in samples]
    recorders = len({s.get("recorderId") for s in samples if s.get("recorderId")})
    observed_users = {s["recorderId"] for s in samples if s.get("recorderId")}
    for sample in samples:
        observed_users.update(sample.get("backgroundUserIds", []))
    required = {"recorderId", "missingAckedChunks", "duplicateChunks", "errors", "latencyMs"}
    complete = all(required <= s.keys() and all(type(s[k]) is int and s[k] >= 0 for k in ("missingAckedChunks", "duplicateChunks", "errors")) for s in samples)
    passed = bool(samples) and complete and recorders == expected_recorders and len(observed_users) == expected_users and missing == 0 and duplicate == 0 and errors == 0 and len(latencies) == len(samples) and all(isfinite(v) and v >= 0 for v in latencies)
    p95 = quantiles(latencies, n=20, method="inclusive")[18] if len(latencies) >= 2 else (latencies[0] if latencies else None)
    return {"passed": passed, "recorders": recorders, "totalUsers": len(observed_users), "expectedUsers": expected_users, "missingAckedChunks": missing,
            "duplicateChunks": duplicate, "latencyP95Ms": p95, "queueAgeP95Ms": max(queue_ages, default=0),
            "errorCounts": errors, "resourcePeaks": {"cpuPercent": max(cpu_peaks, default=0), "memoryBytes": max(memory_peaks, default=0)}, "samples": len(samples)}
