package ama.identity;

import ama.contract.IdentityContracts.ProviderUnavailableException;
import java.io.*;import java.net.URI;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

class ControlledDirectoryAdapterTest {
 static final URI ORIGIN=URI.create("http://127.0.0.1:8792");
 static final class Client extends HttpClient {
  final HttpResponse<InputStream> response;HttpRequest request;Client(HttpResponse<InputStream> response){this.response=response;}
  @Override public Optional<java.net.CookieHandler> cookieHandler(){return Optional.empty();}
  @Override public Optional<Duration> connectTimeout(){return Optional.of(Duration.ofSeconds(3));}
  @Override public Redirect followRedirects(){return Redirect.NEVER;}
  @Override public Optional<java.net.ProxySelector> proxy(){return Optional.empty();}
  @Override public javax.net.ssl.SSLContext sslContext(){try{return javax.net.ssl.SSLContext.getDefault();}catch(Exception e){throw new IllegalStateException(e);}}
  @Override public javax.net.ssl.SSLParameters sslParameters(){return new javax.net.ssl.SSLParameters();}
  @Override public Optional<java.net.Authenticator> authenticator(){return Optional.empty();}
  @Override public Version version(){return Version.HTTP_1_1;}
  @Override public Optional<java.util.concurrent.Executor> executor(){return Optional.empty();}
  @Override @SuppressWarnings("unchecked") public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> body){this.request=request;return (HttpResponse<T>)response;}
  @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> b){throw new UnsupportedOperationException();}
  @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> b,HttpResponse.PushPromiseHandler<T> p){throw new UnsupportedOperationException();}
  @Override public void close(){}
 }
 Client client(int status,InputStream body){return new Client(new HttpResponse<InputStream>(){
  public int statusCode(){return status;}public InputStream body(){return body;}public HttpRequest request(){return null;}public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
  public HttpHeaders headers(){return HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(k,v)->true);}public URI uri(){return ORIGIN;}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}
 });}
 static InputStream bytes(String body){return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}
 @Test void readsActualProviderPeopleAndPreservesDepartmentAndInactiveStatus()throws Exception {
  var client=client(200,bytes("{\"items\":[{\"stableId\":\"person-one\",\"displayName\":\"真实目录姓名\",\"department\":\"真实目录部门\",\"active\":false,\"email\":\"person@example.invalid\"}],\"nextCursor\":\"opaque-cursor\"}"));
  try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client)){
   var page=directory.search("某人%_",null,1);assertEquals(1,page.items().size());assertEquals("person-one",page.items().getFirst().stableId());assertEquals("真实目录部门",page.items().getFirst().department());assertFalse(page.items().getFirst().active());assertEquals("opaque-cursor",page.nextCursor());
   var request=client.request;assertNotNull(request);assertEquals("GET",request.method());assertEquals("127.0.0.1",request.uri().getHost());assertEquals(8792,request.uri().getPort());assertEquals("/v1/directory/people",request.uri().getPath());assertTrue(request.uri().getRawQuery().contains("%25%5F")||request.uri().getRawQuery().contains("%25_"));
  }
 }
 @Test void emptyProviderPageStaysEmptyAndNoLocalFivePersonCatalogueAppears()throws Exception {
  try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(200,bytes("{\"items\":[],\"nextCursor\":null}")))){assertTrue(directory.search("dev-owner",null,20).items().isEmpty());}
 }
 @Test void unavailableOrMalformedProviderNeverBecomesAnEmptySuccess()throws Exception {
  for(var body:List.of("not json","{\"items\":[],\"nextCursor\":null} {}","{\"items\":[],\"items\":[],\"nextCursor\":null}","{\"items\":[{\"stableId\":\"x\",\"displayName\":\"x\",\"department\":null,\"active\":\"yes\"}],\"nextCursor\":null}"))try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(200,bytes(body)))){assertThrows(ProviderUnavailableException.class,()->directory.search("x",null,20));}
  try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(503,bytes("{}")))){assertThrows(ProviderUnavailableException.class,()->directory.search("x",null,20));}
 }
 @Test void duplicateIdentitiesOrLoopingOpaqueCursorCannotBeConsumed()throws Exception {
  String person="{\"stableId\":\"x\",\"displayName\":\"x\",\"department\":null,\"active\":true}";
  for(String body:List.of("{\"items\":["+person+","+person+"],\"nextCursor\":null}","{\"items\":[],\"nextCursor\":\"same\"}"))try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(200,bytes(body)))){assertThrows(ProviderUnavailableException.class,()->directory.search("x","same",20));}
 }
 @Test void slowBodyIsClosedByTheOverallDeadline()throws Exception {
  class Body extends InputStream {boolean closed;@Override public synchronized int read()throws IOException{while(!closed)try{wait();}catch(InterruptedException e){throw new InterruptedIOException();}throw new IOException("closed");}@Override public synchronized void close(){closed=true;notifyAll();}}
  var body=new Body();try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofMillis(100),client(200,body))){assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertThrows(ProviderUnavailableException.class,()->directory.search("x",null,20)));assertTrue(body.closed);}
 }
 @Test void oversizedBodyStopsAtTheBoundAndClosesTheStream()throws Exception {
  class Body extends InputStream {int count;boolean closed;public int read(){count++;return ' ';}@Override public void close(){closed=true;}}
  var body=new Body();try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(200,body))){assertThrows(ProviderUnavailableException.class,()->directory.search("x",null,20));assertTrue(body.closed);assertTrue(body.count<=1048577);}
 }
 @Test void fixedHarnessOriginAndBoundedInputsAreRequired()throws Exception {
  var client=client(200,bytes("{\"items\":[],\"nextCursor\":null}"));for(String origin:List.of("http://127.0.0.1:8793","https://example.invalid","http://127.0.0.1:8792/path","http://user@127.0.0.1:8792"))assertThrows(IllegalArgumentException.class,()->new ControlledDirectoryAdapter(URI.create(origin),Duration.ofSeconds(3),client));
  try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client)){assertThrows(IllegalArgumentException.class,()->directory.search(null,null,20));assertThrows(IllegalArgumentException.class,()->directory.search("x",null,0));assertThrows(IllegalArgumentException.class,()->directory.search("x","a".repeat(4097),20));}
 }
 @Test void departmentCatalogueIsIndependentOfTheCurrentPeoplePage()throws Exception {
  var client=client(200,bytes("{\"items\":[{\"id\":\"dep-last\",\"name\":\"后页部门\",\"parentId\":null,\"active\":true},{\"id\":\"disabled\",\"name\":\"停用部门\",\"parentId\":\"dep-last\",\"active\":false}]}"));
  try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client)){var page=directory.departments();assertEquals(2,page.items().size());assertEquals("dep-last",page.items().getFirst().id());assertFalse(page.items().getLast().active());assertEquals("dep-last",page.items().getLast().parentId());assertEquals("/v1/directory/departments",client.request.uri().getPath());assertThrows(UnsupportedOperationException.class,()->page.items().clear());}
 }
 @Test void departmentFilterIsSentAsAnExactStableIdentity()throws Exception {
  var client=client(200,bytes("{\"items\":[],\"nextCursor\":null}"));try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client)){directory.search("同名","cursor",20,"dep+%_甲");String query=client.request.uri().getRawQuery();assertTrue(query.contains("departmentId=dep%2B%25_"));assertTrue(query.contains("cursor=cursor"));}
 }
 @Test void malformedOrDuplicateDepartmentsCannotBecomeAnEmptyCatalogue()throws Exception {
  String row="{\"id\":\"one\",\"name\":\"部门\",\"parentId\":null,\"active\":true}";
  for(String body:List.of("{}","{\"items\":["+row+","+row+"]}","{\"items\":[{\"id\":\"one\",\"name\":\"部门\",\"parentId\":null,\"active\":1}]}","{\"items\":[]} {}"))try(var directory=new ControlledDirectoryAdapter(ORIGIN,Duration.ofSeconds(3),client(200,bytes(body)))){assertThrows(ProviderUnavailableException.class,directory::departments);}
 }
}
