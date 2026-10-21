"""Independent Python encoder and literal domain fixtures, no production Java/codecs or emitted values read."""
import hashlib,json,struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/"backend/core/src/test/resources/ama/minutes/vectors"
OUT.mkdir(parents=True,exist_ok=True)
def enc(v):
 if v is None:return b"\x00"
 if v is False:return b"\x01"
 if v is True:return b"\x02"
 if isinstance(v,int):return b"\x03"+struct.pack(">Q",v)
 if isinstance(v,str):
  b=v.encode("utf-8",errors="strict");return b"\x04"+struct.pack(">Q",len(b))+b
 if isinstance(v,list):return b"\x05"+struct.pack(">Q",len(v))+b"".join(enc(x) for x in v)
 if isinstance(v,dict):
  keys=sorted(v,key=lambda k:k.encode("utf-8"));return b"\x06"+struct.pack(">Q",len(keys))+b"".join(enc(k)+enc(v[k]) for k in keys)
 raise TypeError(type(v))
def uid(n):return f"00000000-0000-0000-0000-{n:012d}"
H="0"*64
segment={"segmentId":uid(6),"ordinal":1,"body":"meeting 7","sourceFileId":uid(3),"sourceParagraph":4,"startMs":None,"endMs":None,"speakerId":None,"marks":[]}
confirmation={"meetingId":uid(1),"revisionId":uid(5),"batchId":uid(2),"segments":[segment]}
def save(name,profile,kind,data):
 value={"profile":profile,"kind":kind,"data":data};raw=enc(value)
 (OUT/(name+".hex")).write_text(raw.hex()+"\n")
 (OUT/(name+".sha256")).write_text(hashlib.sha256(raw).hexdigest()+"\n")
 (OUT/(name+".json")).write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n")
 print(name,len(raw),hashlib.sha256(raw).hexdigest())
save("confirmation","ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",confirmation)
marked={**confirmation,"segments":[{**segment,"marks":["KEY","CONCLUSION","DISPUTE","GENERATION_FOCUS"]}]}
save("confirmation-marks","ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",marked)
text_hash=hashlib.sha256("A😀B".encode()).hexdigest()
anchor={"schemaVersion":"ama.anchor.v1","unit":"UNICODE_SCALAR","start":0,"end":3,"targetTextSha256":text_hash}
target={"meetingId":uid(1),"reviewKind":"SOURCE_SUPPORT","target":{"kind":"ELEMENT","id":uid(12),"setId":uid(11),"setRevision":1,"minutesRevision":None},"content":{"title":None,"body":"A😀B","category":"CONCLUSION","action":None},"anchor":anchor}
save("review-target","ama.result-review.v2","REVIEW_TARGET",target)
target_hash=hashlib.sha256(enc({"profile":"ama.result-review.v2","kind":"REVIEW_TARGET","data":target})).hexdigest()
evidence_anchor={**anchor,"end":2}
evidence={"id":uid(15),"locator_id":uid(14),"element_id":uid(12),"minutes_revision_id":None,"support_state":"PARTIAL","target_anchor":json.dumps(evidence_anchor,ensure_ascii=True,separators=(",",":"))}
locator={"id":uid(14),"meeting_id":uid(1),"kind":"TRANSCRIPT","transcript_revision_id":uid(5),"segment_id":uid(6),"file_id":None,"material_revision_id":None,"page_no":None,"paragraph_no":None,"section_path":None,"start_ms":None,"end_ms":None}
usage={"usage":{"kind":"EVIDENCE","evidenceId":uid(15)},"locatorId":uid(14),"origin":{"kind":"TRANSCRIPT_CONFIRMATION","confirmationId":uid(7),"revisionId":uid(5),"contentHash":H},"role":"MEETING","sourceVersion":{"kind":"TRANSCRIPT","confirmationId":uid(7),"revisionId":uid(5),"batchId":uid(2),"sourceFileId":uid(3),"sourceFileSha256":H},"sourceContentHash":H}
save("review-sources","ama.result-review.v2","REVIEW_SOURCES",{"meetingId":uid(1),"targetHash":target_hash,"evidenceRows":[evidence],"locatorRows":[locator],"sourceUsages":[usage]})

