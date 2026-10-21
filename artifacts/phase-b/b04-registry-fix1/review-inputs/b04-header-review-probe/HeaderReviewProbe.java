
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;
import org.apache.catalina.startup.Tomcat;
import jakarta.servlet.http.*;
import ama.storage.*;
import ama.contract.IdentityContracts.*;
import ama.contract.StorageContracts.*;
class HeaderReviewProbe {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]);Path runtime=Files.createDirectory(root.resolve("tomcat"));
  Path docbase=Files.createDirectory(runtime.resolve("webroot"));
  var tomcat=new Tomcat();tomcat.setBaseDir(runtime.toString());tomcat.setPort(0);
  var connector=tomcat.getConnector();connector.setProperty("address","127.0.0.1");
  var context=tomcat.addContext("",docbase.toString());
  var registry=(FileRegistry)java.lang.reflect.Proxy.newProxyInstance(HeaderReviewProbe.class.getClassLoader(),new Class<?>[]{FileRegistry.class},(p,m,a)->{
   if(!m.getName().equals("download"))throw new UnsupportedOperationException();
   String name=a[1].equals("long")?"\u6587".repeat(1000):"original";
   new ama.contract.FileContracts.Begin("CONTROLLED_INPUT",name,3,null);
   var file=new FileRegistry.FileRecord("file","meeting",new StorageKey("controlled","files","key"),3,"a".repeat(64),"AVAILABLE",0,0,name);
   String range=(String)a[2];byte[] data=(range==null?"abc":"b").getBytes(java.nio.charset.StandardCharsets.UTF_8);
   return new FileRegistry.Download(file,new ByteArrayInputStream(data),range==null?0:1,data.length);
  });
  var type=Class.forName("ama.api.FileController");
  var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
  Object controller=ctor.newInstance(null,registry,new org.springframework.core.env.StandardEnvironment());
  var method=type.getDeclaredMethod("content",String.class,HttpServletRequest.class,HttpServletResponse.class);method.setAccessible(true);
  var filter=Class.forName("ama.api.SessionSecurityFilter");var key=filter.getDeclaredField("SESSION");key.setAccessible(true);
  var session=new ResolvedSession(new Actor("controlled-review",Status.ACTIVE,Set.of(Role.USER)),"controlled-csrf",Instant.now().plusSeconds(60));
  Tomcat.addServlet(context,"probe",new HttpServlet(){
   protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
    request.setAttribute((String)keyValue(),session);
    try {method.invoke(controller,request.getPathInfo()==null?request.getServletPath().substring(1):request.getPathInfo().substring(1),request,response);}
    catch(InvocationTargetException e){if(e.getCause() instanceof IOException x)throw x;throw new IOException(e.getCause());}
    catch(ReflectiveOperationException e){throw new IOException(e);}
   }
   Object keyValue(){try{return key.get(null);}catch(Exception e){throw new RuntimeException(e);}}
  });
  context.addServletMappingDecoded("/*","probe");
  try{
   tomcat.start();System.out.println("PORT="+connector.getLocalPort()+" responseHeaderLimit="+connector.getProperty("maxHttpResponseHeaderSize"));
   var client=HttpClient.newHttpClient();
   for(String name:List.of("normal","long")) for(boolean range:List.of(false,true)){
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+connector.getLocalPort()+"/"+name));
    if(range)request.header("Range","bytes=1-1");
    try{
     var response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
     System.out.println("RESULT name="+name+" range="+range+" status="+response.statusCode()+" bodyBytes="+response.body().length+" dispositionBytes="+response.headers().firstValue("Content-Disposition").orElse("").length());
    }catch(IOException e){System.out.println("RESULT name="+name+" range="+range+" IOFAIL="+e.getClass().getName()+":"+e.getMessage());}
   }
  } finally {tomcat.stop();tomcat.destroy();}
 }
}
