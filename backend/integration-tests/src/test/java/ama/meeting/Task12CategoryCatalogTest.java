package ama.meeting;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Task12CategoryCatalogTest {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
 final Actor actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
 final TransactionRunner tx=mock(TransactionRunner.class);
 final MeetingAccess access=mock(MeetingAccess.class);
 Task12CategoryCatalogTest() throws Exception {
  when(tx.required(any())).thenAnswer(i->((java.util.concurrent.Callable<?>)i.getArgument(0)).call());
  when(access.tryLockIdentities(eq(actor),eq(List.of()),eq(false))).thenReturn(Optional.of(mock(MeetingAccess.LockedIdentities.class)));
 }
 @Test void everyPageRefreshesIdentityAndUsesSameDictionaryWithBoundCursor() throws Exception {
  var dictionary=load(List.of(new CategoryDictionary.Category("a","A"),new CategoryDictionary.Category("b","B"),new CategoryDictionary.Category("c","C")));
  var reader=new JdbcCategoryCatalog(tx,access,dictionary,new WorkspaceCursor());
  var first=reader.read(actor,2,null);assertEquals(List.of("a","b"),first.items().stream().map(ama.contract.WorkspaceContracts.CategoryItem::id).toList());assertTrue(first.hasMore());
  var last=reader.read(actor,2,first.nextCursor());assertEquals(List.of("c"),last.items().stream().map(ama.contract.WorkspaceContracts.CategoryItem::id).toList());assertFalse(last.hasMore());assertNull(last.nextCursor());
  verify(access,times(2)).tryLockIdentities(actor,List.of(),false);
  when(access.tryLockIdentities(actor,List.of(),false)).thenReturn(Optional.empty());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->reader.read(actor,2,first.nextCursor()));
 }
 @Test void cursorCannotCrossImmutableDictionaryVersions()throws Exception {
  var cursors=new WorkspaceCursor();var first=new JdbcCategoryCatalog(tx,access,load(List.of(new CategoryDictionary.Category("a","A"),new CategoryDictionary.Category("b","B"))),cursors).read(actor,1,null);
  var changed=new JdbcCategoryCatalog(tx,access,load(List.of(new CategoryDictionary.Category("a","changed"),new CategoryDictionary.Category("b","B"))),cursors);
  assertThrows(IllegalArgumentException.class,()->changed.read(actor,1,first.nextCursor()));
 }
 @Test void authorizationPrecedesDictionaryAndUnavailableCatalogDoesNotBecomeEmptySuccess() {
  var dictionary=mock(CategoryDictionary.class);when(access.tryLockIdentities(actor,List.of(),false)).thenReturn(Optional.empty());
  var reader=new JdbcCategoryCatalog(tx,access,dictionary,new WorkspaceCursor());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->reader.read(actor,20,null));verifyNoInteractions(dictionary);
  when(access.tryLockIdentities(actor,List.of(),false)).thenReturn(Optional.of(mock(MeetingAccess.LockedIdentities.class)));
  CategoryDictionary uninstalled=id->{};
  assertThrows(ama.contract.MeetingContracts.CategoryUnavailable.class,()->new JdbcCategoryCatalog(tx,access,uninstalled,new WorkspaceCursor()).read(actor,20,null));
 }
 CategoryDictionary load(List<CategoryDictionary.Category> entries)throws Exception {
  var file=temporary.resolve(UUID.randomUUID()+".categories");try(var out=new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(file))){out.writeInt(0x414d4143);out.writeByte(1);out.writeInt(entries.size());for(var e:entries)for(String v:List.of(e.id(),e.label())){byte[] bytes=v.getBytes(java.nio.charset.StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}}
  String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));return CategoryDictionary.load(file,digest,new WorkspaceReadBudget());
 }
}
