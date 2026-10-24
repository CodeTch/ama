package ama.config.file;

import java.nio.file.Path;
import ama.contract.MinutesContracts.Failure;
import static ama.config.file.ControlledConfigArchiveSourceTest.*;

/** Owned child only: the parent bounds and forcibly terminates the old blocking FIFO-open behavior. */
public final class ControlledArchiveFifoProbe {
 public static void main(String[] args)throws Exception{
  System.out.println("PUBLISH_START");System.out.flush();
  try{new ControlledArchiveReleaseWriter().publish(Path.of(args[0]),null,bundle(),I,B,()->false);System.exit(2);}
  catch(Failure failure){System.out.println("UNAVAILABLE "+failure.code());if(!failure.code().name().equals("PROFILE_UNAVAILABLE"))System.exit(3);}
 }
}
