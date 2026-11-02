package ama.mail;
import java.util.*;
public interface MailPort {
 enum State { SENT,FAILED,UNKNOWN,NOT_FOUND,UNSUPPORTED_QUERY }
 record Request(String businessKey,List<String> recipients,String subject,String body){public Request{recipients=List.copyOf(recipients);}}
 record Outcome(State state,String messageId){}
 Outcome send(Request request);
 Outcome findByBusinessKey(String businessKey);
}
