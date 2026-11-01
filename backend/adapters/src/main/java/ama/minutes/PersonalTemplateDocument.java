package ama.minutes;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Versioned personal themes; deliberately distinct from the installed seven-category v1. */
public record PersonalTemplateDocument(List<String> themes) {
 public static final String SCHEMA="ama.personal-minutes-template.v1";
 public static final int MAX_TEXT_BYTES=8192,MAX_DOCUMENT_BYTES=65536;
 private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 public PersonalTemplateDocument {
  if(themes==null||themes.isEmpty()||themes.size()>8)throw new IllegalArgumentException("One to eight template themes required");
  themes=List.copyOf(themes);for(String theme:themes)text(theme);
 }
 public String json(){try{var value=JSON.createObjectNode();value.put("schemaVersion",SCHEMA);var entries=value.putArray("themes");themes.forEach(entries::add);String encoded=JSON.writeValueAsString(value);if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX_DOCUMENT_BYTES)throw new IllegalArgumentException("Template document exceeds byte budget");return encoded;}catch(java.io.IOException impossible){throw new IllegalStateException(impossible);}}
 public static PersonalTemplateDocument parse(String raw){
  if(raw==null||raw.getBytes(StandardCharsets.UTF_8).length>MAX_DOCUMENT_BYTES)throw new IllegalStateException("Template document unavailable");
  try{var value=JSON.readTree(raw);if(value==null||!value.isObject()||value.size()!=2||!value.has("themes")||!value.path("schemaVersion").isTextual()||!SCHEMA.equals(value.path("schemaVersion").textValue())||!value.path("themes").isArray())throw new IllegalStateException("Template schema unavailable");var themes=new ArrayList<String>();for(var item:value.path("themes")){if(!item.isTextual())throw new IllegalStateException("Template theme unavailable");themes.add(item.textValue());}var document=new PersonalTemplateDocument(themes);if(!document.json().equals(raw))throw new IllegalStateException("Template encoding unavailable");return document;}catch(java.io.IOException|IllegalArgumentException invalid){throw new IllegalStateException("Template document unavailable",invalid);}
 }
 public static String name(String value){text(value);String normalized=ControlledMinutesTemplateInstaller.normalize(value);if(normalized.isEmpty())throw new IllegalArgumentException("Template name required");return normalized;}
 static void text(String value){
  if(value==null||value.isEmpty())throw new IllegalArgumentException("Nonblank template text required");
  boolean visible=false;for(int n=0;n<value.length();){char c=value.charAt(n);if(Character.isSurrogate(c)&&(!Character.isHighSurrogate(c)||n+1>=value.length()||!Character.isLowSurrogate(value.charAt(n+1))))throw new IllegalArgumentException("Unicode scalar template text required");int cp=value.codePointAt(n);if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp))visible=true;n+=Character.charCount(cp);}
  if(!visible||value.getBytes(StandardCharsets.UTF_8).length>MAX_TEXT_BYTES)throw new IllegalArgumentException("Template text exceeds byte budget");
 }
 static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 static String digest(String... fields){
  try{var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);for(String field:fields){if(field==null){out.writeInt(-1);continue;}var encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(field));out.writeInt(encoded.remaining());var value=new byte[encoded.remaining()];encoded.get(value);out.write(value);}return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(java.io.IOException invalid){throw new IllegalArgumentException("Unicode scalar command required",invalid);}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
}