# Complete FULL/TXT/EMPTY-material/EMPTY-input-set generation manifest, independent literal rowsets.
def rowset(tables):return hashlib.sha256(enc({"profile":"ama.rowset.v1","kind":"ROWSET","data":{"tables":[{"name":name,"rows":rows} for name,rows in sorted(tables.items())]}})).hexdigest()
ts="2026-10-22T00:00:00.000000Z"
template_row={"id":uid(9),"template_id":uid(90),"revision":1,"owner_id":None,"name":"default","themes":"themes","theme_count":1,"created_at":ts}
exclusion_row={"id":uid(10),"meeting_id":uid(1),"revision":1,"created_at":ts}
ch=hashlib.sha256(enc({"profile":"ama.transcript-confirmation.v1","kind":"TRANSCRIPT_CONFIRMATION","data":confirmation})).hexdigest()
versions={"CANONICAL_CODEC":"ama.canonical-value.v1","CONFIRMATION_HASH":"ama.transcript-confirmation.v1","GENERATION_HASH":"ama.generation-input.v2","ROWSET_HASH":"ama.rowset.v1","REVIEW_HASH":"ama.result-review.v2","FINAL_SOURCES":"ama.final-sources.v2"}
kinds=["CANONICAL_CODEC","CONFIRMATION_HASH","GENERATION_HASH","ROWSET_HASH","ANCHOR","REVIEW_HASH","FINAL_SOURCES","OUTPUT_SCHEMA","VALIDATION_BUDGET","CATALOG","RENDER","EXCLUSION","SUPPORT","TITLE","CHUNK_PLAN"]
def ref(version):return {"version":version,"sha256":hashlib.sha256(version.encode()).hexdigest()}
source_version={"kind":"TRANSCRIPT","confirmationId":uid(7),"revisionId":uid(5),"batchId":uid(2),"sourceFileId":uid(3),"sourceFileSha256":H}
generation={"meetingId":uid(1),"inputId":uid(8),
"confirmation":{"id":uid(7),"revisionId":uid(5),"hashProfile":"ama.transcript-confirmation.v1","contentHash":ch},
"segments":[{k:v for k,v in segment.items() if k!="body"}],
"batch":{"id":uid(2),"files":[{"ordinal":1,"fileId":uid(3),"sha256":H,"sizeBytes":9}]},
"materials":{"mode":"EMPTY","items":[]},
"template":{"mode":"DEFAULT","revisionId":uid(9),"revision":1,"contentHash":rowset({"template_revision":[template_row]})},
"exclusion":{"mode":"EMPTY","revisionId":uid(10),"revision":1,"contentHash":rowset({"exclusion_revision":[exclusion_row],"sensitive_exclusion":[],"source_locator":[]}),"entries":[]},
"elements":{"mode":"EMPTY","items":[]},
"sources":[{"sourceId":"s1","locator":{"kind":"TRANSCRIPT","transcriptRevisionId":uid(5),"segmentId":uid(6)},"role":"MEETING","sourceVersion":source_version,"sourceContentHash":ch}],
"configs":{"prompt":ref("prompt"),"model":ref("model"),"profiles":[{"kind":kind,**ref(versions.get(kind,kind+".v1"))} for kind in sorted(kinds)]},
"scope":{"kind":"FULL","target":None},"base":{"meetingRevision":1,"subjectBusinessVersion":2,"cancelEpoch":3}}
save("generation","ama.generation-input.v2","GENERATION_INPUT",generation)

final={"meetingId":uid(1),"confirmationId":uid(20),"minutesRevisionId":uid(13),"elementSetId":uid(11),"transcriptConfirmationId":uid(7),"materialSnapshotId":None,"templateRevisionId":uid(9),"exclusionRevisionId":uid(10),"inputFingerprint":H,
"locatorRows":[locator],"sourceUsages":[usage],"evidenceRows":[evidence],
"directoryRows":[{"name":name,"rows":[]} for name in sorted(["sensitive_detection","sensitive_detection_source","background_note","background_note_source","action_suggestion"])],
"exclusionRows":[{"name":"exclusion_revision","rows":[exclusion_row]},{"name":"sensitive_exclusion","rows":[]}],
"reviewRows":[],"reviewEvidenceRows":[],"finalReviewRows":[]}
save("final","ama.final-sources.v2","FINAL_SOURCES",final)

