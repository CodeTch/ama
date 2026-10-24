package ama.config;
import ama.contract.ConfigArchiveContracts.*;
import ama.canonical.CanonicalValue;
public interface ProcessingManifestReader {CanonicalValue.ObjectValue read(ArchiveBytes bytes,ArchiveReadState state);}
