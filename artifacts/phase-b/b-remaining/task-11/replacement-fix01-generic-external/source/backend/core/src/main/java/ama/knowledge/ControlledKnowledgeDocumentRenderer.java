package ama.knowledge;
import ama.canonical.CanonicalValue;
import ama.config.CanonicalJson;
import ama.contract.MinutesContracts.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Exact saved content rendering. Provenance authority remains with the registered S/A/F caller. */
public final class ControlledKnowledgeDocumentRenderer {
 public static final String VERSION="ama.controlled-minutes-render.v1";
 public record Member(String memberKind,String displayName){}
 public record Meeting(String name,String categoryId,String actualDate,List<Member> roster){public Meeting{roster=List.copyOf(roster);}}
 public byte[] render(FinalFacts selected,Meeting meeting,String rendererVersion,String defaultTemplateRevisionId,Artifact artifact,long limit,BooleanSupplier cancelled){return render(selected,meeting,rendererVersion,defaultTemplateRevisionId,artifact,limit,cancelled,null);}
 public byte[] render(FinalFacts selected,Meeting meeting,String rendererVersion,String defaultTemplateRevisionId,Artifact artifact,long limit,BooleanSupplier cancelled,ama.transcript.TranscriptAcquisitionBudget.Ledger budget){
  require(VERSION.equals(rendererVersion)&&limit>0&&limit<=8388608,PROFILE_UNAVAILABLE);
  var facts=selected.stored();var finalRow=one(facts,Table.FINAL_CONFIRMATION,selected.confirmationId());require(defaultTemplateRevisionId.equals(finalRow.string("template_revision_id")),PROFILE_UNAVAILABLE);
  var body=one(facts,Table.MINUTES_REVISION,finalRow.string("minutes_revision_id"));var template=one(facts,Table.TEMPLATE_REVISION,finalRow.string("template_revision_id"));
  require(template.string("themes").equals("{\"schemaVersion\":\"ama.controlled-minutes-template.v1\",\"themes\":[\"OVERVIEW\",\"DISCUSSION\",\"CONCLUSION\",\"ACTION\",\"RISK\",\"OPEN_QUESTION\",\"DISAGREEMENT\"]}"),PROFILE_UNAVAILABLE);
  if(budget!=null){long bound=8192+6L*(body.string("title").length()*2L+body.string("body").length()+meeting.name().length()+meeting.categoryId().length()+(meeting.actualDate()==null?0:meeting.actualDate().length())+artifact.version().length());budget.row(32);for(var member:meeting.roster()){budget.row(2);bound=Math.addExact(bound,128+6L*(member.memberKind().length()+member.displayName().length()));}for(var row:facts.rows())if(row.table()==Table.MINUTES_ELEMENT&&finalRow.string("element_set_id").equals(row.string("set_id"))||row.table()==Table.ACTION_ITEM){budget.row(row.cells().size());bound=Math.addExact(bound,512);for(var cell:row.cells())if(cell instanceof TextCell text)bound=Math.addExact(bound,6L*text.value().length());}require(bound<=limit,BUDGET_EXCEEDED);budget.capture(bound,bound,bound,6);}
  var roster=new ArrayList<CanonicalValue>();for(var member:meeting.roster()){check(cancelled);roster.add(object(field("memberKind",string(member.memberKind())),field("displayName",string(member.displayName()))));}
  var sections=new ArrayList<CanonicalValue>();for(Category category:Category.values()){
   var items=new ArrayList<CanonicalValue>();var elements=facts.rows().stream().filter(r->r.table()==Table.MINUTES_ELEMENT&&finalRow.string("element_set_id").equals(r.string("set_id"))&&category.name().equals(r.string("category"))).sorted(Comparator.comparingLong((FullRow r)->r.number("ordinal")).thenComparing(r->r.string("id"))).toList();
   for(var element:elements){check(cancelled);CanonicalValue accepted=nil();if(category==Category.ACTION){var actions=facts.rows().stream().filter(r->r.table()==Table.ACTION_ITEM&&element.string("id").equals(r.string("element_id"))).toList();require(actions.size()==1,PROVENANCE_UNAVAILABLE);var a=actions.getFirst();accepted=object(field("ownerEnterpriseId",nullable(a.nullableString("owner_enterprise_id"))),field("ownerDisplay",nullable(a.nullableString("owner_display"))),field("dueOn",nullable(a.nullableString("due_on"))));}
    items.add(object(field("elementId",string(element.string("id"))),field("ordinal",number(element.number("ordinal"))),field("body",string(element.string("body"))),field("acceptedAction",accepted)));
   }sections.add(object(field("category",string(category.name())),field("items",new Sequence(items))));
  }
  var payload=object(field("schemaVersion",string("ama.controlled-knowledge-document.v1")),field("title",string(body.string("title"))),field("meeting",object(field("name",string(meeting.name())),field("categoryId",string(meeting.categoryId())),field("actualDate",nullable(meeting.actualDate())),field("roster",new Sequence(roster)))),field("minutes",object(field("revisionId",string(body.string("id"))),field("title",string(body.string("title"))),field("body",string(body.string("body"))))),field("sections",new Sequence(sections)),field("provenance",object(field("confirmationId",string(finalRow.string("id"))),field("minutesRevisionId",string(body.string("id"))),field("elementSetId",string(finalRow.string("element_set_id"))),field("transcriptConfirmationId",string(finalRow.string("transcript_confirmation_id"))),field("inputFingerprint",string(finalRow.string("input_fingerprint"))),field("sourceManifestHash",string(finalRow.string("source_manifest_hash"))),field("renderArtifact",object(field("version",string(artifact.version())),field("sha256",string(artifact.sha256())))))));
  return CanonicalJson.encode(sorted(payload,cancelled),limit,cancelled);
 }
 private static CanonicalValue nullable(String value){return value==null?nil():string(value);}
 private static FullRow one(StoredProvenanceFacts facts,Table table,String id){var rows=facts.rows().stream().filter(r->r.table()==table&&id.equals(r.string("id"))).toList();require(rows.size()==1,PROVENANCE_UNAVAILABLE);return rows.getFirst();}
 public static CanonicalValue sorted(CanonicalValue value,BooleanSupplier cancelled){check(cancelled);if(value instanceof ObjectValue object){var entries=new ArrayList<Entry>();for(var entry:object.entries())entries.add(field(entry.key(),sorted(entry.value(),cancelled)));entries.sort(Comparator.comparing(Entry::key));return new ObjectValue(entries);}if(value instanceof Sequence list){var values=new ArrayList<CanonicalValue>();for(var item:list.values())values.add(sorted(item,cancelled));return new Sequence(values);}return value;}
}