# Adopted raw processing fixture is parsed only by Python's independent standard library.
manifest_path=ROOT/"backend/adapters/src/test/resources/ama/config/manifest.json"
processing_manifest=json.loads(manifest_path.read_bytes())
save("processing","ama.transcription-input.v1","TRANSCRIPTION_INPUT",processing_manifest)
adapter_out=ROOT/"backend/adapters/src/test/resources/ama/config"
for suffix in [".hex",".sha256"]:(adapter_out/("processing-domain"+suffix)).write_bytes((OUT/("processing"+suffix)).read_bytes())

# Full raw archive chain fixture. Values are explicitly synthetic, never installed production settings.
raw_out=ROOT/"backend/adapters/src/test/resources/ama/config/full"
raw_out.mkdir(parents=True,exist_ok=True)
def J(value):return json.dumps(value,ensure_ascii=False,separators=(",",":"),sort_keys=True).encode()
raws={}
for name,kind in [("codec","CANONICAL_CODEC"),("parser","PARSER_CONFIG"),("confirmation","CONFIRMATION_HASH"),("processing","PROCESSING_CONFIG")]:
 raw=(adapter_out/(name+".json")).read_bytes();obj=json.loads(raw);raws[(kind,obj["version"])]=raw
def archive(kind,version,schema,definition,deps):
 obj={"archiveSchema":"ama.config-archive.v1","artifactType":kind,"version":version,"definitionSchema":schema,"definition":definition,"dependencies":[{"slot":slot,"ref":ref} for slot,ref in sorted(deps.items())]}
 raw=J(obj);raws[(kind,version)]=raw
 return {"artifactType":kind,"version":version,"sha256":hashlib.sha256(raw).hexdigest()}
codec_ref={"artifactType":"CANONICAL_CODEC","version":"ama.canonical-value.v1","sha256":"2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19"}
contracts={**versions,"ANCHOR":"ama.anchor.v1","OUTPUT_SCHEMA":"ama.minutes-output.v1","CATALOG":"ama.transcript-catalog.v1","EXCLUSION":"ama.exclusion-physical-union.v1","SUPPORT":"ama.creation-support.v1","TITLE":"ama.title-suggestion.v1","CHUNK_PLAN":"ama.no-chunk.v1"}
refs={}
for kind in kinds:
 if kind in contracts:refs[kind]=archive(kind,contracts[kind],"ama.profile-contract.v1",{"contract":contracts[kind]},{} if kind=="CANONICAL_CODEC" else {"CANONICAL_CODEC":codec_ref})
canonical={"maxEncodedBytes":1000000,"maxDepth":100,"maxNodes":100000,"maxStringBytes":100000,"maxContainerEntries":100000}
output_budget={"maxWireBytes":100000,"maxDepth":30,"maxTokens":10000,"maxNameUtf16":100,"maxStringUtf16":10000,"maxStringUtf8Bytes":10000,"maxStringScalars":10000,"maxAggregateScalars":100000,"maxElements":100,"maxDetections":100,"maxNotes":100,"maxRefs":100,"maxEvidence":100,"maxMemberships":100}
MID="11111111-1111-4111-8111-111111111111";BID="33333333-3333-4333-8333-333333333333";FID="44444444-4444-4444-8444-444444444444";GID="55555555-5555-4555-8555-555555555555";RID="66666666-6666-4666-8666-666666666666";SID="77777777-7777-4777-8777-777777777777";CID="88888888-8888-4888-8888-888888888888";TID="99999999-9999-4999-8999-999999999999";XID="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
refs["VALIDATION_BUDGET"]=archive("VALIDATION_BUDGET","test.minutes-budget.1","ama.validation-budget.v1",{"output":output_budget,"canonical":canonical},{"CANONICAL_CODEC":codec_ref})
refs["RENDER"]=archive("RENDER","test.renderer-binding.1","ama.render-binding.v1",{"contract":"ama.render-binding.v1","rendererVersion":"test.no-runtime-renderer.1","defaultTemplateRevisionId":TID},{"CANONICAL_CODEC":codec_ref})
prompt_ref=archive("PROMPT_CONFIG","test.generation.prompt.1","ama.generation-prompt.v1",{"instructionText":"SYNTHETIC TEST ONLY: no model is called."},refs)
model_ref=archive("MODEL_CONFIG","test.generation.model.1","ama.generation-model.v1",{"protocolVersion":"test.no-runtime.1","modelIdentity":"test.synthetic.1","credentialRef":None,"parameters":{"temperatureMicros":0,"maxOutputTokens":100,"seed":None}},refs)
index=[]
for i,((kind,version),raw) in enumerate(sorted(raws.items())):
 name=f"{i:02d}.json";(raw_out/name).write_bytes(raw);index.append(f"{kind}|{version}|{name}")
