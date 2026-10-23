package ama.config.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Linux/local regular-file acquisition. Unsupported providers fail closed. No arbitrary stream timeout claim. */
final class ProtectedArchiveFiles implements AutoCloseable {
 final Path path;
 final SecureDirectoryStream<Path> root;
 final Object rootKey;
 final BooleanSupplier cancel;
 static BooleanSupplier deadline(BooleanSupplier external){
  require(external!=null,INVALID_ARGUMENT);long end=System.nanoTime()+30_000_000_000L;
  return ()->external.getAsBoolean()||System.nanoTime()-end>=0;
 }
 private ProtectedArchiveFiles(Path path,SecureDirectoryStream<Path> root,BooleanSupplier cancel)throws IOException{
  this.path=path;this.root=root;this.cancel=cancel;rootKey=attributes(root,path.getFileSystem().getPath(".")).fileKey();require(rootKey!=null,PROFILE_UNAVAILABLE);
 }
 static ProtectedArchiveFiles open(Path path,BooleanSupplier cancel)throws IOException{
  check(cancel);require(path!=null&&path.isAbsolute()&&path.equals(path.normalize()),PROFILE_UNAVAILABLE);
  DirectoryStream<Path> initial=Files.newDirectoryStream(path.getRoot());
  if(!(initial instanceof SecureDirectoryStream<?>)){initial.close();throw fail(PROFILE_UNAVAILABLE);}
  @SuppressWarnings("unchecked") var current=(SecureDirectoryStream<Path>)initial;
  boolean handedOff=false;
  try{
   for(Path component:path){check(cancel);var next=current.newDirectoryStream(component,LinkOption.NOFOLLOW_LINKS);current.close();current=next;}
   var result=new ProtectedArchiveFiles(path,current,cancel);check(cancel);handedOff=true;return result;
  }finally{if(!handedOff)current.close();}
 }
 static BasicFileAttributes attributes(SecureDirectoryStream<Path> dir,Path name)throws IOException{
  return dir.getFileAttributeView(name,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
 }
 SecureDirectoryStream<Path> directory(String... components)throws IOException{
  check(cancel);var current=root.newDirectoryStream(path.getFileSystem().getPath("."),LinkOption.NOFOLLOW_LINKS);
  try{
   for(String component:components){check(cancel);var next=current.newDirectoryStream(relative(component),LinkOption.NOFOLLOW_LINKS);current.close();current=next;}
   return current;
  }catch(Throwable failure){current.close();throw failure;}
 }
 Path relative(String name){require(name!=null&&!name.isEmpty()&&!name.equals(".")&&!name.equals("..")&&!name.contains("/")&&!name.contains("\\"),PROFILE_UNAVAILABLE);return path.getFileSystem().getPath(name);}
 static final class Capacity {
  long remaining;
  Capacity(long remaining){require(remaining>=0,INVALID_ARGUMENT);this.remaining=remaining;}
  void reserve(long size){require(size>=0&&size<=remaining,BUDGET_EXCEEDED);remaining-=size;}
 }
 byte[] read(SecureDirectoryStream<Path> dir,String name,long expected,long maximum,Capacity capacity)throws IOException{
  check(cancel);Path relative=relative(name);var before=attributes(dir,relative);
  require(before.isRegularFile()&&!before.isSymbolicLink()&&before.fileKey()!=null,PROFILE_UNAVAILABLE);
  long size=before.size();require(size>0&&size<=maximum&&size<=Integer.MAX_VALUE,BUDGET_EXCEEDED);require(expected<0||expected==size,PROFILE_UNAVAILABLE);capacity.reserve(size);
  try(var channel=dir.newByteChannel(relative,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){
   check(cancel);require(channel.size()==size,PROFILE_UNAVAILABLE);byte[] bytes=new byte[(int)size];var buffer=ByteBuffer.wrap(bytes);
   while(buffer.hasRemaining()){check(cancel);int count=channel.read(buffer);require(count>0,READ_FAILED);}
   check(cancel);require(channel.read(ByteBuffer.allocate(1))==-1&&channel.size()==size,PROFILE_UNAVAILABLE);
   var after=attributes(dir,relative);require(after.isRegularFile()&&before.fileKey().equals(after.fileKey())&&after.size()==size,PROFILE_UNAVAILABLE);check(cancel);return bytes;
  }
 }
 void exactNames(SecureDirectoryStream<Path> dir,Set<String> names,int maximum)throws IOException{
  var seen=new HashSet<String>();for(Path entry:dir){check(cancel);require(seen.size()<maximum&&seen.add(entry.getFileName().toString())&&names.contains(entry.getFileName().toString()),PROFILE_UNAVAILABLE);}
  require(seen.equals(names),PROFILE_UNAVAILABLE);
 }
 /** Offline-only: path mkdir/fsync require the deployment-owned directory and a held publishing lock. */
 void verifyPath(Path directory,Object expected)throws IOException{
  check(cancel);try(var held=open(path,cancel)){require(rootKey.equals(held.rootKey),PROFILE_UNAVAILABLE);}
  var attrs=Files.readAttributes(directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
  require(attrs.isDirectory()&&!attrs.isSymbolicLink()&&expected!=null&&expected.equals(attrs.fileKey()),PROFILE_UNAVAILABLE);
 }
 Object directoryKey(Path directory)throws IOException{
  var a=Files.readAttributes(directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(a.isDirectory()&&!a.isSymbolicLink()&&a.fileKey()!=null,PROFILE_UNAVAILABLE);return a.fileKey();
 }
 void forceDirectory(Path directory,Object key)throws IOException{
  verifyPath(directory,key);try(var channel=FileChannel.open(directory,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){channel.force(true);}verifyPath(directory,key);
 }
 void write(SecureDirectoryStream<Path> dir,String name,java.io.InputStream input,long expected)throws IOException{
  try(var channel=dir.newByteChannel(relative(name),Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS))){
   require(channel instanceof FileChannel,PROFILE_UNAVAILABLE);byte[] block=new byte[8192];long count=0;
   while(true){check(cancel);int n=input.read(block);if(n<0)break;require(n>0&&n<=expected-count,PROFILE_UNAVAILABLE);count+=n;var buffer=ByteBuffer.wrap(block,0,n);while(buffer.hasRemaining()){check(cancel);require(channel.write(buffer)>0,READ_FAILED);}}
   require(count==expected,PROFILE_UNAVAILABLE);((FileChannel)channel).force(true);check(cancel);
  }
 }
 public void close()throws IOException{root.close();}
}
