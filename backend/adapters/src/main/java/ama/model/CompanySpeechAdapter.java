package ama.model;

import ama.contract.SpeechContracts;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.util.function.Supplier;

/** Bounded company speech adapter; transport/authentication stays behind the supplied exchange. */
public final class CompanySpeechAdapter implements SpeechModelPort {
    private final SpeechModelPort exchange;
    private final int maxAttempts; private final Duration deadline;
    public static class RateLimited extends RuntimeException { public RateLimited() { super("Speech provider rate limited"); } }
    public static class TimedOut extends RuntimeException { public TimedOut() { super("Speech provider timed out"); } }
    public CompanySpeechAdapter(SpeechModelPort exchange) { this(exchange, 2, Duration.ofSeconds(30)); }
    public CompanySpeechAdapter(SpeechModelPort exchange, int maxAttempts, Duration deadline) { this.exchange = Objects.requireNonNull(exchange, "exchange"); if (maxAttempts < 1 || deadline == null || deadline.isZero() || deadline.isNegative()) throw new IllegalArgumentException("Invalid speech provider limits"); this.maxAttempts = maxAttempts; this.deadline = deadline; }
    public List<SpeechSpan> vad(SpeechContracts.WavClip clip) { return List.copyOf(call(() -> exchange.vad(clip))); }
    public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip clip, List<SpeechSpan> speech) { return List.copyOf(call(() -> exchange.diarize(clip, List.copyOf(speech)))); }
    public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip clip) { return Objects.requireNonNull(call(() -> exchange.recognize(clip))); }
    public String punctuate(String text) { return Objects.requireNonNull(call(() -> exchange.punctuate(text))); }
    public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip clip) { return Objects.requireNonNull(call(() -> exchange.embed(clip))); }
    private <T> T call(Supplier<T> operation) { long deadlineNanos=System.nanoTime()+deadline.toNanos(); RateLimited limited=null; for(int attempt=1;attempt<=maxAttempts;attempt++){if(System.nanoTime()>deadlineNanos)throw new TimedOut();try{return operation.get();}catch(RateLimited e){limited=e;}}throw limited==null?new TimedOut():limited; }
}