(raw_out/"index.txt").write_text("\n".join(index)+"\n")
full_segment={"segmentId":SID,"ordinal":1,"body":"A","sourceFileId":FID,"sourceParagraph":1,"startMs":None,"endMs":None,"speakerId":None,"marks":[]}
full_confirmation={"meetingId":MID,"revisionId":RID,"batchId":BID,"segments":[full_segment]}
save("full-confirmation","ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",full_confirmation)
full_ch=hashlib.sha256(enc({"profile":"ama.transcript-confirmation.v1","kind":"TRANSCRIPT_CONFIRMATION","data":full_confirmation})).hexdigest()
full_template={"id":TID,"template_id":uid(90),"revision":1,"owner_id":None,"name":"synthetic default","themes":"synthetic","theme_count":1,"created_at":ts}
full_exclusion={"id":XID,"meeting_id":MID,"revision":1,"created_at":ts}
full_generation={"meetingId":MID,"inputId":GID,"confirmation":{"id":CID,"revisionId":RID,"hashProfile":"ama.transcript-confirmation.v1","contentHash":full_ch},
"segments":[{k:v for k,v in full_segment.items() if k!="body"}],"batch":{"id":BID,"files":[{"ordinal":1,"fileId":FID,"sha256":processing_manifest["batch"]["files"][0]["sha256"],"sizeBytes":1}]},
"materials":{"mode":"EMPTY","items":[]},"template":{"mode":"DEFAULT","revisionId":TID,"revision":1,"contentHash":rowset({"template_revision":[full_template]})},
"exclusion":{"mode":"EMPTY","revisionId":XID,"revision":1,"contentHash":rowset({"exclusion_revision":[full_exclusion],"sensitive_exclusion":[],"source_locator":[]}),"entries":[]},
"elements":{"mode":"EMPTY","items":[]},"sources":[{"sourceId":"s1","locator":{"kind":"TRANSCRIPT","transcriptRevisionId":RID,"segmentId":SID},"role":"MEETING","sourceVersion":{"kind":"TRANSCRIPT","confirmationId":CID,"revisionId":RID,"batchId":BID,"sourceFileId":FID,"sourceFileSha256":processing_manifest["batch"]["files"][0]["sha256"]},"sourceContentHash":full_ch}],
"configs":{"prompt":{"version":prompt_ref["version"],"sha256":prompt_ref["sha256"]},"model":{"version":model_ref["version"],"sha256":model_ref["sha256"]},"profiles":[{"kind":kind,"version":ref["version"],"sha256":ref["sha256"]} for kind,ref in sorted(refs.items())]},
"scope":{"kind":"FULL","target":None},"base":{"meetingRevision":10,"subjectBusinessVersion":11,"cancelEpoch":2}}
save("full-generation","ama.generation-input.v2","GENERATION_INPUT",full_generation)
for name in ["full-confirmation","full-generation"]:
 for suffix in [".hex",".sha256"]:(adapter_out/(name+suffix)).write_bytes((OUT/(name+suffix)).read_bytes())

