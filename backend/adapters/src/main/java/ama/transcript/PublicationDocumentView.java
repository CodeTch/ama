package ama.transcript;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Validates immutable provider content and emits a static document with no active markup. */
final class PublicationDocumentView {
 static final String CSS="*{box-sizing:border-box}body{margin:0;background:#f6f7f9;color:#283344;font:14px/1.7 'Microsoft YaHei','PingFang SC',sans-serif}main{max-width:1552px;margin:auto;padding:28px 32px}.page-head{display:flex;justify-content:space-between;gap:24px;margin-bottom:24px}h1{font-size:22px;margin:0;color:#172e50;overflow-wrap:anywhere}.muted,dt,time{color:#7b8596}.page-desc{margin-top:5px;font-size:12px}.actions{display:flex;gap:10px;align-items:center;flex-shrink:0}.btn{display:inline-block;padding:7px 14px;border:1px solid #dfe3e9;border-radius:6px;color:#283344;background:white;text-decoration:none}.tag{padding:3px 9px;border-radius:4px;background:#e8f5ed;color:#278657;font-size:12px}.two-col{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(0,1fr);gap:20px;align-items:start}.card{background:white;border:1px solid #e4e7ed;border-radius:8px;margin-bottom:16px}.card-h{padding:14px 20px;border-bottom:1px solid #edf0f4;font-weight:600}.card-p{padding:20px}pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.9}dl{display:grid;grid-template-columns:92px minmax(0,1fr);gap:10px;margin:0;font-size:13px}dd{margin:0;text-align:right;overflow-wrap:anywhere}article{border-left:2px solid #e4e7ed;padding:0 0 18px 16px}article:last-child{padding-bottom:0}article p{margin:4px 0;font-size:13px}time{font-size:12px}summary{cursor:pointer}details{margin-top:18px}h2{font-size:16px}a{color:#173b65}.source-note{font-size:12px;margin:12px 0 0}@media(max-width:760px){main{padding:20px 16px}.page-head{display:block}.actions{margin-top:16px;flex-wrap:wrap}.two-col{grid-template-columns:minmax(0,1fr)}}";
 static String csp(){try{return "default-src 'none'; style-src 'sha256-"+Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(CSS.getBytes(StandardCharsets.UTF_8)))+"'; base-uri 'none'; frame-ancestors 'self'";}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static final List<String> CATEGORIES=List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT");
 private static final List<String> LABELS=List.of("概述","讨论","结论","行动项","风险","待解决问题","分歧");
 static byte[] render(Map<String,Object> response,Map<String,Object> manifest,Map<String,Object> confirmation,String document,String result,TranscriptAcquisitionBudget.Ledger budget,long limit) {
  return render(response,manifest,confirmation,document,result,Map.of(),budget,limit);
 }
 static byte[] render(Map<String,Object> response,Map<String,Object> manifest,Map<String,Object> confirmation,String document,String result,Map<String,Object> details,TranscriptAcquisitionBudget.Ledger budget,long limit) {
  budget.check();fields(response,"providerId","storeInstanceId","documentId","resultId","publicationId","payloadSha256","payload");
  for(String key:List.of("providerId","storeInstanceId","publicationId","payloadSha256"))require(Objects.equals(manifest.get(key),response.get(key)),PROVENANCE_UNAVAILABLE);
  require(document.equals(response.get("documentId"))&&result.equals(response.get("resultId")),PROVENANCE_UNAVAILABLE);
  var payload=object(response.get("payload"));
  if("ama.controlled-knowledge-document.v2".equals(payload.get("schemaVersion"))){fields(payload,"schemaVersion","title","meeting","minutes","sections","provenance","publicationMetadata");PublicationConfigurationData.validate(payload.get("publicationMetadata"));require(Objects.equals(payload.get("publicationMetadata"),manifest.get("publicationMetadata")),PROVENANCE_UNAVAILABLE);}
  else{fields(payload,"schemaVersion","title","meeting","minutes","sections","provenance");require("ama.controlled-knowledge-document.v1".equals(payload.get("schemaVersion"))&&!manifest.containsKey("publicationMetadata"),PROVENANCE_UNAVAILABLE);}
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
  var measured=new Output(null,cap,budget);emit(payload,confirmation,document,details,measured);long bytes=measured.bytes;
  budget.responseOverhead(bytes);budget.capture(bytes,bytes,bytes,3);
  var builder=new StringBuilder(Math.toIntExact(bytes));var output=new Output(builder,cap,budget);emit(payload,confirmation,document,details,output);require(output.bytes==bytes,PROVENANCE_UNAVAILABLE);budget.check();byte[] encoded=builder.toString().getBytes(StandardCharsets.UTF_8);require(encoded.length==bytes,PROVENANCE_UNAVAILABLE);return encoded;
 }
 private static void emit(Map<?,?> payload,Map<String,Object> confirmation,String document,Map<String,Object> details,Output out) {
  out.raw("<!doctype html><html lang='zh-CN'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>");out.text(payload.get("title"));out.raw("</title><style>");out.raw(CSS);out.raw("</style></head><body><main><header class='page-head'><div><h1>");out.text(payload.get("title"));out.raw("</h1><div class='page-desc'>");out.text(document);out.raw(" · V1 · 发布于 ");out.text(details.getOrDefault("publishedAt","发布时间待核实"));out.raw("</div></div><div class='actions'><span class='tag'>已确认发布</span>");
  if(details.get("meetingId") instanceof String id){uuid(id);out.raw("<a class='btn' href='/meeting/");out.text(id);out.raw("/read?sources=1'>访问原始证据</a>");}
  out.raw("</div></header><div class='two-col'><section class='card card-p' aria-label='正式纪要正文'><pre>");out.text(object(payload.get("minutes")).get("body"));out.raw("</pre><details><summary>确认成果及来源标记</summary>");
  var sections=list(payload.get("sections"));for(int i=0;i<sections.size();i++){out.raw("<h2>");out.text(LABELS.get(i));out.raw("</h2><ol>");for(Object value:list(object(sections.get(i)).get("items"))){var item=object(value);out.raw("<li><pre>");out.text(item.get("body"));out.raw("</pre>");if(item.get("acceptedAction")!=null){var action=object(item.get("acceptedAction"));out.raw("<p>负责人：");out.text(action.get("ownerDisplay"));if(action.get("dueOn")!=null){out.raw("；截止日期：");out.text(action.get("dueOn"));}out.raw("</p>");}out.raw("</li>");}out.raw("</ol>");}out.raw("</details></section><aside><section class='card'><div class='card-h'>文档信息</div><div class='card-p'><dl>");
  var meeting=object(payload.get("meeting"));info(out,"确认状态","已确认 · "+confirmation.get("id"));info(out,"发布版本","V1");info(out,"确认人",confirmation.get("confirmed_by"));info(out,"确认时间",confirmation.get("confirmed_at"));info(out,"发布人",details.get("publishedBy"));info(out,"发布时间",details.get("publishedAt"));info(out,"来源会议",meeting.get("name"));if(details.containsKey("meetingId"))info(out,"会议编号",details.get("meetingId"));info(out,"会议分类",details.getOrDefault("categoryName",meeting.get("categoryId")));if(meeting.get("actualDate")!=null)info(out,"会议日期",meeting.get("actualDate"));
  var people=new ArrayList<String>();for(Object member:list(meeting.get("roster")))people.add(text(object(member).get("displayName")));info(out,"参会人员",people.isEmpty()?"未添加":String.join("、",people));
  if(payload.get("publicationMetadata") instanceof Map<?,?> metadata){info(out,"发布位置",String.join(" / ",list(metadata.get("pathNames")).stream().map(PublicationDocumentView::text).toList()));info(out,"权限",metadata.get("visibility"));info(out,"标签",String.join("、",list(metadata.get("tags")).stream().map(PublicationDocumentView::text).toList()));}
  else{info(out,"发布位置",details.get("path"));info(out,"权限","继承目标知识库当前阅读权限");}
  info(out,"来源材料",details.containsKey("materialCount")?details.get("materialCount")+" 份（按权限可见）":"来源关系待读取");info(out,"知识背景引用",details.containsKey("backgroundCount")?details.get("backgroundCount")+" 条 · 背景补充":"来源关系待读取");info(out,"证据关系",details.containsKey("evidenceCount")?"已保存 "+details.get("evidenceCount")+" 条；其中支持 "+details.get("supportedCount")+" 条":"来源关系待读取");out.raw("</dl><p class='source-note muted'>原录音、会议内容及引用材料分别校验访问权限；无权访问的来源不会展开。</p></div></section><section class='card'><div class='card-h'>历史版本</div><div class='card-p'><article><b>V1（当前）</b><p>与最终确认快照一致，发布后只读</p><time>");out.text(details.get("publishedAt"));out.raw("</time></article><article><b>最终确认</b><p>");out.text(confirmation.get("id"));out.raw("</p><time>");out.text(confirmation.get("confirmed_at"));out.raw("</time></article>");
  if(details.get("attempts") instanceof List<?> attempts)for(Object value:attempts){var attempt=object(value);out.raw("<article><b>");out.text(switch(text(attempt.get("outcome"))){case "SUCCESS"->"发布成功";case "FAILED","REJECTED"->"发布失败";case "UNKNOWN"->"发布结果曾待查回";default->"发布处理记录";});out.raw("</b><p>");out.text(attempt.get("detail"));out.raw("</p><time>");out.text(attempt.get("at"));out.raw("</time></article>");}
  out.raw("</div></section></aside></div></main></body></html>");
 }
 private static void info(Output out,String label,Object value){out.raw("<dt>");out.text(label);out.raw("</dt><dd>");out.text(value);out.raw("</dd>");}
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
