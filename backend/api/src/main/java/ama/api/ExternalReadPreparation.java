package ama.api;

import ama.contract.IdentityContracts.ResolvedSession;
import ama.meeting.JdbcMeetingAccess;
import jakarta.servlet.http.*;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

/** Route metadata only; controllers and registries still enforce their complete transaction gates. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="publication.governance-enabled",havingValue="true")
class ExternalReadPreparation implements WebMvcConfigurer,HandlerInterceptor {
 private final JdbcMeetingAccess access;private final JdbcTemplate j;
 ExternalReadPreparation(JdbcMeetingAccess access,DataSource source,ama.transcript.JdbcMaterialAuthority installation){this.access=access;j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this).addPathPatterns("/api/v1/**");}
 public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  if(!(request.getAttribute(SessionSecurityFilter.SESSION) instanceof ResolvedSession session))return true;
  String path=request.getRequestURI();
  // The formal snapshot has its own current library grant and terminal-state capture.
  // Keep all meeting/source reads on the existing meeting READ preparation.
  if(handler instanceof org.springframework.web.method.HandlerMethod method && method.getBeanType()==PublicationDocumentController.class && method.getMethod().getName().equals("document"))return true;
  // Governance and deletion expose technical recovery metadata under independent owner gates.
  if(handler instanceof org.springframework.web.method.HandlerMethod method && Set.of(GovernanceController.class,MeetingDeletionController.class,KnowledgeGovernanceEventController.class,OperationsController.class).contains(method.getBeanType()))return true;
  // Discard authorizes its own exact receipt replay even after the meeting is deleted.
  if(request.getMethod().equals("POST") && handler instanceof org.springframework.web.method.HandlerMethod method
      && method.getBeanType()==ImportDraftController.class && method.getMethod().getName().equals("discard"))return true;
  if(request.getMethod().equals("DELETE")&&path.matches("/api/v1/meetings/[0-9a-f-]{36}"))return true;
  String meeting=null;var matcher=java.util.regex.Pattern.compile("^/api/v1/meetings/([0-9a-f-]{36})(?:/.*)?$").matcher(path);
  if(matcher.matches())meeting=matcher.group(1);
  else if(path.startsWith("/api/v1/material-libraries"))meeting=request.getParameter("meetingId");
  else {
   String sql=null;var resource=java.util.regex.Pattern.compile("^/api/v1/(exports|uploads|recordings|files)/([0-9a-f-]{36})(?:/.*)?$").matcher(path);
   if(resource.matches()){
    sql=switch(resource.group(1)){case "exports"->"SELECT subject_id FROM durable_task WHERE id=? AND subject_kind='MEETING'";case "uploads"->"SELECT meeting_id FROM upload_session WHERE id=?";case "recordings"->"SELECT meeting_id FROM recording_session WHERE session_id=?";case "files"->"SELECT meeting_id FROM file_object WHERE id=?";default->null;};
    if(sql!=null){var rows=j.queryForList(sql,String.class,resource.group(2));if(rows.size()==1)meeting=rows.getFirst();}
   }
  }
  if(meeting!=null){ama.contract.MinutesContracts.uuid(meeting);access.prepareExternalRead(session.actor(),meeting);}return true;
 }
}
