package ama.meeting;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceCursorTest {
 @Test void cursorBindsActorScopeAndExpiresWithoutExposingPosition() {
  var clock=new MutableClock();var cursor=new WorkspaceCursor(clock);
  String position="2026-10-27T05:00:00.123456Z/00000000-0000-0000-0000-000000000001";
  String token=cursor.encode("owner","owned",position);
  assertEquals(position,cursor.decode("owner","owned",token));
  assertFalse(new String(java.util.Base64.getUrlDecoder().decode(token),java.nio.charset.StandardCharsets.UTF_8).contains(position));
  assertThrows(IllegalArgumentException.class,()->cursor.decode("other","owned",token));
  assertThrows(IllegalArgumentException.class,()->cursor.decode("owner","joined",token));
  byte[] altered=java.util.Base64.getUrlDecoder().decode(token);altered[altered.length-1]^=1;
  assertThrows(IllegalArgumentException.class,()->cursor.decode("owner","owned",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(altered)));
  assertThrows(IllegalArgumentException.class,()->new WorkspaceCursor(clock).decode("owner","owned",token));
  clock.value=clock.value.plusSeconds(300);
  assertThrows(IllegalArgumentException.class,()->cursor.decode("owner","owned",token));
 }
 @Test void validMultibyteIdentityAndCategoryIdsRemainLossless() {
  var cursor=new WorkspaceCursor(new MutableClock());String actor="人".repeat(255),position="类".repeat(255);
  assertEquals(position,cursor.decode(actor,"categories",cursor.encode(actor,"categories",position)));
 }
 @Test void malformedUnicodeIsRejectedBeforeOpaqueEncoding() {
  var cursor=new WorkspaceCursor(new MutableClock());String broken=String.valueOf((char)0xD800);
  assertThrows(IllegalArgumentException.class,()->cursor.encode("owner","scope",broken));
  assertThrows(IllegalArgumentException.class,()->cursor.encode("owner",broken,"position"));
 }
 @Test void malformedAndOversizedCursorsAndPositionsAreRejected() {
  var cursor=new WorkspaceCursor(new MutableClock());
  for(String token:java.util.List.of("","!","a".repeat(2048),"AA=="))assertThrows(IllegalArgumentException.class,()->cursor.decode("owner","owned",token));
  assertThrows(IllegalArgumentException.class,()->cursor.encode("owner","owned","x".repeat(1025)));
 }
 static class MutableClock extends Clock {
  Instant value=Instant.parse("2026-10-27T00:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return this;}
  public Instant instant(){return value;}
 }
}
