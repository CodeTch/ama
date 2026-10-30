package ama.localdev;
import ama.transcript.MediaTranscriptParser;import com.fasterxml.jackson.databind.ObjectMapper;import java.nio.file.*;import java.util.*;
/** Append a distinct media processing installation only after the owned offline engine proof matches the archived profile. */
public final class LocalMediaSetup {
 public static void main(String[] args)throws Exception{
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||(args.length!=3&&args.length!=4))throw new IllegalArgumentException("Explicit local media installation required");
  String version=args.length==4?args[3]:"v2";if(!Set.of("v1","v2").contains(version))throw new IllegalArgumentException("Explicit media profile version required");
  var mapper=new ObjectMapper();var engine=MediaTranscriptParser.fromRuntime(Path.of(args[2]));String expected;
  try(var input=Objects.requireNonNull(LocalMediaSetup.class.getResourceAsStream("/ama/transcript/media-parser-"+version+".json"))){expected=mapper.readTree(input).path("definition").path("engines").path("modelManifestSha256").textValue();}
  if(!engine.manifestSha256().equals(expected))throw new IllegalStateException("Media installation profile mismatch");
  var result=LocalDocumentSetup.install(Path.of(args[0]).toRealPath(),args[1],List.of(new String[]{"media-parser-"+version,"PARSER_CONFIG","ama.media-documents."+version},new String[]{"media-processing-"+version,"PROCESSING_CONFIG","ama.import-media-documents."+version}),"ama.import-media-documents."+version);
  System.out.println(mapper.writeValueAsString(result));
 }
}
