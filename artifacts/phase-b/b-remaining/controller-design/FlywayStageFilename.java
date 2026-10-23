import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.resource.ResourceNameParser;
public final class FlywayStageFilename { public static void main(String[] args) { var a=MigrationVersion.fromVersion("013");var b=MigrationVersion.fromVersion("013.1");var c=MigrationVersion.fromVersion("014");if(!(a.compareTo(b)<0&&b.compareTo(c)<0))throw new AssertionError("order");var parsed=new ResourceNameParser(Flyway.configure()).parse("V013_1__b_runtime_metadata.sql");if(!parsed.isValid()||!b.equals(parsed.getVersion()))throw new AssertionError(parsed.getValidityMessage());System.out.println("VALID "+parsed.getFilename()+" version="+parsed.getVersion()+" order=013<013.1<014 database_connections=0"); }}
