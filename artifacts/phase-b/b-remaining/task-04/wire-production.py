from pathlib import Path
p=Path('backend/api/src/main/java/ama/api/FileConfiguration.java');s=p.read_text().replace('FilePurposePolicy filePurposes() {','FilePurposePolicy filePurposes(Environment env) {\n        if(env.getProperty("processing.enabled",Boolean.class,false))return new ama.transcript.TranscriptFilePurposePolicy();')
s=s.replace('Map<String, FileReferencePolicy> references) {\n        return new JdbcFileRegistry','Map<String, FileReferencePolicy> references,ProcessingConfiguration.Runtime processing) {\n        if(processing.components()!=null)return processing.components().registry();\n        return new JdbcFileRegistry');p.write_text(s)
p=Path('backend/api/src/main/java/ama/api/MeetingConfiguration.java');s=p.read_text().replace('@Bean JdbcOutbox meetingOutbox(DataSource source){return new JdbcOutbox(source,new MeetingEventReferences(source).validators());}', '''@Bean JdbcOutbox meetingOutbox(DataSource source){
        var validators=new java.util.HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());
        new ama.transcript.TranscriptEventReferences(source).validators().forEach((key,value)->{if(validators.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate event validator");});
        return new JdbcOutbox(source,validators);
    }''');p.write_text(s)
p=Path('backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java');s=p.read_text().replace('app=new SpringApplicationBuilder(ApiApplication.class)', '''var bounds=new HashMap<String,Object>();
        String[] names={"artifact-bytes","archive-total-bytes","artifacts","dependencies","graph-depth","json-tokens","json-depth","name-utf16","string-utf16","string-utf8","aggregate-scalars","manifest-bytes","batch-files","fact-rows","fact-edges","canonical-bytes","canonical-depth","canonical-nodes","canonical-string-utf8","canonical-scalars","index-bytes","index-entries","release-bytes","index-generations","total-source-bytes"};
        long[] values={65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000,16777216,32,1000000,2097152,250000,4194304,4096,67108864,128,1048576};
        for(int n=0;n<names.length;n++)bounds.put("processing.bounds."+names[n],values[n]);
        app=new SpringApplicationBuilder(ApiApplication.class).properties(bounds)''');p.write_text(s)
