package ama.storage;
import java.io.InputStream;
import java.util.List;
import ama.contract.StorageContracts.*;
/** Harness owns the adapter lifetime across both application contexts. */
final class Bl5BorrowedStorage implements FileStoragePort {
 final FileStoragePort delegate;
 Bl5BorrowedStorage(FileStoragePort delegate){this.delegate=delegate;}
 public StoredBlob put(StorageKey k,InputStream b,long s,String h){return delegate.put(k,b,s,h);}
 public BlobStat stat(StorageKey k){return delegate.stat(k);}
 public InputStream read(StorageKey k,long o,long l){return delegate.read(k,o,l);}
 public void delete(StorageKey k){delegate.delete(k);}
 public UploadToken beginUpload(UploadSpec s){return delegate.beginUpload(s);}
 public PartReceipt writePart(UploadToken t,int p,InputStream b,long s,String h){return delegate.writePart(t,p,b,s,h);}
 public StoredBlob complete(UploadToken t,List<PartReceipt> p,String h){return delegate.complete(t,p,h);}
 public void abort(UploadToken t){delegate.abort(t);}
 public void discardUpload(UploadToken t,StorageKey k){delegate.discardUpload(t,k);}
 public BlobPage list(String b,String n,String p,String c,int l){return delegate.list(b,n,p,c,l);}
}
