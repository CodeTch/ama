package ama.realtime;
import ama.contract.RecordingContracts;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecordingBinaryCodecTest {
 RecordingContracts.Chunk chunk(){return new RecordingContracts.Chunk(UUID.randomUUID().toString(),7,5,64000,2,new byte[]{1,2,3,4});}
 @Test void binaryHeaderPreservesExactMetadataHashAndPcmWithoutMutatingCallerBuffer(){
  var original=chunk();byte[] frame=RecordingBinaryCodec.encode(original);assertEquals(84,frame.length);assertEquals(0x414d4131,ByteBuffer.wrap(frame).getInt());assertEquals(7,ByteBuffer.wrap(frame).getLong(20));
  var wrapped=ByteBuffer.allocate(frame.length+10).order(ByteOrder.LITTLE_ENDIAN);wrapped.position(3);wrapped.put(frame);wrapped.position(3);wrapped.limit(3+frame.length);var input=wrapped.asReadOnlyBuffer();var decoded=RecordingBinaryCodec.decode(input);
  assertEquals(3,input.position());assertEquals(3+frame.length,input.limit());assertEquals(original.partId(),decoded.partId());assertEquals(original.writerEpoch(),decoded.writerEpoch());assertEquals(original.seq(),decoded.seq());assertEquals(original.sampleStart(),decoded.sampleStart());assertEquals(original.sampleCount(),decoded.sampleCount());assertEquals(original.sha256(),decoded.sha256());assertArrayEquals(original.pcm(),decoded.pcm());
  Arrays.fill(frame,(byte)0);assertArrayEquals(new byte[]{1,2,3,4},decoded.pcm());
 }
 @Test void malformedOversizedAndCorruptedFramesFailBeforeAnyChunkCanBeAccepted(){
  var frame=RecordingBinaryCodec.encode(chunk());
  for(byte[] invalid:List.of(new byte[79],new byte[64081],Arrays.copyOf(frame,frame.length-1),Arrays.copyOf(frame,frame.length+1))){assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(invalid)));}
  var magic=frame.clone();magic[0]=0;assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(magic)));
  var hash=frame.clone();hash[48]^=1;assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(hash)));
  var pcm=frame.clone();pcm[80]^=1;assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(pcm)));
  var count=frame.clone();ByteBuffer.wrap(count).putInt(44,Integer.MAX_VALUE);assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(count)));
  var epoch=frame.clone();ByteBuffer.wrap(epoch).putLong(20,0);assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.decode(ByteBuffer.wrap(epoch)));
 }
 @Test void legacyHeaderlessEpochAndNonCanonicalPartCannotBeEncodedForSocketTransport(){
  assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.encode(new RecordingContracts.Chunk(UUID.randomUUID().toString(),1,0,1,new byte[2])));
  assertThrows(IllegalArgumentException.class,()->RecordingBinaryCodec.encode(new RecordingContracts.Chunk("part",1,1,0,1,new byte[2])));
 }
}