SETID="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";MINID="cccccccc-cccc-4ccc-8ccc-cccccccccccc";FINALID="dddddddd-dddd-4ddd-8ddd-dddddddddddd";LOCID="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";EVID="ffffffff-ffff-4fff-8fff-ffffffffffff";ELEM=uid(12)
gh=hashlib.sha256(enc({"profile":"ama.generation-input.v2","kind":"GENERATION_INPUT","data":full_generation})).hexdigest()
full_anchor={"schemaVersion":"ama.anchor.v1","unit":"UNICODE_SCALAR","start":0,"end":1,"targetTextSha256":processing_manifest["batch"]["files"][0]["sha256"]}
full_target={"meetingId":MID,"reviewKind":"SOURCE_SUPPORT","target":{"kind":"ELEMENT","id":ELEM,"setId":SETID,"setRevision":1,"minutesRevision":None},"content":{"title":None,"body":"A","category":"CONCLUSION","action":None},"anchor":full_anchor}
save("full-review-target","ama.result-review.v2","REVIEW_TARGET",full_target)
full_target_hash=hashlib.sha256(enc({"profile":"ama.result-review.v2","kind":"REVIEW_TARGET","data":full_target})).hexdigest()
full_locator={"id":LOCID,"meeting_id":MID,"kind":"TRANSCRIPT","transcript_revision_id":RID,"segment_id":SID,"file_id":None,"material_revision_id":None,"page_no":None,"paragraph_no":None,"section_path":None,"start_ms":None,"end_ms":None}
full_evidence={"id":EVID,"locator_id":LOCID,"element_id":ELEM,"minutes_revision_id":None,"support_state":"PARTIAL","target_anchor":J(full_anchor).decode()}
# Anchor wire order is explicitly schemaVersion/unit/start/end/hash, unlike sorted archive J.
full_evidence["target_anchor"]=json.dumps(full_anchor,separators=(",",":"))
full_usage={"usage":{"kind":"EVIDENCE","evidenceId":EVID},"locatorId":LOCID,"origin":{"kind":"TRANSCRIPT_CONFIRMATION","confirmationId":CID,"revisionId":RID,"contentHash":full_ch},"role":"MEETING","sourceVersion":{"kind":"TRANSCRIPT","confirmationId":CID,"revisionId":RID,"batchId":BID,"sourceFileId":FID,"sourceFileSha256":processing_manifest["batch"]["files"][0]["sha256"]},"sourceContentHash":full_ch}
save("full-review-sources","ama.result-review.v2","REVIEW_SOURCES",{"meetingId":MID,"targetHash":full_target_hash,"evidenceRows":[full_evidence],"locatorRows":[full_locator],"sourceUsages":[full_usage]})
full_final={"meetingId":MID,"confirmationId":FINALID,"minutesRevisionId":MINID,"elementSetId":SETID,"transcriptConfirmationId":CID,"materialSnapshotId":None,"templateRevisionId":TID,"exclusionRevisionId":XID,"inputFingerprint":gh,"locatorRows":[full_locator],"sourceUsages":[full_usage],"evidenceRows":[full_evidence],
"directoryRows":[{"name":name,"rows":[]} for name in sorted(["sensitive_detection","sensitive_detection_source","background_note","background_note_source","action_suggestion"])],
"exclusionRows":[{"name":"exclusion_revision","rows":[full_exclusion]},{"name":"sensitive_exclusion","rows":[]}],"reviewRows":[],"reviewEvidenceRows":[],"finalReviewRows":[]}
save("full-final","ama.final-sources.v2","FINAL_SOURCES",full_final)
for name in ["full-review-target","full-review-sources","full-final"]:
 for suffix in [".hex",".sha256"]:(adapter_out/(name+suffix)).write_bytes((OUT/(name+suffix)).read_bytes())

# Projection-only occurrence graph: D01 raw material catalog activation is deliberately unavailable.
physical={"id":uid(14),"meeting_id":uid(1),"kind":"PDF_PAGE","transcript_revision_id":None,"segment_id":None,"file_id":uid(31),"material_revision_id":None,"page_no":1,"paragraph_no":None,"section_path":None,"start_ms":None,"end_ms":None}
def material_usage(kind,member,input_id,document,parser,text_hash):
 key={"kind":kind,("detectionId" if kind=="DETECTION" else "noteId"):uid(member),"locatorId":uid(14)}
 return {"usage":key,"locatorId":uid(14),"origin":{"kind":"GENERATION_INPUT","inputId":uid(input_id),"inputFingerprint":H},"role":"BACKGROUND","sourceVersion":{"kind":"FILE","fileId":uid(31),"materialRevisionId":uid(30),"parsedDocumentId":uid(document),"parserVersion":parser,"parsedTextSha256":text_hash},"sourceContentHash":H}
occurrence={"locatorRows":[physical],"sourceUsages":[material_usage("DETECTION",19,8,34,"p1","1"*64),material_usage("DETECTION",20,18,36,"p2","2"*64),material_usage("BACKGROUND",16,8,34,"p1","1"*64),material_usage("BACKGROUND",17,18,36,"p2","2"*64)]}
raw=enc(occurrence)
(OUT/"occurrence-graph.hex").write_text(raw.hex()+"\n");(OUT/"occurrence-graph.sha256").write_text(hashlib.sha256(raw).hexdigest()+"\n");(OUT/"occurrence-graph.json").write_text(json.dumps(occurrence,indent=2)+"\n")
print("occurrence-graph",len(raw),hashlib.sha256(raw).hexdigest())

