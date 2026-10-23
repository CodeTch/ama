package ama.config.file;
import ama.config.ConfigArchiveIndex.IndexBudget;
import ama.contract.ConfigArchiveContracts.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.canonical.CanonicalValueCodec;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class ControlledArchiveReleaseWriter {
 /** Explicit offline administrative operation only. Root must already exist and be protected by deployment. */
 public String publish(Path root,String previousIndexSha,List<RawArchive> appended,IndexBudget ib,ArchiveReadBudget b,BooleanSupplier c){
  require(appended!=null&&ib!=null&&b!=null&&c!=null,INVALID_ARGUMENT);var cancel=ProtectedArchiveFiles.deadline(c);check(cancel);
  require(!appended.isEmpty()&&appended.size()<=Math.min(ib.maxEntries(),b.maxArtifacts()),BUDGET_EXCEEDED);if(previousIndexSha!=null)hash(previousIndexSha);
  try(var files=ProtectedArchiveFiles.open(root,cancel);var lockChannel=files.root.newByteChannel(files.relative(".publish.lock"),Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS))){
   require(lockChannel instanceof FileChannel&&ProtectedArchiveFiles.attributes(files.root,files.relative(".publish.lock")).isRegularFile(),PROFILE_UNAVAILABLE);
   try(var lock=((FileChannel)lockChannel).tryLock()){
    require(lock!=null,PROFILE_UNAVAILABLE);check(cancel);var state=new ArchiveReadState(b,cancel);var owned=new LinkedHashMap<Identity,ArchiveBytes>();var entries=new LinkedHashMap<Identity,ConfigArchiveIndex.Entry>();long generation=1,ownedBytes=0;
    if(previousIndexSha!=null){
     var previous=new ControlledConfigArchiveSource().open(root,previousIndexSha,ib,b,cancel);require(previous.index().generation()<Long.MAX_VALUE,PROFILE_UNAVAILABLE);generation=previous.index().generation()+1;
     require(generation<=ib.maxGenerations(),BUDGET_EXCEEDED);
     var refs=new ArrayList<Ref>();for(var entry:previous.index().entries())refs.add(entry.ref());
     for(var raw:previous.loadClosure(refs,b,0,cancel)){check(cancel);require(raw.bytes().length<=Math.min(1048576,b.maxTotalBytes())-ownedBytes,BUDGET_EXCEEDED);ownedBytes+=raw.bytes().length;var bytes=state.capture(raw.bytes(),false);owned.put(raw.claimedIdentity(),bytes);}
     for(var entry:previous.index().entries())entries.put(entry.ref().identity(),entry);
    }
    // Own caller arrays once, after cumulative reservation, and write those same immutable carriers.
    for(var raw:appended){
     check(cancel);require(raw!=null&&!entries.containsKey(raw.claimedIdentity())&&!owned.containsKey(raw.claimedIdentity()),PROFILE_UNAVAILABLE);
     require(owned.size()<Math.min(ib.maxEntries(),b.maxArtifacts())&&raw.bytes().length<=65536,BUDGET_EXCEEDED);
     require(raw.bytes().length<=Math.min(1048576,b.maxTotalBytes())-ownedBytes,BUDGET_EXCEEDED);ownedBytes+=raw.bytes().length;
     var bytes=state.capture(raw.bytes(),false);owned.put(raw.claimedIdentity(),bytes);entries.put(raw.claimedIdentity(),new ConfigArchiveIndex.Entry(new Ref(raw.claimedArtifactType(),raw.claimedVersion(),bytes.sha256(cancel)),bytes.size()));
    }
    long total=0;for(var bytes:owned.values()){require(bytes.size()<=Math.min(1048576,b.maxTotalBytes())-total,BUDGET_EXCEEDED);total+=bytes.size();}
    var nodes=new LinkedHashMap<Identity,ControlledConfigArchiveSource.Node>();for(var entry:entries.values()){check(cancel);nodes.put(entry.ref().identity(),ControlledConfigArchiveSource.validate(entry,owned.get(entry.ref().identity()),state));}
    ControlledConfigArchiveSource.verifyGraph(nodes,nodes.keySet(),b,state,cancel);
    var sorted=new ArrayList<>(entries.values());sorted.sort((a,z)->{int order=a.ref().artifactType().name().compareTo(z.ref().artifactType().name());return order!=0?order:CanonicalValueCodec.compareUtf8(a.ref().version(),z.ref().version(),cancel);});
    var index=new ConfigArchiveIndex(generation,previousIndexSha,sorted);byte[] indexBytes=CanonicalJson.encode(index.wire(),ib.maxIndexBytes(),cancel);require(indexBytes.length<=ib.maxReleaseBytes()-total,BUDGET_EXCEEDED);
    String sha=ControlledConfigArchiveSource.digest(indexBytes,cancel);
    // JDK has no handle-relative mkdir/fsync. These path operations are bounded to protected admin paths,
    // checked before/after by stable file keys; they are not safe against a malicious privileged administrator.
    files.verifyPath(root,files.rootKey);Path releases=root.resolve("releases");
    try{Files.createDirectory(releases);}catch(FileAlreadyExistsException existing){/* Verify exact regular directory below. */}
    Object releasesKey=files.directoryKey(releases);files.verifyPath(releases,releasesKey);
    try(var releaseHandle=files.directory("releases")){
     if(previousIndexSha==null)require(!releaseHandle.iterator().hasNext(),PROFILE_UNAVAILABLE);
     try{ProtectedArchiveFiles.attributes(releaseHandle,files.relative(sha));throw fail(PROFILE_UNAVAILABLE);}catch(NoSuchFileException absent){/* Never overwrite an existing release. */}
     String stageName=".staging-"+UUID.randomUUID();Path stage=releases.resolve(stageName);Files.createDirectory(stage);Object stageKey=files.directoryKey(stage);files.verifyPath(stage,stageKey);
     Path objects=stage.resolve("objects");Files.createDirectory(objects);Object objectsKey=files.directoryKey(objects);files.verifyPath(objects,objectsKey);
     try(var stageHandle=releaseHandle.newDirectoryStream(files.relative(stageName),LinkOption.NOFOLLOW_LINKS);var objectHandle=stageHandle.newDirectoryStream(files.relative("objects"),LinkOption.NOFOLLOW_LINKS)){
      for(var entry:sorted){check(cancel);var bytes=owned.get(entry.ref().identity());try(var input=bytes.openStream()){files.write(objectHandle,entry.ref().sha256()+".json",input,bytes.size());}}
      files.write(stageHandle,"index.json",new ByteArrayInputStream(indexBytes),indexBytes.length);
      files.forceDirectory(objects,objectsKey);files.forceDirectory(stage,stageKey);files.forceDirectory(releases,releasesKey);files.forceDirectory(root,files.rootKey);
      check(cancel);files.verifyPath(stage,stageKey);files.verifyPath(releases,releasesKey);
      // SecureDirectoryStream.move has ATOMIC_MOVE semantics, or fails on this provider.
      releaseHandle.move(files.relative(stageName),releaseHandle,files.relative(sha));
      files.forceDirectory(releases,releasesKey);check(cancel);return sha;
     }
    }
   }
  }catch(Failure safe){throw safe;}catch(IOException|UnsupportedOperationException|SecurityException|OverlappingFileLockException e){check(cancel);throw fail(PROFILE_UNAVAILABLE);}
 }
}
