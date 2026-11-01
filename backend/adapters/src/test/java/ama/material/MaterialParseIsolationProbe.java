package ama.material;
import java.nio.file.*;
/** Actual child probe: no external request can be emitted if the installed policy is effective. */
public final class MaterialParseIsolationProbe {
 @SuppressWarnings("removal") public static void main(String[] args)throws Exception{
  if(System.getSecurityManager()==null)throw new IllegalStateException("Missing confinement");var checks=new StringBuilder(Files.readString(Path.of(args[0])));
  try{Files.readString(Path.of(args[2]));throw new AssertionError("Ambient file readable");}catch(SecurityException denied){checks.append("|FILE_DENIED");}
  try(var socket=new java.net.Socket("127.0.0.1",8792)){throw new AssertionError("Network permitted");}catch(SecurityException denied){checks.append("|NETWORK_DENIED");}
  try{new ProcessBuilder("/bin/true").start();throw new AssertionError("Process permitted");}catch(SecurityException denied){checks.append("|EXEC_DENIED");}
  try{System.getenv("AMA_TEST_PRIVATE");throw new AssertionError("Environment permitted");}catch(SecurityException denied){checks.append("|ENV_DENIED");}
  Files.writeString(Path.of(args[1]),checks,StandardOpenOption.CREATE_NEW);
 }
}
