package ama.meeting;

import ama.contract.MeetingContracts.SaveInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Versioned, complete, length-prefixed UTF-8 fields; null and empty remain distinct. */
final class MeetingDigests {
    private MeetingDigests() {}
    static String fields(String... fields){return digest(Arrays.asList(fields));}
    static String save(SaveInfo r){
        var fields=new ArrayList<String>();
        Collections.addAll(fields,"meeting-info-v1",r.expectedRevision().toString(),r.name(),r.category(),r.summary(),r.occurredOn()==null?null:r.occurredOn().toString(),Integer.toString(r.rosterDraftIds().size()));
        fields.addAll(r.rosterDraftIds());fields.add(Integer.toString(r.externalRosterNames().size()));fields.addAll(r.externalRosterNames());
        return digest(fields);
    }
    private static String digest(List<String> fields){
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            for(String field:fields){
                if(field==null){out.writeInt(-1);continue;}
                var encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(field));
                byte[] value=new byte[encoded.remaining()];encoded.get(value);out.writeInt(value.length);out.write(value);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException("Malformed Unicode");}
        catch(IOException|java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
