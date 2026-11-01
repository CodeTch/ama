package ama.meeting;

import ama.access.MeetingAccess.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Complete, bounded permission dependencies of all retained material versions. Metadata only;
 * a captured set does not authorize either the originating meeting or the consumer's contents. */
public final class MaterialAuthorityDependencies {
 private MaterialAuthorityDependencies(){}
 public record Item(String provider,String library,String document,String version){
  public Item{if(!id(provider)||!id(library)||!id(document)||version==null||!version.matches("[0-9a-f]{64}"))throw new AuthorityUnavailableException();}
 }
 public record Snapshot(List<Item> items,List<String> origins,String fingerprint){public Snapshot{items=List.copyOf(items);origins=List.copyOf(origins);}}
 private record Node(String meeting,int depth){}
 public static Snapshot capture(JdbcTemplate j,String meeting){return capture(j,List.of(new Node(meeting,0)),List.of());}
 private static Snapshot capture(JdbcTemplate j,List<Node> roots,Collection<Item> added){
  var todo=new ArrayDeque<Node>(roots);var visited=new HashSet<String>();var origins=new TreeSet<String>();
  var items=new TreeSet<Item>(Comparator.comparing(Item::provider).thenComparing(Item::library).thenComparing(Item::document).thenComparing(Item::version));items.addAll(added);int edges=added.size();for(var root:roots)if(root.depth()>0)origins.add(root.meeting());
  while(!todo.isEmpty()){
   var node=todo.removeFirst();if(!visited.add(node.meeting()))continue;if(visited.size()>64||node.depth()>16)throw new AuthorityUnavailableException();
   var rows=j.queryForList("SELECT r.provider_id,r.external_id,COALESCE(k.library_id,p.library_id) library_id,IF(OCTET_LENGTH(v.external_version)=64,v.external_version,NULL) external_version,d.publication_id,m.id source_meeting,m.lifecycle,m.governance_state,d.authority_state mapping_state FROM material_reference r LEFT JOIN material_revision v ON v.reference_id=r.id LEFT JOIN material_knowledge_location k ON k.reference_id=r.id LEFT JOIN external_document_mapping d ON d.provider_id=r.provider_id AND d.external_document_id=r.external_id LEFT JOIN publication p ON p.id=d.publication_id AND p.state='SUCCEEDED' LEFT JOIN meeting m ON m.id=p.meeting_id AND m.final_confirmation_id=p.confirmation_id WHERE r.meeting_id=? AND r.kind='KNOWLEDGE' ORDER BY r.id,v.revision LIMIT 257",node.meeting());
   if((edges+=rows.size())>256)throw new AuthorityUnavailableException();
   for(var row:rows){
    items.add(new Item(string(row,"provider_id"),string(row,"library_id"),string(row,"external_id"),string(row,"external_version")));
    if(items.size()>64)throw new AuthorityUnavailableException();
    if(row.get("publication_id")!=null){
     if(!(row.get("source_meeting") instanceof String origin)||!"ACTIVE".equals(row.get("lifecycle"))||!"ACTIVE".equals(row.get("governance_state"))||!"ACTIVE".equals(row.get("mapping_state")))throw new AccessDeniedException();
     origins.add(origin);todo.addLast(new Node(origin,node.depth()+1));
    }
   }
  }
  return new Snapshot(List.copyOf(items),List.copyOf(origins),fingerprint(items));
 }
 public static Snapshot captureAdditional(JdbcTemplate j,String meeting,Collection<Item> added){
  if(added==null||added.size()>64)throw new AuthorityUnavailableException();var roots=new ArrayList<Node>();roots.add(new Node(meeting,0));
  for(var item:added){var rows=j.queryForList("SELECT d.publication_id,m.id source_meeting,m.lifecycle,m.governance_state,d.authority_state mapping_state,p.library_id FROM external_document_mapping d LEFT JOIN publication p ON p.id=d.publication_id AND p.state='SUCCEEDED' LEFT JOIN meeting m ON m.id=p.meeting_id AND m.final_confirmation_id=p.confirmation_id WHERE d.provider_id=? AND d.external_document_id=? LIMIT 2",item.provider(),item.document());if(rows.size()>1)throw new AuthorityUnavailableException();if(!rows.isEmpty()){var row=rows.getFirst();if(!(row.get("source_meeting") instanceof String origin)||!"ACTIVE".equals(row.get("lifecycle"))||!"ACTIVE".equals(row.get("governance_state"))||!"ACTIVE".equals(row.get("mapping_state"))||!item.library().equals(row.get("library_id")))throw new AccessDeniedException();roots.add(new Node(origin,1));}}
  return capture(j,roots,added);
 }
 public static Snapshot withAdditional(Snapshot current,Collection<Item> added){
  var all=new TreeSet<Item>(Comparator.comparing(Item::provider).thenComparing(Item::library).thenComparing(Item::document).thenComparing(Item::version));all.addAll(current.items());all.addAll(added);if(all.size()>64)throw new AuthorityUnavailableException();return new Snapshot(List.copyOf(all),current.origins(),fingerprint(all));
 }
 private static String fingerprint(Collection<Item> items){
  try{var digest=MessageDigest.getInstance("SHA-256");digest.update("ama.material-authority.dependencies.v1\n".getBytes(StandardCharsets.US_ASCII));
   for(var item:items)for(String field:List.of(item.provider(),item.library(),item.document(),item.version())){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());
  }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private static String string(Map<String,Object> row,String field){return row.get(field) instanceof String value?value:null;}
 private static boolean id(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{1,128}");}
}
