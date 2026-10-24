package ama.minutes.contract;
import ama.contract.MinutesContracts.*;
import ama.canonical.CanonicalValue;
import static ama.canonical.CanonicalValue.*;
/** Schema-owned projections, never reflective or implicit object conversion. */
public final class ContractValues {
 private ContractValues(){}
 public static CanonicalValue nullable(String s){return s==null?nil():string(s);}
 public static CanonicalValue nullable(Long n){return n==null?nil():number(n);}
 public static CanonicalValue locator(Locator l) {
  return switch(l) {
   case TranscriptLocator x -> object(field("kind",string("TRANSCRIPT")),field("transcriptRevisionId",string(x.transcriptRevisionId())),field("segmentId",string(x.segmentId())));
   case AudioLocator x -> object(field("kind",string("AUDIO")),field("fileId",string(x.fileId())),field("startMs",number(x.startMs())),field("endMs",number(x.endMs())));
   case PageLocator x -> object(field("kind",string(x.kind().name())),field("fileId",string(x.fileId())),field("pageNo",number(x.pageNo())));
   case ParagraphLocator x -> object(field("kind",string(x.kind().name())),field("fileId",string(x.fileId())),field("paragraphNo",number(x.paragraphNo())),field("sectionPath",nullable(x.sectionPath())));
   case KnowledgeLocator x -> object(field("kind",string("KNOWLEDGE_VERSION")),field("materialRevisionId",string(x.materialRevisionId())));
  };
 }
 public static CanonicalValue sourceVersion(SourceVersion version) {
  return switch(version) {
   case TranscriptVersion x -> object(field("kind",string("TRANSCRIPT")),field("confirmationId",string(x.confirmationId())),field("revisionId",string(x.revisionId())),field("batchId",string(x.batchId())),field("sourceFileId",string(x.sourceFileId())),field("sourceFileSha256",string(x.sourceFileSha256())));
   case FileVersion x -> object(field("kind",string("FILE")),field("fileId",string(x.fileId())),field("materialRevisionId",nullable(x.materialRevisionId())),field("parsedDocumentId",nullable(x.parsedDocumentId())),field("parserVersion",nullable(x.parserVersion())),field("parsedTextSha256",nullable(x.parsedTextSha256())));
   case KnowledgeVersion x -> object(field("kind",string("KNOWLEDGE")),field("materialRevisionId",string(x.materialRevisionId())),field("providerId",string(x.providerId())),field("externalId",string(x.externalId())),field("externalVersion",string(x.externalVersion())),field("contentFileId",string(x.contentFileId())),field("parsedDocumentId",string(x.parsedDocumentId())),field("parserVersion",string(x.parserVersion())),field("parsedTextSha256",string(x.parsedTextSha256())));
  };
 }
 public static CanonicalValue anchor(Anchor a) {
  return object(field("schemaVersion",string("ama.anchor.v1")),field("unit",string("UNICODE_SCALAR")),field("start",number(a.start())),field("end",number(a.end())),field("targetTextSha256",string(a.targetTextSha256())));
 }
 public static CanonicalValue envelope(String profile,String kind,CanonicalValue data) {
  return object(field("profile",string(profile)),field("kind",string(kind)),field("data",data));
 }
}
