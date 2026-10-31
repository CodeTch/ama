package ama.storage;

import ama.contract.StorageContracts.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Exact per-chunk verification with one opened object at a time and no cross-backend fallback. */
public final class ChunkSequenceStream extends InputStream {
    private final InputStream raw;
    private final ReadSequence sequence;
    private final MessageDigest digest;
    private int index;
    private long remaining;
    private boolean closed,complete;
    private ChunkSequenceStream(InputStream raw,ReadSequence sequence){
        this.raw=Objects.requireNonNull(raw);this.sequence=Objects.requireNonNull(sequence);remaining=sequence.chunks().getFirst().size();
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static InputStream verify(InputStream raw,ReadSequence sequence){return new ChunkSequenceStream(raw,sequence);}
    public static InputStream open(FileStoragePort source,ReadSequence sequence){
        Objects.requireNonNull(source);Objects.requireNonNull(sequence);
        return open(sequence,chunk->source.read(chunk.key(),0,chunk.size()));
    }
    /** Transport/data callback only; the caller still owns all source authorization. */
    public static InputStream open(ReadSequence sequence,java.util.function.Function<ReadChunk,InputStream> open){
        Objects.requireNonNull(sequence);Objects.requireNonNull(open);
        var lazy=new InputStream(){
            int next;InputStream current;boolean closed;
            @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
            @Override public int read(byte[] bytes,int off,int length)throws IOException{
                Objects.checkFromIndexSize(off,length,bytes.length);if(closed)throw new IOException("Chunk sequence closed");if(length==0)return 0;
                while(true){
                    if(current==null){if(next==sequence.chunks().size())return -1;var chunk=sequence.chunks().get(next++);current=open.apply(chunk);}
                    int count=current.read(bytes,off,length);if(count>=0)return count;current.close();current=null;
                }
            }
            @Override public void close()throws IOException{if(closed)return;closed=true;if(current!=null)current.close();}
        };
        return verify(lazy,sequence);
    }
    @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
    @Override public int read(byte[] bytes,int off,int length)throws IOException{
        Objects.checkFromIndexSize(off,length,bytes.length);if(closed)throw new IOException("Chunk sequence closed");if(length==0)return 0;if(complete)return -1;
        try{
            if(index==sequence.chunks().size()){
                if(raw.read()!=-1)throw new IOException("Chunk sequence has extra bytes");complete=true;return -1;
            }
            int count=raw.read(bytes,off,(int)Math.min(length,remaining));if(count<0)throw new EOFException("Chunk sequence truncated");
            digest.update(bytes,off,count);remaining-=count;
            if(remaining==0){if(!HexFormat.of().formatHex(digest.digest()).equals(sequence.chunks().get(index).sha256()))throw new IOException("Immutable chunk checksum mismatch");index++;if(index<sequence.chunks().size())remaining=sequence.chunks().get(index).size();}
            return count;
        }catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public void close()throws IOException{if(closed)return;closed=true;raw.close();}
}
