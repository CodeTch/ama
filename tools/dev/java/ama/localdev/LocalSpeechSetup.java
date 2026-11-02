package ama.localdev;

import ama.task.ControlledSpeechProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Explicit append-only local simulator profile; fixed harness ports are not reconfigured. */
public final class LocalSpeechSetup {
 public static void main(String[] args)throws Exception {
  if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=3)throw new IllegalArgumentException("Explicit local speech installation required");
  var profile=ControlledSpeechProfile.fromRuntime(Path.of(args[2]));var json=new ObjectMapper();
  try(var in=Objects.requireNonNull(LocalSpeechSetup.class.getResourceAsStream("/ama/transcript/media-parser-v3.json"))){if(!json.readTree(in).path("definition").path("engines").path("modelManifestSha256").asText().equals(profile.sha256()))throw new IllegalStateException("Speech installation profile mismatch");}
  var result=new LinkedHashMap<>(LocalDocumentSetup.install(Path.of(args[0]).toRealPath(),args[1],List.of(new String[]{"media-parser-v3","PARSER_CONFIG",ControlledSpeechProfile.PARSER},new String[]{"media-processing-v3","PROCESSING_CONFIG","ama.import-media-documents.v3"}),"ama.import-media-documents.v3"));
  var recording=LocalDocumentSetup.install(Path.of(args[0]).toRealPath(),result.get("processing.index-sha"),List.of(new String[]{"recording-parser-v1","PARSER_CONFIG",ama.transcript.RecordingTranscriptProfile.PARSER},new String[]{"recording-processing-v1","PROCESSING_CONFIG",ama.transcript.RecordingTranscriptProfile.PROCESSING}),ama.transcript.RecordingTranscriptProfile.PROCESSING);
  result.put("processing.index-sha",recording.get("processing.index-sha"));result.put("minutes.index-sha",recording.get("minutes.index-sha"));
  result.put("processing.recording-config-version",recording.get("processing.config-version"));result.put("processing.recording-config-sha",recording.get("processing.config-sha"));result.put("processing.bounds.recording-source-bytes",Long.toString(ama.transcript.RecordingTranscriptProfile.MAX_BYTES));
  result.put("processing.bounds.recording-batch-deadline-ms","600000");
  result.put("transcript.correction.mode","CONTROLLED_SPEECH");result.put("transcript.correction.installation","CONTROLLED_SPEECH");result.put("transcript.correction.installation-version","1");
  System.out.println(json.writeValueAsString(result));
 }
}
