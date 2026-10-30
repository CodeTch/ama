package ama.transcript;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
/** Fixture driver tests the process/result boundary only, never claims real speech inference. */
class MediaTranscriptParserTest {
 @TempDir Path root;final UUID file=UUID.fromString("00000000-0000-4000-8000-000000000001");
 final TranscriptDocumentParser.ParseBudget budget=new TranscriptDocumentParser.ParseBudget(1024,100,10);
 String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 byte[] wav(){return "RIFF0000WAVEunit boundary bytes".getBytes(StandardCharsets.UTF_8);}
 MediaTranscriptParser.Config config(String body)throws Exception{Path manifest=root.resolve("model.json");Files.writeString(manifest,"{\"unitBoundary\":true}");String hash=sha(Files.readAllBytes(manifest));Path script=root.resolve("driver.py");Files.writeString(script,"import json,time\n"+body.replace("MODEL_HASH",hash));return new MediaTranscriptParser.Config(Path.of("/usr/bin/python3"),script,root,manifest,hash,2000);}
 String result(){return "print(json.dumps({'schema':'ama.local-asr-result.v1','modelManifestSha256':'MODEL_HASH','language':'en','durationMs':1200,'decodedUtf16CodeUnits':8,'segments':[{'text':'fixture','startMs':100,'endMs':1100}]}))\n";}
 @Test void processResultKeepsOriginalHashMeasuredBytesTimesAndStableIds()throws Exception{var parser=new MediaTranscriptParser(config(result()));byte[] original=wav();var first=parser.parseMeasured(new ByteArrayInputStream(original),file,sha(original),budget,()->false);var second=parser.parseMeasured(new ByteArrayInputStream(original),file,sha(original),budget,()->false);assertEquals(first,second);assertEquals(MediaTranscriptParser.PROFILE_VERSION,first.result().parserProfileVersion());assertEquals(sha(original),first.result().sourceSha256());assertEquals(original.length,first.usage().sourceBytes());assertEquals(8,first.usage().decodedUtf16CodeUnits());var s=first.result().segments().getFirst();assertEquals(100,s.startMs());assertEquals(1100,s.endMs());assertEquals("fixture",s.text());assertNull(s.speakerId());}
 @Test void falseProofWrongTimesUnderstatedUnitsAndEmptyResultFailClosed()throws Exception{for(String bad:List.of(result().replace("MODEL_HASH","0".repeat(64)),result().replace("'endMs':1100","'endMs':99"),result().replace("'decodedUtf16CodeUnits':8","'decodedUtf16CodeUnits':1"),result().replace("[{'text':'fixture','startMs':100,'endMs':1100}]","[]"))){var parser=new MediaTranscriptParser(config(bad));assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false));}}
 @Test void originalHashBytesAndOutputLimitsCannotBeBypassedByDriver()throws Exception{var parser=new MediaTranscriptParser(config(result()));assertEquals(TranscriptDocumentParser.ErrorCode.SOURCE_HASH_MISMATCH,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(new ByteArrayInputStream(wav()),file,"0".repeat(64),budget,()->false)).code());assertEquals(TranscriptDocumentParser.ErrorCode.SOURCE_BYTES_EXCEEDED,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(new ByteArrayInputStream(wav()),file,sha(wav()),new TranscriptDocumentParser.ParseBudget(5,100,10),()->false)).code());assertEquals(TranscriptDocumentParser.ErrorCode.UTF16_CODE_UNITS_EXCEEDED,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(new ByteArrayInputStream(wav()),file,sha(wav()),new TranscriptDocumentParser.ParseBudget(1024,4,10),()->false)).code());}
 @Test void cancellationStopsOnlyTheSpawnedDriverAndPreservesCallerSource()throws Exception{var parser=new MediaTranscriptParser(config("time.sleep(30)\n"+result()));var cancel=new AtomicBoolean();boolean[] closed={false};var input=new ByteArrayInputStream(wav()){public void close(){closed[0]=true;}};var thread=new Thread(()->{try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();}cancel.set(true);});long start=System.nanoTime();thread.start();try{assertEquals(TranscriptDocumentParser.ErrorCode.CANCELLED,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(input,file,sha(wav()),budget,cancel::get)).code());assertTrue((System.nanoTime()-start)/1000000<4000);assertFalse(closed[0]);}finally{thread.join();}}
 @Test void nonzeroDriverExitCannotClaimSuccessfulInference()throws Exception{var parser=new MediaTranscriptParser(config(result()+"raise SystemExit(1)\n"));assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parse(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false));}
 @Test void authenticatedDocumentNameCannotBeMistakenForAudioHeader()throws Exception{var parser=new MediaTranscriptParser(config(result()));byte[] txt="ID3会议文本\n真正的文本段落".getBytes(StandardCharsets.UTF_8);var value=parser.parseMeasured(new ByteArrayInputStream(txt),file,sha(txt),budget,()->false,"会议.TXT");assertEquals("ID3会议文本",value.result().segments().getFirst().text());assertNull(value.result().segments().getFirst().startMs());}
 @Test void declaredMovWithoutModernFtypStillReachesRealDecoder()throws Exception{var parser=new MediaTranscriptParser(config(result()));byte[] mov="0000wide0000mdat older movie header".getBytes(StandardCharsets.UTF_8);var value=parser.parseMeasured(new ByteArrayInputStream(mov),file,sha(mov),budget,()->false,"older.MOV");assertEquals("fixture",value.result().segments().getFirst().text());assertEquals(100,value.result().segments().getFirst().startMs());}


 @Test void authenticatedExtraFormatsReachTheDeclaredDecoderRatherThanPlainTextFallback()throws Exception{
  for(String format:List.of("AAC","FLAC","OGG","M4V","AVI","SRT","VTT")){
   var parser=new MediaTranscriptParser(config("import sys\nassert sys.argv[sys.argv.index('--format')+1]=='"+format+"'\nassert '--max-source-bytes' in sys.argv\n"+result()));
   byte[] original="source boundary bytes".getBytes(StandardCharsets.UTF_8);
   var value=parser.parseMeasured(new ByteArrayInputStream(original),file,sha(original),budget,()->false,"会议."+format.toLowerCase(Locale.ROOT));
   assertEquals(sha(original),value.result().sourceSha256());assertEquals(original.length,value.usage().sourceBytes());assertEquals(100,value.result().segments().getFirst().startMs());
  }
 }
 @Test void subtitleFailuresRetainSpecificSafeDiagnosticsAndNeverBecomePlainText()throws Exception{
  for(var code:List.of(TranscriptDocumentParser.ErrorCode.INVALID_UTF8,TranscriptDocumentParser.ErrorCode.INVALID_TEXT,TranscriptDocumentParser.ErrorCode.SOURCE_BYTES_EXCEEDED)){
   var parser=new MediaTranscriptParser(config("print('{\"error\":\""+code.name()+"\"}')\nraise SystemExit(2)\n"));
   assertEquals(code,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parseMeasured(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false,"meeting.srt")).code());
  }
 }
 @Test void legacyProfileKeepsItsVersionStableIdsAndOriginalFormatBoundary()throws Exception{
  var parser=new MediaTranscriptParser(config(result()),MediaTranscriptParser.LEGACY_PROFILE_VERSION);
  var value=parser.parseMeasured(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false,"original.wav");
  assertEquals("ama.media-documents.v1",value.result().parserProfileVersion());
  assertEquals(TranscriptDocumentParser.ErrorCode.INVALID_ARGUMENT,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parseMeasured(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false,"extra.flac")).code());
  var current=new MediaTranscriptParser(config(result())).parseMeasured(new ByteArrayInputStream(wav()),file,sha(wav()),budget,()->false,"original.wav");
  assertNotEquals(value.result().segments().getFirst().stableId(),current.result().segments().getFirst().stableId());
 }
 @Test void hashOverflowAndCancellationAreCheckedBeforeSubtitleDriver()throws Exception{
  var parser=new MediaTranscriptParser(config("raise RuntimeError('must never invoke')\n"));byte[] bytes="1\n00:00:01,000 --> 00:00:02,000\nActual cue".getBytes(StandardCharsets.UTF_8);
  assertEquals(TranscriptDocumentParser.ErrorCode.SOURCE_HASH_MISMATCH,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parseMeasured(new ByteArrayInputStream(bytes),file,"0".repeat(64),budget,()->false,"meeting.srt")).code());
  assertEquals(TranscriptDocumentParser.ErrorCode.SOURCE_BYTES_EXCEEDED,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parseMeasured(new ByteArrayInputStream(bytes),file,sha(bytes),new TranscriptDocumentParser.ParseBudget(5,100,10),()->false,"meeting.vtt")).code());
  assertEquals(TranscriptDocumentParser.ErrorCode.CANCELLED,assertThrows(TranscriptDocumentParser.ParseException.class,()->parser.parseMeasured(new ByteArrayInputStream(bytes),file,sha(bytes),budget,()->true,"meeting.srt")).code());
 }

}
