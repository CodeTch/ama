package ama.model;

import ama.canonical.CanonicalValue;
import java.util.*;
import static ama.config.WireValues.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Strict additive wire shape; EMPTY requests preserve their original bytes and hashes. */
final class MaterialMinutesWire {
 static Map<String,CanonicalValue> payload(CanonicalValue value){if(!(value instanceof CanonicalValue.ObjectValue object))throw fail(INVALID_SCHEMA);boolean background=object.entries().stream().anyMatch(e->e.key().equals("backgroundMaterials"));return background?fields(value,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions","backgroundMaterials"):fields(value,"protocolVersion","modelIdentity","parameters","instructionText","inputManifest","transcript","template","pinnedElements","exclusions");}
 static void verify(Map<String,CanonicalValue> payload,Map<String,CanonicalValue> manifest){var materialValue=manifest.get("materials");if(!(materialValue instanceof CanonicalValue.ObjectValue object))throw fail(INVALID_SCHEMA);var mode=object.entries().stream().filter(e->e.key().equals("mode")).map(CanonicalValue.Entry::value).findFirst().orElseThrow(()->fail(INVALID_SCHEMA));if(text(mode).equals("EMPTY")){var empty=fields(materialValue,"mode","items");require(array(empty.get("items")).isEmpty()&&!payload.containsKey("backgroundMaterials"),INPUT_INVALID);return;}
  literal(mode,"PINNED");var materials=fields(materialValue,"mode","snapshotId","revision","inputFingerprint","contextId","items");uuid(text(materials.get("snapshotId")));uuid(text(materials.get("contextId")));require(materials.get("contextId").equals(manifest.get("inputId")),INPUT_INVALID);hash(text(materials.get("inputFingerprint")));require(number(materials.get("revision"))>0&&!array(materials.get("items")).isEmpty(),INPUT_INVALID);var revisions=new HashSet<String>();for(var value:array(materials.get("items"))){var member=fields(value,"ordinal","materialRevisionId","parsedDocumentId","contentHash");uuid(text(member.get("materialRevisionId")));uuid(text(member.get("parsedDocumentId")));hash(text(member.get("contentHash")));require(number(member.get("ordinal"))>0&&revisions.add(text(member.get("materialRevisionId"))),INPUT_INVALID);}
  var sources=new HashMap<String,Map<String,CanonicalValue>>();for(var value:array(manifest.get("sources"))){var source=fields(value,"sourceId","locator","role","sourceVersion","sourceContentHash");require(sources.putIfAbsent(text(source.get("sourceId")),source)==null,INPUT_INVALID);}
  require(payload.containsKey("backgroundMaterials"),INPUT_INVALID);var seen=new HashSet<String>();for(var value:array(payload.get("backgroundMaterials"))){var unit=fields(value,"sourceId","locator","sourceVersion","body");String alias=text(unit.get("sourceId"));var original=sources.get(alias);require(original!=null&&seen.add(alias)&&text(original.get("role")).equals("BACKGROUND")&&original.get("locator").equals(unit.get("locator"))&&original.get("sourceVersion").equals(unit.get("sourceVersion"))&&!text(unit.get("body")).isBlank(),INPUT_INVALID);}
  var excluded=new HashSet<String>();for(var value:array(fields(payload.get("exclusions"),"revisionId","entries").get("entries")))excluded.add(text(fields(value,"sourceId","locatorId","category","reasonHash").get("sourceId")));
  for(var source:sources.entrySet())if(text(source.getValue().get("role")).equals("BACKGROUND")&&!excluded.contains(source.getKey()))require(seen.contains(source.getKey()),INPUT_INVALID);
 }
 private MaterialMinutesWire(){}
}
