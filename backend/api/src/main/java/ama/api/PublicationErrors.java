package ama.api;
import jakarta.servlet.http.HttpServletResponse;import java.io.IOException;import org.springframework.web.bind.annotation.*;
/** Publication failures remain on the business route, never a secondary /error dispatch. */
@RestControllerAdvice(assignableTypes={PublicationController.class,FinalConfirmationController.class,PublicationDiscoveryController.class,PublicationDocumentController.class})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class PublicationErrors {
 private void write(HttpServletResponse response,int status,String code)throws IOException{response.setHeader("Cache-Control","private, no-store");var privateResponse=new jakarta.servlet.http.HttpServletResponseWrapper(response){@Override public void setHeader(String name,String value){super.setHeader(name,name.equalsIgnoreCase("Cache-Control")?"private, no-store":value);}};BusinessErrors.write(privateResponse,status,code,"Publication request could not be completed");}
 @ExceptionHandler(ama.contract.TranscriptContracts.Failure.class) void explicit(ama.contract.TranscriptContracts.Failure failure,HttpServletResponse response)throws IOException{write(response,failure.status(),failure.code());}
 @ExceptionHandler(ama.contract.MinutesContracts.Failure.class) void domain(ama.contract.MinutesContracts.Failure failure,HttpServletResponse response)throws IOException{switch(failure.code()){case INVALID_ARGUMENT,INVALID_SCALAR,INVALID_ID->write(response,400,"INVALID_ARGUMENT");case BUDGET_EXCEEDED->write(response,413,"REQUEST_TOO_LARGE");default->write(response,503,"PUBLICATION_UNAVAILABLE");}}
 @ExceptionHandler(ama.access.MeetingAccess.AccessDeniedException.class) void denied(HttpServletResponse response)throws IOException{write(response,403,"MEETING_FORBIDDEN");}
 @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void encoding(HttpServletResponse response)throws IOException{write(response,503,"PUBLICATION_UNAVAILABLE");}
 @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) void invalidParameter(HttpServletResponse response)throws IOException{write(response,400,"INVALID_ARGUMENT");}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class) void database(HttpServletResponse response)throws IOException{write(response,503,"PUBLICATION_UNAVAILABLE");}
 @ExceptionHandler(org.springframework.transaction.TransactionException.class) void transaction(HttpServletResponse response)throws IOException{write(response,503,"PUBLICATION_UNAVAILABLE");}
 @ExceptionHandler(IOException.class) void providerIo(HttpServletResponse response)throws IOException{write(response,503,"PUBLICATION_UNAVAILABLE");}
 @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class) void saturated(HttpServletResponse response)throws IOException{write(response,503,"PUBLICATION_READ_UNAVAILABLE");}
 @ExceptionHandler(IllegalArgumentException.class) void invalid(HttpServletResponse response)throws IOException{write(response,400,"INVALID_ARGUMENT");}
}
