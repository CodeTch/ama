package ama.identity;

import ama.contract.IdentityContracts.*;
import com.fasterxml.jackson.core.*;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;import java.util.concurrent.*;

/** Explicit fixed-sibling directory. It does not assert company authority or grant meeting access. */
public final class ControlledDirectoryAdapter implements DirectoryPort,AutoCloseable {
 private static final URI FIXED=URI.create("http://127.0.0.1:8792");private static final int MAX_BYTES=1048576;
 private static final JsonFactory JSON=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(16384).maxNameLength(64).maxNumberLength(12).build()).build();
 private final URI origin;private final Duration deadline;private final HttpClient client;private volatile boolean closed;
 private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"ama-directory-deadline");t.setDaemon(true);return t;});
 public ControlledDirectoryAdapter(URI origin,Duration deadline){this(origin,deadline,client(origin,deadline));}
 ControlledDirectoryAdapter(URI origin,Duration deadline,HttpClient client){validate(origin,deadline);this.origin=origin;this.deadline=deadline;this.client=Objects.requireNonNull(client);}
 private static void validate(URI origin,Duration deadline){if(!FIXED.equals(origin)||deadline==null||deadline.isNegative()||deadline.isZero()||deadline.compareTo(Duration.ofSeconds(3))>0)throw new IllegalArgumentException("Explicit fixed harness directory and bounded deadline required");}
 private static HttpClient client(URI origin,Duration deadline){validate(origin,deadline);return HttpClient.newBuilder().connectTimeout(deadline).followRedirects(HttpClient.Redirect.NEVER).build();}
 @Override public PeoplePage search(String query,String cursor,int limit){return search(query,cursor,limit,null);}
 @Override public PeoplePage search(String query,String cursor,int limit,String departmentId){
  if(query==null||query.length()>255||limit<1||limit>100||cursor!=null&&(cursor.isEmpty()||cursor.length()>4096))throw new IllegalArgumentException("Invalid directory query");
  if(departmentId!=null)ama.contract.IdentityContracts.stableId(departmentId);
  String path="/v1/directory/people?query="+encode(query)+"&limit="+limit+(cursor==null?"":"&cursor="+encode(cursor))+(departmentId==null?"":"&departmentId="+encode(departmentId));
  return read(path,(bytes,end)->parse(bytes,cursor,limit,end));
 }
 @Override public Departments departments(){return read("/v1/directory/departments",ControlledDirectoryAdapter::parseDepartments);}
 @FunctionalInterface private interface Decode<T>{T parse(byte[] bytes,long end)throws IOException;}
 private <T> T read(String path,Decode<T> decode){
  if(closed)throw unavailable();long end=System.nanoTime()+deadline.toNanos();
  var request=HttpRequest.newBuilder(origin.resolve(path)).timeout(deadline).header("Accept","application/json").GET().build();
  try{
   var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(var body=response.body()){
    if(body==null||response.statusCode()!=200||!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).matches("application/json(?:\\s*;.*)?"))throw unavailable();
    long remaining=end-System.nanoTime();if(remaining<=0)throw unavailable();
    var cutoff=timer.schedule(()->{try{body.close();}catch(IOException ignored){}},remaining,TimeUnit.NANOSECONDS);
    try{byte[] bytes=body.readNBytes(MAX_BYTES+1);check(end);if(bytes.length>MAX_BYTES)throw unavailable();return decode.parse(bytes,end);}finally{cutoff.cancel(false);}
   }
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw unavailable();}catch(IOException|IllegalArgumentException|RejectedExecutionException failure){throw unavailable();}
 }
 private static Departments parseDepartments(byte[] bytes,long end)throws IOException {
  try(var p=JSON.createParser(bytes)){
   expect(p.nextToken(),JsonToken.START_OBJECT);expect(p.nextToken(),JsonToken.FIELD_NAME);if(!p.currentName().equals("items"))throw unavailable();expect(p.nextToken(),JsonToken.START_ARRAY);
   var items=new ArrayList<Department>();var ids=new HashSet<String>();
   while(p.nextToken()!=JsonToken.END_ARRAY){check(end);if(items.size()>=1000)throw unavailable();expect(p.currentToken(),JsonToken.START_OBJECT);var fields=new HashSet<String>();String id=null,name=null,parent=null;Boolean active=null;
    while(p.nextToken()!=JsonToken.END_OBJECT){expect(p.currentToken(),JsonToken.FIELD_NAME);String field=p.currentName();if(!fields.add(field))throw unavailable();var token=p.nextToken();switch(field){
     case "id"->id=text(p,token,1020);case "name"->name=text(p,token,16384);case "parentId"->{if(token!=JsonToken.VALUE_NULL)parent=text(p,token,1020);}case "active"->{if(token!=JsonToken.VALUE_TRUE&&token!=JsonToken.VALUE_FALSE)throw unavailable();active=token==JsonToken.VALUE_TRUE;}default->throw unavailable();
    }}
    if(!fields.equals(Set.of("id","name","parentId","active"))||name==null||name.isBlank()||active==null)throw unavailable();ama.contract.IdentityContracts.stableId(id);if(parent!=null)ama.contract.IdentityContracts.stableId(parent);if(!ids.add(id)||id.equals(parent))throw unavailable();items.add(new Department(id,name,parent,active));
   }
   expect(p.nextToken(),JsonToken.END_OBJECT);if(p.nextToken()!=null)throw unavailable();check(end);return new Departments(items);
  }
 }
 private static PeoplePage parse(byte[] bytes,String cursor,int limit,long end)throws IOException {
  try(var p=JSON.createParser(bytes)){
   expect(p.nextToken(),JsonToken.START_OBJECT);var fields=new HashSet<String>();List<Person> people=null;String next=null;
   while(p.nextToken()!=JsonToken.END_OBJECT){check(end);expect(p.currentToken(),JsonToken.FIELD_NAME);String field=p.currentName();if(!fields.add(field))throw unavailable();var token=p.nextToken();
    if(field.equals("items")){expect(token,JsonToken.START_ARRAY);var rows=new ArrayList<Person>();var ids=new HashSet<String>();while(p.nextToken()!=JsonToken.END_ARRAY){check(end);if(rows.size()>=limit)throw unavailable();var person=person(p);if(!ids.add(person.stableId()))throw unavailable();rows.add(person);}people=List.copyOf(rows);}
    else if(field.equals("nextCursor")){if(token!=JsonToken.VALUE_NULL){next=text(p,token,4096);if(next.isEmpty())throw unavailable();}}
    else throw unavailable();
   }
   if(!fields.equals(Set.of("items","nextCursor"))||people==null||p.nextToken()!=null||next!=null&&(next.equals(cursor)||people.size()!=limit))throw unavailable();check(end);return new PeoplePage(people,next);
  }
 }
 private static Person person(JsonParser p)throws IOException {
  expect(p.currentToken(),JsonToken.START_OBJECT);var fields=new HashSet<String>();String id=null,name=null,department=null,title=null;Boolean active=null;
  while(p.nextToken()!=JsonToken.END_OBJECT){expect(p.currentToken(),JsonToken.FIELD_NAME);String field=p.currentName();if(!fields.add(field))throw unavailable();var token=p.nextToken();switch(field){
   case "stableId"->id=text(p,token,1020);case "displayName"->name=text(p,token,16384);case "department"->{if(token!=JsonToken.VALUE_NULL)department=text(p,token,16384);}
   case "title"->{if(token!=JsonToken.VALUE_NULL)title=text(p,token,16384);}
   case "active"->{if(token!=JsonToken.VALUE_TRUE&&token!=JsonToken.VALUE_FALSE)throw unavailable();active=token==JsonToken.VALUE_TRUE;}
   case "email"->{if(token!=JsonToken.VALUE_NULL)text(p,token,16384);}default->throw unavailable();
  }}
  if(!fields.containsAll(Set.of("stableId","displayName","department","active"))||id==null||!id.equals(id.trim())||name==null||name.isBlank()||active==null)throw unavailable();ama.contract.IdentityContracts.stableId(id);return new Person(id,name,department,active,title);
 }
 private static String text(JsonParser p,JsonToken token,int maximum)throws IOException {expect(token,JsonToken.VALUE_STRING);String text=p.getText();if(text.getBytes(StandardCharsets.UTF_8).length>maximum)throw unavailable();for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw unavailable();}else if(Character.isLowSurrogate(c))throw unavailable();}return text;}
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
 private static void expect(JsonToken actual,JsonToken expected){if(actual!=expected)throw unavailable();}
 private static void check(long end){if(Thread.currentThread().isInterrupted()||System.nanoTime()>=end)throw unavailable();}
 private static ProviderUnavailableException unavailable(){return new ProviderUnavailableException();}
 @Override public void close(){closed=true;try{client.close();}finally{timer.shutdownNow();}}
}
