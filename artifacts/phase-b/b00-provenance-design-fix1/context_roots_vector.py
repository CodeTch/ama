"""Reduced synthetic R1 model only; no DB, archive retrieval or normative wire codec.

The literal expectations below are independent domain fixtures. Short IDs and
synthetic SHA values deliberately do not qualify a runtime/archive-bound graph.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHECKS = []


def check(label, condition):
    assert condition, label
    CHECKS.append(label)


def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def ordered(rows, pk):
    keys = [tuple(row[k] for k in pk) for row in rows]
    assert len(keys) == len(set(keys)), "duplicate full PK: " + repr(pk)
    return sorted(rows, key=lambda row: tuple(row[k].encode("utf-8") for k in pk))


def fixture():
    contexts, selections, profiles, snapshots, saved = [], [], [], {}, {}
    for n in range(1, 9):
        cid = "C" + str(n)
        gid = None if n in (2, 4) else "G" + str(n)
        contexts.append(dict(id=cid, meeting_id="M", origin_kind="MANUAL_SELECTION" if gid is None else "GENERATION_INPUT",
                             generation_input_id=gid, transcript_confirmation_id="T" + str(n), material_snapshot_id="S" + str(n),
                             catalog_version="CAT" + str(n), catalog_sha256=sha("catalog" + str(n)),
                             created_at="2026-10-22T00:00:00.000000Z"))
        snapshots["S" + str(n)] = ["RA", "RB"]
        for rev in ("RA", "RB"):
            doc = "D" + str(n) + rev
            selections.append(dict(context_id=cid, material_revision_id=rev, parsed_document_id=doc))
            profiles.append(dict(document_id=doc, format="PDF", parser_config_version="P" + str(n), parser_config_sha256=sha("parser" + str(n))))
        if gid:
            saved[gid] = dict(fingerprint=sha("saved" + gid), dependencies=[])
    # Current context C7 is raw draft/frozen final input, never an inherited root.
    elements = [dict(id="E1", source_context_id="C1"), dict(id="E0", source_context_id="C3"), dict(id="EM", source_context_id="C4")]
    evidence = [dict(id="EV1", element_id="E1", context_id="C2", start=0, end=5),
                dict(id="EV2", element_id="E1", context_id="C2", start=10, end=20),
                dict(id="EVM", element_id="EM", context_id="C2", start=0, end=5),
                dict(id="EVB", element_id=None, context_id="C6", start=0, end=5)]
    return dict(contexts=contexts, selections=selections, profiles=profiles, snapshots=snapshots, saved=saved,
                elements=elements, evidence=evidence,
                directories=[dict(kind="BACKGROUND", key=["N1", "L"], context_id="C5")],
                exclusions=[dict(kind="EXCLUSION", key=["XR", "L", "A"], context_id="C7")])


def scope(f, domain, selected="E1"):
    elements = [e for e in f["elements"] if domain != "REVIEW_SOURCES" or e["id"] == selected]
    ids = {e["id"] for e in elements}
    evidence = [e for e in f["evidence"] if e["element_id"] in ids or (e["element_id"] is None and (domain == "FINAL" or (domain == "REVIEW_SOURCES" and selected == "BODY")))]
    if domain == "REVIEW_SOURCES":
        evidence = [e for e in evidence if e["start"] < 10 and e["end"] > 0]  # exact [0,10) intersection
    usages = [dict(kind="EVIDENCE", key=[e["id"]], context_id=e["context_id"]) for e in evidence]
    if domain != "REVIEW_SOURCES":
        usages += copy.deepcopy(f["directories"])
    if domain == "FINAL":
        usages += copy.deepcopy(f["exclusions"])
    return elements, evidence, usages


def projection(f, domain, selected="E1", old=False):
    elements, evidence, usages = scope(f, domain, selected)
    # Preserve the historical zero-evidence recipe ONLY as a RED counterexample.
    bases = {e["source_context_id"] for e in elements if not old or not any(ev["element_id"] == e["id"] for ev in evidence)}
    roots = bases | {u["context_id"] for u in usages}
    contexts = ordered([copy.deepcopy(c) for c in f["contexts"] if c["id"] in roots], ["id"])
    selections = ordered([copy.deepcopy(s) for s in f["selections"] if s["context_id"] in roots], ["context_id", "material_revision_id"])
    docs = {s["parsed_document_id"] for s in selections}
    profiles = ordered([copy.deepcopy(p) for p in f["profiles"] if p["document_id"] in docs], ["document_id"])
    return dict(domain=domain, elementRows=ordered(copy.deepcopy(elements), ["id"]), evidenceRows=ordered(copy.deepcopy(evidence), ["id"]),
                usages=sorted(usages, key=lambda u: (u["kind"].encode(), tuple(k.encode() for k in u["key"]))),
                contextRows=contexts, contextMaterialRows=selections, parserProfileRows=profiles)


def validate(f, p, domain, selected="E1", draft=False):
    expected = projection(f, domain, selected)
    # Membership exactness is checked before normalization; duplicates must fail.
    for field, pk in (("contextRows", ["id"]), ("contextMaterialRows", ["context_id", "material_revision_id"]), ("parserProfileRows", ["document_id"])):
        actual = ordered(p[field], pk)
        assert actual == expected[field], "exact " + field
    assert p["elementRows"] == expected["elementRows"], "exact included elements"
    assert p["evidenceRows"] == expected["evidenceRows"], "exact intersecting/domain evidence"
    assert p["usages"] == expected["usages"], "exact usage occurrences; no fake basis usage"
    for c in p["contextRows"]:
        selected_rows = [s for s in p["contextMaterialRows"] if s["context_id"] == c["id"]]
        assert sorted(s["material_revision_id"] for s in selected_rows) == sorted(f["snapshots"][c["material_snapshot_id"]]), "complete snapshot selection"
        gid = c["generation_input_id"]
        assert (c["origin_kind"] == "MANUAL_SELECTION" and gid is None) or (c["origin_kind"] == "GENERATION_INPUT" and gid is not None), "origin union"
        def visit(input_id, visiting):
            assert input_id not in visiting, "generation dependency cycle"
            assert not draft or input_id != "G7", "current input is not inherited ancestor"
            assert input_id in f["saved"] and f["saved"][input_id]["fingerprint"], "persisted independently verified input required"
            for dep in f["saved"][input_id]["dependencies"]:
                visit(dep, visiting | {input_id})
        if gid:
            visit(gid, set())
    return True


def rejected(label, fn):
    try:
        fn()
    except AssertionError:
        check(label, True)
    else:
        raise AssertionError("must reject: " + label)


def run():
    f = fixture()
    expected = {"PINNED": ["C1", "C2", "C3", "C4", "C5"], "REVIEW_SOURCES": ["C1", "C2"],
                "FINAL": ["C1", "C2", "C3", "C4", "C5", "C6", "C7"]}
    result = {}
    for domain, roots in expected.items():
        p = projection(f, domain)
        check(domain + " exact independent literal roots", [c["id"] for c in p["contextRows"]] == roots)
        check(domain + " complete graph accepted", validate(f, p, domain, draft=domain == "PINNED"))
        check(domain + " two complete selections per root", len(p["contextMaterialRows"]) == 2 * len(roots))
        check(domain + " two complete profiles per root", len(p["parserProfileRows"]) == 2 * len(roots))
        check(domain + " basis creates no usage", len(p["usages"]) == {"PINNED": 4, "REVIEW_SOURCES": 1, "FINAL": 6}[domain])
        check(domain + " exact evidence IDs", [e["id"] for e in p["evidenceRows"]] == {"PINNED": ["EV1", "EV2", "EVM"], "REVIEW_SOURCES": ["EV1"], "FINAL": ["EV1", "EV2", "EVB", "EVM"]}[domain])
        # All C1 evidence uses C2; retaining C1 must not depend on some usage.
        check(domain + " C1 has no usage", not any(u["context_id"] == "C1" for u in p["usages"]))
        for field in ("contextRows", "contextMaterialRows", "parserProfileRows"):
            missing = copy.deepcopy(p); missing[field].pop(0)
            rejected(domain + " missing " + field, lambda: validate(f, missing, domain))
            extra = copy.deepcopy(p)
            key = {"contextRows": "contexts", "contextMaterialRows": "selections", "parserProfileRows": "profiles"}[field]
            extra[field].append(copy.deepcopy(f[key][-1]))  # unrelated C8 / D8RB
            rejected(domain + " extra " + field, lambda: validate(f, extra, domain))
            duplicate = copy.deepcopy(p); duplicate[field].append(copy.deepcopy(p[field][0]))
            rejected(domain + " duplicate full PK " + field, lambda: validate(f, duplicate, domain))
        fake = copy.deepcopy(p); fake["usages"].append(dict(kind="EVIDENCE", key=["FAKE"], context_id="C1"))
        rejected(domain + " fake evidence for basis", lambda: validate(f, fake, domain))
        # Content of a reached basis is independently bound even with no usage.
        for field, key in (("contexts", "catalog_sha256"), ("profiles", "parser_config_sha256")):
            changed = copy.deepcopy(f); changed[field][0][key] = sha("changed basis")
            check(domain + " basis " + key + " affects reduced digest", canonical(p) != canonical(projection(changed, domain)))
        changed = copy.deepcopy(f)
        changed["selections"][0]["parsed_document_id"] = "D1RA-reparse"
        changed_profile = copy.deepcopy(changed["profiles"][0]); changed_profile["document_id"] = "D1RA-reparse"
        changed["profiles"].append(changed_profile)
        check(domain + " basis exact parser selection affects reduced digest", canonical(p) != canonical(projection(changed, domain)))
        changed = copy.deepcopy(f); changed["elements"][0]["source_context_id"] = "C4"
        check(domain + " changed basis ID affects reduced digest", canonical(p) != canonical(projection(changed, domain)))
        permuted = copy.deepcopy(f)
        for field in ("contexts", "selections", "profiles", "elements", "evidence", "directories", "exclusions"):
            permuted[field].reverse()
        check(domain + " permutation canonical bytes", canonical(p) == canonical(projection(permuted, domain)))
        result[domain] = dict(expectedRoots=roots, reducedSyntheticSha256=hashlib.sha256(canonical(p)).hexdigest(), projection=p)
    for target, roots in (("E0", ["C3"]), ("EM", ["C2", "C4"]), ("BODY", ["C6"])):
        p = projection(f, "REVIEW_SOURCES", target)
        check(target + " selected basis exact roots", [c["id"] for c in p["contextRows"]] == roots)
        check(target + " selected review valid", validate(f, p, "REVIEW_SOURCES", target))
        check(target + " no support inferred from basis", bool(p["usages"]) == (target != "E0"))
        result["REVIEW_" + target] = p
    bad = copy.deepcopy(f); bad["elements"][0]["source_context_id"] = "C7"
    rejected("draft current input rejected even as nonusage basis", lambda: validate(bad, projection(bad, "PINNED"), "PINNED", draft=True))
    bad = copy.deepcopy(f); bad["saved"]["G1"]["dependencies"] = ["G7"]
    rejected("draft descendant rejected through basis dependency", lambda: validate(bad, projection(bad, "PINNED"), "PINNED", draft=True))
    bad = copy.deepcopy(f); bad["saved"]["G1"]["dependencies"] = ["G3"]; bad["saved"]["G3"]["dependencies"] = ["G1"]
    rejected("persisted dependency cycle rejected", lambda: validate(bad, projection(bad, "FINAL"), "FINAL"))
    bad = copy.deepcopy(f); del bad["saved"]["G1"]
    rejected("nonusage generated basis still needs saved verification", lambda: validate(bad, projection(bad, "FINAL"), "FINAL"))
    current_selections = [copy.deepcopy(s) for s in f["selections"] if s["context_id"] == "C7"]
    current_docs = {s["parsed_document_id"] for s in current_selections}
    draft = dict(sourceContext=dict(row=copy.deepcopy(f["contexts"][6]), materials=current_selections,
                                   parserProfiles=[copy.deepcopy(p) for p in f["profiles"] if p["document_id"] in current_docs]),
                 elementsSourceGraph=projection(f, "PINNED"),
                 exclusion=dict(appliedContextId="C7", entries=[dict(locatorId="L", category="A")]))
    check("draft current exclusion context stays outside inherited graph", "C7" not in expected["PINNED"])
    check("draft current fingerprint and own TaskSpec absent", "fingerprint" not in json.dumps(draft) and "TaskSpec" not in json.dumps(draft))
    # This model never uses created_at/UUID ordering to decide ancestry.
    permuted_time = copy.deepcopy(f); permuted_time["contexts"][0]["created_at"] = "2099-01-01T00:00:00.000000Z"
    check("graph ancestry independent of timestamp order", validate(permuted_time, projection(permuted_time, "PINNED"), "PINNED", draft=True))
    return dict(scope=__doc__, assertions=len(CHECKS), checks=CHECKS, domainVectors=result, acyclicDraft=draft,
                limitations="Reduced JSON digest sensitivity only; no full normative profile codec, valid raw archives, SQL, runtime, ACL, parser or adoption qualification.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["old-recipe-red", "verify"])
    args = parser.parse_args()
    if args.mode == "old-recipe-red":
        f = fixture(); f["elements"] = f["elements"][:1]; f["directories"] = []
        actual = [c["id"] for c in projection(f, "PINNED", old=True)["contextRows"]]
        assert actual == ["C1", "C2"], "R1 static recipe assertion: expected C1+C2, old zero-evidence recipe produced " + repr(actual)
    else:
        result = run()
        (HERE / "context-roots-vector.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(dict(assertions=result["assertions"], domains=list(result["domainVectors"]), qualification="reduced synthetic offline model only")))
