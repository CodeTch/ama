package ama.realtime;
import ama.contract.RecordingContracts;
import java.nio.*;
import java.util.*;
/** AMA1: 80 bytes of bounded metadata followed by exact PCM16 mono16k bytes. */
public final class RecordingBinaryCodec {
 public static final int HEADER_BYTES=80,MAX_FRAME_BYTES=HEADER_BYTES+RecordingContracts.MAX_PCM_BYTES;
 private static final int MAGIC=0x414d4131;
 private RecordingBinaryCodec(){}
 public static byte[] encode(RecordingContracts.Chunk chunk){
  Objects.requireNonNull(chunk);ama.contract.OutboxContracts.uuid(chunk.partId());if(chunk.writerEpoch()<1)throw new IllegalArgumentException("Writer epoch required");
  byte[] pcm=chunk.pcm();var part=UUID.fromString(chunk.partId());var frame=ByteBuffer.allocate(HEADER_BYTES+pcm.length).order(ByteOrder.BIG_ENDIAN);
  frame.putInt(MAGIC).putLong(part.getMostSignificantBits()).putLong(part.getLeastSignificantBits()).putLong(chunk.writerEpoch()).putLong(chunk.seq()).putLong(chunk.sampleStart()).putInt(Math.toIntExact(chunk.sampleCount())).put(HexFormat.of().parseHex(chunk.sha256())).put(pcm);return frame.array();
 }
 public static RecordingContracts.Chunk decode(ByteBuffer frame){
  if(frame==null||frame.remaining()<HEADER_BYTES+2||frame.remaining()>MAX_FRAME_BYTES)throw new IllegalArgumentException("Recording frame size invalid");
  var in=frame.duplicate().order(ByteOrder.BIG_ENDIAN);if(in.getInt()!=MAGIC)throw new IllegalArgumentException("Recording frame version invalid");
  var part=new UUID(in.getLong(),in.getLong());long epoch=in.getLong(),seq=in.getLong(),start=in.getLong();int count=in.getInt();
  if(epoch<1||seq<1||start<0||count<1||count>RecordingContracts.MAX_PCM_BYTES/2)throw new IllegalArgumentException("Recording frame metadata invalid");
  byte[] digest=new byte[32];in.get(digest);if(in.remaining()!=count*2)throw new IllegalArgumentException("Recording frame PCM size invalid");byte[] pcm=new byte[in.remaining()];in.get(pcm);
  return new RecordingContracts.Chunk(part.toString(),epoch,seq,start,count,HexFormat.of().formatHex(digest),pcm);
 }
}