def config_j(value):
 if isinstance(value,str):
  return ('"'+''.join(('\\'+c) if c in ['"','\\'] else ('\\u%04x'%ord(c)) if ord(c)<32 else c for c in value)+'"').encode()
 if value is None:return b"null"
 if value is False:return b"false"
 if value is True:return b"true"
 if isinstance(value,int):return str(value).encode()
 if isinstance(value,list):return b"["+b",".join(config_j(v) for v in value)+b"]"
 return b"{"+b",".join(config_j(k)+b":"+config_j(value[k]) for k in sorted(value,key=lambda k:k.encode()))+b"}"
control_prompt=json.loads(raws[("PROMPT_CONFIG","test.generation.prompt.1")])
control_prompt["definition"]["instructionText"]="synthetic controls:"+chr(0)+chr(9)+chr(10)+chr(34)+chr(92)+"😀"
control_raw=config_j(control_prompt)
(adapter_out/"control-prompt.json").write_bytes(control_raw);(adapter_out/"control-prompt.sha256").write_text(hashlib.sha256(control_raw).hexdigest()+"\n")
print("control-prompt",len(control_raw),hashlib.sha256(control_raw).hexdigest())

# Full raw-supported PINNED set: intrinsic element evidence plus real body/action/directory origin G.
import copy
matrix_element={"id":ELEM,"set_id":SETID,"category":"CONCLUSION","ordinal":1,"body":"A","support_state":"PARTIAL"}
action_id=uid(41);body_evid=uid(40);action_evid=uid(42);note_id=uid(43);detection_ids=[uid(44),uid(45)]
action_element={"id":action_id,"set_id":SETID,"category":"ACTION","ordinal":1,"body":"A","support_state":"PARTIAL"}
action_suggestion={"element_id":action_id,"input_id":GID,"owner_mention":None,"due_on":None}
action_item={"element_id":action_id,"owner_enterprise_id":None,"owner_display":None,"due_on":None}
def evidence_row(eid,element=None,minutes=None):return {**full_evidence,"id":eid,"element_id":element,"minutes_revision_id":minutes}
action_evidence=evidence_row(action_evid,action_id);body_evidence=evidence_row(body_evid,None,MINID)
body_evidence["target_anchor"]=json.dumps({**full_anchor,"end":4,"targetTextSha256":hashlib.sha256(b"body").hexdigest()},separators=(",",":"))
note={"id":note_id,"set_id":SETID,"input_id":GID,"ordinal":1,"body":"context"}
note_source={"note_id":note_id,"locator_id":LOCID}
detections=[{"id":did,"set_id":SETID,"input_id":GID,"category":cat,"ordinal":1} for did,cat in zip(detection_ids,["INTERNAL_BUSINESS_DATA","POLITICAL_SENSITIVE"])]
detection_sources=[{"detection_id":did,"locator_id":LOCID} for did in detection_ids]
def generation_usage(key):
 return {**full_usage,"usage":key,"origin":{"kind":"GENERATION_INPUT","inputId":GID,"inputFingerprint":gh}}
