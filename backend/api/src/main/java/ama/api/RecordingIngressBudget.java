package ama.api;
import ama.realtime.RecordingBinaryCodec;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
/** Resource admission only. This process-local budget never grants identity, lease, persistence or ACK. */
final class RecordingIngressBudget {
 static final class RateLimited extends RuntimeException {RateLimited(){super("Recording ingress budget exhausted");}}
 record Limits(int keys,long bytesPerSecond,long burstBytes,long globalBytesPerSecond,long globalBurstBytes,Duration idle){
  Limits{if(keys<1||keys>4096||bytesPerSecond<1||bytesPerSecond>1073741824L||globalBytesPerSecond<1||globalBytesPerSecond>1073741824L||burstBytes<RecordingBinaryCodec.MAX_FRAME_BYTES||burstBytes>1073741824L||globalBurstBytes<RecordingBinaryCodec.MAX_FRAME_BYTES||globalBurstBytes>1073741824L||idle==null||idle.compareTo(Duration.ofSeconds(1))<0||idle.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("Finite recording ingress limits required");}
 }
 private record Key(String actor,String session,String collector){}
 private static final class Bucket {double tokens;long time,touch;Bucket(long burst,long now){tokens=burst;time=touch=now;}}
 private final Limits limits;private final LongSupplier clock;private final LinkedHashMap<Key,Bucket> buckets=new LinkedHashMap<>();private final Bucket global;
 RecordingIngressBudget(Limits limits,LongSupplier clock){this.limits=Objects.requireNonNull(limits);this.clock=Objects.requireNonNull(clock);global=new Bucket(limits.globalBurstBytes(),clock.getAsLong());}
 private static void refill(Bucket bucket,long now,long rate,long burst){long elapsed=now-bucket.time;if(elapsed>0){bucket.tokens=Math.min(burst,bucket.tokens+elapsed/1_000_000_000.0*rate);bucket.time=now;}}
 synchronized boolean admit(String actor,String session,String collector,int bytes){
  if(actor==null||actor.isBlank()||actor.length()>1020||bytes<1||bytes>RecordingBinaryCodec.MAX_FRAME_BYTES)throw new IllegalArgumentException("Invalid ingress request");ama.contract.OutboxContracts.uuid(session);ama.contract.OutboxContracts.uuid(collector);long now=clock.getAsLong();
  buckets.entrySet().removeIf(entry->now-entry.getValue().touch>=limits.idle().toNanos());var key=new Key(actor,session,collector);var bucket=buckets.get(key);
  if(bucket==null){if(buckets.size()>=limits.keys())return false;bucket=new Bucket(limits.burstBytes(),now);buckets.put(key,bucket);}bucket.touch=now;
  refill(bucket,now,limits.bytesPerSecond(),limits.burstBytes());refill(global,now,limits.globalBytesPerSecond(),limits.globalBurstBytes());if(bucket.tokens<bytes||global.tokens<bytes)return false;
  bucket.tokens-=bytes;global.tokens-=bytes;return true;
 }
 void require(String actor,String session,String collector,int bytes){if(!admit(actor,session,collector,bytes))throw new RateLimited();}
}
