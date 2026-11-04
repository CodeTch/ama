package ama.recording;
import ama.contract.RecordingContracts;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecordingPartWaveTest {
 private static RecordingService.Proof proof(String part,long seq,long start,byte[] pcm)throws Exception{return new RecordingService.Proof(part,seq,start,pcm.length/2,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pcm)));}
 @Test void eachSealedPartHasItsOwnExactWavAndRangesWithoutReadingOtherParts()throws Exception{
  byte[] first={1,0,2,0},second={3,0,4,0};
  var snapshot=new RecordingService.Snapshot(new RecordingContracts.Session("session","meeting","two",2,"backend"),true,false,List.of(proof("one",1,0,first),proof("two",1,0,second)),List.of("one","two"));
  var parts=RecordingPartWave.parts(snapshot);assertEquals(2,parts.get(1).startSample());assertTrue(parts.get(1).available());
  var selected=parts.get(1);var full=RecordingPartWave.open(selected,null,p->{assertEquals("two",p.partId());return new ByteArrayInputStream(second);});
  byte[] wav=full.body().readAllBytes();assertEquals(48,wav.length);assertArrayEquals(second,Arrays.copyOfRange(wav,44,48));assertEquals(4,java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(40));full.close();
  try(var range=RecordingPartWave.open(selected,"bytes=42-46",p->new ByteArrayInputStream(second))){assertArrayEquals(new byte[]{0,0,3,0,4},range.body().readAllBytes());assertEquals(42,range.offset());assertEquals(5,range.length());}
  try(var suffix=RecordingPartWave.open(selected,"bytes=-2",p->new ByteArrayInputStream(second))){assertArrayEquals(new byte[]{4,0},suffix.body().readAllBytes());}
 }
 @Test void activeEmptyGappedAndCorruptPartsCannotBePresentedAsPlayable()throws Exception{
  byte[] pcm={1,0};var session=new RecordingContracts.Session("session","meeting","part",1,"backend");
  var active=RecordingPartWave.parts(new RecordingService.Snapshot(session,false,false,List.of(proof("part",1,0,pcm)),List.of("part"))).getFirst();
  assertFalse(active.available());assertThrows(IllegalStateException.class,()->RecordingPartWave.open(active,null,p->new ByteArrayInputStream(pcm)));
  assertFalse(RecordingPartWave.parts(new RecordingService.Snapshot(session,true,false,List.of(),List.of("part"))).getFirst().available());
  assertThrows(RecordingService.Gap.class,()->RecordingPartWave.parts(new RecordingService.Snapshot(session,true,false,List.of(proof("part",2,1,pcm)),List.of("part"))));
  var sealed=RecordingPartWave.parts(new RecordingService.Snapshot(session,true,false,List.of(proof("part",1,0,pcm)),List.of("part"))).getFirst();
  assertThrows(IOException.class,()->{try(var download=RecordingPartWave.open(sealed,null,p->new ByteArrayInputStream(new byte[]{9,0}))){download.body().readAllBytes();}});
  assertThrows(RecordingPartWave.InvalidRange.class,()->RecordingPartWave.open(sealed,"bytes=100-",p->new ByteArrayInputStream(pcm)));
 }
}
