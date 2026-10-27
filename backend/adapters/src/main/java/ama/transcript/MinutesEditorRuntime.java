package ama.transcript;
import ama.config.ConfigArchiveSource;import ama.contract.ConfigArchiveContracts.Ref;import java.util.Objects;import java.util.function.Supplier;
/** One immutable non-Spring assembly. Each service retains this exact genuine authority; no new-effect enable flag. */
public final class MinutesEditorRuntime {
 private final JdbcTranscriptAuthority authority;private final Supplier<ConfigArchiveSource.Snapshot> archives;private final String pin;private final Ref prompt,model;private final TranscriptAcquisitionBudget bounds;private final JdbcSavedTranscripts transcripts;
 private record Combined(JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcMinutesQueries projections,JdbcFinalConfirmations finals,JdbcPublicationService publications,JdbcPublicationLocalRecovery local){}
 private volatile Combined combined;
 private final JdbcMinutesSavedQueries queries;private final JdbcMinutesSavedCommands saved;private final JdbcMinutesReviews reviews;private final JdbcMinutesSettingsCommands settings;private final JdbcMinutesDecisions decisions;private final JdbcMinutesEditor editor;
 private MinutesEditorRuntime(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,Ref prompt,Ref model,TranscriptAcquisitionBudget bounds,JdbcSavedTranscripts transcripts){this.authority=Objects.requireNonNull(authority);this.archives=Objects.requireNonNull(archives);this.pin=pin;this.prompt=prompt;this.model=model;this.bounds=Objects.requireNonNull(bounds);this.transcripts=transcripts;authority.minutesDomain().configure(bounds);queries=new JdbcMinutesSavedQueries(authority,archives,pin,bounds,transcripts);saved=new JdbcMinutesSavedCommands(authority,archives,pin,bounds);reviews=new JdbcMinutesReviews(authority,archives,pin,bounds);settings=new JdbcMinutesSettingsCommands(authority,archives,pin,prompt,model,bounds);decisions=new JdbcMinutesDecisions(authority);editor=new JdbcMinutesEditor(authority);}
 public static MinutesEditorRuntime compose(JdbcTranscriptAuthority authority,Supplier<ConfigArchiveSource.Snapshot> archives,String pin,Ref prompt,Ref model,TranscriptAcquisitionBudget bounds,JdbcSavedTranscripts transcripts){return new MinutesEditorRuntime(authority,archives,pin,prompt,model,bounds,transcripts);}
 /** Admission is derived from these actual instances, never an enable flag or detached DTO. */
 public void requireCombined(JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcMinutesQueries projections){
  var installed=combined;if(installed==null||installed.initial()!=initial||installed.generation()!=generation||installed.projections()!=projections)throw new IllegalArgumentException("Incomplete minutes installation");
  requireBase(initial,generation,projections);
 }
 private void requireBase(JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcMinutesQueries projections){
  if(transcripts==null||initial==null||generation==null||projections==null)throw new IllegalArgumentException("Incomplete minutes installation");
  transcripts.requireCombinedAuthority(authority);authority.requireSharedPublicationGuard();initial.requireConfiguration(authority,archives,prompt,model,bounds);generation.requireConfiguration(authority,archives,pin,prompt,model,bounds);projections.requireConfiguration(authority,archives,pin,prompt,model,bounds);
 }
 public void requireCombined(JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcMinutesQueries projections,JdbcFinalConfirmations finals,JdbcPublicationService publications,JdbcPublicationLocalRecovery local){requireCombined(initial,generation,projections);var installed=combined;if(installed.finals()!=finals||installed.publications()!=publications||installed.local()!=local)throw new IllegalArgumentException("Foreign final/publication installation");}
 public synchronized void installReadiness(JdbcMinutesInitialSettings initial,JdbcMinutesGenerationCommands generation,JdbcMinutesQueries projections,JdbcFinalConfirmations finals,JdbcPublicationService publications,JdbcPublicationLocalRecovery local){
  if(combined!=null)throw new IllegalStateException("Combined installation already bound");requireBase(initial,generation,projections);
  if(finals==null||publications==null||local==null)throw new IllegalArgumentException("Complete final/publication installation required");
  finals.requireConfiguration(authority,archives,pin,bounds,transcripts);publications.requireConfiguration(authority,archives,pin,bounds,transcripts,local);generation.requireQueue(publications.queue);
  combined=new Combined(initial,generation,projections,finals,publications,local);projections.bindCombined(this,initial,generation);
 }
 public JdbcMinutesSavedQueries queries(){return queries;}public JdbcMinutesSavedCommands saved(){return saved;}public JdbcMinutesReviews reviews(){return reviews;}public JdbcMinutesSettingsCommands settings(){return settings;}public JdbcMinutesDecisions decisions(){return decisions;}public JdbcMinutesEditor editor(){return editor;}
}
