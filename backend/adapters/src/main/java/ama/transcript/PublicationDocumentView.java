package ama.transcript;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Validates immutable provider content and emits a static document with no active markup. */
final class PublicationDocumentView {
 private static final List<String> CATEGORIES=List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT");
 private static final List<String> LABELS=List.of("概述","讨论","结论","行动项","风险","待解决问题","分歧");
 static byte[] render(Map<String,Object> response,Map<String,Object> manifest,Map<String,Object> confirmation,String document,String result,TranscriptAcquisitionBudget.Ledger budget,long limit) {
  budget.check();fields(response,"providerId","storeInstanceId","documentId","resultId","publicationId","payloadSha256","payload");
  for(String key:List.of("providerId","storeInstanceId","publicationId","payloadSha256"))require(Objects.equals(manifest.get(key),response.get(key)),PROVENANCE_UNAVAILABLE);
  require(document.equals(response.get("documentId"))&&result.equals(response.get("resultId")),PROVENANCE_UNAVAILABLE);
  var payload=object(response.get("payload"));fields(payload,"schemaVersion","title","meeting","minutes","sections","provenance");
  require("ama.controlled-knowledge-document.v1".equals(payload.get("schemaVersion")),PROVENANCE_UNAVAILABLE);
  var provenance=object(payload.get("provenance"));fields(provenance,"confirmationId","minutesRevisionId","elementSetId","transcriptConfirmationId","inputFingerprint","sourceManifestHash","renderArtifact");
  for(var pair:Map.of("confirmationId","id","minutesRevisionId","minutes_revision_id","elementSetId","element_set_id","transcriptConfirmationId","transcript_confirmation_id","inputFingerprint","input_fingerprint","sourceManifestHash","source_manifest_hash").entrySet())require(Objects.equals(provenance.get(pair.getKey()),confirmation.get(pair.getValue())),PROVENANCE_UNAVAILABLE);
  require(manifest.get("confirmationId").equals(provenance.get("confirmationId"))&&Objects.equals(manifest.get("renderArtifact"),provenance.get("renderArtifact")),PROVENANCE_UNAVAILABLE);
  var artifact=object(provenance.get("renderArtifact"));fields(artifact,"version","sha256");
  var meeting=object(payload.get("meeting"));fields(meeting,"name","categoryId","actualDate","roster");
  text(meeting.get("name"));text(meeting.get("categoryId"));nullable(meeting.get("actualDate"));
  for(Object member:list(meeting.get("roster"))){budget.check();var m=object(member);fields(m,"memberKind","displayName");text(m.get("memberKind"));text(m.get("displayName"));}
  var minutes=object(payload.get("minutes"));fields(minutes,"revisionId","title","body");
  require(confirmation.get("minutes_revision_id").equals(minutes.get("revisionId"))&&Objects.equals(payload.get("title"),minutes.get("title")),PROVENANCE_UNAVAILABLE);text(payload.get("title"));text(minutes.get("body"));
  var sections=list(payload.get("sections"));require(sections.size()==CATEGORIES.size(),PROVENANCE_UNAVAILABLE);var ids=new HashSet<String>();
  for(int i=0;i<sections.size();i++) {
   budget.check();var section=object(sections.get(i));fields(section,"category","items");require(CATEGORIES.get(i).equals(section.get("category")),PROVENANCE_UNAVAILABLE);long previous=-1;
   for(Object value:list(section.get("items"))) {
    var item=object(value);fields(item,"elementId","ordinal","body","acceptedAction");String id=text(item.get("elementId"));uuid(id);require(ids.add(id),PROVENANCE_UNAVAILABLE);
    require(item.get("ordinal") instanceof Integer||item.get("ordinal") instanceof Long,PROVENANCE_UNAVAILABLE);long ordinal=((Number)item.get("ordinal")).longValue();require(ordinal>=0&&ordinal>=previous,PROVENANCE_UNAVAILABLE);previous=ordinal;text(item.get("body"));
    if(i==3){var action=object(item.get("acceptedAction"));fields(action,"ownerEnterpriseId","ownerDisplay","dueOn");for(Object cell:action.values())nullable(cell);}else require(item.get("acceptedAction")==null,PROVENANCE_UNAVAILABLE);
   }
  }
  // Canonical payload and every owned copy share the operation ledger; no database transaction is open here.
  byte[] canonical=ControlledKnowledgeReadJson.canonical(payload,budget);require(canonical.length<=8388608,BUDGET_EXCEEDED);require(ControlledKnowledgeClient.digest(canonical).equals(manifest.get("payloadSha256")),FINGERPRINT_MISMATCH);
  long cap=Math.min(33554432,limit);require(cap>0,BUDGET_EXCEEDED);
  var measured=new Output(null,cap,budget);emit(payload,measured);long bytes=measured.bytes;
  budget.responseOverhead(bytes);budget.capture(bytes,bytes,bytes,3);
  var builder=new StringBuilder(Math.toIntExact(bytes));var output=new Output(builder,cap,budget);emit(payload,output);require(output.bytes==bytes,PROVENANCE_UNAVAILABLE);budget.check();byte[] encoded=builder.toString().getBytes(StandardCharsets.UTF_8);require(encoded.length==bytes,PROVENANCE_UNAVAILABLE);return encoded;
 }
 private static void emit(Map<?,?> payload,Output out) {
  out.raw("<!doctype html><html lang='zh-CN'><head><meta charset='UTF-8'><title>");out.text(payload.get("title"));out.raw("</title></head><body><h1>");out.text(payload.get("title"));out.raw("</h1>");
  var meeting=object(payload.get("meeting"));out.raw("<h2>会议</h2><p>");out.text(meeting.get("name"));out.raw("</p><p>类别：");out.text(meeting.get("categoryId"));out.raw("</p><p>日期：");out.text(meeting.get("actualDate"));out.raw("</p><h3>参会人员</h3><ul>");
  for(Object member:list(meeting.get("roster"))){var m=object(member);out.raw("<li>");out.text(m.get("displayName"));out.raw("</li>");}out.raw("</ul><h2>纪要</h2><pre>");out.text(object(payload.get("minutes")).get("body"));out.raw("</pre>");
  var sections=list(payload.get("sections"));for(int i=0;i<sections.size();i++){out.raw("<h2>");out.text(LABELS.get(i));out.raw("</h2><ol>");for(Object value:list(object(sections.get(i)).get("items"))){var item=object(value);out.raw("<li><pre>");out.text(item.get("body"));out.raw("</pre>");if(item.get("acceptedAction")!=null){var action=object(item.get("acceptedAction"));out.raw("<p>负责人：");out.text(action.get("ownerDisplay"));out.raw("；截止日期：");out.text(action.get("dueOn"));out.raw("</p>");}out.raw("</li>");}out.raw("</ol>");}out.raw("</body></html>");
 }
 private static final class Output {
  final StringBuilder value;final long maximum;final TranscriptAcquisitionBudget.Ledger budget;long bytes;
  Output(StringBuilder value,long maximum,TranscriptAcquisitionBudget.Ledger budget){this.value=value;this.maximum=maximum;this.budget=budget;}
  void code(int cp){int size=cp<128?1:cp<2048?2:cp<65536?3:4;require(size<=maximum-bytes,BUDGET_EXCEEDED);bytes+=size;if(value!=null)value.appendCodePoint(cp);}
  void raw(String text){budget.check();for(int i=0;i<text.length();){int cp=text.codePointAt(i);code(cp);i+=Character.charCount(cp);}}
  void text(Object cell){budget.check();if(cell==null){raw("未填写");return;}String text=PublicationDocumentView.text(cell);for(int i=0;i<text.length();){if((i&4095)==0)budget.check();int cp=text.codePointAt(i);i+=Character.charCount(cp);switch(cp){case '&'->raw("&amp;");case '<'->raw("&lt;");case '>'->raw("&gt;");case '"'->raw("&quot;");case '\''->raw("&#39;");default->{if(cp<32&&cp!='\n'&&cp!='\r'&&cp!='\t')raw(String.format(Locale.ROOT,"\\u%04x",cp));else code(cp);}}}}
 }
 private static String text(Object value){require(value instanceof String,PROVENANCE_UNAVAILABLE);String s=(String)value;scalars(s);return s;}
 private static void nullable(Object value){if(value!=null)text(value);}
 private static Map<?,?> object(Object value){require(value instanceof Map<?,?>,PROVENANCE_UNAVAILABLE);return (Map<?,?>)value;}
 private static List<?> list(Object value){require(value instanceof List<?>,PROVENANCE_UNAVAILABLE);return (List<?>)value;}
 private static void fields(Map<?,?> value,String... names){require(value.keySet().equals(Set.of(names)),PROVENANCE_UNAVAILABLE);}
}
