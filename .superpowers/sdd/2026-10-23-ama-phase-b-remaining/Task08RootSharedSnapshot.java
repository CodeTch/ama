import java.nio.file.*;
public class Task08RootSharedSnapshot {
 public static void main(String[] a)throws Exception {
  if(a.length!=1||!"jdbc:mysql://127.0.0.1:46653/ama_b01_fixture".equals(System.getenv("AMA_TEST_DATABASE_URL")))throw new AssertionError("Exact retained shared target only");
  Path p=Path.of(a[0]);if(Files.exists(p))throw new AssertionError("New output only");
  Task08MigrationProbe.JSON.writerWithDefaultPrettyPrinter().writeValue(p.toFile(),Task08MigrationProbe.capture());
 }
}
