package ama.localdev;

import ama.config.ConfigArchiveIndex;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.storage.ManagedBackendId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;

/** Explicit local administration. Publishes only the existing TXT processing profiles. */
public final class LocalProcessingSetup {
    public static void main(String[] args) throws Exception {
        if (!"1".equals(System.getenv("AMA_LOCAL_DEV")) || args.length != 5)
            throw new IllegalArgumentException("Explicit local processing setup required");
        Path archive = Path.of(args[0]).toRealPath(), receipt = archive.resolveSibling("processing.json");
        var read = ama.minutes.ControlledMinutesAdmin.READ;
        var index = ama.minutes.ControlledMinutesAdmin.INDEX;
        String pin;
        if (Files.exists(receipt)) {
            pin = new ObjectMapper().readTree(Files.readString(receipt)).get("indexSha").asText();
        } else {
            var raw = new ArrayList<RawArchive>();
            byte[] codec = ama.config.CanonicalJson.encode(object(
                field("archiveSchema",string("ama.config-archive.v1")),field("artifactType",string("CANONICAL_CODEC")),
                field("definition",object(field("contract",string("ama.canonical-value.v1")))),
                field("definitionSchema",string("ama.profile-contract.v1")),field("dependencies",new Sequence(List.of())),
                field("version",string("ama.canonical-value.v1"))),65536,()->false);
            raw.add(new RawArchive(ArtifactType.CANONICAL_CODEC,"ama.canonical-value.v1",codec));
            String codecSha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(codec));
            byte[] confirmation=ama.config.CanonicalJson.encode(object(
                field("archiveSchema",string("ama.config-archive.v1")),field("artifactType",string("CONFIRMATION_HASH")),
                field("definition",object(field("contract",string("ama.transcript-confirmation.v1")))),
                field("definitionSchema",string("ama.profile-contract.v1")),field("dependencies",new Sequence(List.of(object(
                    field("ref",object(field("artifactType",string("CANONICAL_CODEC")),field("sha256",string(codecSha)),field("version",string("ama.canonical-value.v1")))),field("slot",string("CANONICAL_CODEC")))))),
                field("version",string("ama.transcript-confirmation.v1"))),65536,()->false);
            raw.add(new RawArchive(ArtifactType.CONFIRMATION_HASH,"ama.transcript-confirmation.v1",confirmation));
            for(String name:List.of("parser","processing1")) {
                try(var in=LocalProcessingSetup.class.getResourceAsStream("/ama/minutes/controlled/"+name+".json")) {
                    if(in==null)throw new IllegalStateException("Existing processing resource missing");
                    raw.add(new RawArchive(name.equals("parser")?ArtifactType.PARSER_CONFIG:ArtifactType.PROCESSING_CONFIG,
                        name.equals("parser")?"ama.strict-utf8-lines.v1":"b06.controlled.import-txt.1",in.readNBytes(65537)));
                }
            }
            pin=new ControlledArchiveReleaseWriter().publish(archive,null,raw,index,read,()->false);
        }
        var snapshot=new ControlledConfigArchiveSource().open(archive,pin,index,read,()->false);
        var processing=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref)
            .filter(r->r.artifactType()==ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
        snapshot.loadClosure(List.of(processing),read,0,()->false);
        var result=Map.of("indexSha",pin,"configVersion",processing.version(),"configSha",processing.sha256(),
            "backendId",new ManagedBackendId.Local(args[1],args[2],args[3],args[4]).id());
        String json=new ObjectMapper().writeValueAsString(result);
        if(!Files.exists(receipt))Files.writeString(receipt,json,StandardOpenOption.CREATE_NEW);
        System.out.println(json);
    }
}
