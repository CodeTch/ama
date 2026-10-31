package ama.localdev;

import ama.db.MysqlDataSources;
import ama.recording.JdbcRecordingPersistence;
import ama.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only evidence tool; private runtime properties never enter its JSON output. */
public final class LocalRecordingReadback {
    public static void main(String[] args)throws Exception {
        if(!"1".equals(System.getenv("AMA_LOCAL_DEV"))||args.length!=2||!System.getenv().getOrDefault("DATABASE_URL","").matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"))throw new IllegalArgumentException("Explicit owned local database required");
        String id=UUID.fromString(args[1]).toString();
        var properties=new Properties();try(var in=Files.newInputStream(Path.of(args[0]))){properties.load(in);}
        var environment=new StandardEnvironment();environment.getPropertySources().addFirst(new PropertiesPropertySource("owned-recording-readback",properties));
        var source=MysqlDataSources.fromEnvironment("DATABASE_");var jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(5);
        try(var definitions=ConfiguredBackends.read(environment)) {
            var catalog=new FileBackendCatalog(source,definitions.backends(),definitions.defaultId());var persistence=new JdbcRecordingPersistence(source,catalog);
            var rows=jdbc.queryForList("SELECT c.part_id,c.seq,c.bytes,c.sha256,c.pcm_storage_key,s.backend_id FROM recording_chunk c JOIN recording_session s ON s.session_id=c.session_id JOIN recording_part p ON p.session_id=c.session_id AND p.part_id=c.part_id WHERE c.session_id=? ORDER BY p.part_no,c.seq LIMIT 4097",id);
            if(rows.isEmpty()||rows.size()>4096)throw new IllegalStateException("Bounded chunk evidence required");
            var chunks=new ArrayList<Map<String,Object>>();
            for(var row:rows) {
                String part=row.get("part_id").toString();long seq=((Number)row.get("seq")).longValue(),bytes=0;var digest=MessageDigest.getInstance("SHA-256");
                try(var input=persistence.readChunk(id,part,seq)){byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))>=0;){bytes+=n;digest.update(buffer,0,n);}}
                String actual=HexFormat.of().formatHex(digest.digest());if(!actual.equals(row.get("sha256"))||bytes!=((Number)row.get("bytes")).longValue())throw new IllegalStateException("Actual PCM mismatch");
                chunks.add(Map.of("partId",part,"seq",seq,"bytes",bytes,"actualSha256",actual,"backendId",row.get("backend_id"),"fileBacked",row.get("pcm_storage_key")!=null));
            }
            System.out.println(new ObjectMapper().writeValueAsString(Map.of("pass",true,"sessionId",id,"chunks",chunks)));
        }
    }
}
