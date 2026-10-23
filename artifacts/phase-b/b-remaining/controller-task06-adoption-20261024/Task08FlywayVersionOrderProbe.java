import org.flywaydb.core.api.MigrationVersion;
class Task08FlywayVersionOrderProbe {
 public static void main(String[] args) {
  for(String x:args) System.out.println("version|"+x+"|"+MigrationVersion.fromVersion(x).getVersion());
  System.out.println("equalLeadingZero|"+MigrationVersion.fromVersion("013.1").equals(MigrationVersion.fromVersion("13.1")));
  System.out.println("candidateAfterCurrent|"+MigrationVersion.fromVersion("013.2").compareTo(MigrationVersion.fromVersion("013.1")));
  System.out.println("candidateBeforeReserved014|"+MigrationVersion.fromVersion("013.2").compareTo(MigrationVersion.fromVersion("014")));
  System.out.println("nextIntegerAfterReserved027|"+MigrationVersion.fromVersion("028").compareTo(MigrationVersion.fromVersion("027")));
  System.out.println("maxHeap|"+Runtime.getRuntime().maxMemory());
  System.out.println("processors|"+Runtime.getRuntime().availableProcessors());
 }
}
