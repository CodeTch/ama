package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import ama.canonical.CanonicalValue;
public interface ConfigArchiveReader {
 record ParsedArchive(Identity identity,String rawSha256,CanonicalValue.ObjectValue wire){}
 ParsedArchive read(ArchiveBytes bytes,Identity expected,ArchiveReadState state);
}
