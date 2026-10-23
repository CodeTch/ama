package ama.config.file;
import ama.config.*;
import ama.config.ConfigArchiveIndex.IndexBudget;
import ama.contract.ConfigArchiveContracts.*;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.*;
import java.io.IOException;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.config.json.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

public final class ControlledConfigArchiveSource implements ConfigArchiveSource {
 record Node(ConfigArchiveIndex.Entry entry,Map<String,Ref> dependencies){Node{dependencies=Map.copyOf(dependencies);}}
 public Snapshot open(Path root,String sha,IndexBudget ib,ArchiveReadBudget b,BooleanSupplier c){
  require(ib!=null&&b!=null&&c!=null,INVALID_ARGUMENT);var cancel=ProtectedArchiveFiles.deadline(c);check(cancel);hash(sha);
  try(var files=ProtectedArchiveFiles.open(root,cancel)){
   var capacity=new ProtectedArchiveFiles.Capacity(ib.maxReleaseBytes());var selected=readIndex(files,sha,ib,capacity);var newer=selected;String previous=selected.previousIndexSha256();int generations=1;
   while(previous!=null){
    check(cancel);require(generations<ib.maxGenerations(),BUDGET_EXCEEDED);generations++;
    var older=readIndex(files,previous,ib,capacity);require(older.generation()==newer.generation()-1,PROFILE_UNAVAILABLE);
    var nextEntries=new HashMap<Identity,ConfigArchiveIndex.Entry>();for(var entry:newer.entries())nextEntries.put(entry.ref().identity(),entry);
    for(var entry:older.entries())require(entry.equals(nextEntries.get(entry.ref().identity())),PROFILE_UNAVAILABLE);
    newer=older;previous=older.previousIndexSha256();
   }
   require(selected.entries().size()<=b.maxArtifacts(),BUDGET_EXCEEDED);
   var acquisition=new ProtectedArchiveFiles.Capacity(Math.min(1048576,b.maxTotalBytes()));var state=new ArchiveReadState(b,cancel);var nodes=new LinkedHashMap<Identity,Node>();
   try(var release=files.directory("releases",sha);var objects=files.directory("releases",sha,"objects")){
    files.exactNames(release,Set.of("index.json","objects"),2);var names=new HashSet<String>();for(var entry:selected.entries())names.add(entry.ref().sha256()+".json");files.exactNames(objects,names,ib.maxEntries());
    for(var entry:selected.entries()){
     byte[] raw=files.read(objects,entry.ref().sha256()+".json",entry.sizeBytes(),Math.min(65536,b.maxArtifactBytes()),acquisition);capacity.reserve(raw.length);
     var owned=state.capture(raw,false);nodes.put(entry.ref().identity(),validate(entry,owned,state));
    }
   }
   verifyGraph(nodes,nodes.keySet(),b,state,cancel);check(cancel);
   return new ImmutableSnapshot(root,sha,selected,nodes);
  }catch(Failure safe){throw safe;}catch(IOException|UnsupportedOperationException|SecurityException e){check(cancel);throw fail(PROFILE_UNAVAILABLE);}
 }
 private static ConfigArchiveIndex readIndex(ProtectedArchiveFiles files,String sha,IndexBudget ib,ProtectedArchiveFiles.Capacity capacity)throws IOException{
  try(var release=files.directory("releases",sha)){
   byte[] raw=files.read(release,"index.json",-1,ib.maxIndexBytes(),capacity);
   var owned=ArchiveBytes.capture(raw,ib.maxIndexBytes(),ib.maxIndexBytes(),files.cancel);require(owned.sha256(files.cancel).equals(sha),PROFILE_UNAVAILABLE);
   return new StrictConfigIndexReader().read(raw,ib,files.cancel);
  }
 }
 static Node validate(ConfigArchiveIndex.Entry entry,ArchiveBytes owned,ArchiveReadState state){
  require(owned.size()==entry.sizeBytes()&&owned.sha256(state.cancelled).equals(entry.ref().sha256()),PROFILE_UNAVAILABLE);
  var parsed=new StrictConfigArchiveReader().read(owned,entry.ref().identity(),state);
  require(ConfigSchema.archive(parsed.wire(),state).equals(entry.ref().identity())&&CanonicalJson.matches(parsed.wire(),owned,state.cancelled),PROFILE_UNAVAILABLE);
  var dependencies=ConfigSchema.dependencies(parsed.wire(),state);state.dependencies(dependencies.size());return new Node(entry,dependencies);
 }
 static LinkedHashSet<Identity> verifyGraph(Map<Identity,Node> nodes,Collection<Identity> roots,ArchiveReadBudget b,ArchiveReadState state,BooleanSupplier cancel){
  var seen=new LinkedHashSet<Identity>();for(var root:roots)visit(root,nodes,seen,new HashSet<>(),1,b,state,cancel);return seen;
 }
 private static void visit(Identity identity,Map<Identity,Node> nodes,Set<Identity> seen,Set<Identity> active,int depth,ArchiveReadBudget b,ArchiveReadState state,BooleanSupplier cancel){
  check(cancel);require(depth<=b.maxGraphDepth(),BUDGET_EXCEEDED);require(!active.contains(identity),PROFILE_UNAVAILABLE);var node=nodes.get(identity);require(node!=null,PROFILE_UNAVAILABLE);
  if(!seen.contains(identity)){require(seen.size()<b.maxArtifacts(),BUDGET_EXCEEDED);seen.add(identity);}active.add(identity);
  for(var dep:node.dependencies.values()){check(cancel);var target=nodes.get(dep.identity());require(target!=null&&target.entry.ref().equals(dep),PROFILE_UNAVAILABLE);if(state!=null)state.edges(1);visit(dep.identity(),nodes,seen,active,depth+1,b,state,cancel);}
  active.remove(identity);
 }
 private static final class ImmutableSnapshot implements Snapshot {
  private final Path root;private final String sha;private final ConfigArchiveIndex index;private final Map<Identity,Node> nodes;
  ImmutableSnapshot(Path root,String sha,ConfigArchiveIndex index,Map<Identity,Node> nodes){this.root=root;this.sha=sha;this.index=index;this.nodes=Map.copyOf(nodes);}
  public String indexSha256(){return sha;}public ConfigArchiveIndex index(){return index;}
  public List<RawArchive> loadClosure(List<Ref> roots,ArchiveReadBudget b,long reservation,BooleanSupplier external){
   require(roots!=null&&b!=null&&external!=null&&reservation>=0,INVALID_ARGUMENT);var cancel=ProtectedArchiveFiles.deadline(external);check(cancel);
   require(reservation<=65536&&reservation<=b.maxManifestBytes()&&reservation<=Math.min(1048576,b.maxTotalBytes()),BUDGET_EXCEEDED);require(!roots.isEmpty()&&roots.size()<=b.maxArtifacts(),BUDGET_EXCEEDED);
   var identities=new LinkedHashSet<Identity>();for(var ref:roots){check(cancel);require(ref!=null&&nodes.containsKey(ref.identity())&&nodes.get(ref.identity()).entry.ref().equals(ref),PROFILE_UNAVAILABLE);identities.add(ref.identity());}
   var state=new ArchiveReadState(b,cancel);var closure=verifyGraph(nodes,identities,b,state,cancel);for(var identity:closure)state.dependencies(nodes.get(identity).dependencies.size());long remaining=Math.min(1048576,b.maxTotalBytes())-reservation;long required=0;
   for(var identity:closure){check(cancel);long size=nodes.get(identity).entry.sizeBytes();require(size<=Math.min(65536,b.maxArtifactBytes())&&size<=remaining-required,BUDGET_EXCEEDED);required+=size;}
   // Reserve the complete closure before the first transport byte array. S1 later owns exactly one capture.
   var capacity=new ProtectedArchiveFiles.Capacity(required);var result=new ArrayList<RawArchive>();
   try(var files=ProtectedArchiveFiles.open(root,cancel);var objects=files.directory("releases",sha,"objects")){
    for(var identity:closure){check(cancel);var entry=nodes.get(identity).entry;byte[] raw=files.read(objects,entry.ref().sha256()+".json",entry.sizeBytes(),Math.min(65536,b.maxArtifactBytes()),capacity);
     // SHA over transport directly; never ArchiveBytes -> array. Original startup metadata stays immutable.
     require(digest(raw,cancel).equals(entry.ref().sha256()),PROFILE_UNAVAILABLE);result.add(new RawArchive(identity.artifactType(),identity.version(),raw));
    }
    check(cancel);return List.copyOf(result);
   }catch(Failure safe){throw safe;}catch(IOException|UnsupportedOperationException|SecurityException e){check(cancel);throw fail(PROFILE_UNAVAILABLE);}
  }
 }
 static String digest(byte[] bytes,BooleanSupplier cancel){
  try{var digest=java.security.MessageDigest.getInstance("SHA-256");for(int i=0;i<bytes.length;){check(cancel);int n=Math.min(8192,bytes.length-i);digest.update(bytes,i,n);i+=n;}check(cancel);return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256");}
 }
}
