package ama.transcript;
/** Shared shape predicate; a verified input profile is required before time-bearing source metadata is allowed. */
final class TranscriptSegmentProvenance {
 private TranscriptSegmentProvenance(){}
 static boolean timing(String verifiedParser,Long start,Long end,String speaker){return speaker==null&&(start==null&&end==null||MediaTranscriptParser.PROFILE_VERSION.equals(verifiedParser)&&start!=null&&end!=null&&start>=0&&end>start&&end<=7200000);}
}
