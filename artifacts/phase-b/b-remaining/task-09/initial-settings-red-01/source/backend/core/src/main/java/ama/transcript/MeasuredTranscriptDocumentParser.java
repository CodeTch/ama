package ama.transcript;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.BooleanSupplier;
/** Successful usage measured from the original bytes and all decoded input units. */
public interface MeasuredTranscriptDocumentParser extends TranscriptDocumentParser {
 record Usage(long sourceBytes,long decodedUtf16CodeUnits,int segments) {}
 record MeasuredResult(Result result,Usage usage) {}
 MeasuredResult parseMeasured(InputStream source,UUID fileId,String expectedSourceSha256,
     ParseBudget budget,BooleanSupplier cancelled) throws ParseException;
}
