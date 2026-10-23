from pathlib import Path
p=Path('artifacts/phase-b/b-remaining/task-04/run.py');s=p.read_text().replace("env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2'", "env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2';env['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2'")
s=s.replace("for n in ['local-fixtures','registry-fixtures','registry-restart']", "for n in ['local-fixtures','registry-fixtures','registry-restart']")
s=s.replace("'ArchiveRestartProbe' in a or", "'surefirebooter' in a or 'maven.multiModule' in a or 'ArchiveRestartProbe' in a or")
s=s.replace("created=[]\n", "created=[]\n")
p.write_text(s)
s=Path('backend/integration-tests/src/test/java/ama/storage/LocalFixture.java').read_text();a=s.index('        run(\n',s.index('    static synchronized void ensurePki()'));b=s.index('\n    static void run(',a);body=s[a:b].rstrip();assert body.endswith('}');body=body[:-1]
new='''package ama.storage;
import java.nio.file.*;
import java.util.*;
import static ama.storage.LocalFixture.*;
/** Exact new UUID custody, using the already inspected PKI commands on only the new directory. */
final class Task04Pki {
 static void create() throws Exception {
  Path parent=Path.of("target/local-fixtures").toAbsolutePath().normalize();Files.createDirectories(parent);
  Path fresh=parent.resolve("task04-"+UUID.randomUUID());
  if(!fresh.startsWith(parent)||Files.exists(fresh))throw new IllegalStateException("Task4 PKI custody collision");
  Files.createDirectory(fresh);privateMode(fresh,true);pki=fresh;
'''+body+'\n }\n}\n';Path('backend/integration-tests/src/test/java/ama/storage/Task04Pki.java').write_text(new)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text().replace('        LocalFixture.pki=null;','        Task04Pki.create();').replace('" archive="+archive+"\\n"','" archive="+archive+" pki="+LocalFixture.pki+" javaMaxMemory="+Runtime.getRuntime().maxMemory()+" processors="+Runtime.getRuntime().availableProcessors()+"\\n"');p.write_text(s)
