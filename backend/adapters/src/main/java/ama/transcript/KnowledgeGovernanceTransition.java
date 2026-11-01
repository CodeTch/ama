package ama.transcript;

import java.util.Set;

/** Pure monotonic fold. A caller still needs an actual pinned signature and resource binding. */
final class KnowledgeGovernanceTransition {
 private static final Set<String> TERMINAL=Set.of("WITHDRAWN","OBSOLETE");
 static String next(String local,String prior,long previous,String observed,long version){
  if(!Set.of("ACTIVE","UNKNOWN","WITHDRAW_PENDING","OBSOLETE_PENDING","WITHDRAWN","OBSOLETE").contains(local)
      ||!Set.of("ACTIVE","WITHDRAWN","OBSOLETE").contains(observed)||version<0||version<previous
      ||prior!=null&&version==previous&&!prior.equals(observed)
      ||TERMINAL.contains(local)&&!local.equals(observed)
      ||prior!=null&&TERMINAL.contains(prior)&&!prior.equals(observed))
   throw new IllegalArgumentException("Stale or conflicting governance observation");
  if(TERMINAL.contains(observed))return observed;
  return local.endsWith("_PENDING")?local:"ACTIVE";
 }
}
