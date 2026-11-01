package ama.api;

import ama.access.MeetingAccess.AccessDeniedException;
import ama.contract.IdentityContracts.Actor;
import ama.storage.FileRegistry;
import ama.storage.RevocableInputStream;
import java.io.IOException;
import java.util.function.*;

/** Shared Range-preserving lifetime for all protected file/source routes. */
final class AuthorizedDownloads {
 private AuthorizedDownloads(){}
 static FileRegistry.Download guard(FileRegistry.Download initial,Supplier<Actor> identity,BiFunction<Actor,String,FileRegistry.Download> reopen)throws IOException{
  Supplier<java.time.Instant> validate=()->{
   try(var fresh=reopen.apply(identity.get(),initial.file().size()==0?null:"bytes=0-0")){
    if(!initial.file().equals(fresh.file()))throw new AccessDeniedException();
    return fresh.authorityDeadline();
   }catch(IOException unavailable){throw new AccessDeniedException();}
  };
  try{
   // Storage opening may have blocked after the original authorization transaction.
   var deadline=validate.get();if(!deadline.isAfter(java.time.Instant.now()))throw new AccessDeniedException();
   return new FileRegistry.Download(initial.file(),new RevocableInputStream(initial.body(),deadline,validate),initial.offset(),initial.length(),deadline);
  }catch(IOException|RuntimeException|Error failure){try{initial.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
 }
}
