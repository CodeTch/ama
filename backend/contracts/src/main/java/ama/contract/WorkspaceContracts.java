package ama.contract;
import java.util.List;

/** Bounded navigation projections; no command authority is represented by these values. */
public final class WorkspaceContracts {
 private WorkspaceContracts(){}
 public record CategoryItem(String id,String label){}
 public record CategoryPage(List<CategoryItem> items,String nextCursor,boolean hasMore) {
  public CategoryPage {items=List.copyOf(items);}
 }
 public record MeetingItem(String id,String name,String category,java.time.LocalDate occurredOn,String stage,String lifecycle,String relation,String writeState,String revisionToken,String accessRevisionToken){}
 public record MeetingPage(List<MeetingItem> items,String nextCursor,boolean hasMore) {
  public MeetingPage {items=List.copyOf(items);}
 }

 public record CommandAssociation(String operation,String meetingId,String resultKind,String resultId,String acceptedRevisionToken,int originalHttpStatus){}
 public record CommandObservation(String meetingId,String revisionToken,String accessRevisionToken,String stage){}
 public record CommandLookup(String status,CommandAssociation association,CommandObservation current){}
}
