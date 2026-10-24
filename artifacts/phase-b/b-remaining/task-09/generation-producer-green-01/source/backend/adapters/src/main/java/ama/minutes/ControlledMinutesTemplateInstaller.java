package ama.minutes;

import ama.db.TransactionRunner;
import ama.contract.MinutesContracts;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Explicit controlled administrator action. Never called from application constructors/startup. */
public final class ControlledMinutesTemplateInstaller {
 public static final String NAME="Controlled minutes";
 public static final String THEMES="{\"schemaVersion\":\"ama.controlled-minutes-template.v1\",\"themes\":[\"OVERVIEW\",\"DISCUSSION\",\"CONCLUSION\",\"ACTION\",\"RISK\",\"OPEN_QUESTION\",\"DISAGREEMENT\"]}";
 private final JdbcTemplate j;private final TransactionRunner tx;
 public ControlledMinutesTemplateInstaller(DataSource source,String explicitMode){
  if(!"CONTROLLED_INSTALLATION".equals(explicitMode))throw new IllegalArgumentException("Explicit controlled installation required");
  j=new JdbcTemplate(Objects.requireNonNull(source));j.setQueryTimeout(2);tx=new TransactionRunner(source,10);
 }
 public record Installed(String templateId,String revisionId,String name,String themes,int themeCount) {}
 public Installed install(String templateId,String revisionId){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Independent installation required");
  MinutesContracts.uuid(templateId);MinutesContracts.uuid(revisionId);
  String normalized=normalize(NAME),hash=hash(normalized); // No hashing under retained database roots.
  return tx.required(()->{
   var parent=parent(templateId);var revision=revision(revisionId);
   if(parent==null&&revision==null){
    var now=j.queryForObject("SELECT UTC_TIMESTAMP(6)",java.time.LocalDateTime.class);
    j.update("INSERT INTO minutes_template(id,owner_id,name,name_normalized,name_hash,current_revision_id,revision,created_at) VALUES (?,NULL,?,?,?,?,1,?)",templateId,NAME,normalized,hash,revisionId,now);
    j.update("INSERT INTO template_revision(id,template_id,revision,owner_id,name,themes,theme_count,created_at) VALUES (?,?,1,NULL,?,?,7,?)",revisionId,templateId,NAME,THEMES,now);
    parent=parent(templateId);revision=revision(revisionId);
   }
   if(parent==null||revision==null||parent.get("owner_id")!=null||revision.get("owner_id")!=null
     ||!revisionId.equals(parent.get("current_revision_id"))||!templateId.equals(revision.get("template_id"))
     ||number(parent,"revision")!=1||number(revision,"revision")!=1||number(revision,"theme_count")!=7
     ||!NAME.equals(parent.get("name"))||!NAME.equals(revision.get("name"))||!normalized.equals(parent.get("name_normalized"))||!hash.equals(parent.get("name_hash"))
     ||!THEMES.equals(revision.get("themes"))||!Objects.equals(parent.get("created_at"),revision.get("created_at")))
    throw new IllegalArgumentException("Immutable controlled template conflict");
   return new Installed(templateId,revisionId,NAME,THEMES,7);
  });
 }
 private Map<String,Object> parent(String id){
  var lengths=j.queryForList("SELECT id,OCTET_LENGTH(name) AS n,OCTET_LENGTH(name_normalized) AS z FROM minutes_template WHERE id=? FOR UPDATE",id);
  if(lengths.isEmpty())return null;if(lengths.size()!=1||number(lengths.getFirst(),"n")!=NAME.getBytes(StandardCharsets.UTF_8).length||number(lengths.getFirst(),"z")!=NAME.length())throw new IllegalArgumentException("Invalid template content size");
  return j.queryForMap("SELECT id,owner_id,name,name_normalized,name_hash,current_revision_id,revision,created_at FROM minutes_template WHERE id=?",id);
 }
 private Map<String,Object> revision(String id){
  var lengths=j.queryForList("SELECT id,OCTET_LENGTH(name) AS n,OCTET_LENGTH(themes) AS t FROM template_revision WHERE id=? FOR UPDATE",id);
  if(lengths.isEmpty())return null;if(lengths.size()!=1||number(lengths.getFirst(),"n")!=NAME.length()||number(lengths.getFirst(),"t")!=THEMES.length())throw new IllegalArgumentException("Invalid template revision size");
  return j.queryForMap("SELECT id,template_id,revision,owner_id,name,themes,theme_count,created_at FROM template_revision WHERE id=?",id);
 }
 private static long number(Map<String,Object> row,String field){return ((Number)row.get(field)).longValue();}
 static String normalize(String name){
  MinutesContracts.scalars(name);String n=Normalizer.normalize(name,Normalizer.Form.NFC);int a=0,b=n.length();
  while(a<b){int cp=n.codePointAt(a);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;a+=Character.charCount(cp);}
  while(b>a){int cp=n.codePointBefore(b);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))break;b-=Character.charCount(cp);}
  return n.substring(a,b);
 }
 private static String hash(String name){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
