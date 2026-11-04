package ama.minutes;
import java.util.*;

/** Presentation freshness is distinct from candidate adoption and final readiness. */
public final class MinutesGenerationFreshness {
 public record Input(String confirmation,String materials,String template,String exclusions,String elements,long revision){}
 public record Completed(Input input,boolean full,boolean pending,boolean succeeded){}
 public static boolean afterEdit(boolean elementsChanged,boolean previous){return elementsChanged||previous;}
 public static boolean needsPrompt(boolean previous,Input current,List<Completed> candidates){return previous&&candidates.stream().noneMatch(candidate->candidate.full()&&candidate.pending()&&candidate.succeeded()&&current.equals(candidate.input()));}
 private MinutesGenerationFreshness(){}
}
