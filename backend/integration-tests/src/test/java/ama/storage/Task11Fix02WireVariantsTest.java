package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix02WireVariantsTest {
 @Test void exactOriginalCanonicalSemanticControlsWithoutDatabase()throws Exception{
  byte[] bytes=Files.readAllBytes(Path.of(System.getenv("AMA_TASK11_RUN_ROOT"),"c-no2-original-manifest-20261025.json"));assertEquals("71278c0421e6b198bab3e241de61c89b07b34b7b17c36d3106b775916e370b74",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));String original=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);var variants=Task11Fix02WireVariants.of(original);assertEquals(11,variants.size());var verified=new ArrayList<String>();for(var e:variants.entrySet()){ama.transcript.Task11Fix02WireSemanticsProbe.verify(original,e.getValue(),!e.getKey().equals("noncanonical-order"));verified.add(e.getKey());}Files.writeString(Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"),"canonical-wire-control.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("originalBytes",bytes.length,"variants",verified,"semanticVariantsCanonical",10,"noncanonicalEquivalent",1,"databaseConnections",0,"qualification","pure production canonical/semantic controls for captured exact original; no worker, database, lease, provider or marker")));
 }
}
