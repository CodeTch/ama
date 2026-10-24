package ama.storage.s3;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
class Task05S3DeadlineIT {
 @Test void actualS3SuccessAndControlBodiesExitBeforeProxyReleaseAndReturnAdmission()throws Exception {
  try(var proxy=new WireProxy();var f=new S3Fixture("task05-deadline",proxy.endpoint());var client=S3FileStorage.client(f.config)){
   var c=f.config;var bound=new S3Config(c.backendId(),c.endpoint(),c.region(),c.dataBucket(),c.controlBucket(),c.credentials(),c.pathStyle(),c.keyBudget(),c.chunkBytes(),c.nativeMinimum(),c.nativeMaximum(),c.nativeMaxParts(),c.maximumObject(),2,Duration.ofMillis(350));
   var io=new S3Objects(client,bound);
   for(String bucket:new String[]{c.dataBucket(),c.controlBucket()}){
    String key="task05-body";f.admin.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),RequestBody.fromString("actual original source"));
    var fault=proxy.arm("stall-body",path->path.startsWith("GET ")&&path.contains("/"+bucket+"/"));
    var pool=Executors.newSingleThreadExecutor();
    try{
     var result=pool.submit(()->{try(var body=io.raw(GetObjectRequest.builder().bucket(bucket).key(key).build())){return body.readAllBytes();}});
     assertTrue(fault.reached.await(3,TimeUnit.SECONDS));try{assertThrows(ExecutionException.class,()->result.get(2,TimeUnit.SECONDS));}catch(AssertionError failure){
      var trace=new StringBuilder();Thread.getAllStackTraces().forEach((thread,stack)->{trace.append(thread.getName()).append(" ").append(thread.getState()).append("\n");for(var frame:stack)trace.append("  ").append(frame).append("\n");});
      java.nio.file.Path evidence=java.nio.file.Path.of("../../artifacts/phase-b/b-remaining/task-05/s3-blocked-"+java.util.UUID.randomUUID()+".txt");java.nio.file.Files.writeString(evidence,trace);throw failure;
     }assertEquals(1,fault.release.getCount());
     // Both finite permits remain available after watchdog+finally close compete.
     try(var one=io.raw(GetObjectRequest.builder().bucket(bucket).key(key).build());var two=io.raw(GetObjectRequest.builder().bucket(bucket).key(key).build())){assertEquals(22,one.readAllBytes().length);assertEquals(22,two.readAllBytes().length);}
    }finally{fault.release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));proxy.settle();}
   }
  }
 }
 @Test void sdkErrorBodyDeadlineActuallyTerminatesReader()throws Exception {
  try(var proxy=new WireProxy();var f=new S3Fixture("task05-errors",proxy.endpoint())){
   var c=f.config;var bound=new S3Config(c.backendId(),c.endpoint(),c.region(),c.dataBucket(),c.controlBucket(),c.credentials(),c.pathStyle(),c.keyBudget(),c.chunkBytes(),c.nativeMinimum(),c.nativeMaximum(),c.nativeMaxParts(),c.maximumObject(),2,Duration.ofMillis(500));
   try(var storage=new S3FileStorage(bound)){
    var fault=proxy.arm("stall-error",path->path.startsWith("GET "));var pool=Executors.newSingleThreadExecutor();
    try{var result=pool.submit(()->storage.stat(new ama.contract.StorageContracts.StorageKey(c.backendId(),"test","missing")));assertTrue(fault.reached.await(3,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->result.get(2,TimeUnit.SECONDS));assertEquals(1,fault.release.getCount());}
    finally{fault.release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
   }
  }
 }
}
