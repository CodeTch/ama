package ama.governance;
import ama.contract.GovernanceContracts.*;
import java.util.*;
public final class EvaluationService {
 public static Metrics metrics(List<SampleResult> results){if(results.isEmpty())throw new IllegalArgumentException("Samples required");int ok=(int)results.stream().filter(SampleResult::succeeded).count();return new Metrics(results.size(),ok,results.size()-ok,results.stream().mapToDouble(SampleResult::score).sum()/results.size());}
 public static double textScore(String expected,String actual){if(expected==null||actual==null||expected.length()>8192||actual.length()>16384)throw new IllegalArgumentException("Bounded quality text required");int[] a=expected.codePoints().toArray(),b=actual.codePoints().toArray();if(a.length==0)return b.length==0?1:0;int[] previous=new int[b.length+1];for(int i=0;i<=b.length;i++)previous[i]=i;for(int i=1;i<=a.length;i++){int[] next=new int[b.length+1];next[0]=i;for(int k=1;k<=b.length;k++)next[k]=Math.min(Math.min(next[k-1]+1,previous[k]+1),previous[k-1]+(a[i-1]==b[k-1]?0:1));previous=next;}return Math.max(0,1d-(double)previous[b.length]/a.length);}
}
