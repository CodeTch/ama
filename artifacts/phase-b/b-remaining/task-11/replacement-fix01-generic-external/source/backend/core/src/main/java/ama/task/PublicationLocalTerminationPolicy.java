package ama.task;
/** Fixed publication-only ordinary terminal hook. No caller-selected writer or constructible context. */
public interface PublicationLocalTerminationPolicy extends RecordedFactRecoveryPolicy {
 interface LocalTerminalContext {}
 interface PreparedLocalRecovery {ama.contract.TaskContracts.TaskSpec spec();}
 record RecoveryBinding(ama.contract.TaskContracts.TaskSpec spec,String taskId,PreparedDomain prepared) {}
 default RecoveryBinding requireLocalRecovery(PreparedLocalRecovery recovery){throw new IllegalArgumentException("Explicit local recovery not installed");}
 enum Phase { VALIDATING, SEALING }
 boolean validateLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
 void sealLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
}