matrix_usages=[generation_usage({"kind":"EVIDENCE","evidenceId":body_evid}),generation_usage({"kind":"EVIDENCE","evidenceId":action_evid}),full_usage]
# Usage rank EVIDENCE; within rank full evidence IDs ascending.
matrix_usages.sort(key=lambda u:u["usage"]["evidenceId"])
matrix_usages += [generation_usage({"kind":"DETECTION","detectionId":did,"locatorId":LOCID}) for did in detection_ids]
matrix_usages += [generation_usage({"kind":"BACKGROUND","noteId":note_id,"locatorId":LOCID})]
matrix_tables={"element_set":[{"id":SETID,"meeting_id":MID,"revision":1,"created_at":ts}],
"minutes_element":[matrix_element,action_element],"action_item":[action_item],"action_suggestion":[action_suggestion],
"sensitive_detection":detections,"sensitive_detection_source":detection_sources,"background_note":[note],"background_note_source":[note_source],
"evidence_link":[action_evidence,full_evidence],"source_locator":[full_locator]}
# Physical PK sorting is bytewise canonical UUID text, so action first then original element.
matrix_tables["minutes_element"].sort(key=lambda r:r["id"])
pinned=copy.deepcopy(full_generation);pinned["inputId"]=uid(50);pinned["base"]={"meetingRevision":12,"subjectBusinessVersion":13,"cancelEpoch":2}
# Body evidence is outside the pinned element set.
pinned_usages=[u for u in matrix_usages if not (u["usage"]["kind"]=="EVIDENCE" and u["usage"]["evidenceId"]==body_evid)]
pinned["elements"]={"mode":"PINNED","setId":SETID,"revision":1,"contentHash":rowset(matrix_tables),"sourceGraph":{"locatorRows":[full_locator],"sourceUsages":pinned_usages}}
save("full-generation-pinned","ama.generation-input.v2","GENERATION_INPUT",pinned)
matrix_final=copy.deepcopy(full_final);matrix_final["sourceUsages"]=matrix_usages;matrix_final["evidenceRows"]=[body_evidence,action_evidence,full_evidence]
matrix_final["directoryRows"]=[{"name":name,"rows":matrix_tables[name]} for name in sorted(["sensitive_detection","sensitive_detection_source","background_note","background_note_source","action_suggestion"])]
save("full-final-matrix","ama.final-sources.v2","FINAL_SOURCES",matrix_final)
# New exclusion revision, never changing historical G's immutable exclusion snapshot.
x2=uid(51);exclusion2={**full_exclusion,"id":x2,"revision":2}
excluded=copy.deepcopy(full_generation);excluded["inputId"]=uid(52);excluded["base"]={"meetingRevision":12,"subjectBusinessVersion":13,"cancelEpoch":2}
excluded_rows=[{"revision_id":x2,"locator_id":LOCID,"category":cat,"reason":"saved reason"} for cat in ["INTERNAL_BUSINESS_DATA","POLITICAL_SENSITIVE"]]
excluded["exclusion"]={"mode":"PINNED","revisionId":x2,"revision":2,"contentHash":rowset({"exclusion_revision":[exclusion2],"sensitive_exclusion":excluded_rows,"source_locator":[full_locator]}),
"entries":[{"locatorId":LOCID,"locator":{"kind":"TRANSCRIPT","transcriptRevisionId":RID,"segmentId":SID},"category":r["category"],"reasonHash":hashlib.sha256(b"saved reason").hexdigest()} for r in excluded_rows]}
save("full-generation-excluded","ama.generation-input.v2","GENERATION_INPUT",excluded)
# Selected review is frozen to its exact saved memberships.
review_id=uid(53)
review_row={"id":review_id,"meeting_id":MID,"review_no":1,"review_kind":"SOURCE_SUPPORT","element_id":ELEM,"minutes_revision_id":None,"target_anchor":full_evidence["target_anchor"],"target_hash":full_target_hash,
"source_set_hash":hashlib.sha256(enc({"profile":"ama.result-review.v2","kind":"REVIEW_SOURCES","data":{"meetingId":MID,"targetHash":full_target_hash,"evidenceRows":[full_evidence],"locatorRows":[full_locator],"sourceUsages":[full_usage]}})).hexdigest(),
"hash_profile":"ama.result-review.v2","outcome":"PARTIAL","reviewed_by":"human","reviewed_at":ts}
review_final=copy.deepcopy(full_final);review_final["reviewRows"]=[review_row];review_final["reviewEvidenceRows"]=[{"review_id":review_id,"evidence_id":EVID}];review_final["finalReviewRows"]=[{"confirmation_id":FINALID,"review_id":review_id}]
save("full-final-review","ama.final-sources.v2","FINAL_SOURCES",review_final)
for name in ["full-generation-pinned","full-final-matrix","full-generation-excluded","full-final-review"]:
 for suffix in [".hex",".sha256"]:(adapter_out/(name+suffix)).write_bytes((OUT/(name+suffix)).read_bytes())

# Saved manual child revision inherits the original processing profile, while exact saved text/marks are hashed.
manual={"meetingId":MID,"revisionId":uid(54),"batchId":BID,"segments":[{**full_segment,"segmentId":uid(55),"body":"manual"+chr(10),"marks":["KEY","CONCLUSION","DISPUTE","GENERATION_FOCUS"]}]}
save("full-manual-confirmation","ama.transcript-confirmation.v1","TRANSCRIPT_CONFIRMATION",manual)
for suffix in [".hex",".sha256"]:(adapter_out/("full-manual-confirmation"+suffix)).write_bytes((OUT/("full-manual-confirmation"+suffix)).read_bytes())
