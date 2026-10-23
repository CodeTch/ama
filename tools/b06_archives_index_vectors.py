"""Independent public B06.2 raw JSON/index fixtures; no application imports."""
import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend/adapters/src/test/resources/ama/config/file/b06"


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def typed(value):
    """Independent v1 codec used only to cross-check the public controlled processing fixture."""
    if value is None:
        return b"\x00"
    if isinstance(value, str):
        raw = value.encode("utf-8")
        return b"\x04" + struct.pack(">Q", len(raw)) + raw
    if isinstance(value, int):
        return b"\x03" + struct.pack(">Q", value)
    if isinstance(value, list):
        return b"\x05" + struct.pack(">Q", len(value)) + b"".join(typed(v) for v in value)
    if isinstance(value, dict):
        keys = sorted(value, key=lambda k: k.encode("utf-8"))
        return b"\x06" + struct.pack(">Q", len(keys)) + b"".join(typed(k) + typed(value[k]) for k in keys)
    raise ValueError("unsupported literal")


def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / ".gitattributes").write_text("* -text\n", encoding="ascii")
    nodes = {}

    def archive(name, kind, version, schema, definition, dependencies):
        raw = encoded(dict(archiveSchema="ama.config-archive.v1", artifactType=kind, version=version,
                           definitionSchema=schema, definition=definition, dependencies=dependencies))
        nodes[name] = (dict(artifactType=kind, version=version, sha256=digest(raw)), raw)
        (OUT / (name + ".json")).write_bytes(raw)
        return nodes[name][0]

    codec = archive("codec", "CANONICAL_CODEC", "ama.canonical-value.v1", "ama.profile-contract.v1", {"contract": "ama.canonical-value.v1"}, [])
    parser = archive("parser", "PARSER_CONFIG", "ama.strict-utf8-lines.v1", "ama.txt-parser-config.v1", {"contract": "ama.strict-utf8-lines.v1"}, [])
    confirmation = archive("confirmation", "CONFIRMATION_HASH", "ama.transcript-confirmation.v1", "ama.profile-contract.v1", {"contract": "ama.transcript-confirmation.v1"}, [{"slot": "CANONICAL_CODEC", "ref": codec}])
    definition = dict(contract="ama.import-txt.v1", manifestSchemaVersion=1,
                      parseBudget=dict(maxSourceBytes=65536, maxUtf16CodeUnits=65536, maxSegments=2048),
                      canonicalBudget=dict(maxEncodedBytes=16777216, maxDepth=32, maxNodes=1000000, maxStringBytes=2097152, maxContainerEntries=250000))
    deps = [{"slot": slot, "ref": ref} for slot, ref in [("CANONICAL_CODEC", codec), ("CONFIRMATION_HASH", confirmation), ("PARSER_CONFIG", parser)]]
    processing1 = archive("processing1", "PROCESSING_CONFIG", "b06.controlled.import-txt.1", "ama.import-txt-config.v1", definition, deps)
    archive("processing2", "PROCESSING_CONFIG", "b06.controlled.import-txt.2", "ama.import-txt-config.v1", definition, deps)
    previous = None
    for generation, names in [(1, ["codec", "confirmation", "parser", "processing1"]), (2, list(nodes))]:
        entries = [dict(**nodes[name][0], sizeBytes=len(nodes[name][1])) for name in names]
        entries.sort(key=lambda e: (e["artifactType"].encode(), e["version"].encode()))
        raw = encoded(dict(indexSchema="ama.config-index.v1", generation=generation, previousIndexSha256=previous, entries=entries))
        (OUT / f"index{generation}.json").write_bytes(raw)
        (OUT / f"index{generation}.sha256").write_text(digest(raw), encoding="ascii")
        previous = digest(raw)
    meeting = "11111111-1111-4111-8111-111111111111"
    batch = "33333333-3333-4333-8333-333333333333"
    manifest = dict(schemaVersion=1, profile="ama.transcription-input.v1", meetingId=meeting, inputId="22222222-2222-4222-8222-222222222222", kind="IMPORT",
                    batch=dict(id=batch, kind="TRANSCRIPT", files=[dict(ordinal=1, fileId="44444444-4444-4444-8444-444444444444", sha256=hashlib.sha256(b"A").hexdigest(), sizeBytes=1)]),
                    sourceRevisionId=None, materials=dict(mode="EMPTY", items=[]), configs=dict(processing=processing1, parser=parser, model=None, prompt=None,
                    profiles=[dict(kind="CANONICAL_CODEC", ref=codec), dict(kind="CONFIRMATION_HASH", ref=confirmation)]),
                    base=dict(meetingRevision=9, subjectBusinessVersion=9, cancelEpoch=2),
                    steps=[dict(ordinal=1, kind="IMPORT_TXT", parentInput=None, priorOutput=None, source=dict(kind="INPUT_BATCH", id=batch), config=processing1, parser=parser, model=None, prompt=None)])
    (OUT / "manifest1.json").write_bytes(encoded(manifest))
    envelope = dict(profile="ama.transcription-input.v1", kind="TRANSCRIPTION_INPUT", data=manifest)
    (OUT / "manifest1.business.sha256").write_text(digest(typed(envelope)), encoding="ascii")
    print("generated", len(nodes), "raw nodes and two immutable index generations")


if __name__ == "__main__":
    generate()
