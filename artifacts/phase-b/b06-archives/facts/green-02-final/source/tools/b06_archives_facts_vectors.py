"""Independent controlled processing archive raw JSON; does not import application code."""
import hashlib,json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/"artifacts/phase-b/b06-archives/facts/controlled-raw"
E.mkdir(parents=True,exist_ok=True)
def canonical(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode("utf-8")
def archive(kind,version,schema,definition,dependencies):
 value={"archiveSchema":"ama.config-archive.v1","artifactType":kind,"definition":definition,"definitionSchema":schema,"dependencies":dependencies,"version":version}
 raw=canonical(value);sha=hashlib.sha256(raw).hexdigest();(E/(kind+".json")).write_bytes(raw)
 return {"artifactType":kind,"version":version,"sha256":sha}
def dependency(slot,ref):return {"slot":slot,"ref":ref}
codec=archive("CANONICAL_CODEC","ama.canonical-value.v1","ama.profile-contract.v1",{"contract":"ama.canonical-value.v1"},[])
parser=archive("PARSER_CONFIG","ama.strict-utf8-lines.v1","ama.txt-parser-config.v1",{"contract":"ama.strict-utf8-lines.v1"},[])
confirmation=archive("CONFIRMATION_HASH","ama.transcript-confirmation.v1","ama.profile-contract.v1",{"contract":"ama.transcript-confirmation.v1"},[dependency("CANONICAL_CODEC",codec)])
processing=archive("PROCESSING_CONFIG","b06.controlled.import-txt.1","ama.import-txt-config.v1",{"contract":"ama.import-txt.v1","manifestSchemaVersion":1,"canonicalBudget":{"maxEncodedBytes":16777216,"maxDepth":32,"maxNodes":1000000,"maxStringBytes":2097152,"maxContainerEntries":250000},"parseBudget":{"maxSourceBytes":65536,"maxUtf16CodeUnits":65536,"maxSegments":2048}},[dependency("CANONICAL_CODEC",codec),dependency("CONFIRMATION_HASH",confirmation),dependency("PARSER_CONFIG",parser)])
(E/"refs.json").write_bytes(canonical([codec,parser,confirmation,processing]))
for ref in [codec,parser,confirmation,processing]:print(ref["artifactType"]+" "+ref["sha256"])
