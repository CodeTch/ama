package ama.storage.s3;

import ama.contract.StorageContracts.*;
import java.util.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

/** Permanent key retirement. The entity at the data key, not just the control marker,
 * fences in-flight If-None-Match publications. Historical versions/MPU are retained. */
final class RetiredUploads {
 static String markerKey(S3Layout layout,StorageKey key){return layout.bindingKey(key)+"-retired";}
 static boolean retired(S3Objects io,S3Layout layout,StorageKey key){
  byte[] marker=io.optional(markerKey(layout,key));if(marker==null)return false;
  String[] p=ControlCodec.unpack(marker,"discard-upload-v1",7);
  if(!p[1].equals(io.config.identity())||!p[2].equals(key.backendId())||!p[5].equals(key.namespace())||!p[6].equals(key.objectKey())||!p[3].matches("[0-9a-f-]{36}")||!p[4].matches("[0-9a-f]{64}"))throw ControlCodec.unavailable();return true;
 }
 static void live(S3Objects io,S3Layout layout,StorageKey key){if(retired(io,layout,key))throw new StorageException(ErrorCode.NOT_FOUND);}
 static void discard(S3Objects io,S3Layout layout,SessionJournal.Session session,StorageKey key){
  if(!session.key().equals(key))throw new StorageException(ErrorCode.INVALID_ARGUMENT);
  io.binding(layout,key,true);
  byte[] marker=ControlCodec.pack("discard-upload-v1",io.config.identity(),key.backendId(),session.id(),session.specHash(),key.namespace(),key.objectKey());
  io.exact(markerKey(layout,key),marker);
  // A nonempty, explicitly identified sentinel also cannot be mistaken for a valid empty blob.
  String identity=ControlCodec.hash(marker);byte[] sentinel=ControlCodec.pack("retired-object-v1",identity);
  io.call(()->io.client.putObject(PutObjectRequest.builder().bucket(io.config.dataBucket()).key(layout.data(key)).metadata(Map.of("ama-retired-v1",identity)).build(),RequestBody.fromBytes(sentinel)));
  var head=io.call(()->io.client.headObject(HeadObjectRequest.builder().bucket(io.config.dataBucket()).key(layout.data(key)).build()));
  if(head.contentLength()==null||head.contentLength()!=sentinel.length||!identity.equals(head.metadata().get("ama-retired-v1")))throw ControlCodec.unavailable();
 }
}
