package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.ProcessingContracts.*;
import ama.transcript.JdbcProcessingIntake;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController class ProcessingController {
    private final JdbcProcessingIntake intake;
    private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(256).maxNumberLength(19).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    ProcessingController(JdbcProcessingIntake intake) {
        this.intake=intake;
    }
    @PostMapping("/api/v1/meetings/{id}/transcript-imports") ResponseEntity<ImportAccepted> accept(@PathVariable String id,HttpServletRequest request)throws IOException {
        if(request.getContentLengthLong()>65536)throw new Failure(413,"REQUEST_TOO_LARGE");
        byte[] bytes;
        try(var stream=request.getInputStream()) {
            bytes=stream.readNBytes(65537);
        }
        if(bytes.length>65536)throw new Failure(413,"REQUEST_TOO_LARGE");
        try(var parser=json.getFactory().createParser(bytes)) {
            int tokens=0;
            while(parser.nextToken()!=null)if(++tokens>4096)throw new Failure(413,"REQUEST_TOO_LARGE");
        }
        catch(IOException invalid) {
            throw new IllegalArgumentException("Invalid JSON");
        }
        JsonNode body;
        try {
            body=json.readTree(bytes);
        }
        catch(IOException failure) {
            throw new IllegalArgumentException("Invalid processing request");
        }
        if(body==null||!body.isObject())throw new IllegalArgumentException("Object required");
        body.fieldNames().forEachRemaining(field-> {
            if(!Set.of("expectedRevision","expectedRevisionToken","orderedFileIds").contains(field))throw new IllegalArgumentException("Unknown field");
        });
        var files=body.get("orderedFileIds");
        if(files==null||!files.isArray())throw new IllegalArgumentException("Ordered files required");
        if(files.size()>64)throw new Failure(413,"REQUEST_TOO_LARGE");
        var ids=new ArrayList<String>();
        for(var value:files) {
            if(!value.isTextual())throw new IllegalArgumentException("File UUID required");
            ids.add(value.textValue());
        }
        var keys=Collections.list(request.getHeaders("Idempotency-Key"));
        if(keys.size()!=1)throw new IllegalArgumentException("Exactly one idempotency key required");
        var accepted=intake.accept(((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor(),id,keys.getFirst(),new ImportRequest(HttpRevisionTokens.expected(body),ids),UUID.randomUUID().toString());
        return ResponseEntity.accepted().location(URI.create("/api/v1/meetings/"+id+"/processing-inputs/"+accepted.inputId())).header("Cache-Control","private, no-store").body(accepted);
    }
}
