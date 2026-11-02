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

def qualify_subtitles(scenario, recordings):
    """Every actual source chunk needs an ordered replay event and client arrival time."""
    from math import ceil
    rows=list(recordings);count=ceil(scenario['durationSeconds']*1000/scenario['chunkMs']);reasons=[];temporary=[];stable=[]
    if len(rows)!=scenario['recorders']:reasons.append('SUBTITLE_RECORDER_COUNT')
    for index,row in enumerate(rows):
        events=row.get('subtitleEvents');seen=set();parts=set();valid=row.get('subtitleState')=='DONE' and row.get('subtitleErrors')==0 and isinstance(events,list)
        if isinstance(events,list):
            for position,event in enumerate(events,1):
                seq=event.get('sourceFinalSeq');latency=event.get('latencyMs');status=event.get('status')
                timing=type(seq) is int and 1<=seq<=count and event.get('endMs')==min(seq*scenario['chunkMs'],scenario['durationSeconds']*1000)
                expected='stable' if type(seq) is int and seq%3==0 else 'temporary'
                tail=position==count+1 and seq==count and count%3!=0 and status=='stable'
                if not(timing and event.get('eventId')==str(position) and event.get('sessionId')==row.get('sessionId') and isinstance(event.get('partId'),str) and event['partId'] and (position==seq and status==expected or tail) and type(latency) in (int,float) and isfinite(latency) and latency>=0):valid=False;continue
                parts.add(event['partId']);seen.add(seq)
                (stable if status=='stable' else temporary).append(latency)
            if seen!=set(range(1,count+1)) or len(parts)!=1 or len(events)!=count+(count%3!=0):valid=False
        if not valid:reasons.append('INCOMPLETE_SUBTITLES_'+str(index))
    p95=lambda values:quantiles(values,n=20,method='inclusive')[18] if len(values)>1 else values[0] if values else None
    temp=p95(temporary);final=p95(stable)
    if temp is None or temp>3000:reasons.append('TEMPORARY_SUBTITLE_LATENCY')
    if final is None or final>5000:reasons.append('STABLE_SUBTITLE_LATENCY')
    return dict(passed=not reasons,reasons=reasons,scope='ACTUAL_CLIENT_ARRIVAL_FROM_SOURCE_END_CONTROLLED_HARNESS',temporarySubtitleP95Ms=temp,stableSubtitleP95Ms=final,temporaryEvents=len(temporary),stableEvents=len(stable),realModelQualified=False)

def qualify_long_file(scenario,recordings):
    required=scenario.get('longFileRecorderCount',0);selected=[r for r in recordings if r.get('longFileSelected') is True];reasons=[]
    if len(selected)!=required:reasons.append('LONG_FILE_RECORDER_COUNT')
    for index,r in enumerate(selected):
        value=r.get('longFileTranscription',{});valid=(value.get('passed') is True and value.get('state')=='SUCCEEDED' and value.get('sourceSha256')==r.get('wavSha256') and isinstance(value.get('sourceSha256'),str) and len(value['sourceSha256'])==64 and value.get('sourceBytes')==r.get('wavBytes')==44+2*r.get('sampleCount',0) and value.get('sourceSamples')==r.get('sampleCount') and value.get('lastEndMs')==scenario['durationSeconds']*1000 and type(value.get('segments')) is int and value['segments']>0 and all(isinstance(value.get(k),str) and value[k] for k in ('taskId','inputId','sourceFileId','rawRevisionId')))
        if not valid:reasons.append('INCOMPLETE_LONG_FILE_'+str(index))
    return dict(passed=not reasons,reasons=reasons,scope='POST_CAPTURE_COMPLETE_ARCHIVE_COPY_TO_INDEPENDENT_RAW',requiredRecorders=required,observedRecorders=len(selected),durationSeconds=scenario['durationSeconds'])
