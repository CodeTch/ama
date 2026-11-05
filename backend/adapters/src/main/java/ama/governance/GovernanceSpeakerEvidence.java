package ama.governance;
import ama.task.SpeechCheckpointCodec;
import ama.transcript.SpeechPipeline;
import org.springframework.jdbc.core.JdbcTemplate;
/** A manually named raw window is usable only when its retained diarization is unambiguous. */
final class GovernanceSpeakerEvidence {
 static boolean single(JdbcTemplate j,String input,String file,long start,long end){
  var rows=j.queryForList("SELECT payload,payload_sha256 FROM speech_checkpoint WHERE input_id=? AND file_id=? AND stage='DIARIZATION' AND start_sample<=? AND start_sample+sample_count>=? LIMIT 2",input,file,start,end);if(rows.size()!=1)return false;var row=rows.getFirst();if(!(row.get("payload") instanceof byte[] bytes)||!MeetingVoiceSample.sha(bytes).equals(row.get("payload_sha256")))return false;
  try{var result=(SpeechPipeline.Diarization)SpeechCheckpointCodec.decode(SpeechPipeline.Stage.DIARIZATION,bytes);return result.spans().stream().filter(s->s.startSample()<end&&s.endSample()>start).map(ama.contract.SpeechContracts.SpeakerSpan::speakerId).distinct().count()==1;}catch(RuntimeException invalid){return false;}
 }
 private GovernanceSpeakerEvidence(){}
}
