import org.flywaydb.core.api.MigrationVersion;
public final class FlywayStageOrder { public static void main(String[] args) { var a=MigrationVersion.fromVersion("13");var b=MigrationVersion.fromVersion("13.1");var c=MigrationVersion.fromVersion("14");if(!(a.compareTo(b)<0&&b.compareTo(c)<0))throw new AssertionError("ordering");System.out.println("13 < 13.1 < 14: PASS"); }}
