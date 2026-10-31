package ama.recording;

import ama.contract.RecordingContracts;
import ama.contract.StorageContracts.*;
import ama.storage.FileBackendCatalog;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Prepares immutable audio outside business transactions; task completion still owns registration. */
public final class RecordingArchivePreparation {
    public record PartRange(String partId,long startSample,long sampleCount) {}
    public record Prepared(String sessionId,String inputFingerprint,StoredBlob blob,long sampleCount,List<PartRange> parts) {
        public Prepared { parts=List.copyOf(parts); }
    }
    private final JdbcRecordingPersistence recordings;
    private final FileBackendCatalog backends;
    public RecordingArchivePreparation(JdbcRecordingPersistence recordings,FileBackendCatalog backends) {
        this.recordings=Objects.requireNonNull(recordings);this.backends=Objects.requireNonNull(backends);
    }
    /** Bounded metadata only. This fingerprint grants no source or write authority. */
    public String inputFingerprint(String sessionId) {
        var snapshot=recordings.load(sessionId);if(!snapshot.finished())throw new IllegalStateException("Recording is not finished");
        var parts=new LinkedHashMap<String,List<RecordingService.Proof>>();
        for(var part:snapshot.parts())parts.put(part,new ArrayList<>());
        for(var proof:snapshot.chunks()){var list=parts.get(proof.partId());if(list==null)throw new IllegalStateException("Unissued part");list.add(proof);}
        for(var list:parts.values())list.sort(Comparator.comparingLong(RecordingService.Proof::seq));
        return fingerprint(snapshot,parts);
    }
    /** Caller must authorize this exact immutable session and bind cancellation to its task lease. */
    public Prepared prepare(String sessionId,BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled);check(cancelled);
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Archive I/O requires independent preparation");
        var snapshot=recordings.load(sessionId);if(!snapshot.finished())throw new IllegalStateException("Recording is not finished");
        var byPart=new LinkedHashMap<String,List<RecordingService.Proof>>();
        for(var part:snapshot.parts())byPart.put(part,new ArrayList<>());
        for(var proof:snapshot.chunks()) {
            var values=byPart.get(proof.partId());if(values==null)throw new IllegalStateException("Unissued archive part");values.add(proof);
        }
        long samples=0;var ranges=new ArrayList<PartRange>();
        for(var entry:byPart.entrySet()) {
            entry.getValue().sort(Comparator.comparingLong(RecordingService.Proof::seq));long seq=1,position=0;
            for(var proof:entry.getValue()) {
                if(proof.seq()!=seq++||proof.sampleStart()!=position||proof.sampleCount()<1||proof.sampleCount()>RecordingContracts.MAX_PCM_BYTES/2)throw new RecordingService.Gap();
                position=Math.addExact(position,proof.sampleCount());
            }
            ranges.add(new PartRange(entry.getKey(),samples,position));samples=Math.addExact(samples,position);
        }
        if(samples<1||samples>RecordingContracts.MAX_SESSION_SAMPLES||snapshot.chunks().size()>RecordingContracts.MAX_SESSION_CHUNKS)throw new IllegalStateException("Archive exceeds recording bounds");
        String fingerprint=fingerprint(snapshot,byPart);long size=Math.addExact(44,Math.multiplyExact(samples,2));
        var key=new StorageKey(snapshot.session().backendId(),"recordings",sessionId+"/archive-v1/"+fingerprint);
        Path directory=null,file=null;
        try {
            directory=Files.createTempDirectory("ama-recording-archive-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            file=Files.createTempFile(directory,"audio-",".wav",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            var complete=digest();
            try(var output=new DigestOutputStream(Files.newOutputStream(file),complete)) {
                output.write(header(samples));byte[] buffer=new byte[8192];
                var ordered=byPart.values().stream().flatMap(List::stream).toList();
                try(var input=recordings.readFrozenChunks(snapshot,ordered)){
                    for(var proof:ordered){
                        check(cancelled);var chunkHash=digest();long copied=0;
                        while(copied<proof.sampleCount()*2) {
                            check(cancelled);int count=input.read(buffer,0,(int)Math.min(buffer.length,proof.sampleCount()*2-copied));if(count<0)throw new EOFException("Frozen chunk sequence truncated");
                            copied+=count;
                            chunkHash.update(buffer,0,count);output.write(buffer,0,count);
                        }
                        if(!hex(chunkHash.digest()).equals(proof.sha256()))throw new IOException("Frozen archive chunk mismatch");
                    }
                    if(input.read()!=-1)throw new IOException("Frozen chunk sequence has extra bytes");
                }
            }
            if(Files.size(file)!=size)throw new IOException("Archive size mismatch");check(cancelled);
            String sha256=hex(complete.digest());StoredBlob blob;
            try(var input=Files.newInputStream(file)) {
                var guarded=new FilterInputStream(input){
                    @Override public int read()throws IOException{check(cancelled);return super.read();}
                    @Override public int read(byte[] bytes,int off,int length)throws IOException{check(cancelled);return in.read(bytes,off,length);}
                };
                blob=backends.require(key.backendId(),true).storage().put(key,guarded,size,sha256);
            }
            check(cancelled);if(!blob.key().equals(key)||blob.size()!=size||!blob.sha256().equals(sha256))throw new IllegalStateException("Archive storage proof mismatch");
            return new Prepared(sessionId,fingerprint,blob,samples,ranges);
        }catch(IOException failure){throw new IllegalStateException("Recording archive unavailable",failure);}
        finally{remove(file);remove(directory);}
    }
    private static String fingerprint(RecordingService.Snapshot snapshot,Map<String,List<RecordingService.Proof>> parts) {
        var digest=digest();
        try(var output=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
            value(output,"ama.recording-archive.v1");value(output,snapshot.session().sessionId());value(output,snapshot.session().backendId());output.writeInt(parts.size());
            for(var entry:parts.entrySet()) {
                value(output,entry.getKey());output.writeInt(entry.getValue().size());
                for(var proof:entry.getValue()){output.writeLong(proof.seq());output.writeLong(proof.sampleStart());output.writeLong(proof.sampleCount());value(output,proof.sha256());}
            }
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
        return hex(digest.digest());
    }
    private static void value(DataOutputStream output,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);output.writeInt(bytes.length);output.write(bytes);}
    private static byte[] header(long samples) {
        int bytes=Math.toIntExact(samples*2);var b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);return b.array();
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String hex(byte[] bytes){return HexFormat.of().formatHex(bytes);}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Recording archive cancelled");}
    private static void remove(Path path){if(path!=null)try{Files.deleteIfExists(path);}catch(IOException ignored){/* Only private scratch remains; it is never exposed as an archive. */}}
}
