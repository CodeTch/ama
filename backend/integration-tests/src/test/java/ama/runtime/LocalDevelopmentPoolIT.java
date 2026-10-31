package ama.runtime;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual dev bootstrap factory in an isolated JVM; never opens the fixture DB URL. */
class LocalDevelopmentPoolIT {
 @TempDir Path root;
 @Test void configuredBoundedPoolSizeIsHonoredWithoutRelaxingConnectionTimeout()throws Exception {
  Path repo=Path.of("").toAbsolutePath();while(repo!=null&&!Files.exists(repo.resolve("tools/dev/local_app.py")))repo=repo.getParent();assertNotNull(repo);
  Path classes=Files.createDirectory(root.resolve("classes")),probe=root.resolve("LocalPoolProbe.java");
  Files.writeString(probe,"""
   package ama.localdev;
   public final class LocalPoolProbe {
    public static void main(String[] args)throws Exception {
     var env=new org.springframework.mock.env.MockEnvironment();
     if(!args[0].equals("default"))env.withProperty("spring.datasource.hikari.maximum-pool-size",args[0]);
     var method=java.util.Arrays.stream(LocalApplication.class.getDeclaredMethods()).filter(m->m.getName().equals("localDevelopmentDataSource")).findFirst().orElseThrow();method.setAccessible(true);
     try(var pool=(com.zaxxer.hikari.HikariDataSource)(method.getParameterCount()==0?method.invoke(new LocalApplication()):method.invoke(new LocalApplication(),env))){System.out.println("POOL="+pool.getMaximumPoolSize()+";TIMEOUT="+pool.getConnectionTimeout()+";IDLE="+pool.getMinimumIdle());}
    }
   }
   """);
  var inputs=new ArrayList<Path>();try(var paths=Files.walk(repo.resolve("tools/dev/java"))){inputs.addAll(paths.filter(p->p.toString().endsWith(".java")).toList());}inputs.add(probe);
  var compiler=ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);var errors=new StringWriter();
  try(var files=compiler.getStandardFileManager(null,null,null)){
   assertTrue(compiler.getTask(errors,files,null,List.of("--release","21","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()),null,files.getJavaFileObjectsFromPaths(inputs)).call(),errors.toString());
  }
  String cp=classes+File.pathSeparator+System.getProperty("java.class.path");
  assertEquals("POOL=32;TIMEOUT=1000;IDLE=0",launch(cp,"32",true));
  assertEquals("POOL=20;TIMEOUT=1000;IDLE=0",launch(cp,"default",true));
  launch(cp,"0",false);launch(cp,"65",false);
 }
 String launch(String cp,String selected,boolean valid)throws Exception {
  var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx96m","-cp",cp,"ama.localdev.LocalPoolProbe",selected).redirectErrorStream(true);
  builder.environment().put("AMA_LOCAL_DEV","1");builder.environment().put("DATABASE_URL","jdbc:mysql://127.0.0.1:9/ama_local_dev?connectTimeout=1000");builder.environment().put("DATABASE_USERNAME","pool-fixture");builder.environment().put("DATABASE_PASSWORD","pool-fixture");
  var process=builder.start();try{assertTrue(process.waitFor(15,TimeUnit.SECONDS));String output=new String(process.getInputStream().readNBytes(32768));if(valid){assertEquals(0,process.exitValue(),output);return output.lines().filter(line->line.startsWith("POOL=")).findFirst().orElseThrow();}assertNotEquals(0,process.exitValue(),"invalid local pool limit must fail startup");return "REJECTED";}finally{process.destroyForcibly();}
 }
}
