package ama.transcript;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual TLS sockets and genuine discovery authorization; DB rows are explicit boundary substitutes. */
class Task11KnowledgeTlsTransportTest {
 @TempDir static Path directory;
 static Path ca,wrongCa;static SSLContext validServer,wrongNameServer;
 static final String PASSWORD="owned-test-password";
 @BeforeAll static void certificates()throws Exception {
  Path trusted=directory.resolve("trusted.p12"),foreign=directory.resolve("foreign.p12");
  keytool("-genkeypair","-alias","ca","-keyalg","EC","-groupname","secp256r1","-dname","CN=Owned test CA","-ext","bc:c","-validity","2","-keystore",trusted.toString());
  keytool("-genkeypair","-alias","ca","-keyalg","EC","-groupname","secp256r1","-dname","CN=Other test CA","-ext","bc:c","-validity","2","-keystore",foreign.toString());
  ca=directory.resolve("ca.pem");wrongCa=directory.resolve("wrong-ca.pem");keytool("-exportcert","-rfc","-alias","ca","-keystore",trusted.toString(),"-file",ca.toString());keytool("-exportcert","-rfc","-alias","ca","-keystore",foreign.toString(),"-file",wrongCa.toString());
  validServer=server(trusted,"correct","ip:127.0.0.1");wrongNameServer=server(trusted,"wrong-name","dns:wrong.example.invalid");
  try(var files=Files.list(directory)){for(Path f:files.toList())Files.setPosixFilePermissions(f,PosixFilePermissions.fromString("rw-------"));}
 }
 static SSLContext server(Path signer,String name,String san)throws Exception {
  Path store=directory.resolve(name+".p12"),request=directory.resolve(name+".csr"),cert=directory.resolve(name+".pem");
  keytool("-genkeypair","-alias","server","-keyalg","EC","-groupname","secp256r1","-dname","CN="+name,"-validity","2","-keystore",store.toString());
  keytool("-certreq","-alias","server","-keystore",store.toString(),"-file",request.toString());
  keytool("-gencert","-rfc","-alias","ca","-keystore",signer.toString(),"-infile",request.toString(),"-outfile",cert.toString(),"-validity","2","-ext","SAN="+san,"-ext","EKU=serverAuth","-ext","KU=digitalSignature");
  keytool("-importcert","-alias","ca","-keystore",store.toString(),"-file",ca.toString());keytool("-importcert","-alias","server","-keystore",store.toString(),"-file",cert.toString());
  var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,PASSWORD.toCharArray());}var factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(keys,PASSWORD.toCharArray());var context=SSLContext.getInstance("TLS");context.init(factory.getKeyManagers(),null,null);return context;
 }
 static void keytool(String...arguments)throws Exception {var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/keytool").toString(),"-J-Xmx64m","-J-XX:ActiveProcessorCount=2"));command.addAll(List.of(arguments));command.addAll(List.of("-storepass",PASSWORD,"-noprompt"));Path log=directory.resolve("keytool-"+UUID.randomUUID()+".log");var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();try{assertTrue(process.waitFor(15,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),()->{try{return Files.readString(log);}catch(IOException e){return e.getClass().getName();}});}finally{if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}}}
 static Path privateFile(Path path,byte[] bytes)throws IOException {Files.write(path,bytes,StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));return path;}
 final class Harness implements AutoCloseable {
  final ServerSocket server;final CountDownLatch accepted=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicReference<String> request=new AtomicReference<>();final AtomicReference<Throwable> failure=new AtomicReference<>();final ControlledKnowledgeClient client;final Thread responder;final Task11DiscoveryServiceBoundaryTest.Fixture fixture;
  Harness(SSLContext tls,Path trusted,boolean stall)throws Exception {
   server=stall?new ServerSocket(0,1,InetAddress.getByName("127.0.0.1")):tls.getServerSocketFactory().createServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
   try{Path root=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var paths=Map.of("serviceBearer",privateFile(root.resolve("bearer"),"A".repeat(43).getBytes(StandardCharsets.US_ASCII)).toString(),"delegationPrivateKey",privateFile(root.resolve("signing"),pair.getPrivate().getEncoded()).toString(),"providerAssertionPublicKey",privateFile(root.resolve("assertion"),pair.getPublic().getEncoded()).toString(),"providerTlsTrustCertificate",trusted.toString());var config=Map.of("role","application","providerId","kb","storeInstanceId","store","clientId","client","delegationKeyId","delegation","assertionKeyId","assertion","paths",paths);client=new ControlledKnowledgeClient(privateFile(root.resolve("application.json"),ControlledKnowledgeClient.canonical(config)),URI.create("https://127.0.0.1:"+server.getLocalPort()),"CONTROLLED_TLS");fixture=new Task11DiscoveryServiceBoundaryTest.Fixture(client);}catch(Throwable error){server.close();throw error;}
   responder=new Thread(()->{try(var socket=server.accept()){accepted.countDown();socket.setSoTimeout(3000);if(stall){release.await(8,TimeUnit.SECONDS);return;}((SSLSocket)socket).startHandshake();var in=socket.getInputStream();var header=new ByteArrayOutputStream();int last=0;while(header.size()<16384){int b=in.read();if(b<0)throw new EOFException();header.write(b);last=(last<<8)|b;if(last==0x0d0a0d0a)break;}request.set(header.toString(StandardCharsets.US_ASCII));Task11KnowledgeReadTransportTest.wire(socket,Task11KnowledgeReadTransportTest.ok(Task11KnowledgeReadTransportTest.PAGE));}catch(SSLException rejected){}catch(Throwable error){if(!server.isClosed())failure.set(error);}},"owned-kb-tls");responder.setDaemon(true);responder.start();
  }
  Map<String,Object> call(Task11KnowledgeReadTransportTest.Cancel cancel)throws Exception{return fixture.service.destinations(Task11DiscoveryServiceBoundaryTest.ACTOR,Task11DiscoveryServiceBoundaryTest.M,null,Map.of(),cancel);}
  public void close()throws Exception{release.countDown();server.close();client.close();responder.join(5000);assertFalse(responder.isAlive());if(failure.get()!=null)throw new AssertionError("TLS responder failed",failure.get());}
 }
 @Test void pinnedCaAndIpSanPermitActualSignedDiscovery()throws Exception{try(var h=new Harness(validServer,ca,false)){assertEquals(List.of(),h.call(new Task11KnowledgeReadTransportTest.Cancel()).get("items"));assertEquals("https://127.0.0.1:"+h.server.getLocalPort()+"/v1/knowledge/documents/document-1",h.client.documentUrl("document-1"));assertTrue(h.request.get().contains("X-Controlled-Delegation:"));assertEquals(Map.of("getWriteAttempts",1L,"postWriteAttempts",0L),h.client.traffic());assertEquals(2,h.fixture.roots.get());}}
 @Test void wrongCaCannotSendCredentials()throws Exception{try(var h=new Harness(validServer,wrongCa,false)){assertThrows(IOException.class,()->h.call(new Task11KnowledgeReadTransportTest.Cancel()));assertNull(h.request.get());assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),h.client.traffic());assertEquals(1,h.fixture.roots.get());}}
 @Test void wrongIpSanCannotSendCredentials()throws Exception{try(var h=new Harness(wrongNameServer,ca,false)){assertThrows(IOException.class,()->h.call(new Task11KnowledgeReadTransportTest.Cancel()));assertNull(h.request.get());assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),h.client.traffic());assertEquals(1,h.fixture.roots.get());}}
 @Test void closeAbortsAnIncompleteHandshake()throws Exception{try(var h=new Harness(validServer,ca,true)){var closer=new Thread(()->{try{assertTrue(h.accepted.await(3,TimeUnit.SECONDS));h.client.close();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"owned-tls-close");closer.start();long start=System.nanoTime();try{assertThrows(IOException.class,()->h.call(new Task11KnowledgeReadTransportTest.Cancel()));}finally{closer.join(4000);}assertFalse(closer.isAlive());assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(4));assertEquals(0,h.client.traffic().get("getWriteAttempts"));}}
 @Test void cancellationAbortsAnIncompleteHandshake()throws Exception{try(var h=new Harness(validServer,ca,true)){var cancel=new Task11KnowledgeReadTransportTest.Cancel();var canceller=new Thread(()->{try{assertTrue(h.accepted.await(3,TimeUnit.SECONDS));cancel.stop();}catch(InterruptedException e){Thread.currentThread().interrupt();}},"owned-tls-cancel");canceller.start();long start=System.nanoTime();try{assertThrows(IOException.class,()->h.call(cancel));}finally{canceller.join(4000);}assertFalse(canceller.isAlive());assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(4));assertNull(cancel.abort.get());assertEquals(0,h.client.traffic().get("getWriteAttempts"));}}

 @Test void stalledHandshakeHonorsSocketTimeout()throws Exception{try(var h=new Harness(validServer,ca,true)){long start=System.nanoTime();assertThrows(IOException.class,()->h.call(new Task11KnowledgeReadTransportTest.Cancel()));long elapsed=System.nanoTime()-start;assertTrue(elapsed>=TimeUnit.SECONDS.toNanos(4)&&elapsed<TimeUnit.SECONDS.toNanos(8));assertEquals(0,h.client.traffic().get("getWriteAttempts"));}}
 @Test void tlsModeRejectsDowngradeAndForeignOriginsBeforeReadingCredentials(){for(String origin:List.of("http://127.0.0.1:1234","https://localhost:1234","https://name@127.0.0.1:1234","https://127.0.0.1:1234/path","https://127.0.0.1:1234?query=1","https://127.0.0.1:1234#fragment"))assertThrows(IllegalArgumentException.class,()->new ControlledKnowledgeClient(directory.resolve("absent-credentials"),URI.create(origin),"CONTROLLED_TLS"));}
}
