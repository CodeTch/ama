package ama.task;
/** Fixed publication-only ordinary terminal hook. No caller-selected writer or constructible context. */
public interface PublicationLocalTerminationPolicy extends RecordedFactRecoveryPolicy {
 interface LocalTerminalContext {}
 enum Phase { VALIDATING, SEALING }
 boolean validateLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
 void sealLocalTerminal(PreparedDomain prepared,LocalTerminalContext context);
}
