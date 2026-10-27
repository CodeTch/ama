package ama.storage;
import java.nio.file.*;import java.util.Map;
/** Cross-process fcntl counters shared with the Python frontdoor. Conservative charges precede I/O. */
final class Bl5Budget {
 static void ready(Path root)throws Exception {
  if(Files.exists(root.resolve("stopping.json")))throw new IllegalStateException("B-L5 stopping");var v=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(root.resolve("monitor.json")));long now=System.currentTimeMillis();
  if(!v.path("owner").asText().equals(root.getFileName().toString())||!v.path("status").asText().equals("ADMIT")||now<v.path("observedUtcMillis").asLong()||now>v.path("expiresUtcMillis").asLong())throw new IllegalStateException("Fresh aggregate admission required");
 }
 static synchronized void consume(Path root,String name,long amount,long maximum)throws Exception {
  ready(root);if(!name.matches("[a-z-]+")||amount<0)throw new IllegalArgumentException("Counter shape");Path path=root.resolve("budget-"+name);if(Files.isSymbolicLink(path))throw new IllegalArgumentException("Foreign counter");
  try(var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock()){
   ready(root);if(channel.size()>32)throw new IllegalStateException("Counter size");var buffer=java.nio.ByteBuffer.allocate(32);channel.read(buffer);buffer.flip();String text=java.nio.charset.StandardCharsets.US_ASCII.decode(buffer).toString();long count=text.isEmpty()?0:Long.parseLong(text);if(count<0||count>maximum||amount>maximum-count){stop(root,"BUDGET_"+name);throw new IllegalStateException("B-L5 "+name+" budget exhausted");}channel.position(0);channel.truncate(0);channel.write(java.nio.ByteBuffer.wrap(Long.toString(count+amount).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));channel.force(true);
  }
 }
 static void stop(Path root,String reason)throws Exception {
  try{Files.writeString(root.resolve("stopping.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("owner",root.getFileName().toString(),"reason",reason)),StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException alreadyStopping){}
 }
 static void http(Path root,String kind,String method)throws Exception {http(root,kind,method,"");}
 static void http(Path root,String kind,String method,String path)throws Exception {
  consume(root,"http",2,11000); // Incoming request and its actual downstream forwarding are both charged.
  if(kind.equals("model"))consume(root,"model",2,20);
  if(kind.equals("knowledge")&&method.equals("POST")&&path.equals("/v1/knowledge/publications"))consume(root,"publication",2,10);
 }
}
