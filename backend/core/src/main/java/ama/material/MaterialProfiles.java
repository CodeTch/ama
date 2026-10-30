package ama.material;

import java.util.*;
import ama.canonical.CanonicalValue;
import ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static ama.config.WireValues.*;

/** Finite archived parser semantics; extensions require a new implementation/profile identity. */
public final class MaterialProfiles {
 public static final String CATALOG="ama.material-catalog.v2";
 public static final List<String> FORMATS=List.of("PDF","PPT","TXT","WORD");
 public static String parser(String format){require(FORMATS.contains(format),PROFILE_UNAVAILABLE);return "ama.material-"+format.toLowerCase(Locale.ROOT)+".v1";}
 public static String format(String parser){return FORMATS.stream().filter(f->parser(f).equals(parser)).findFirst().orElseThrow(()->fail(PROFILE_UNAVAILABLE));}
 public static void definition(CanonicalValue definition,String version){var d=fields(definition,"contract","format","parserIdentity","unitMapping");literal(d.get("contract"),"ama.material-parser.v1");String format=text(d.get("format"));require(parser(format).equals(version),PROFILE_UNAVAILABLE);literal(d.get("parserIdentity"),MaterialParser.PROFILE);literal(d.get("unitMapping"),format.equals("PDF")||format.equals("PPT")?"PAGE":"PARAGRAPH");}
 private MaterialProfiles(){}
}
