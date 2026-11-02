package ama.governance;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** Deliberately accepts canonical WAV only. Ordinary speech import remains more general. */
public final class GovernanceAudio {
 public static long require(byte[] wav){
  if(wav==null||wav.length<96044||wav.length>960044||(wav.length-44)%2!=0)throw new IllegalArgumentException("3 to 30 second PCM WAV required");
  var b=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
  if(!tag(wav,0,"RIFF")||!tag(wav,8,"WAVE")||!tag(wav,12,"fmt ")||!tag(wav,36,"data")||b.getInt(4)!=wav.length-8||b.getInt(16)!=16||b.getShort(20)!=1||b.getShort(22)!=1||b.getInt(24)!=16000||b.getInt(28)!=32000||b.getShort(32)!=2||b.getShort(34)!=16||b.getInt(40)!=wav.length-44)throw new IllegalArgumentException("Canonical mono 16kHz 16bit WAV required");
  double energy=0;for(int i=44;i<wav.length;i+=2){double v=b.getShort(i)/32768d;energy+=v*v;}if(Math.sqrt(energy/((wav.length-44)/2))<0.005)throw new IllegalArgumentException("Voiced sample required");return (wav.length-44)/2;
 }
 private static boolean tag(byte[] bytes,int offset,String text){return new String(bytes,offset,4,StandardCharsets.US_ASCII).equals(text);}
}
