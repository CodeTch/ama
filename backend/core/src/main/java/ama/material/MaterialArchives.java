package ama.material;

import ama.canonical.CanonicalValue;
import ama.config.CanonicalJson;
import ama.contract.ConfigArchiveContracts.*;
import java.util.*;
import static ama.canonical.CanonicalValue.*;

/** Deterministic immutable parser declarations shared by installation and parser provenance. */
public final class MaterialArchives {
 public static RawArchive parser(String format){String version=MaterialProfiles.parser(format);byte[] bytes=CanonicalJson.encode(object(field("archiveSchema",string("ama.config-archive.v1")),field("artifactType",string("PARSER_CONFIG")),field("version",string(version)),field("definitionSchema",string("ama.material-parser.v1")),field("definition",object(field("contract",string("ama.material-parser.v1")),field("format",string(format)),field("parserIdentity",string(MaterialParser.PROFILE)),field("unitMapping",string(format.equals("PDF")||format.equals("PPT")?"PAGE":"PARAGRAPH")))),field("dependencies",list())),65536,()->false);return new RawArchive(ArtifactType.PARSER_CONFIG,version,bytes);}
 public static Ref ref(RawArchive archive){return new Ref(archive.claimedArtifactType(),archive.claimedVersion(),HexFormat.of().formatHex(ama.canonical.CanonicalValueCodec.digest().digest(archive.bytes())));}
 private MaterialArchives(){}
}
