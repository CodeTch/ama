package ama.localdev;

import ama.minutes.ControlledMinutesAdmin;
import ama.minutes.ControlledMinutesDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Explicit local administration, using existing immutable template/archive/model installation. */
public final class LocalMinutesSetup {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("DATABASE_URL");
        if (!"1".equals(System.getenv("AMA_LOCAL_DEV")) || args.length!=1 || url==null
            || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"))
            throw new IllegalArgumentException("Explicit isolated local minutes setup required");
        Path configuration=Path.of(args[0]).toRealPath();
        var source=ControlledMinutesDataSource.create(url,System.getenv("DATABASE_USERNAME"),System.getenv("DATABASE_PASSWORD"));
        var installed=ControlledMinutesAdmin.install(source,configuration);
        var values=new TreeMap<String,String>();
        var properties=new Properties();
        try(var in=Files.newInputStream(configuration.resolveSibling("minutes.properties"))) { properties.load(in); }
        properties.forEach((key,value)->values.put(key.toString(),value.toString()));
        values.put("processing.archive-root",configuration.resolveSibling("archive").toString());
        values.put("processing.index-sha",installed.indexSha());
        values.put("processing.config-version",installed.processingVersion());
        values.put("processing.config-sha",installed.processingSha());
        System.out.println(new ObjectMapper().writeValueAsString(values));
    }
}
