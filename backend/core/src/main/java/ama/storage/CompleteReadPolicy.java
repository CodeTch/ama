package ama.storage;
import java.util.List;
import ama.contract.IdentityContracts.Actor;
/** Registered complete protocol. No inherited no-op can omit producer/lease finalization. */
public interface CompleteReadPolicy extends FileReferencePolicy {
    Object prelock(FileRegistry.LockedMeeting gate, String meetingId,
                   FileRegistry.Reference reference, String fileId, FileRegistry.ReferenceContext context);
    interface ReadPreparation {}
    /** Only an observed root change before source authorization permits bounded reacquisition. */
    final class ReadSnapshotChanged extends RuntimeException {}
    ReadPreparation prepareRead(Actor actor, String meetingId, FileRegistry.ReadRequest request);
    /** Optional identity-complete prologue. The registry still validates its own gate and current READ. */
    default FileRegistry.LockedMeeting lockReadRoot(FileRegistry registry,Actor actor,String meetingId,ReadPreparation prepared){return registry.lock(actor,meetingId,ama.access.MeetingAccess.Action.READ);}
    /** A graph policy can prelock the full source/artifact union before producer tasks. */
    default java.util.Optional<FileRegistry.AcquiredSources> prelockedReadSources(List<Object> scopes){return java.util.Optional.empty();}
    void authorizeRead(FileRegistry.LockedMeeting gate, String meetingId, FileRegistry.ReadRequest request);
    Object prelockCurrent(FileRegistry.LockedMeeting gate, String meetingId,
                          FileRegistry.Reference reference, String fileId, ReadPreparation prepared);
    ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
                         FileRegistry.ReadRequest request, ReadPreparation prepared);
    List<ReadMember> readMembers(Object domain, FileRegistry.Reference reference, String requestedFileId);
    void prelockReadTasks(List<Object> completeAuthenticScopes);
    void finishRead(Object domain, List<FileRegistry.CompletedSource> actualSources);
    record ReadPlan(FileRegistry.ReferenceContext context, List<ReadMember> completeMembers, String requestedFileId) {
        public ReadPlan { completeMembers=List.copyOf(completeMembers); }
    }
    record ReadMember(FileRegistry.Reference reference, String fileId) {}
}
