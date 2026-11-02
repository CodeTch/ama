package ama.mail;
public final class MailService {
 public static MailPort.Outcome attempt(MailPort.Request request,MailPort port,boolean firstAttempt){try{var outcome=firstAttempt?port.send(request):port.findByBusinessKey(request.businessKey());if(outcome!=null&&(outcome.state()==MailPort.State.SENT||outcome.state()==MailPort.State.FAILED))return outcome;}catch(RuntimeException unavailable){/* The remote effect is unknown. Only query may follow. */}return new MailPort.Outcome(MailPort.State.UNKNOWN,null);}
}
