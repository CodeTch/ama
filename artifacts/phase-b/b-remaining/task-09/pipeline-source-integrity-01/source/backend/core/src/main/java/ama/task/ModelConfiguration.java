package ama.task;
import static ama.contract.OutboxContracts.protocolCode;
/** Explicit reviewed deployment authority; construction never writes database policy. */
public record ModelConfiguration(String model,int capacity,long revision,boolean enabled,CutoffPolicy cutoffPolicy){
 public enum CutoffPolicy { EXPLICIT_SETTLEMENT, PROVEN_DEADLINE }
 public ModelConfiguration {protocolCode(model);if(capacity<1||revision<1||cutoffPolicy==null)throw new IllegalArgumentException("Explicit valid capacity policy required");}
}
