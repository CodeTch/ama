package ama.transcript;
import ama.config.*;import ama.config.json.*;import ama.contract.ConfigArchiveContracts.*;import ama.contract.ConfigArchiveContracts.Ref;import ama.contract.MinutesContracts.*;import ama.minutes.contract.*;import java.util.*;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Frozen membership and source identity validation only; current publication readiness is a separate consumer. */
final class MinutesStoredFinals {
 static List<Ref> validate(MinutesGraphCapture.Graph graph,StoredProvenanceFacts facts,ConfigArchiveSource.Snapshot snapshot,ConfigBindings bindings,ArchiveReadBudget read,GraphBudget graphBudget,TranscriptAcquisitionBudget.Ledger budget){
  var roots=new LinkedHashSet<Ref>();for(var row:JdbcMinutesFactsReader.rows(graph,Table.FINAL_CONFIRMATION)){
   budget.check();String meeting=graph.discovery().meeting();require(meeting.equals(row.string("meeting_id"))&&row.number("meeting_revision")>0&&row.string("confirmed_by").equals(graph.discovery().root().get("owner_id")),PROVENANCE_UNAVAILABLE);
   var info=control(graph,"meeting_info_revision",row.string("info_revision_id"));var roster=control(graph,"roster_revision",row.string("roster_revision_id"));require(meeting.equals(info.get("meeting_id"))&&meeting.equals(roster.get("meeting_id"))&&((Number)info.get("revision")).longValue()>=0&&((Number)roster.get("revision")).longValue()>=0&&"CONFIRMED".equals(roster.get("state"))&&roster.get("confirmed_at")!=null&&roster.get("confirmed_by")!=null,PROVENANCE_UNAVAILABLE);
   var body=JdbcMinutesFactsReader.one(graph,Table.MINUTES_REVISION,row.string("minutes_revision_id"));String input=body.nullableString("generation_input_id");require(input!=null,PROFILE_UNAVAILABLE);var configurations=facts.configurations().stream().filter(c->c.inputId().equals(input)).toList();require(configurations.size()==1,PROFILE_UNAVAILABLE);var refs=new GenerationArchiveRefs(facts,snapshot.index(),new StrictProcessingManifestReader(),read,graphBudget,()->false).finalSources(row.string("id"));var raw=MinutesStoredReviews.load(snapshot,refs,read,budget);String actual=new ResultHashProfiles(bindings,raw,read).finalSources(new FinalFacts(row.string("id"),facts),configurations.getFirst().profiles().definition(ProfileKind.FINAL_SOURCES),graphBudget,()->false);require(actual.equals(row.string("source_manifest_hash")),FINGERPRINT_MISMATCH);roots.addAll(refs);
  }return List.copyOf(roots);
 }
 private static Map<String,Object> control(MinutesGraphCapture.Graph graph,String table,String id){var rows=graph.auxiliary().getOrDefault(table,List.of()).stream().filter(r->id.equals(r.get("id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
}
