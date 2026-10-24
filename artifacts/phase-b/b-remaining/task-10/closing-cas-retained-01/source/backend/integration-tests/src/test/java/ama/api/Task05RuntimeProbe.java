package ama.api;
/** Test access to the actual single installed assembly, never an alternate authority. */
public final class Task05RuntimeProbe {
 private Task05RuntimeProbe(){}
 public static ama.transcript.JdbcTranscriptAuthority.Components components(org.springframework.context.ApplicationContext context){return context.getBean(ProcessingConfiguration.Runtime.class).components();}
}
