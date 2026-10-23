from pathlib import Path
p=Path('backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java');s=p.read_text();needle='    Backend requireHistoricalMetadata(String id) {';assert needle in s;s=s.replace(needle,'''    /** Safe route observation only; it grants no source, byte or new-write authority. */
    public String historicalStatus(String id) { return requireHistoricalMetadata(id).status(); }

'''+needle);p.write_text(s)
p=Path('backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java');s=p.read_text().replace('Map<String,Object> budgets,BooleanSupplier available) {','Map<String,Object> budgets,BooleanSupplier available,long partSize) {').replace('this.available=available;','this.available=available;if(partSize<=0)throw new IllegalArgumentException("Positive part size required");this.partSize=partSize;')
a=s.index(' private long partSize=');b=s.index(' public Page parts(',a);s=s[:a]+' private final long partSize;\n'+s[b:];p.write_text(s)
p=Path('backend/api/src/main/java/ama/api/ProcessingConfiguration.java');s=p.read_text().replace('Map<String,Object> budgets) {}','Map<String,Object> budgets,java.util.function.BooleanSupplier available) {}').replace('throw unavailable();},Map.of());','throw unavailable();},Map.of(),()->false);')
s=s.replace('return new Runtime(components,prepare,Collections.unmodifiableMap(effective));','''return new Runtime(components,prepare,Collections.unmodifiableMap(effective),()->{
   try {
    var snapshot=new ControlledConfigArchiveSource().open(Path.of(env.getRequiredProperty("processing.archive-root")),env.getRequiredProperty("processing.index-sha"),indexBudget,budget,()->false);
    var ref=new Ref(ArtifactType.PROCESSING_CONFIG,env.getRequiredProperty("processing.config-version"),env.getRequiredProperty("processing.config-sha"));
    snapshot.loadClosure(List.of(ref),budget,0,()->false);backends.defaultWrite();
    return new org.springframework.jdbc.core.JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='input_batch_fingerprint_profile'",Long.class)==1;
   }catch(ama.contract.MinutesContracts.Failure|ama.contract.FileContracts.Unavailable|IllegalStateException failure){return false;}
  });''')
needle=' private static long n(Environment env,String key)'
s=s.replace(needle,''' @Bean JdbcProcessingDiscovery processingDiscovery(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,FileBackendCatalog backends,FileRegistry registry,Runtime runtime,Environment env) {
  return new JdbcProcessingDiscovery(source,tx,access,purposes,backends,runtime.components()==null?null:registry,runtime.budgets(),runtime.available(),env.getProperty("files.part-size",Long.class,8L*1024*1024));
 }
'''+needle);p.write_text(s)
