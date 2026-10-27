package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.WorkspaceContracts.*;
import ama.db.TransactionRunner;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fresh identity then version-bound metadata, cumulative reservation, and selected immutable label reads. */
public final class JdbcCategoryCatalog {
 private final TransactionRunner tx;private final MeetingAccess access;private final CategoryDictionary dictionary;private final WorkspaceCursor cursors;private final Supplier<WorkspaceReadBudget> budgets;
 public JdbcCategoryCatalog(TransactionRunner tx,MeetingAccess access,CategoryDictionary dictionary,WorkspaceCursor cursors){this(tx,access,dictionary,cursors,WorkspaceReadBudget::new);}
 public JdbcCategoryCatalog(TransactionRunner tx,MeetingAccess access,CategoryDictionary dictionary,WorkspaceCursor cursors,Supplier<WorkspaceReadBudget> budgets){this.tx=Objects.requireNonNull(tx);this.access=Objects.requireNonNull(access);this.dictionary=Objects.requireNonNull(dictionary);this.cursors=Objects.requireNonNull(cursors);this.budgets=Objects.requireNonNull(budgets);}
 public CategoryPage read(Actor actor,int limit,String cursor) {
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Category reader owns its transaction");
  if(limit<1||limit>50)throw new IllegalArgumentException("Invalid page limit");
  var budget=Objects.requireNonNull(budgets.get());
  // Fixed schema-bounded status-only identity and cursor/envelope copies; no identity display name.
  budget.reserve(2,4,4096,4096,4096,4,true);
  return tx.required(()->{
   budget.check();access.tryLockIdentities(actor,List.of(),false).orElseThrow(MeetingAccess.AccessDeniedException::new);budget.check();
   var catalog=dictionary.catalog(budget);String scope="categories/"+catalog.version(budget);
   String after=cursor==null?null:cursors.decode(actor.enterpriseId(),scope,cursor);
   var metadata=catalog.metadata(budget);var selected=metadata.stream().filter(m->after==null||m.id().compareTo(after)>0).limit(limit+1L).toList();
   boolean more=selected.size()>limit;var items=new ArrayList<CategoryItem>();
   for(var descriptor:selected.stream().limit(limit).toList()) {
    var entry=catalog.read(descriptor,budget);items.add(new CategoryItem(entry.id(),entry.label()));
   }
   String next=more?cursors.encode(actor.enterpriseId(),scope,items.getLast().id()):null;budget.check();
   return new CategoryPage(items,next,more);
  });
 }
}
