import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.resource.ResourceNameParser;

/** Offline actual Flyway API/parser; never loads or opens a database. */
class FlywayVersionProbe {
    public static void main(String[] args) {
        var first=MigrationVersion.fromVersion("011");
        var correction=MigrationVersion.fromVersion("011.1");
        var next=MigrationVersion.fromVersion("012");
        if(!(first.compareTo(correction)<0 && correction.compareTo(next)<0)) throw new AssertionError("bad order");
        var parsed=new ResourceNameParser(Flyway.configure()).parse("V011_1__transcript_material_index.sql");
        if(!parsed.isValid() || !correction.equals(parsed.getVersion())) throw new AssertionError(parsed.getValidityMessage());
        System.out.println("{\"offline\":true,\"ordering\":\"011<011.1<012\",\"filename\":\""+parsed.getFilename()+"\",\"parsed_version\":\""+parsed.getVersion()+"\",\"valid\":"+parsed.isValid()+",\"jar_source\":\""+MigrationVersion.class.getProtectionDomain().getCodeSource().getLocation()+"\"}");
    }
}
